package com.deepseek.dsh.runtime.host

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * App 侧的插件安装兜底。
 *
 * Host 内的 pnpm 在 proot 下会拿到 `ENOSYS`（mkdir/rename/unlink 都不可用，
 * 实测；见 `BridgePluginInstaller` 的同类说明），所以「添加插件」不能只靠
 * Host。这里用 App 进程自己的文件与网络能力完成同一件事：
 *
 * 1. 解析 spec（GitHub 仓库 / npm 包名 / rootfs 内绝对目录）；
 * 2. 取包内容（GitHub 源码归档、npm tarball 或本地目录）；
 * 3. 递归安装运行时依赖（只做 `dependencies`，peer 由运行时提供）；
 * 4. 铺进 profile 的 `node_modules`；
 * 5. 把包自带的 `cordis.patch.yml` 挂进 profile 的用户 patch 层。
 *
 * 装完需要重启 Host 才会加载新插件；调用方负责这一步。
 */
class PluginBundleInstaller(
    private val context: Context,
    private val layout: RuntimeLayout,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val profileDir: File
        get() = File(layout.rootfsDir, "root/.dsh/profiles/web")
    private val nodeModules: File
        get() = File(profileDir, "node_modules")

    /**
     * 安装一个插件 spec。
     * @param rawSpec 对话框输入的原始 spec。
     * @param onProgress 进度与步骤文案。
     * @return 成功时给出 `包名@版本`。
     */
    suspend fun install(rawSpec: String, onProgress: (Float, String) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            val spec = PluginInstallSpec.parse(rawSpec)
                ?: return@withContext Result.failure(
                    IllegalStateException("无法识别的插件地址：$rawSpec")
                )
            if (!profileDir.isDirectory) {
                return@withContext Result.failure(
                    IllegalStateException("Host profile 尚未初始化：请先启动一次 Host")
                )
            }
            ensurePresetLink()
            onProgress(0.02f, "解析插件地址…")
            val staged = try {
                when (spec) {
                    is PluginInstallSpec.GitHub -> stageFromGitHub(spec, onProgress)
                    is PluginInstallSpec.Registry -> stageFromRegistry(spec.name, spec.range, onProgress)
                    is PluginInstallSpec.LocalPath -> stageFromLocalPath(spec.path, onProgress)
                }
            } catch (error: Exception) {
                return@withContext Result.failure(
                    IllegalStateException("获取插件包失败：${error.message}")
                )
            }
            staged.use { packageDir ->
                val manifest = readManifest(packageDir.dir)
                    ?: return@withContext Result.failure(
                        IllegalStateException("插件包缺少可读的 package.json")
                    )
                val name = manifest.name?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(
                        IllegalStateException("插件 package.json 没有包名")
                    )
                val version = manifest.version.orEmpty()
                if (manifest.patch.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("$name 没有声明 dsh.bundle.patch，不能作为插件安装")
                    )
                }
                onProgress(0.55f, "安装运行时依赖…")
                val missing = installDependencies(packageDir.dir, manifest, onProgress)
                if (missing.isNotEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("依赖安装失败：${missing.joinToString()}")
                    )
                }
                onProgress(0.8f, "写入 profile…")
                val target = File(nodeModules, name)
                target.parentFile?.mkdirs()
                target.deleteRecursively()
                if (!packageDir.dir.renameTo(target)) {
                    // 不同文件系统时退回整树复制。
                    packageDir.dir.copyRecursively(target, overwrite = true)
                    packageDir.dir.deleteRecursively()
                }
                val patchText = File(target, manifest.patch).takeIf { it.isFile }?.readText()
                if (patchText.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("$name 的 bundle patch 不可读")
                    )
                }
                mountWebAppPatch(name, patchText)
                onProgress(1f, "已安装 $name@$version")
                Result.success(if (version.isBlank()) name else "$name@$version")
            }
        }

    /**
     * 列出 App 安装器挂载过的包名（读 web-app patch 里的 `android-installed` 标记）。
     *
     * 用来区分「随安装自带」的官方插件和用户后来通过「添加插件」装进来的官方包。
     *
     * @returns 包名集合；没有标记时为空。
     */
    fun managedPackages(): Set<String> {
        val patchFile = webAppPatchFile()
        if (!patchFile.isFile) return emptySet()
        val marker = "# android-installed: "
        return patchFile.readLines()
            .mapNotNull { line ->
                line.trim().takeIf { it.startsWith(marker) }?.removePrefix(marker)?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }

    /**
     * 持久化一条 Loader 行的启停状态。
     *
     * Host 的 `setPluginEnabled` 把 `disabled` 覆盖写进 profile patch；而这台设备
     * 每次启动都会重建 profile patch，覆盖会丢。这里再写一份到 web-app patch，
     * 重启后仍然生效；开启时删除这份覆盖。
     *
     * @param patchId Loader 行的 patch id（entryId 去掉 `include:` 前缀）。
     * @param enabled 目标状态。
     */
    suspend fun setRowEnabled(patchId: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val patchFile = webAppPatchFile()
            if (!patchFile.isFile) return@runCatching
            val marker = "# android-row-override: $patchId"
            var text = patchFile.readText()
            if (text.contains(marker)) {
                text = removeMarkerBlock(text, marker)
            }
            if (!enabled) {
                text = text.trimEnd() + "\n\n$marker\n- id: $patchId\n  disabled: true\n"
            }
            patchFile.writeText(text)
        }
    }

    /**
     * 摘掉 web-app patch 里 `# android-installed: <包名>` 的整块挂载。
     *
     * 旧实现只删 `name:` 行、留一个 `- id:` 占位；Loader 仍会创建这一行，
     * 而包已经不存在 → Web 客户端启动时该 entry 激活失败，整个浏览器界面停在
     * 「Failed to load plugins」。行插件（没有 bundle 记录）卸载后正是这条路。
     * 现在整块摘掉：没有 id、没有占位，Loader 也就不会再创建它。
     *
     * @param packageName 安装时写入的包名。
     * @returns 是否改动了 patch（摘掉了挂载块或其中的 name 行）。
     */
    suspend fun stripRowMountName(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val patchFile = webAppPatchFile()
        if (!patchFile.isFile) return@withContext false
        val marker = "# android-installed: $packageName"
        var changed = false
        runCatching {
            val text = patchFile.readText()
            if (!text.contains(marker)) return@runCatching
            val lines = text.lines()
            val start = lines.indexOfFirst { it.trim() == marker }
            if (start < 0) return@runCatching
            val end = lines.withIndex()
                .firstOrNull { (index, line) -> index > start && line.startsWith("# android-") }
                ?.index ?: lines.size
            val next = (lines.subList(0, start) + lines.subList(end, lines.size)).toMutableList()
            while (next.isNotEmpty() && next.last().isBlank()) next.removeAt(next.lastIndex)
            patchFile.writeText(next.joinToString("\n") + "\n")
            changed = true
        }
        changed
    }

    /**
     * 卸载一个由 App 侧安装的插件。
     *
     * 行插件没有 bundle 记录，Host 的 `removeBundle` 不适用；这里做安装的逆操作：
     * 删掉 profile 里的包目录，并摘掉 web-app patch 里的挂载块。调用方随后重启
     * Host 才会真正少掉这一条 Loader 行。
     *
     * @param packageName 安装时写入的包名。
     * @return 删除结果；目录不存在也视为成功（幂等）。
     */
    suspend fun uninstall(packageName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(nodeModules, packageName)
            if (target.exists() && !target.deleteRecursively()) {
                error("删除插件目录失败：$packageName")
            }
            val patchFile = webAppPatchFile()
            if (patchFile.isFile) {
                val marker = "# android-installed: $packageName"
                val text = patchFile.readText()
                if (text.contains(marker)) {
                    patchFile.writeText(removeMarkerBlock(text, marker))
                }
            }
        }
    }

    /** 摘掉 [removeMarkerBlock] 使用的挂载块头：从标记行到下一个标记行（或文件尾）。 */
    private fun removeMarkerBlock(text: String, marker: String): String {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.trim() == marker }
        if (start < 0) return text
        val end = lines.withIndex()
            .firstOrNull { (index, line) -> index > start && line.startsWith("# android-") }
            ?.index ?: lines.size
        val kept = (lines.subList(0, start) + lines.subList(end, lines.size))
            .toMutableList()
        while (kept.isNotEmpty() && kept.last().isBlank()) kept.removeAt(kept.lastIndex)
        return kept.joinToString("\n") + "\n"
    }

    // ── 包来源 ────────────────────────────────────────────────────────────

    /** GitHub 源码归档；tag/分支由 `#ref` 指定，缺省用 HEAD。 */
    private fun stageFromGitHub(
        spec: PluginInstallSpec.GitHub,
        onProgress: (Float, String) -> Unit,
    ): StagedPackage {
        val refSegment = spec.ref?.let { "refs/tags/$it" } ?: "HEAD"
        val url = "https://codeload.github.com/${spec.owner}/${spec.repo}/zip/$refSegment"
        onProgress(0.05f, "下载 GitHub 归档…")
        val bytes = download(url)
        onProgress(0.3f, "解压插件…")
        val source = extractZipToStaging(bytes)
        // GitHub 归档是源码仓库：构建产物（lib/）通常不在里面。能在 npm 上
        // 找到同一版本时，用已构建的 tarball 替换源码目录 —— 否则装上去的
        // 插件没有入口文件，Host 会直接报 import 失败。
        val manifest = readManifest(source.dir) ?: return source
        val name = manifest.name
        val version = manifest.version
        if (name != null && !File(source.dir, manifest.main).isFile) {
            val published = if (version != null) {
                findPublishedTarball(name, version)
            } else {
                null
            }
            if (published != null) {
                onProgress(0.35f, "下载已构建的 $name@$version…")
                source.close()
                return extractTarGzToStaging(download(published))
            }
            source.close()
            throw IllegalStateException(
                "$name 的源码归档里没有构建产物（${manifest.main}），且 npm 上没有对应版本；" +
                    "请先用 npm 包名安装，或安装已发布的版本"
            )
        }
        return source
    }

    /** 查一个包某个版本的 tarball 地址；没有该版本时返回 null。 */
    private fun findPublishedTarball(name: String, version: String): String? {
        val metadata = fetchRegistryMetadata(name) ?: return null
        return metadata["versions"]?.jsonObjectOrNull()
            ?.get(version)?.jsonObjectOrNull()
            ?.get("dist")?.jsonObjectOrNull()
            ?.get("tarball")?.jsonPrimitive?.contentOrNull
    }

    /** npm registry：拿 metadata 定版本，再下 tarball。 */
    private fun stageFromRegistry(
        name: String,
        range: String?,
        onProgress: (Float, String) -> Unit,
    ): StagedPackage {
        onProgress(0.05f, "查询 $name 版本…")
        val metadata = fetchRegistryMetadata(name)
            ?: throw IllegalStateException("registry 上没有 $name")
        val versions = metadata["versions"]?.jsonObjectOrNull()?.keys.orEmpty()
        val version = Semver.maxSatisfying(versions, range)
            ?: throw IllegalStateException("$name 没有满足 ${range ?: "latest"} 的版本")
        val tarball = metadata["versions"]?.jsonObjectOrNull()
            ?.get(version)?.jsonObjectOrNull()
            ?.get("dist")?.jsonObjectOrNull()
            ?.get("tarball")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("$name@$version 没有 tarball 地址")
        onProgress(0.1f, "下载 $name@$version…")
        val bytes = download(tarball)
        onProgress(0.35f, "解压 $name@$version…")
        return extractTarGzToStaging(bytes)
    }

    /** rootfs 内的本地目录（映射 App 可见路径）。 */
    private fun stageFromLocalPath(
        path: String,
        onProgress: (Float, String) -> Unit,
    ): StagedPackage {
        val source = resolveLocalPath(path)
            ?: throw IllegalStateException("本地路径不在 App 可访问范围内：$path")
        if (!File(source, "package.json").isFile) {
            throw IllegalStateException("本地目录缺少 package.json：$path")
        }
        onProgress(0.3f, "复制本地插件目录…")
        val staging = StagedPackage(newStagingDir())
        source.copyRecursively(staging.dir, overwrite = true)
        return staging
    }

    /** 把 rootfs 内的绝对路径映射到 App 可直接访问的目录。 */
    private fun resolveLocalPath(path: String): File? {
        val mappings = listOf(
            "/root/.dsh" to layout.dshHomeDir,
            "/cache" to layout.cacheDir,
            "/workspace" to File(layout.rootfsDir, "workspace"),
            "/tmp" to File(layout.rootfsDir, "tmp"),
        )
        for ((prefix, appDir) in mappings) {
            if (path == prefix || path.startsWith("$prefix/")) {
                return File(appDir, path.removePrefix(prefix).trimStart('/'))
            }
        }
        if (path.startsWith(layout.rootfsDir.absolutePath)) return File(path)
        return null
    }

    // ── 依赖 ──────────────────────────────────────────────────────────────

    /**
     * 递归安装 `dependencies` 到包自己的 `node_modules`。
     *
     * peer 依赖交给运行时（profile 的 `@deepseek-ai` 链接），可选依赖失败不阻断。
     * @return 失败的依赖名；空表示全部装好。
     */
    private fun installDependencies(
        packageDir: File,
        manifest: PackageManifest,
        onProgress: (Float, String) -> Unit,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 0,
    ): List<String> {
        if (depth > 6) return emptyList()
        val failures = mutableListOf<String>()
        val dependencies = manifest.dependencies
        var index = 0
        for ((name, range) in dependencies) {
            index += 1
            if (!visited.add(name)) continue
            val installDir = File(packageDir, "node_modules/$name")
            if (installDir.isDirectory) continue
            if (resolvableElsewhere(name)) continue
            onProgress(
                (0.55f + (index.toFloat() / (dependencies.size + 1)) * 0.2f).coerceAtMost(0.78f),
                "安装依赖 $name…",
            )
            val result = runCatching { installOneDependency(packageDir, name, range, onProgress, visited, depth) }
            result.exceptionOrNull()?.let { failures.add("$name(${it.message})") }
        }
        return failures
    }

    /** 从 registry 装一个依赖（含它的传递依赖）。 */
    private fun installOneDependency(
        parentDir: File,
        name: String,
        range: String,
        onProgress: (Float, String) -> Unit,
        visited: MutableSet<String>,
        depth: Int,
    ) {
        val metadata = fetchRegistryMetadata(name)
            ?: throw IllegalStateException("registry 上没有 $name")
        val versions = metadata["versions"]?.jsonObjectOrNull()?.keys.orEmpty()
        val version = Semver.maxSatisfying(versions, range)
            ?: throw IllegalStateException("没有满足 $range 的版本")
        val tarball = metadata["versions"]?.jsonObjectOrNull()
            ?.get(version)?.jsonObjectOrNull()
            ?.get("dist")?.jsonObjectOrNull()
            ?.get("tarball")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalStateException("没有 tarball 地址")
        val bytes = download(tarball)
        val staging = extractTarGzToStaging(bytes)
        staging.use { extracted ->
            val destination = File(parentDir, "node_modules/$name")
            destination.parentFile?.mkdirs()
            destination.deleteRecursively()
            if (!extracted.dir.renameTo(destination)) {
                extracted.dir.copyRecursively(destination, overwrite = true)
                extracted.dir.deleteRecursively()
            }
            val nested = readManifest(destination) ?: return@use
            installDependencies(destination, nested, onProgress, visited, depth + 1)
        }
    }

    /**
     * 依赖是否已经能被 Node 从插件目录解析到。
     *
     * 只有 profile 的 `node_modules` 算数：`@deepseek-ai/<name>` 通过 profile 里的
     * 链接解析，其余包必须真的在解析路径上。DSH 安装目录自己嵌套的依赖不在
     * 插件的解析链里，不能算作已提供。
     */
    private fun resolvableElsewhere(name: String): Boolean =
        File(nodeModules, name).exists()

    // ── Host 挂载 ─────────────────────────────────────────────────────────

    /**
     * 把插件的 bundle patch 追加进 `dsh-web-app` 的 patch 层。
     *
     * 为什么不是 profile 的用户 patch 层：这台设备上 Host 每次启动都会重建
     * profile 的 `package.json`/`cordis.patch.yml`（实测，启动日志里能抓到
     * `initProfile` 被调用），写进 profile 的挂载下一轮启动就没了。web-app
     * 自己的 patch 属于「随安装发布的状态」，不受那次重建影响 —— bridge 插件
     * 用的也是这一层。
     *
     * 幂等：标记已存在就不再追加。
     */
    private fun mountWebAppPatch(packageName: String, patchText: String) {
        val patchFile = webAppPatchFile()
        if (!patchFile.isFile) return
        val marker = "# android-installed: $packageName"
        val current = patchFile.readText()
        if (current.contains(marker)) return
        val body = patchText.trimEnd()
        val next = if (current.trim() == "[]" || current.isBlank()) {
            "$marker\n$body\n"
        } else {
            current.trimEnd() + "\n\n$marker\n" + body + "\n"
        }
        patchFile.writeText(next)
    }

    /** `dsh-web-app` 的 patch 文件：第三方插件的挂载层。 */
    private fun webAppPatchFile(): File = File(
        layout.rootfsDir,
        "usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/" +
            "@deepseek-ai/dsh-web-app/cordis.patch.yml",
    )

    // ── HTTP / 归档 / 清单 ────────────────────────────────────────────────

    /** 下载一个 URL；上限 64MB。 */
    private fun download(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "dsh-android")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}：$url")
            val body = response.body ?: throw IllegalStateException("空响应：$url")
            val buffer = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > MAX_DOWNLOAD_BYTES) {
                        throw IllegalStateException("下载超过 64MB：$url")
                    }
                }
            }
            return buffer.toByteArray()
        }
    }

    /** registry metadata（安装用的精简格式）。 */
    private fun fetchRegistryMetadata(name: String): JsonObject? {
        val encoded = name.replace("/", "%2F")
        val request = Request.Builder()
            .url("https://registry.npmjs.org/$encoded")
            .header("Accept", "application/vnd.npm.install-v1+json")
            .header("User-Agent", "dsh-android")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        }
    }

    /** 解压 GitHub zipball：去掉单一顶层目录，拒绝越界路径。 */
    private fun extractZipToStaging(bytes: ByteArray): StagedPackage {
        val staging = StagedPackage(newStagingDir())
        ZipInputStream(bytes.inputStream()).use { zip ->
            var rootPrefix: String? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                rootPrefix = rootPrefix ?: name.substringBefore('/', "") + "/"
                val relative = name.removePrefix(rootPrefix).trimEnd('/')
                if (relative.isNotBlank()) {
                    val target = safeChild(staging.dir, relative)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
            }
        }
        return staging
    }

    /** 解压 npm tarball：去掉 `package/` 前缀，拒绝越界路径。 */
    private fun extractTarGzToStaging(bytes: ByteArray): StagedPackage {
        val staging = StagedPackage(newStagingDir())
        GzipCompressorInputStream(bytes.inputStream()).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val name = entry.name.removePrefix("package/")
                    if (name.isBlank() || entry.isDirectory) continue
                    val target = safeChild(staging.dir, name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> tar.copyTo(output) }
                }
            }
        }
        return staging
    }

    /** 拼接子路径并拒绝逃出包目录。 */
    private fun safeChild(root: File, relative: String): File {
        if (relative.startsWith("/") || relative.contains("..")) {
            throw IllegalStateException("归档含越界路径：$relative")
        }
        val file = File(root, relative)
        if (!file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            throw IllegalStateException("归档路径逃出目标目录：$relative")
        }
        return file
    }

    /** 读插件清单。 */
    private fun readManifest(dir: File): PackageManifest? {
        val file = File(dir, "package.json")
        if (!file.isFile) return null
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val dsh = root["dsh"]?.jsonObjectOrNull()
            val bundle = dsh?.get("bundle")?.jsonObjectOrNull()
            val patch = bundle?.get("patch")?.jsonPrimitive?.contentOrNull
            val dependencies = root["dependencies"]?.jsonObjectOrNull()
                ?.mapValues { (_, value) -> value.jsonPrimitive.contentOrNull ?: "" }
                .orEmpty()
            PackageManifest(
                name = root["name"]?.jsonPrimitive?.contentOrNull,
                version = root["version"]?.jsonPrimitive?.contentOrNull,
                main = root["main"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: "lib/index.js",
                patch = patch,
                dependencies = dependencies,
            )
        }.getOrNull()
    }

    /** 新建本次安装的临时目录。 */
    private fun newStagingDir(): File {
        val root = File(layout.cacheDir, "plugin-install").apply { mkdirs() }
        return File(root, "package-${System.nanoTime()}").apply { mkdirs() }
    }

    /** 保证 profile 的 @deepseek-ai 链接存在（peer 依赖的解析锚点）。 */
    private fun ensurePresetLink() {
        ensureProfilePresetLink(profileDir)
    }

    private fun kotlinx.serialization.json.JsonElement?.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun okhttp3.ResponseBody.copyTo(output: java.io.OutputStream) {
        byteStream().use { input -> input.copyTo(output, bufferSize = 1 shl 16) }
    }

    private fun java.io.InputStream.copyTo(output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
    }

    /** 一次安装里的包目录，结束时清理。 */
    private class StagedPackage(val dir: File) : AutoCloseable {
        override fun close() {
            dir.deleteRecursively()
        }
    }

    /** 安装需要的清单字段。 */
    private data class PackageManifest(
        val name: String?,
        val version: String?,
        val main: String,
        val patch: String?,
        val dependencies: Map<String, String>,
    )

    private companion object {
        /** 单个下载上限；插件与依赖都在几 MB 内。 */
        const val MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024
    }
}
