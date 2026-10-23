package com.deepseek.dsh

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val STORE_TOPIC = "dsh-mobile-plugin"
private const val STORE_API = "https://api.github.com"

/** GitHub Action 定时生成的商店索引（Pages 托管）；App 不直接打 GitHub API。 */
private const val STORE_INDEX_URL = "https://ericfl1998.github.io/dsh-mobile-plugin-sdk/store/index.json"

/** 索引里已带 releases/latest 的仓库 → Release，详情页不再逐仓请求 API。 */
private val storeIndexReleases = java.util.concurrent.ConcurrentHashMap<String, GitHubRelease>()

private val storeClient = OkHttpClient()
private val storeJson = Json { ignoreUnknownKeys = true }

/** 最近一次成功的响应：GitHub 匿名 API 403（限流）时先用缓存兜底，再报错。 */
private data class CachedStoreJson(val at: Long, val value: JsonObject)
private val storeJsonCache = java.util.concurrent.ConcurrentHashMap<String, CachedStoreJson>()
private const val STORE_CACHE_TTL_MS = 10 * 60 * 1000L

private data class GitHubPluginRepo(
    val fullName: String,
    val description: String,
    val stars: Int,
    val htmlUrl: String,
    val pushedAt: Long,
)

private data class GitHubApkAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)

private data class GitHubRelease(
    val tag: String,
    val htmlUrl: String,
    val publishedAt: Long,
    val apks: List<GitHubApkAsset>,
)

/**
 * 已知插件仓库的展示文案（仓库里只有英文 description）。
 *
 * 键是 `owner/repo` 的小写形式；未登记的仓库回落到「仓库名 → 可读标题」的推导规则。
 */
private val STORE_LOCALIZED: Map<String, Pair<String, String>> = mapOf(
    "ericfl1998/dsh-subscription-oauth" to (
        "订阅" to
            "用 Kimi Code / Grok（X Premium）/ Google Antigravity / GLM 订阅账号接入模型，无需 API Key。"
        ),
)

/** 商店列表/详情的标题：优先本地化名称，其次把仓库名还原成可读标题。 */
private fun storeDisplayTitle(
    repo: GitHubPluginRepo,
    installedName: String? = null,
): String = installedName?.takeIf { it.isNotBlank() }
    ?: STORE_LOCALIZED[repo.fullName.lowercase()]?.first
    ?: friendlyRepoTitle(repo.fullName)

/** 商店列表/详情的简介：优先本地化文案，其次仓库自带的 description。 */
private fun storeDisplayDescription(repo: GitHubPluginRepo): String =
    STORE_LOCALIZED[repo.fullName.lowercase()]?.second ?: repo.description

/** `dsh-plugin-subscription-oauth` → `Subscription OAuth`（去掉生态前缀与连字符）。 */
private fun friendlyRepoTitle(fullName: String): String {
    val repo = fullName.substringAfterLast('/')
    val stripped = repo
        .removePrefix("dsh-mobile-plugin-")
        .removePrefix("dsh-plugin-")
        .removePrefix("dsh-")
        .ifBlank { repo }
    return stripped.split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifBlank { repo }
}

/**
 * 插件商店：GitHub topic `dsh-mobile-plugin` → 仓库列表 → 详情 → Release APK 安装。
 *
 * 与“插件”tab 的区别：这里不依赖已经装好的 companion，直接发现远程仓库。
 * GitHub API 未认证请求有频率限制，失败时显示可重试错误，不伪造列表。
 */
