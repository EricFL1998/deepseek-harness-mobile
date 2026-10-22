package com.deepseek.dsh.core.bridge

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 插件页数据源：把 bridge 的两种 provider 归一成一份目录。
 *
 * 优先级严格按设计文档：
 * 1. `client-slots` 的 `plugins.item` 注册 —— 分类、文案、图标全部来自官方客户端资源；
 * 2. `package-inventory` 的 bundle / row 清单 —— Host 原生清单；
 * 3. 两者都没有时返回 [BridgePluginCatalogSource.NONE]，UI 显示空态而不是伪造数据。
 *
 * 图标优先取 `client-slots/icon-svg`（官方图形），取不到时回落到该包自己的
 * `meta.icon` data URL。两者都没有就留空，绝不用本地常量顶替。
 */
object BridgePluginCatalog {

    /** `plugins.item` slot 名；官方插件管理页用它承载「内置插件配置」分组。 */
    const val PLUGINS_ITEM_SLOT: String = "plugins.item"

    /** 图标请求的默认边长。 */
    const val DEFAULT_ICON_SIZE: Int = 40

    /**
     * 读取一份插件目录。
     *
     * @param registry 已协商的 provider 门面。
     * @param iconSize 图标边长。
     * @return 目录；两个 provider 都不可用时返回 `source = NONE` 的空目录。
     */
    suspend fun load(
        registry: BridgeRegistry,
        iconSize: Int = DEFAULT_ICON_SIZE,
        locale: String? = null,
    ): BridgePluginCatalogResult {
        val warnings = mutableListOf<String>()

        // 先协商：provider 是否存在由 manifest 决定，未协商时所有 has* 都为 false。
        when (val negotiated = registry.negotiate()) {
            is BridgeResult.Success -> warnings += negotiated.warnings
            is BridgeResult.Failure -> return BridgePluginCatalogResult(
                catalog = BridgePluginCatalogData(source = BridgePluginCatalogSource.NONE),
                warnings = listOf(hintFor(negotiated)),
            )
        }

        val slotList = if (registry.hasClientSlots()) {
            when (val result = registry.slotRegistrations(PLUGINS_ITEM_SLOT, locale)) {
                is BridgeResult.Success -> {
                    warnings += result.warnings
                    result.value
                }
                is BridgeResult.Failure -> {
                    warnings += "client-slots unavailable: ${result.message}"
                    null
                }
            }
        } else null

        val inventory = if (registry.hasPackageInventory()) {
            when (val result = registry.bundles(locale = locale)) {
                is BridgeResult.Success -> {
                    warnings += result.warnings
                    result.value
                }
                is BridgeResult.Failure -> {
                    warnings += "package-inventory unavailable: ${result.message}"
                    null
                }
            }
        } else null

        val categories = slotList?.registrations
            ?.filter { it.slot == PLUGINS_ITEM_SLOT }
            ?.sortedWith(compareBy({ it.order ?: 0 }, { it.identity }))
            ?.map { registration ->
                BridgePluginCategory(
                    id = registration.identity,
                    label = registration.label ?: registration.identity,
                    packageName = registration.packageName,
                    locale = registration.locale,
                    order = registration.order ?: 0,
                    componentId = registration.componentId,
                    // 官方条目没有可评估的组件：bridge 直接按注册 id 返回固定
                    // artwork，所以取图标的键在两种情况下都可用。
                    iconSvg = iconOf(
                        registry,
                        registration.componentId ?: registration.identity,
                        iconSize,
                        warnings,
                    ),
                )
            }
            .orEmpty()

        val bundles = inventory?.bundles.orEmpty().map { bundle ->
            BridgePluginBundleSummary(
                name = bundle.name,
                title = bundle.title ?: bundle.name,
                description = bundle.description,
                version = bundle.version,
                official = bundle.official,
                optional = bundle.optional,
                removable = bundle.removable,
                enabled = bundle.enabled,
                installed = bundle.installed,
                availability = bundle.availability,
                errorCode = bundle.errorCode,
                iconDataUrl = bundle.icon,
                profileRoot = bundle.profileRoot,
                pinned = bundle.pinned,
                order = bundle.order,
                hidden = bundle.hidden,
                market = bundle.market,
                locked = bundle.locked,
                rows = bundle.rows.map { row ->
                    BridgePluginRowSummary(
                        rowId = row.rowId,
                        moduleName = row.moduleName,
                        title = row.title ?: row.moduleName,
                        description = row.description,
                        iconDataUrl = row.icon,
                        enabled = row.enabled,
                        entryId = row.entryId,
                    )
                },
            )
        }

        val source = when {
            categories.isNotEmpty() -> BridgePluginCatalogSource.CLIENT_SLOTS
            bundles.isNotEmpty() -> BridgePluginCatalogSource.PACKAGE_INVENTORY
            else -> BridgePluginCatalogSource.NONE
        }
        return BridgePluginCatalogResult(
            catalog = BridgePluginCatalogData(
                source = source,
                categories = categories,
                bundles = bundles,
                managementAvailable = inventory?.managementAvailable == true,
            ),
            warnings = warnings,
        )
    }

