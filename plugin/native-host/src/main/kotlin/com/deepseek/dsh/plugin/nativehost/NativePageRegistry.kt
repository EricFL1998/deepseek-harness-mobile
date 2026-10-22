package com.deepseek.dsh.plugin.nativehost

import com.deepseek.dsh.plugin.nativesdk.pages.PageContent
import com.deepseek.dsh.plugin.nativesdk.pages.PageEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pages contributed by companion APKs that were successfully loaded in this process.
 *
 * A discovered APK is not enough to take over a Web settings section: its ABI/signature
 * checks and class loading must have succeeded, and it must have registered a page whose
 * id matches the Web `settings.section` id. The registry is replaced after every package
 * rescan so uninstalling or disabling a companion also removes its native route.
 */
data class NativePageRegistration(
    val pluginId: String,
    val jsPackage: String?,
    val pages: List<PageContent>,
)

object NativePageRegistry {
    private val mutableRegistrations = MutableStateFlow<List<NativePageRegistration>>(emptyList())

    /** Observed by Compose so a package install/uninstall updates an open settings page. */
    val registrations: StateFlow<List<NativePageRegistration>> = mutableRegistrations.asStateFlow()

    fun replaceAll(registrations: List<NativePageRegistration>) {
        mutableRegistrations.value = registrations
    }

    /** Resolve the exact Web component (`<package>#<settings.section id>`) to a native page. */
    fun find(componentId: String): PageContent? =
        find(componentId, mutableRegistrations.value)

    fun find(
        componentId: String,
        registrations: List<NativePageRegistration>,
    ): PageContent? {
        val separator = componentId.lastIndexOf('#')
        if (separator <= 0 || separator == componentId.lastIndex) return null
        val owner = componentId.substring(0, separator)
        val pageId = componentId.substring(separator + 1)

        return registrations.firstNotNullOfOrNull { registration ->
            if (!registration.owns(owner)) return@firstNotNullOfOrNull null
            registration.pages.firstOrNull { page ->
                page.entry.placement == PageEntry.Placement.SETTINGS && page.entry.id == pageId
            }
        }
    }

    /**
     * 把 `settings.section` 组件匹配到伴侣的原生设置页，比 [find] 更宽容。
     *
     * 精确的 `<package>#<id>` 匹配依赖两边的 id 完全一致；实际插件里原生页的 id
     * 常常是作者另起的名字（例如 Web 分区 id 是 `subscriptions`，原生页 id 是
     * `providers`）。在精确匹配失败后按顺序退让：
     *
     * 1. **标题相同**：同一份文案（含「订阅 / Subscriptions」这类中英混排，
     *    去掉空白与分隔符后互相包含也算）；
     * 2. **一一对应**：调用方确认该插件只有一个 `settings.section`，而伴侣只
     *    注册了一个设置页时，它们就是同一页。
     *
     * 不能匹配时返回 [SettingsPageMatch.reason]：把「伴侣没加载 / 没注册设置页 /
     * id 对不上（列出已注册 id）」直接展示给用户，避免继续无提示地退回 Web UI。
     *
     * @param componentId `<package>#<id>` 组件身份。
     * @param title 设置分区标题（已本地化）。
     * @param registrations 已加载伴侣的页面注册。
     * @param allowSinglePageFallback 是否允许一一对应兜底（插件只有一个分区时）。
     */
    fun findSettingsSection(
        componentId: String,
        title: String?,
        registrations: List<NativePageRegistration>,
        allowSinglePageFallback: Boolean = false,
    ): SettingsPageMatch {
        find(componentId, registrations)?.let { return SettingsPageMatch(it) }
        val separator = componentId.lastIndexOf('#')
        if (separator <= 0 || separator == componentId.lastIndex) return SettingsPageMatch(null)
        val owner = componentId.substring(0, separator)
        val ownedRegistrations = registrations.filter { it.owns(owner) }
        val allOwnedPages = ownedRegistrations.flatMap { it.pages }
        val settingsPages = allOwnedPages
            .filter { it.entry.placement == PageEntry.Placement.SETTINGS }
        if (settingsPages.isEmpty()) {
            return SettingsPageMatch(
                page = null,
                reason = when {
                    ownedRegistrations.isEmpty() -> "原生伴侣未加载或未配对（$owner）"
                    allOwnedPages.isEmpty() -> "原生伴侣已加载，但没有注册页面"
                    else -> "原生伴侣没有 SETTINGS 页：已注册 " +
                        allOwnedPages.joinToString { "${it.entry.placement}:${it.entry.id}" }
                },
            )
        }
        val wanted = normalizeLabel(title)
        if (wanted != null) {
            settingsPages.firstOrNull { normalizeLabel(it.entry.title) == wanted }?.let {
                return SettingsPageMatch(it)
            }
            settingsPages.firstOrNull {
                val candidate = normalizeLabel(it.entry.title)
                candidate != null && (candidate.contains(wanted) || wanted.contains(candidate))
            }?.let { return SettingsPageMatch(it) }
        }
        if (allowSinglePageFallback && settingsPages.size == 1) {
            return SettingsPageMatch(settingsPages.first())
        }
        val ids = settingsPages.joinToString(", ") { it.entry.id }
        return SettingsPageMatch(page = null, reason = "原生页未匹配：$componentId；已注册 [$ids]")
    }

    /** 匹配结果：命中的原生页，或未命中时给界面看的原因。 */
    data class SettingsPageMatch(
        val page: PageContent?,
        val reason: String? = null,
    )

    /** 去掉空白与常见分隔符后的小写文案，用于标题比对。 */
    private fun normalizeLabel(raw: String?): String? = raw
        ?.replace(Regex("[\\s/·|,，、()（）\\[\\]]"), "")
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

    private fun NativePageRegistration.owns(owner: String): Boolean {
        if (owner == pluginId || owner == jsPackage) return true
        val declared = jsPackage?.takeIf { it.isNotBlank() } ?: return false
        val candidates = buildSet {
            add(declared.substringBeforeLast('@'))
            add(declared.substringAfterLast('/').substringBefore('@'))
            if (declared.contains("://") || declared.startsWith("git+")) {
                // GitHub/Git 地址：取仓库名（去 .git / #ref / ?query）作为包名。
                val path = declared.substringBefore('#').substringBefore('?').trimEnd('/')
                add(path.substringAfterLast('/').removeSuffix(".git"))
            }
        }
        // 一边叫 `dsh-plugin-x`、另一边只叫 `x` 是常见写法；去掉已知前缀再比一次。
        val ownerAliases = buildSet {
            add(owner)
            PACKAGE_PREFIXES.forEach { prefix ->
                if (owner.startsWith(prefix)) add(owner.removePrefix(prefix))
            }
        }
        return candidates.any { candidate ->
            if (candidate.isBlank()) return@any false
            val aliases = buildSet {
                add(candidate)
                PACKAGE_PREFIXES.forEach { prefix ->
                    if (candidate.startsWith(prefix)) add(candidate.removePrefix(prefix))
                }
            }
            ownerAliases.any { it in aliases }
        }
    }

    /** 包名里常见、但不改变插件身份的前缀。 */
    private val PACKAGE_PREFIXES = listOf("dsh-plugin-", "dsh-")
}