@Composable
internal fun PluginStoreTab(
    items: List<com.deepseek.dsh.plugin.nativehost.NativePluginItem>,
    initialRepoUrl: String? = null,
    backSignal: Int = 0,
    onDetailOpenChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    val context = LocalContext.current
    var repos by remember { mutableStateOf<List<GitHubPluginRepo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selected by remember(initialRepoUrl) {
        mutableStateOf(initialRepoUrl?.let { seedRepoFromUrl(it) })
    }

    // 详情打开时通知外层：系统返回/顶部返回先回商店列表，不离开插件页。
    LaunchedEffect(selected) { onDetailOpenChange(selected != null) }
    LaunchedEffect(backSignal) {
        if (backSignal > 0 && selected != null) selected = null
    }
    androidx.activity.compose.BackHandler(enabled = selected != null) {
        selected = null
    }

    LaunchedEffect(reloadKey) {
        error = null
        repos = null
        runCatching { fetchStoreIndex() }
            .recoverCatching { fetchTopicRepos() }
            .onSuccess { repos = it }
            .onFailure { error = it.message ?: context.getString(R.string.store_github_failed) }
    }

    if (selected != null) {
        PluginStoreDetail(
            repo = selected!!,
            installed = items.firstOrNull { samePluginRepo(it, selected!!.fullName) },
            onBack = { selected = null },
            modifier = modifier,
        )
        return
    }

    val list = repos.orEmpty().filter {
        query.isBlank() ||
            it.fullName.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = TextStyle(fontSize = 13.sp, color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.labelPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL3, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isBlank()) {
                        Text(
                            stringResource(R.string.plugins_store_search),
                            color = colors.labelTertiary,
                            fontSize = 13.sp,
                        )
                    }
                    inner()
                }
            },
        )

        when {
            error != null && repos == null -> {
                StoreMessage(stringResource(R.string.plugins_store_load_failed), colors)
                StoreAction(stringResource(R.string.plugins_store_retry)) { reloadKey += 1 }
            }
            repos == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            list.isEmpty() -> {
                StoreMessage(
                    if (query.isBlank()) stringResource(R.string.plugins_store_empty)
                    else stringResource(R.string.plugins_store_no_match, query),
                    colors,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(list, key = { it.fullName }) { repo ->
                    StoreRepoRow(
                        repo = repo,
                        installed = items.firstOrNull { samePluginRepo(it, repo.fullName) },
                        onClick = { selected = repo },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreRepoRow(
    repo: GitHubPluginRepo,
    installed: com.deepseek.dsh.plugin.nativehost.NativePluginItem?,
    onClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.bgLayer3)
            .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                repo.fullName.substringAfterLast('/').take(1).uppercase(),
                color = colors.labelPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                storeDisplayTitle(repo, installed?.name),
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                storeDisplayDescription(repo).ifBlank { repo.htmlUrl },
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (installed != null) {
                val hasUpdate = repo.pushedAt > installed.lastUpdateTime
                Text(
                    text = if (hasUpdate) stringResource(R.string.plugins_store_update_available)
                    else stringResource(R.string.plugins_store_installed),
                    color = if (hasUpdate) colors.labelPrimary else colors.labelTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                stringResource(R.string.plugins_store_stars, repo.stars),
                color = colors.labelTertiary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun PluginStoreDetail(
    repo: GitHubPluginRepo,
    installed: com.deepseek.dsh.plugin.nativehost.NativePluginItem?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    val context = LocalContext.current
    var release by remember { mutableStateOf<GitHubRelease?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repo.fullName) {
        loading = true
        error = null
        runCatching { fetchLatestRelease(repo.fullName) }
            .onSuccess { release = it }
            .onFailure { error = it.message ?: context.getString(R.string.store_release_failed) }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // 返回由 App 顶栏承载，详情页内不再重复画一条。
        Spacer(Modifier.height(12.dp))
        Text(
            storeDisplayTitle(repo, installed?.name),
            color = colors.labelPrimary,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            repo.fullName,
            color = colors.labelTertiary,
            fontSize = 11.5.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            storeDisplayDescription(repo).ifBlank { repo.htmlUrl },
            color = colors.labelSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.plugins_store_stars, repo.stars),
            color = colors.labelTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (installed != null) {
            val hasUpdate = repo.pushedAt > installed.lastUpdateTime
            Text(
                text = if (hasUpdate) stringResource(R.string.plugins_store_update_available)
                else stringResource(R.string.plugins_store_installed),
                color = if (hasUpdate) colors.labelPrimary else colors.labelSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (hasUpdate) {
                Text(
                    stringResource(R.string.plugins_store_update_hint),
                    color = colors.labelTertiary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            error != null -> {
                StoreMessage(error!!, colors)
            }
            release == null || release!!.apks.isEmpty() -> {
                StoreMessage(stringResource(R.string.plugins_store_no_apk), colors)
            }
            else -> {
                Text(
                    stringResource(R.string.plugins_store_assets) + " · " + release!!.tag,
                    color = colors.labelPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                val hasUpdate = installed != null && repo.pushedAt > installed.lastUpdateTime
                release!!.apks.forEach { asset ->
                    ApkInstallRow(
                        asset = asset,
                        installed = installed != null,
                        hasUpdate = hasUpdate,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkInstallRow(
    asset: GitHubApkAsset,
    installed: Boolean,
    hasUpdate: Boolean,
) {
    val colors = LocalDshPanelColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var installedNow by remember { mutableStateOf(false) }
    val isInstalled = installed || installedNow
    val canInstall = !installing && (!isInstalled || hasUpdate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bgLayer3)
            .border(0.5.dp, colors.borderL3, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(asset.name, color = colors.labelPrimary, fontSize = 13.sp)
            Text(
                "${asset.sizeBytes / 1024} KB",
                color = colors.labelTertiary,
                fontSize = 11.sp,
            )
            message?.let {
                Text(it, color = colors.labelSecondary, fontSize = 11.sp)
            }
        }
        Text(
            text = when {
                installing -> stringResource(R.string.plugins_store_downloading)
                isInstalled && hasUpdate -> stringResource(R.string.plugins_store_update)
                isInstalled -> stringResource(R.string.plugins_store_installed)
                else -> stringResource(R.string.plugins_store_install)
            },
            color = if (isInstalled && !hasUpdate) colors.labelTertiary else colors.labelPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL3, RoundedCornerShape(10.dp))
                .clickable(enabled = canInstall) {
                    installing = true
                    message = null
                    scope.launch {
                        runCatching { downloadAndInstallApk(context, asset) }
                            .onSuccess { installedNow = true }
                            .onFailure { message = it.message ?: context.getString(R.string.store_install_failed) }
                        installing = false
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun StoreMessage(text: String, colors: com.deepseek.dsh.core.designsystem.DshPanelColors) {
    Text(
        text = text,
        color = colors.labelSecondary,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun StoreAction(label: String, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Text(
        text = label,
        color = colors.labelPrimary,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

private fun isoToMillis(value: String?): Long =
    runCatching { java.time.Instant.parse(value ?: return 0L).toEpochMilli() }.getOrDefault(0L)

/** `https://github.com/owner/repo` → 详情页所需的仓库快照。 */
private fun seedRepoFromUrl(url: String): GitHubPluginRepo? {
    val match = Regex("github\\.com/([^/]+)/([^/#?]+)").find(url) ?: return null
    val repo = match.groupValues[2].removeSuffix(".git")
    return GitHubPluginRepo(
        fullName = "${match.groupValues[1]}/$repo",
        description = url,
        stars = 0,
        htmlUrl = url,
        pushedAt = 0L,
    )
}

/** 把 `dsh-plugin-subscription-oauth` / `dsh-subscription-oauth` 归一成同一 key。 */
private fun normalizePluginKey(value: String?): String =
    (value ?: "")
        .substringAfterLast('/')
        .lowercase()
        .removePrefix("dsh-plugin-")
        .removePrefix("dsh-")
        .replace(Regex("[^a-z0-9]"), "")

private fun samePluginRepo(
    item: com.deepseek.dsh.plugin.nativehost.NativePluginItem,
    repoFullName: String,
): Boolean {
    val repoKey = normalizePluginKey(repoFullName)
    val candidates = listOf(item.jsPackage, item.packageName, item.pluginId, item.name)
    return candidates.any { candidate ->
        val key = normalizePluginKey(candidate)
        key.isNotBlank() && (key == repoKey || key.contains(repoKey) || repoKey.contains(key))
    }
}

/**
 * 优先读静态商店索引：一次 HTTPS 请求拿到仓库列表与 latest release，
 * 不受 GitHub 匿名 API 60 次/小时限制。失败时调用方回退 [fetchTopicRepos]。
 */
private suspend fun fetchStoreIndex(): List<GitHubPluginRepo> = withContext(Dispatchers.IO) {
    val json = getJson(STORE_INDEX_URL)
    json["repos"]?.jsonArray.orEmpty().mapNotNull { element ->
        val item = element.jsonObject
        val fullName = item["fullName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        item["release"]?.takeIf { it is JsonObject }?.let { releaseElement ->
            val release = releaseElement.jsonObject
            val apks = release["apks"]?.jsonArray.orEmpty().mapNotNull { assetElement ->
                val asset = assetElement.jsonObject
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                GitHubApkAsset(
                    name = name,
                    url = url,
                    sizeBytes = asset["sizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
            storeIndexReleases[fullName] = GitHubRelease(
                tag = release["tag"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                htmlUrl = release["htmlUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                publishedAt = release["publishedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                apks = apks,
            )
        }
        GitHubPluginRepo(
            fullName = fullName,
            description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            stars = item["stars"]?.jsonPrimitive?.intOrNull ?: 0,
            htmlUrl = item["htmlUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pushedAt = item["pushedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }
}

private suspend fun fetchTopicRepos(): List<GitHubPluginRepo> = withContext(Dispatchers.IO) {
    val url = "$STORE_API/search/repositories?q=topic:$STORE_TOPIC&sort=updated&order=desc&per_page=50"
    val json = getJson(url)
    json["items"]?.jsonArray.orEmpty().mapNotNull { element ->
        val item = element.jsonObject
        val fullName = item["full_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        GitHubPluginRepo(
            fullName = fullName,
            description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            stars = item["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
            htmlUrl = item["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            pushedAt = isoToMillis(item["pushed_at"]?.jsonPrimitive?.contentOrNull),
        )
    }
}

private suspend fun fetchLatestRelease(fullName: String): GitHubRelease? = withContext(Dispatchers.IO) {
    storeIndexReleases[fullName]?.let { return@withContext it }
    val json = getJson("$STORE_API/repos/$fullName/releases/latest")
    val assets = json["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
        val asset = element.jsonObject
        val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        GitHubApkAsset(
            name = name,
            url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }
    GitHubRelease(
        tag = json["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        htmlUrl = json["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        publishedAt = isoToMillis(json["published_at"]?.jsonPrimitive?.contentOrNull),
        apks = assets,
    )
}

private fun getJson(url: String): JsonObject {
    val now = System.currentTimeMillis()
    val cached = storeJsonCache[url]
    if (cached != null && now - cached.at < STORE_CACHE_TTL_MS) {
        return cached.value
    }
    val request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "dsh-mobile-plugin-store")
        .build()
    storeClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            val remaining = response.header("X-RateLimit-Remaining")
            val reset = response.header("X-RateLimit-Reset")
            val retryAfter = response.header("Retry-After")
            android.util.Log.w(
                "PluginStore",
                "GET $url -> ${response.code}; remaining=$remaining reset=$reset retryAfter=$retryAfter body=${body.take(500)}",
            )
            // 限流时优先返回 10 分钟内的旧数据，商店不至于整页失败。
            if (cached != null) return cached.value
            val detail = buildString {
                append("GitHub API ${response.code}")
                remaining?.let { append(" · 剩余 $it") }
                retryAfter?.let { append(" · Retry-After ${it}s") }
                if (body.isNotBlank()) append(" · ").append(body.take(180))
            }
            error(detail)
        }
        val body = response.body?.string().orEmpty()
        val value = storeJson.parseToJsonElement(body).jsonObject
        storeJsonCache[url] = CachedStoreJson(now, value)
        return value
    }
}

private suspend fun downloadAndInstallApk(context: Context, asset: GitHubApkAsset) = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "apk-store").apply { mkdirs() }
    val target = File(dir, asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    val request = Request.Builder()
        .url(asset.url)
        .header("User-Agent", "dsh-mobile-plugin-store")
        .build()
    storeClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error(context.getString(R.string.store_download_failed, response.code))
        response.body?.byteStream()?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error(context.getString(R.string.store_empty_download))
    }
    withContext(Dispatchers.Main) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