    /**
     * 取一个组件的官方图形。
     *
     * 失败不抛：图标是装饰，缺了只是少了图形，目录本身仍然可用。失败原因进
     * warnings，便于诊断「为什么这个插件没有图标」。
     */
    private suspend fun iconOf(
        registry: BridgeRegistry,
        componentId: String?,
        size: Int,
        warnings: MutableList<String>,
    ): String? {
        if (componentId == null) return null
        return when (val result = registry.iconSvg(componentId, size)) {
            is BridgeResult.Success -> result.value.svg
            is BridgeResult.Failure -> {
                warnings += "icon $componentId: ${result.message}"
                null
            }
        }
    }

    /**
     * 渲染一个 `plugins.item` 组件的元素树。     *
     * 客户端拿到树后自行决定渲染成原生控件；bridge 不理解 UI，只交数据。
     *
     * @param registry 已协商的 provider 门面。
     * @param componentId 组件身份。
     * @param props 传给组件的 props，默认 `view = "summary"`，即官方卡片的一句话说明。
     */
    suspend fun renderItem(
        registry: BridgeRegistry,
        componentId: String,
        props: JsonObject = buildJsonObject { put("view", "summary") },
    ): BridgeResult<BridgeRenderTree> = registry.renderTree(componentId, props)
}

/** 目录来源，决定 UI 展示哪种形态。 */
/**
 * 把一次失败翻译成用户能照做的提示。
 *
 * 插件页不能只报「bridge unavailable」：最常见的情形是 rootfs 里的插件还没装
 * （旧镜像），那需要去设置页点「更新插件」，而不是检查网络。
 * @param failure 协商失败的稳定码与诊断文本。
 * @returns 面向用户的一句话。
 */
private fun hintFor(failure: BridgeResult.Failure): String = when (failure.code) {
    BridgeErrorCode.TRANSPORT_UNREACHABLE ->
        "Host 未就绪：无法连接本地运行时。请稍候或重启运行环境。"
    BridgeErrorCode.PROVIDER_UNKNOWN ->
        "Host 上没有 bridge 路由：兼容层插件未安装。请在「设置 → 更新」中点击「更新插件」。"
    BridgeErrorCode.TRANSPORT_DECODE_FAILED ->
        "Host 的 bridge 响应无法解析（${failure.message}）。插件可能需要更新。"
    BridgeErrorCode.AUTH_REQUIRED ->
        "Host 拒绝了本次请求：会话未建立。请重新登录或重启运行时。"
    BridgeErrorCode.PROVIDER_VERSION_MISMATCH ->
        "插件协议版本与客户端不匹配（${failure.message}）。请在「设置 → 更新」中更新插件。"
    else -> "bridge 不可用：${failure.message}"
}

enum class BridgePluginCatalogSource {
    /** 来自官方客户端 slot 注册：分类与图标都是官方资源。 */
    CLIENT_SLOTS,

    /** 来自 Host 原生清单：只有 bundle / row，没有官方分类。 */
    PACKAGE_INVENTORY,

    /** 两者都不可用：UI 显示空态。 */
    NONE,
}

/** 一份插件目录。 */
data class BridgePluginCatalogData(
    val source: BridgePluginCatalogSource,
    val categories: List<BridgePluginCategory> = emptyList(),
    val bundles: List<BridgePluginBundleSummary> = emptyList(),
    val managementAvailable: Boolean = false,
)

/** 目录 + 非致命说明。 */
data class BridgePluginCatalogResult(
    val catalog: BridgePluginCatalogData,
    val warnings: List<String> = emptyList(),
)

/** 一个官方插件分类（`plugins.item` 的一条注册）。 */
data class BridgePluginCategory(
    val id: String,
    val label: String,
    val packageName: String?,
    val locale: String?,
    val order: Int,
    val componentId: String?,
    val iconSvg: String?,
)

/** Host 原生清单里的一个 bundle。 */
data class BridgePluginBundleSummary(
    val name: String,
    val title: String,
    val description: String?,
    val version: String?,
    val official: Boolean,
    val optional: Boolean,
    val removable: Boolean,
    val enabled: Boolean,
    val installed: Boolean,
    /** Host 报告的可用性：随安装提供、已安装或缺失。 */
    val availability: String = "",
    val errorCode: String?,
    val iconDataUrl: String?,
    val profileRoot: Boolean = false,
    val pinned: Boolean = false,
    val order: Int = 0,
    val hidden: Boolean = false,
    val market: Boolean = false,
    val locked: Boolean = false,
    val rows: List<BridgePluginRowSummary> = emptyList(),
)

/** bundle 的一条 row。 */
data class BridgePluginRowSummary(
    val rowId: String,
    val moduleName: String,
    val title: String,
    val description: String?,
    val iconDataUrl: String?,
    /** Whether this row is enabled; null means the Host did not report it. */
    val enabled: Boolean? = null,
    /** The Loader entry this row resolves to, when the Host reports one. */
    val entryId: String? = null,
)
