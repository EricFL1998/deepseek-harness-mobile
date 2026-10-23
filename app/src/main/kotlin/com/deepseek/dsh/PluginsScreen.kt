package com.deepseek.dsh

import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginComponentInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.PluginRowInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.AnchoredMenuPopup
import com.deepseek.dsh.core.designsystem.DshDialog
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.DshIcons
import com.deepseek.dsh.core.designsystem.DshMenuAlign
import com.deepseek.dsh.core.designsystem.DshMenuIcon
import com.deepseek.dsh.core.designsystem.DshMenuRow
import com.deepseek.dsh.core.designsystem.DshSwitch
import com.deepseek.dsh.core.designsystem.DshSvgIcon
import com.deepseek.dsh.core.designsystem.DshTextInput
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.designsystem.dshPanelColors
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.core.model.SurfaceKind
import com.deepseek.dsh.core.bridge.BridgePluginBundleSummary
import com.deepseek.dsh.core.bridge.BridgePluginCatalogData
import com.deepseek.dsh.core.bridge.BridgePluginCatalogSource

/**
 * 插件管理页（手机端）。
 *
 * 视觉对齐 dsh-upstream/packages/client/ui-plugin-manager 的 PluginManagerPage：
 * - 页头：标题 20/28 500 + 引导语 13/20 二级色（上距 4），右上主按钮高 32 / 13-20 / R12 / 内距 12；
 * - 页签：SegmentedTabs（容器 padding 4 / R16 / bg-module-platform，
 *   选中项 0.5dp border-l3 / R12 / bg-layer-3，页签高 34 / padding 0 12 / 14-20 / 选中 600）；
 * - 分组：标题 14/22 500，组内卡片 2dp 节奏；
 * - 卡片头：48dp 图标框（0.5dp border-l3 / R16）+ 标题 14/20 500 + 说明 13/18 三级色（1 行）；
 * - 组件行：40dp 图标框（0.5dp border-l3 / R12）+ 行名 13.5/20 500 + 等宽说明 11.5/16 三级色，
 *   行内 padding 12/2，行间 0.5dp border-l2 细线；
 * - 状态胶囊：高 18 / 内距 7 / 10sp / bg-module-platform；
 * - 按钮：sm 高 28 / 12-18 / R8 / 内距 10，主按钮走 button-primary-fill；
 * - 页面：左右 24dp、上 16dp、下 32dp，区块间距 32dp（页签与页头按 -16dp 折半为 16dp）。
 *
 * 颜色全部取自 panel token（[LocalDshPanelColors]），随系统深浅色切换；不引入 Material 配色。
 * 回调与原 PluginsListScreen 完全一致：onOpenSdui / onInstallPlugin / onOpenSurface。
 */
@Composable
fun PluginsScreen(
    surfaces: List<SurfaceItem>,
    pluginManagerSnapshot: PluginManagerSnapshot? = null,
    onOpenSurface: (SurfaceItem) -> Unit,
    onOpenSdui: () -> Unit,
    /**
     * 安装一个插件 spec（包名 / Git 地址 / 本地绝对路径）。挂起：Host 安装耗时以秒计。
     *
     * 第二个参数是进度回调 `(fraction 0..1, step 文案)`：安装器每完成一步（解析地址 /
     * 下载 / 解压 / 装依赖 / 写 profile / 重启 Host）就回放一次，「添加插件」对话框据此
     * 画进度条与步骤文字。不关心进度的地方（例如插件市场 tab）传 `{ _, _ -> }` 即可。
     */
    onInstallPlugin: suspend (String, (Float, String) -> Unit) -> Result<String>,
    onToggleBundle: (String, Boolean) -> Unit = { _, _ -> },
    /** 拨动 bundle 总开关并等 Host 结果（详情页开关用；失败时界面回弹并 toast）。 */
    onToggleBundleAwait: suspend (String, Boolean) -> Boolean = { _, _ -> false },
    /** 启停一条 Loader 行插件（第三方插件，非 bundle）：参数为 entryId 与目标状态。 */
    onTogglePluginRow: (String, Boolean) -> Unit = { _, _ -> },
    /** 启停 bundle 里的一个子组件（bundle patch 插进来的一行 Loader entry）。 */
    onToggleComponent: (String, Boolean) -> Unit = { _, _ -> },
    /** 卸载一个伴侣 APK（系统卸载确认框由外层发起）。 */
    onUninstallCompanion: (com.deepseek.dsh.plugin.nativehost.NativePluginItem) -> Unit = {},
    onUninstall: (SurfaceItem) -> Unit = {},
    onCreatePlugin: () -> Unit = {},
    pluginRows: List<PluginRowInfo> = emptyList(),
    bridgeCatalog: BridgePluginCatalogData = BridgePluginCatalogData(BridgePluginCatalogSource.NONE),
    bridgeWarnings: List<String> = emptyList(),
    /** Host 声明的设置页位置（`settings.page`）：官方配置条目的唯一来源。 */
    hostSettingsPages: List<com.deepseek.dsh.core.model.PluginSettingsPage> = emptyList(),
    /** 插件声明在行级的原生配置入口（plugins.row.config），来自 Host 的服务端数据。 */
    rowConfigSurfaces: List<com.deepseek.dsh.core.bridge.BridgeSlotSurface> = emptyList(),
    /** App 安装器管理的包名（web-app patch 里的挂载标记）。 */
    managedPluginPackages: Set<String> = emptySet(),
    /** 读取一份插件设置表单。 */
    loadPluginSettings: suspend (String) -> com.deepseek.dsh.core.model.PluginSettingsDocument? = { null },
    /** 回写插件设置字段。 */
    savePluginSettings: suspend (String, kotlinx.serialization.json.JsonObject, Int) -> Boolean =
        { _, _, _ -> false },
    /** 读取一个凭据引用的状态（配置状态 + 可写性），用于官方页面的 API Key。 */
    loadCredential: suspend (String) -> com.deepseek.dsh.core.data.DshRepository.CredentialStatus? = { null },
    /** 写入一个凭据引用。 */
    saveCredential: suspend (String, String) -> Boolean = { _, _ -> false },
    /** 外层 App 返回按钮的点击计数：详情页打开时自增，由本页关掉详情。 */
    backSignal: Int = 0,
    /** 详情页开关状态上报：外层据此决定返回是关详情还是离开插件页。 */
    onDetailOpenChange: (Boolean) -> Unit = {},
    /** 「原生插件」tab 的商店列表项（可装伴侣 APK）。 */
    nativePluginItems: List<com.deepseek.dsh.plugin.nativehost.NativePluginItem> = emptyList(),
    /** 从“JS 安装后提示伴生 APK”跳转时，直接打开该仓库详情。 */
    storeSeedUrl: String? = null,
    /** 初始页签：0 = 插件，1 = 插件商店。 */
    initialTab: Int = 0,
    /** 插件市场的数据源；「自定义插件市场」bundle 启用时 tab 才会出现。 */
    marketBackend: MarketBackend? = null,
    /** 语音识别的 Host 接口（语音输入插件用）。 */
    speechHooks: SpeechHooks? = null,
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showInstallDialog by remember { mutableStateOf(false) }
    var pluginPackageInput by remember { mutableStateOf("") }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    // 安装过程三件套：busy 期间对话框不可关闭、输入与主按钮禁用；进度与步骤由
    // onInstallPlugin 的回调逐帧写入（安装耗时以秒计，没有这两项就是「点了没反应」）。
    var installBusy by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf(0f) }
    var installStep by remember { mutableStateOf("") }
    var addMenuOpen by remember { mutableStateOf(false) }
    var selectedPlugin by remember { mutableStateOf<SurfaceItem?>(null) }
    // 只记包名：详情每次从最新桥目录取对象，开关后状态才能立刻反映（否则会停留在打开时的快照，表现为「关不掉」）。
    var selectedBundleName by remember { mutableStateOf<String?>(null) }
    // 「内置插件配置」的每一行后面都是 Host 的设置文档：点开就是可编辑的表单。
    var selectedConfig by remember { mutableStateOf<ConfigEntryRow?>(null) }
    // 已安装的第三方行插件详情（伴侣 APK 作为它的子组件出现在里面）。
    var selectedRowPlugin by remember { mutableStateOf<PluginRowInfo?>(null) }
    // 「更多插件」页面：从实验性分组头部的「更多 ›」进入。
    var showMorePlugins by remember { mutableStateOf(false) }
    // 原生伴侣：详情页 + 停用集合 + 已装列表（merge 进已安装分组）。
    var selectedNativePlugin by remember {
        mutableStateOf<com.deepseek.dsh.plugin.nativehost.NativePluginItem?>(null)
    }
    var disabledNativePlugins by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 插件页分段页签：0 = 插件（现有列表），1 = 原生插件商店，2 = 发现（插件市场）。
    var pluginsTabIndex by remember(initialTab) { mutableStateOf(initialTab) }
    // 「发现」tab 由「自定义插件市场」bundle 的客户端插件注册；bundle 关闭时没有
    // 这个页签（官方页面同样只在插件注册了 plugins.tab 时才画页签条）。
    val marketEnabled = remember(bridgeCatalog) {
        bridgeCatalog.bundles.any { it.market && it.enabled }
    }
    LaunchedEffect(marketEnabled) {
        if (!marketEnabled && pluginsTabIndex == 2) pluginsTabIndex = 0
    }
    /**
     * Host 的全部设置命名空间。
     *
     * 两个来源合成一份：`settings.page`（没有所属 Loader 行的独立设置页）与
     * `plugins.row.config`（挂在某个 bundle 行上的行级配置）。两者的 id 都是
     * `<bundle>#<namespace>`，取 `#` 之后的部分就是 bridge `settings/describe`
     * 要的命名空间。
     */
    val settingsNamespaces = remember(hostSettingsPages, rowConfigSurfaces) {
        val fromPages = hostSettingsPages.map { page ->
            SettingsNamespace(
                ns = page.id.substringAfterLast('#'),
                title = page.title,
                description = page.description.orEmpty(),
                module = page.module,
            )
        }
        val fromRows = rowConfigSurfaces.map { surface ->
            SettingsNamespace(
                ns = surface.id.substringAfterLast('#'),
                title = surface.title,
                description = surface.description.orEmpty(),
                module = surface.module,
            )
        }
        (fromPages + fromRows)
            .filter { it.ns.isNotBlank() }
            .distinctBy { it.ns }
    }

    /**
     * 官方设置页面背后是哪几份文档。
     *
     * 官方页面的组件是自定义界面，App 没有它们；但每份设置文档都能用 Host 投影
     * 出来的表单原生渲染。命名空间来自各官方页面自己的实现（`ui-settings-*`
     * 里读的 `configForms` 命名空间），按页面注册 id 对到文档；表里没有的页面
     * 按「页面 id 是命名空间前缀」兜底，再匹配不到就返回空（页面会说明没有可读
     * 设置文档，而不是假装打开）。
     */
    fun namespacesForRow(rowId: String, packageName: String?): List<String> {
        // 命名空间归属来自 bridge 的 host-slots（`<bundle>#<namespace>`），
        // 不再维护「页面 id → 文档」的本地映射表。
        val family = packageName
            ?.substringAfterLast("settings-")
            ?.takeIf { it.isNotBlank() && it != packageName }
        val exact = settingsNamespaces.filter { it.ns == rowId }
        if (exact.isNotEmpty()) return exact.map { it.ns }
        val prefixed = settingsNamespaces.filter { namespace ->
            listOfNotNull(rowId, family).any { prefix ->
                namespace.ns == prefix || namespace.ns.startsWith("$prefix-")
            }
        }
        return prefixed.map { it.ns }
    }

    // 官方插件分类只有一个来源：bridge 的 `client-slots/plugins.item` 注册。
    // 文案、顺序与图形都是官方客户端自己的资源。bridge 不可用时这个分组直接
    // 不出现 —— 宁可少一行，也不拿写死的分类表冒充官方数据。
    //
    // `@deepseek-ai/dsh-cordis-client-runner` 的注册要排除：它把「插槽目录示例」
    // 也当成了真实注册（每个 slot 都有一条 `id: my-entry / label: My entry`），
    // 那不是插件页面，列出来只会在设置里多一行永远打不开的「My entry」。
    // bridge 策略里标记为隐藏的 bundle（例如只提供插槽示例的 runner）不列出来。
    val hiddenBundlePackages = remember(bridgeCatalog) {
        bridgeCatalog.bundles.filter { it.hidden }.map { it.name }.toSet()
    }
    val officialConfigItems = remember(bridgeCatalog, settingsNamespaces, hiddenBundlePackages) {
        bridgeCatalog.categories
            .filter { it.packageName == null || it.packageName !in hiddenBundlePackages }
            .map { category ->
                ConfigEntryRow(
                    id = category.id,
                    title = category.label,
                    description = "",
                    iconSvg = category.iconSvg,
                    namespaces = namespacesForRow(category.id, category.packageName),
                    official = true,
                )
            }
    }
    var disabledPlugins by remember { mutableStateOf<Set<String>>(emptySet()) }

    /**
     * 三个分组的来源。
     *
     * 全部由 Host 报告的事实推导，客户端不维护「哪些是实验性」的白名单：
     *
     * - **实验性插件** = Host 标记为 `optional` 的 bundle（安装里带、但默认关闭）；
     * - **已安装插件** = 既非官方也非可选、且确实装上了的 bundle，也就是第三方；
     * - 内置配置来自 `plugins.item` 的官方条目。
     *
     * bridge 不可用时退回 Host 原生清单（没有图标，但分组仍成立）。
     */
    val derivedExperimentalBundles = remember(bridgeCatalog, pluginManagerSnapshot) {
        val fromBridge = bridgeCatalog.bundles
            .filter { it.optional && !it.hidden && !it.profileRoot }
            .map { it.toPluginBundleItem() }
            // 主列表 = 后端标记为常驻的，加上用户已启用的。
            .filter { it.pinned || it.enabled }
            .sortedBy { it.order }
        fromBridge.ifEmpty { pluginManagerSnapshot?.experimentalBundles.orEmpty() }
    }
    /**
     * 默认收起、只有「更多插件」里才出现的插件。
     *
     * 两类：一类是官方可选实验性 bundle 里被主列表收起的；另一类是官方的非可选
     * 包但设备上装不上（`availability = missing`，例如两个 subagent 包）——它们
     * 不在「实验性」定义里，可官方页面照样列出来标「未安装」，漏掉就少两行。
     */
    val derivedMoreBundles = remember(bridgeCatalog, pluginManagerSnapshot) {
        bridgeCatalog.bundles
            .map { it.toPluginBundleItem() }
            .filter { bundle ->
                !bundle.hidden && !bundle.profileRoot && bundle.official && (
                    if (bundle.isExperimental) {
                        !bundle.pinned
                    } else {
                        !bundle.installed && bundle.availability != "installation"
                    }
                    )
            }
            .sortedBy { it.order }
    }
    val derivedInstalledBundles = remember(bridgeCatalog, pluginManagerSnapshot) {
        val fromBridge = bridgeCatalog.bundles
            .filter { it.installed && !it.official && !it.optional && !it.hidden && !it.profileRoot }
            .map { it.toPluginBundleItem() }
        fromBridge.ifEmpty { pluginManagerSnapshot?.installedBundles.orEmpty() }
    }

    /**
     * 已安装的第三方「行插件」。
     *
     * 走 patch 挂上的插件没有 bundle 记录，Host 只把它们报成 Loader 行
     * （`pluginManager/listPlugins`）。只列第三方：官方模块与 bridge 自己是
     * 基础层，停用/卸载都不该出现在用户列表里。
     */
    val derivedInstalledRows = remember(pluginRows) {
        pluginRows.filter { row ->
            val module = row.moduleName
            module.isNotBlank() &&
                !module.startsWith("@deepseek-ai/") &&
                !module.startsWith("cordis:") &&
                module != BRIDGE_PLUGIN_PACKAGE &&
                row.entryId != null
        }
    }

    /**
     * 已配对的伴侣 APK 不再在「已安装插件」里单占一行：它作为所属 JS 插件的
     * 子组件出现在详情页里。没配对的（作者没声明 `dsh.plugin.jsPackage`）
     * 仍然独立成行，否则就无处可达。
     */
    val unpairedNativePlugins = remember(nativePluginItems, derivedInstalledRows, derivedInstalledBundles) {
        val pairedByRow = derivedInstalledRows
            .flatMap { row -> companionsOf(row.moduleName, nativePluginItems) }
        val pairedByBundle = derivedInstalledBundles
            .flatMap { bundle -> companionsOf(bundle.packageName, nativePluginItems) }
        val paired = (pairedByRow + pairedByBundle).map { it.pluginId }.toSet()
        nativePluginItems.filter { it.installed && it.pluginId !in paired }
    }

    /** Host 上已安装的 JS 插件名：行插件的模块名 + bundle 的包名 + App 安装器管理的包。 */
    val installedJsPluginNames = remember(
        derivedInstalledRows,
        derivedInstalledBundles,
        managedPluginPackages,
    ) {
        (derivedInstalledRows.map { it.moduleName } +
            derivedInstalledBundles.map { it.packageName } +
            managedPluginPackages).toSet()
    }

    /**
     * 伴侣独立成行时的提醒。
     *
     * 只有「作者声明了配对的 JS 插件、而它没装」才需要提醒；没声明
     * `dsh.plugin.jsPackage` 的伴侣是作者没做配对，提醒也无从对照。
     * @param plugin 列表里的伴侣。
     * @returns 需要展示的状态胶囊文案，或 null。
     */
    fun orphanCompanionNote(plugin: com.deepseek.dsh.plugin.nativehost.NativePluginItem): String? {
        val declared = plugin.jsPackage ?: return null
        val normalized = normalizeModuleName(declared)
        val installed = installedJsPluginNames.contains(normalized) ||
            installedJsPluginNames.contains(declared)
        return if (installed) null else context.getString(R.string.plugin_not_installed)
    }

    fun keyOf(item: SurfaceItem): String = "${item.pluginId}::${item.slot}"
    fun enabledOf(item: SurfaceItem): Boolean = keyOf(item) !in disabledPlugins

    /**
     * 子页面与 App 顶栏的协作。
     *
     * 详情页/「更多插件」页都渲染在本页内部，而返回按钮画在 App 的 [PageBackBar] 里，
     * 两者不在同一棵组合树：本页只能用「上报开关 + 消费信号」这一对参数跟顶栏沟通。
     * 打开子页面时报 `true`，顶栏的返回就只关子页面、不离开插件页；关完报 `false`，
     * 返回恢复成「离开插件页」。子页面自己不再画返回箭头 —— 否则一条返回会出现两次。
     */
    val detailOpen = selectedBundleName != null ||
        selectedNativePlugin != null ||
        selectedRowPlugin != null ||
        selectedConfig != null ||
        showMorePlugins ||
        selectedPlugin != null
    LaunchedEffect(detailOpen) { onDetailOpenChange(detailOpen) }
    // 离开插件页时（例如从抽屉切走）把开关复位，避免下次进页面时返回键被吃掉一次。
    DisposableEffect(Unit) {
        onDispose { onDetailOpenChange(false) }
    }
    // 顶栏返回的点击计数：只消费比上次更大的信号，避免重组时重复关闭。
    var handledBackSignal by remember { mutableStateOf(0) }
    LaunchedEffect(backSignal) {
        if (backSignal <= handledBackSignal) return@LaunchedEffect
        handledBackSignal = backSignal
        when {
            selectedConfig != null -> selectedConfig = null
            selectedNativePlugin != null -> selectedNativePlugin = null
            selectedRowPlugin != null -> selectedRowPlugin = null
            selectedBundleName != null -> selectedBundleName = null
            showMorePlugins -> showMorePlugins = false
            selectedPlugin != null -> selectedPlugin = null
        }
    }

    val bundleDetail = selectedBundleName?.let { name ->
        derivedExperimentalBundles.firstOrNull { it.packageName == name }
            ?: derivedInstalledBundles.firstOrNull { it.packageName == name }
            ?: derivedMoreBundles.firstOrNull { it.packageName == name }
    }
    // 从 bundle 详情点开伴侣 APK 详情时，先让伴侣详情占据整页；返回后回到 bundle
    // 详情（`selectedBundle` 仍保留）。
    if (bundleDetail != null && selectedNativePlugin == null) {
        CompositionLocalProvider(LocalDshPanelColors provides colors) {
            Box(modifier = modifier.fillMaxSize().background(colors.bgBase)) {
                BundleDetailContent(
                    bundle = bundleDetail,
                    companions = companionsOf(bundleDetail.packageName, nativePluginItems),
                    companionEnabled = { companion -> companion.pluginId !in disabledNativePlugins },
                    onToggleCompanion = { companion, enabled ->
                        disabledNativePlugins = if (enabled) {
                            disabledNativePlugins - companion.pluginId
                        } else {
                            disabledNativePlugins + companion.pluginId
                        }
                    },
                    onOpenCompanion = { companion -> selectedNativePlugin = companion },
                    onUninstallCompanion = onUninstallCompanion,
                    onToggle = { enabled -> onToggleBundleAwait(bundleDetail.packageName, enabled) },
                    onToggleComponent = onToggleComponent,
                    onUninstall = { onUninstall(SurfaceItem(pluginId = bundleDetail.packageName, slot = "host.bundle", title = bundleDetail.title)) },
                    pluginHooks = PluginUiHooks(
                        loadDocument = loadPluginSettings,
                        saveDocument = savePluginSettings,
                        loadCredential = loadCredential,
                        saveCredential = saveCredential,
                        speech = speechHooks,
                    ),
                )
            }
        }
        return
    }
    CompositionLocalProvider(LocalDshPanelColors provides colors) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgBase)
        ) {
            val detail = selectedPlugin
            val nativeDetail = selectedNativePlugin
            val configDetail = selectedConfig
            val rowDetail = selectedRowPlugin
            if (configDetail != null) {
                // 「内置插件配置」的详情。
                //
                // 字段、标签与控件类型全部来自 Host 的 `settings/describe`
                // schema（bridge 投影），不再逐页复刻官方客户端：官方页面改了
                // 字段，这里跟着 Host 一起变；官方页面没注册的命名空间同样能打开。
                PluginSettingsEditorPage(
                    title = configDetail.title,
                    namespaces = configDetail.namespaces,
                    load = loadPluginSettings,
                    save = savePluginSettings,
                )
            } else if (nativeDetail != null) {
                NativePluginDetailPage(
                    item = nativeDetail,
                    onBack = { selectedNativePlugin = null },
                    onUninstall = { item ->
                        onUninstallCompanion(item)
                        selectedNativePlugin = null
                    },
                )
            } else if (rowDetail != null) {
                // 已安装的第三方行插件：伴侣 APK 在这里作为子组件出现。
                InstalledPluginDetailPage(
                    plugin = rowDetail,
                    companions = companionsOf(rowDetail.moduleName, nativePluginItems),
                    companionEnabled = { companion -> companion.pluginId !in disabledNativePlugins },
                    onToggleCompanion = { companion, enabled ->
                        disabledNativePlugins = if (enabled) {
                            disabledNativePlugins - companion.pluginId
                        } else {
                            disabledNativePlugins + companion.pluginId
                        }
                    },
                    onOpenCompanion = { companion -> selectedNativePlugin = companion },
                    onUninstallCompanion = onUninstallCompanion,
                    onToggle = { enabled ->
                        rowDetail.entryId?.let { entryId -> onTogglePluginRow(entryId, enabled) }
                    },
                    onUninstall = {
                        onUninstall(
                            SurfaceItem(
                                pluginId = rowDetail.moduleName,
                                slot = "plugin.row",
                                title = rowDetail.title,
                            )
                        )
                        selectedRowPlugin = null
                    },
                )
            } else if (showMorePlugins) {
                // 「更多插件」单独页面：实验性分组头部的「更多 ›」进入。
                // 返回由 App 顶栏承载（`backSignal`），页面内不再重复画箭头。
                MorePluginsScreen(
                    bundles = derivedMoreBundles,
                    onOpen = { selectedBundleName = it.packageName },
                    onToggle = { bundle, enabled -> onToggleBundle(bundle.packageName, enabled) },
                )
            } else if (detail != null) {
                PluginDetailContent(
                    item = detail,
                    enabled = enabledOf(detail),
                    onEnabledChange = { enabled ->
                        disabledPlugins = if (enabled) disabledPlugins - keyOf(detail)
                        else disabledPlugins + keyOf(detail)
                    },
                    onBack = { selectedPlugin = null },
                    onUninstall = {
                        onUninstall(detail)
                        selectedPlugin = null
                    },
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 分段页签在最顶；页头（含「添加插件」按钮）两个 tab 共用，
                    // 按钮的文案与动作随 tab 变：插件 tab 装 JS 插件，原生 tab 装伴侣 APK。
                    PluginsNativeTabs(
                        selectedIndex = pluginsTabIndex,
                        onSelect = { pluginsTabIndex = it },
                        labels = if (marketEnabled) {
                            listOf(
                                stringResource(R.string.nav_plugins),
                                stringResource(R.string.plugins_tab_store),
                                stringResource(R.string.plugins_tab_discover),
                            )
                        } else {
                            listOf(
                                stringResource(R.string.nav_plugins),
                                stringResource(R.string.plugins_tab_store),
                            )
                        },
                        modifier = Modifier.padding(
                            start = PluginsDimens.PageHorizontal,
                            end = PluginsDimens.PageHorizontal,
                            top = PluginsDimens.PageTop,
                            bottom = 16.dp,
                        ),
                    )
                    PluginsPageHead(
                        isNativeTab = pluginsTabIndex == 1,
                        onAddPluginClick = {
                            if (pluginsTabIndex == 1) {
                                // 原生 tab：安装原生伴侣（商店浏览由列表承载，这里给安装入口）。
                                showInstallDialog = true
                            } else {
                                showInstallDialog = true
                            }
                        },
                        addMenuOpen = addMenuOpen,
                        onToggleAddMenu = { addMenuOpen = !addMenuOpen },
                        onDismissAddMenu = { addMenuOpen = false },
                        onRefresh = { /* 本地目录刷新由 Host 推送 */ },
                        onCreatePlugin = onCreatePlugin,
                        modifier = Modifier.padding(
                            start = PluginsDimens.PageHorizontal,
                            end = PluginsDimens.PageHorizontal,
                        ),
                    )
                    if (pluginsTabIndex == 1) {
                        // 「插件商店」tab：GitHub topic dsh-mobile-plugin 远程发现。
                        PluginStoreTab(
                            items = nativePluginItems,
                            initialRepoUrl = storeSeedUrl,
                            backSignal = backSignal,
                            onDetailOpenChange = onDetailOpenChange,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (pluginsTabIndex == 2) {
                        // 「发现」tab：浏览器市场（官方 client-ui-plugin-market 的 plugins.tab）。
                        val hooks = marketBackend?.let { backend ->
                            MarketHooks(
                                loadIndexes = backend.loadIndexes,
                                search = backend.search,
                                saveIndexes = backend.saveIndexes,
                                // 市场安装用不着这里的进度（「发现」tab 自己的页面自带安装态），
                                // 回调直接丢掉；签名仍要两个参数。
                                install = { spec -> onInstallPlugin(spec) { _, _ -> } },
                            )
                        }
                        if (hooks == null) {
                            Text(
                                text = stringResource(R.string.market_no_market),
                                color = colors.labelTertiary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(PluginsDimens.PageHorizontal, 16.dp),
                            )
                        } else {
                            PluginMarketTab(
                                hooks = hooks,
                                onInstalled = { },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PluginsDimens.PageHorizontal,
                        end = PluginsDimens.PageHorizontal,
                        bottom = PluginsDimens.PageBottom,
                    ),
                ) {
                    if (officialConfigItems.isNotEmpty()) {
                        item(key = "builtin_config_head") {
                            PluginsGroupTitle(
                                text = stringResource(R.string.plugins_builtin_config),
                                modifier = Modifier.padding(
                                    top = PluginsDimens.SectionGap,
                                    bottom = PluginsDimens.GroupTitleGap,
                                ),
                            )
                        }
                        itemsIndexed(officialConfigItems, key = { index, item -> "official_config_${index}_${item.id}" }) { index, item ->
                            OfficialConfigRow(
                                item = item,
                                showDivider = index != officialConfigItems.lastIndex,
                                onClick = { selectedConfig = item },
                            )
                        }
                    }

                    // bridge 报告的非致命问题（例如某个 bundle 无法求值、图标缺失）。
                    // 展示出来而不是隐藏，是因为它解释了「为什么这里少了东西」。
                    if (bridgeWarnings.isNotEmpty()) {
                        item(key = "bridge_warnings") {
                            Text(
                                text = stringResource(R.string.plugin_bridge_warning, bridgeWarnings.joinToString("; ")),
                                color = colors.labelTertiary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = PluginsDimens.SectionGap),
                            )
                        }
                    }

                    val experimentalBundles = derivedExperimentalBundles
                    if (experimentalBundles.isNotEmpty()) {
                        item(key = "experimental_group_head") {
                            // 实验性分组头部：左标题，右「更多 ›」（官方页面一致——更多插件从这里进）。
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = PluginsDimens.SectionGap,
                                        bottom = PluginsDimens.GroupTitleGap,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.plugins_experimental),
                                    color = colors.labelPrimary,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (derivedMoreBundles.isNotEmpty()) {
                                    Text(
                                        text = stringResource(R.string.plugins_more) + " ›",
                                        color = colors.labelTertiary,
                                        fontSize = 12.5.sp,
                                        modifier = Modifier.clickable { showMorePlugins = true },
                                    )
                                }
                            }
                        }
                        itemsIndexed(experimentalBundles, key = { index, bundle -> "experimental_${index}_${bundle.packageName}" }) { index, bundle ->
                            InstalledHostBundleRow(
                                bundle = bundle,
                                showDivider = index != experimentalBundles.lastIndex,
                                onOpen = { selectedBundleName = bundle.packageName },
                                onToggle = { enabled -> onToggleBundle(bundle.packageName, enabled) },
                                onUninstall = { },
                                locked = bundle.locked,
                            )
                        }
                    }

                    // 默认收起的实验性插件：与官方页面一致，从实验性分组头部的「更多 ›」
                    // 进入单独页面，不再在主列表里占一节。
                    // （原「更多插件」整组已移除，见 MorePluginsScreen。）

                    // 行级配置入口（plugins.row.config）不进主列表：官方页面把一行的
                    // 配置放在它所属组合包的详情页里，主列表多出一节「插件配置」既不是
                    // 官方结构，也和上面的分组重复。

                    // 1. 已安装插件：只列第三方（官方与实验性已各自成组）
                    item(key = "installed_group_head") {
                        PluginsGroupTitle(
                            text = stringResource(R.string.plugins_installed_section),
                            modifier = Modifier.padding(
                                top = PluginsDimens.SectionGap,
                                bottom = PluginsDimens.GroupTitleGap,
                            ),
                        )
                    }

                    val hostBundles = derivedInstalledBundles
                    val hostRows = derivedInstalledRows
                    if (surfaces.isEmpty() && hostBundles.isEmpty() && hostRows.isEmpty() &&
                        unpairedNativePlugins.isEmpty()
                    ) {
                        item(key = "installed_empty") {
                            Text(
                                text = stringResource(R.string.plugins_empty),
                                color = colors.labelTertiary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                    } else {
                        // 1.1 先渲染 Host 端实际安装的 Bundle
                        if (hostBundles.isNotEmpty()) {
                            itemsIndexed(
                                items = hostBundles,
                                key = { index, bundle -> "host_bundle_${index}_${bundle.packageName}" },
                            ) { index, bundle ->
                                InstalledHostBundleRow(
                                    bundle = bundle,
                                    showDivider = index != hostBundles.lastIndex ||
                                        surfaces.isNotEmpty() || hostRows.isNotEmpty(),
                                    onOpen = { selectedBundleName = bundle.packageName },
                                    onToggle = { enabled ->
                                        onToggleBundle(bundle.packageName, enabled)
                                    },
                                    onUninstall = {
                                        onUninstall(
                                            SurfaceItem(
                                                pluginId = bundle.packageName,
                                                slot = "host.bundle",
                                                title = bundle.title
                                            )
                                        )
                                    },
                                    locked = bundle.locked,
                                )
                            }
                        }

                        // 1.2 再渲染第三方「行插件」：patch 挂上的插件没有 bundle
                        // 记录，Host 只把它们报成 Loader 行；不列出来，用户装了插件
                        // 也会看到空列表。
                        if (hostRows.isNotEmpty()) {
                            itemsIndexed(
                                items = hostRows,
                                key = { index, row -> "host_row_${index}_${row.moduleName}" },
                            ) { index, row ->
                                InstalledPluginRowItem(
                                    row = row,
                                    showDivider = index != hostRows.lastIndex || surfaces.isNotEmpty(),
                                    onClick = { selectedRowPlugin = row },
                                    onToggle = { enabled ->
                                        row.entryId?.let { entryId ->
                                            onTogglePluginRow(entryId, enabled)
                                        }
                                    },
                                    onUninstall = {
                                        onUninstall(
                                            SurfaceItem(
                                                pluginId = row.moduleName,
                                                slot = "plugin.row",
                                                title = row.title,
                                            )
                                        )
                                    },
                                )
                            }
                        }

                        // 1.2 再渲染本地 Surface 交互界面项
                        if (surfaces.isNotEmpty()) {
                            itemsIndexed(
                                items = surfaces,
                                key = { index, item -> "surface_${index}_${item.pluginId}_${item.slot}" },
                            ) { index, item ->
                                InstalledSurfaceRow(
                                    item = item,
                                    showDivider = index != surfaces.lastIndex || unpairedNativePlugins.isNotEmpty(),
                                    onOpen = { onOpenSurface(item) },
                                    onOpenDetail = { selectedPlugin = item },
                                )
                            }
                        }

                        // 1.3 没配对的伴侣 APK：同行样式 + 「原生」标记 + 开关。
                        // 配对上的伴侣已经作为子组件进了所属 JS 插件详情页。
                        if (unpairedNativePlugins.isNotEmpty()) {
                            itemsIndexed(
                                items = unpairedNativePlugins,
                                key = { index, p -> "native_${index}_${p.pluginId}" },
                            ) { index, plugin ->
                                NativeInstalledRow(
                                    plugin = plugin,
                                    showDivider = index != unpairedNativePlugins.lastIndex,
                                    enabled = plugin.pluginId !in disabledNativePlugins,
                                    onToggle = { enabled ->
                                        disabledNativePlugins =
                                            if (enabled) disabledNativePlugins - plugin.pluginId
                                            else disabledNativePlugins + plugin.pluginId
                                    },
                                    onClick = { selectedNativePlugin = plugin },
                                    note = orphanCompanionNote(plugin),
                                )
                            }
                        }
                    }
                }
                    }
                }
            }
        }

        // 按包名/地址添加插件对话框（Modal 材质 + 40dp 输入 + 整宽主按钮）。
        if (showInstallDialog) {
            PluginsInstallDialog(
                packageInput = pluginPackageInput,
                onPackageInputChange = { pluginPackageInput = it },
                statusMessage = installStatusMessage,
                busy = installBusy,
                progress = installProgress,
                step = installStep,
                onInstall = {
                    val spec = pluginPackageInput.trim()
                    // 双击防抖：busy 期间主按钮已禁用，这里再拦一层（输入法回车 / 无障碍点击）。
                    if (spec.isNotEmpty() && !installBusy) {
                        // 老的进度/结论先清掉，否则会顶着上一次的「安装失败」画进度条。
                        installStatusMessage = null
                        installBusy = true
                        installProgress = 0f
                        installStep = context.getString(R.string.plugins_install_preparing)
                        // 安装是 suspend（Host 安装耗时以秒计），包一层协程；进度回调可能在
                        // 安装器的 IO 线程上触发，Compose 快照状态允许跨线程写入。
                        scope.launch {
                            try {
                                val result = runCatching {
                                    onInstallPlugin(spec) { fraction, step ->
                                        installProgress = fraction.coerceIn(0f, 1f)
                                        installStep = step
                                    }
                                }
                                // 成功/失败都留在对话框里回显，不自动关闭：用户要看结论。
                                installStatusMessage = result.fold(
                                    onSuccess = {
                                        context.getString(R.string.plugins_install_success, spec)
                                    },
                                    onFailure = {
                                        context.getString(
                                            R.string.plugins_install_failed,
                                            it.message ?: it.toString(),
                                        )
                                    },
                                )
                                if (result.isSuccess) pluginPackageInput = ""
                                // 对话框在安装途中被收起时，结论不能只写进看不见的状态：
                                // 用系统 Toast 补一条，避免用户以为点了没反应。
                                if (!showInstallDialog) {
                                    installStatusMessage?.let { message ->
                                        android.widget.Toast.makeText(
                                            context,
                                            message,
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            } finally {
                                // 成功、失败、被取消都要复位 busy，否则进度永远停在最后一帧。
                                installBusy = false
                            }
                        }
                    }
                },
                onDismiss = {
                    // 安装继续在后台跑，但允许收起对话框：拒绝关闭只会在安装卡住
                    //（本机安装含重启 Host，最长 90 秒）时把界面困成一个退不出的弹窗。
                    showInstallDialog = false
                    if (!installBusy) installStatusMessage = null
                },
            )
        }
    }
}

/**
 * 客户端与 Host 之间的兼容层插件包名。
 *
 * 与 `runtime:host` 的 `BridgePluginManifest.DEFAULT_PACKAGE` 保持一致：那边负责
 * 安装与版本判断，这里负责在插件页里禁止停用/卸载。两边都以常量而非字符串
 * 字面量出现，改名时只需改一处定义。
 */
/**
 * 把 bridge 的 bundle 摘要转成列表行模型。
 *
 * 图标是 Host 给出的 `meta.icon` data URL，原样传给行渲染；没有就是没有，
 * 不用本地常量顶替。
 * @param summary bridge 的 bundle 摘要。
 * @returns 列表行模型。
 */
private fun BridgePluginBundleSummary.toPluginBundleItem(): PluginBundleItem = PluginBundleItem(
    packageName = name,
    title = title,
    description = description.orEmpty(),
    isExperimental = optional,
    enabled = enabled,
    official = official,
    installed = installed,
    availability = availability,
    version = version,
    removable = removable,
    error = errorCode,
    iconData = iconDataUrl,
    profileRoot = profileRoot,
    pinned = pinned,
    order = order,
    hidden = hidden,
    market = market,
    locked = locked,
    rows = rows.map { row ->
        PluginComponentInfo(
            rowId = row.rowId,
            moduleName = row.moduleName,
            title = row.title,
            description = row.description.orEmpty(),
            // Host 报的开关状态就是真实状态：不要写死 `true`，否则详情页的
            // 「N 运行中」和每一行的开关都会和 Host 不一致。
            enabled = row.enabled == true,
            entryId = row.entryId,
        )
    },
)

private const val BRIDGE_PLUGIN_PACKAGE = "dsh-mobile-bridge-plugin"

/** 包名归一化：取最后一段并去掉版本后缀，用于跨来源比对。 */
private fun normalizeModuleName(value: String): String =
    value.substringAfterLast('/').substringBefore('@')

/**
 * 一个 JS 插件配对的伴侣 APK。
 *
 * 配对依据是 APK manifest 的 `dsh.plugin.jsPackage`（作者声明），不按包名猜：
 * 声明缺失时这个伴侣不挂到任何插件下，而是留在列表里独立成行。
 *
 * @param moduleName JS 插件在 Host 里的模块名（Loader 行的 `moduleName`）。
 * @param items 已扫描到的伴侣 APK。
 * @returns 配对的伴侣，按扫描顺序。
 */
internal fun companionsOf(
    moduleName: String,
    items: List<com.deepseek.dsh.plugin.nativehost.NativePluginItem>,
): List<com.deepseek.dsh.plugin.nativehost.NativePluginItem> =
    items.filter { item ->
        val declared = item.jsPackage ?: return@filter false
        item.installed && (declared == moduleName || normalizeModuleName(declared) == moduleName)
    }



/**
 * 一条行级配置入口：标题 + 说明，点开所属 bundle 的详情页。
 *
 * 与设置页的插件条目同一套数据，只是落在插件页：位置由 slot 决定，客户端不自己判断。
 */
@Composable
private fun RowConfigRow(
    surface: com.deepseek.dsh.core.bridge.BridgeSlotSurface,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgModulePlatform),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_nav_plugin),
                    contentDescription = null,
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = surface.title,
                    color = colors.labelPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = surface.description ?: surface.id.substringAfter("#"),
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.borderL2),
            )
        }
    }
}

/**
 * 这个包是否随 DSH 安装一起提供。
 *
 * `@deepseek-ai/dsh-experimental-*` 是官方发布但默认关闭的可选包，它们和基础层
 * 一样属于「内置」；只有用户自己装的包才需要按包名区分来源。
 * @param packageName bundle 的包名。
 * @returns 是否内置。
 */
private fun isBuiltInBundle(packageName: String): Boolean =
    packageName.startsWith("@deepseek-ai/")

/**
 * 这个 bundle 是否是客户端必需的基础设施。
 *
 * bridge 是 App 与 Host 的契约对端：停用它，插件页会失去官方分类与图标，
 * 整页退化成原生清单。因此它只能在设置里升级，不能在插件页关掉。
 * @param packageName bundle 的包名。
 */
/** 页面与卡片的间距/圆角常量（PluginManagerPage.module.css + ui-primitives）。 */
internal object PluginsDimens {    /** 手机页面左右安全边距（clamp(24px, 4vw, 48px) 的下限）。 */
    val PageHorizontal = 24.dp

    /** 页面顶部内距（header 的 28px 在手机收窄）。 */
    val PageTop = 16.dp

    /** 页面底部内距（48px 在手机收窄）。 */
    val PageBottom = 32.dp

    /** 区块间距：`.page > *` / `.installedPanel` 的 32px 行距。 */
    val SectionGap = 32.dp

    /** 分组标题到卡片：`.group` 的 8px。 */
    val GroupTitleGap = 8.dp
}

/** 页头：左标题/引导语，右上刷新 + 官方拆分「添加插件」按钮（主按钮 + 下拉）。 */
@Composable
private fun PluginsPageHead(
    isNativeTab: Boolean = false,
    onAddPluginClick: () -> Unit,
    addMenuOpen: Boolean,
    onToggleAddMenu: () -> Unit,
    onDismissAddMenu: () -> Unit,
    onRefresh: () -> Unit,
    onCreatePlugin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        // 按钮保持标题右侧原位；说明单独占满整行，避免英文长文案被按钮挤窄。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = if (isNativeTab) stringResource(R.string.plugins_store_title)
                    else stringResource(R.string.nav_plugins),
                    color = colors.labelPrimary,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // 官方刷新：28dp ghost 图标按钮
            val refreshInteraction = remember { MutableInteractionSource() }
            val refreshPressed by refreshInteraction.collectIsPressedAsState()
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (refreshPressed) colors.interactiveHover else Color.Transparent)
                    .clickable(
                        interactionSource = refreshInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onRefresh,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DshIcons.RefreshOutline,
                    contentDescription = stringResource(R.string.plugins_refresh),
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box {
                PluginsSplitAddButton(
                    expanded = addMenuOpen,
                    primaryLabel = if (isNativeTab) stringResource(R.string.plugins_install_native)
                    else stringResource(R.string.action_add_plugin),
                    onPrimaryClick = {
                        onDismissAddMenu()
                        onAddPluginClick()
                    },
                    onChevronClick = onToggleAddMenu,
                )
                PluginsAddMenu(
                    expanded = addMenuOpen,
                    onDismiss = onDismissAddMenu,
                    onInstallExternal = {
                        onDismissAddMenu()
                        onAddPluginClick()
                    },
                    onCreatePlugin = {
                        onDismissAddMenu()
                        onCreatePlugin()
                    },
                )
            }
            } // actions row
        } // title row
        Text(
            text = if (isNativeTab) stringResource(R.string.plugins_store_subtitle)
            else stringResource(R.string.plugins_subtitle),
            color = colors.labelSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 官方拆分主按钮：左「+ 添加插件/安装原生插件」，右侧 32dp 下拉段，中间 0.5dp 分隔线。 */
@Composable
private fun PluginsSplitAddButton(
    expanded: Boolean,
    primaryLabel: String = "",
    onPrimaryClick: () -> Unit,
    onChevronClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    val primaryInteraction = remember { MutableInteractionSource() }
    val primaryPressed by primaryInteraction.collectIsPressedAsState()
    val chevronInteraction = remember { MutableInteractionSource() }
    val chevronPressed by chevronInteraction.collectIsPressedAsState()
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(if (primaryPressed) colors.buttonPrimaryHover else colors.buttonPrimaryFill)
                .clickable(
                    interactionSource = primaryInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onPrimaryClick,
                )
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = DshIcons.PlusOutline,
                contentDescription = null,
                tint = colors.labelPrimaryForeground,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = primaryLabel,
                color = colors.labelPrimaryForeground,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .height(32.dp)
                .background(colors.labelPrimaryForeground.copy(alpha = 0.3f)),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (chevronPressed) colors.buttonPrimaryHover else colors.buttonPrimaryFill)
                .clickable(
                    interactionSource = chevronInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onChevronClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_down),
                contentDescription = stringResource(R.string.plugin_add_method),
                tint = colors.labelPrimaryForeground,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** 官方「添加插件」下拉：安装第三方插件 / 让 Agent 创建插件（标题 13-20 + 说明 12-18）。 */
@Composable
private fun PluginsAddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInstallExternal: () -> Unit,
    onCreatePlugin: () -> Unit,
) {
    AnchoredMenuPopup(
        expanded = expanded,
        onDismiss = onDismiss,
        menuWidth = 300.dp,
        align = DshMenuAlign.End,
    ) {
        AddPluginMenuItem(
            iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_download_outline,
            title = stringResource(R.string.plugins_add_third_party),
            description = stringResource(R.string.plugins_add_third_party_desc),
            onClick = onInstallExternal,
        )
        AddPluginMenuItem(
            iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_agent_preset_outline,
            title = stringResource(R.string.plugins_agent_create),
            description = stringResource(R.string.plugins_agent_create_desc),
            onClick = onCreatePlugin,
        )
    }
}

/** 两行式菜单项：14dp 图标 + 标题 13/20 + 说明 12/18 三级色。 */
@Composable
private fun AddPluginMenuItem(
    iconResId: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    DshMenuRow(onClick = onClick) {
        DshMenuIcon(iconResId, size = 14.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = colors.labelPrimary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = description,
                color = colors.labelTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** 分组标题：14/22 500，一级色。 */
@Composable
private fun PluginsGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalDshPanelColors.current.labelPrimary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

/** SDUI 声明式插件特性卡：卡片头（48dp 图标框 + 标题/说明 + 尾部 sm 主按钮「体验」）。 */
@Composable
private fun SduiFeatureCard(
    onOpenSdui: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    // .cardHead 的 8px 上下内距；水平内距因手机不保留 -8px 外扩而收为 0，
    // 让图标框与页头标题对齐（同样对齐页面左边线）。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PluginsIconFrame(
            icon = Icons.Default.Widgets,
            frameSize = 48.dp,
            frameRadius = 16.dp,
            glyphSize = 20.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_sdui_title),
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.plugin_sdui_desc),
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PluginsButton(
            text = stringResource(R.string.plugin_try),
            onClick = onOpenSdui,
            height = 28.dp,
            horizontalPadding = 10.dp,
            radius = 8.dp,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun BundleDetailContent(
    bundle: PluginBundleItem,
    companions: List<com.deepseek.dsh.plugin.nativehost.NativePluginItem>,
    companionEnabled: (com.deepseek.dsh.plugin.nativehost.NativePluginItem) -> Boolean,
    onToggleCompanion: (com.deepseek.dsh.plugin.nativehost.NativePluginItem, Boolean) -> Unit,
    onOpenCompanion: (com.deepseek.dsh.plugin.nativehost.NativePluginItem) -> Unit,
    onUninstallCompanion: (com.deepseek.dsh.plugin.nativehost.NativePluginItem) -> Unit,
    /** 拨动 bundle 总开关；返回 Host 是否接受（失败由调用方 toast，界面回弹）。 */
    onToggle: suspend (Boolean) -> Boolean,
    onToggleComponent: (String, Boolean) -> Unit,
    onUninstall: () -> Unit,
    pluginHooks: PluginUiHooks,
) {
    val colors = LocalDshPanelColors.current
    // 开关双向：点下立刻反映在界面上；Host 确认后由最新的 bundle.enabled 纠正，
    // 失败或 Host 重载期间也不会停留在旧快照上「看起来关不掉」。
    var enabledLocal by remember(bundle.packageName) { mutableStateOf(bundle.enabled) }
    LaunchedEffect(bundle.enabled) { enabledLocal = bundle.enabled }
    val toggleScope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PluginsDimens.PageHorizontal, vertical = PluginsDimens.PageTop),
    ) {
        // 返回不放这里：App 顶栏的 [PageBackBar] 已经在页面顶部画了返回，
        // 子页面再画一条会让顶部出现两条返回（用户明确要求复用顶栏）。
        // 头部：真实图标 + 标题 / 描述 / 包名，成一块；右侧只放开关或「必需」标记。
        Row(verticalAlignment = Alignment.Top) {
            BundleArtwork(bundle)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bundle.title,
                        color = colors.labelPrimary,
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!bundle.version.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("v${bundle.version}", color = colors.labelTertiary, fontSize = 12.sp)
                    }
                }
                if (bundle.isExperimental) {
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.plugin_experimental_tag), color = colors.labelSecondary, fontSize = 11.sp)
                }
                // 标题就是包名时不再重复一遍。
                if (bundle.title != bundle.packageName) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = bundle.packageName,
                        color = colors.labelTertiary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bundle.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = bundle.description,
                        color = colors.labelSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                if (bundle.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.plugin_error_prefix, bundle.error ?: ""), color = colors.stateErrorPrimary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (bundle.locked) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgModulePlatform)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (bundle.enabled) stringResource(R.string.plugin_required_enabled) else stringResource(R.string.plugin_required),
                        color = colors.labelTertiary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                DshSwitch(
                    checked = enabledLocal,
                    onCheckedChange = { next ->
                        // 先乐观更新；Host 拒绝就回弹，失败提示由调用方 toast。
                        enabledLocal = next
                        toggleScope.launch {
                            val ok = onToggle(next)
                            if (!ok) enabledLocal = bundle.enabled
                        }
                    },
                )
            }
        }
        // 插件自己的原生 UI：官方把 `plugins.bundle.config` 画在「描述」和「包含的
        // 组件」之间（slot 契约明确要求），所以设置面板放在这里，而不是详情底部。
        PluginUis.forBundle(bundle.packageName)?.let { pluginUi ->
            Spacer(Modifier.height(24.dp))
            pluginUi.Section(pluginHooks)
        }
        Spacer(Modifier.height(28.dp))
        // 主 bundle 关掉时，它包含的行全部不运行：组件状态与开关都要跟着总开关
        // 立刻刷新（否则会出现「总开关关着、子组件还显示运行中」的错觉）。
        val bundleOn = enabledLocal
        Text(
            text = stringResource(
                R.string.plugin_components_summary,
                bundle.rows.size + companions.size,
                if (bundleOn) bundle.rows.count { it.enabled } else 0,
            ),
            color = colors.labelPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        bundle.rows.forEach { component ->
            val componentRunning = bundleOn && component.enabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BundleArtwork(bundle = bundle, frameSize = 34.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(component.title, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(component.description.ifBlank { component.moduleName }, color = colors.labelTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(component.moduleName, color = colors.labelTertiary, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                }
                // 开关反映这一行的真实状态（Host 的 Loader inventory），拨动后
                // 由 Host 只重载这一行；Host 没把这一行解析成 Loader 入口或总开关
                // 关着时没有可拨的目标，开关保持禁用而不是假装能开。
                Text(
                    text = if (componentRunning) stringResource(R.string.plugins_status_running) else stringResource(R.string.plugins_status_stopped),
                    color = if (componentRunning) colors.labelSecondary else colors.labelTertiary,
                    fontSize = 11.sp,
                )
                DshSwitch(
                    checked = componentRunning,
                    enabled = bundleOn && component.entryId != null,
                    onCheckedChange = { enabled ->
                        component.entryId?.let { entryId ->
                            onToggleComponent(entryId, enabled)
                        }
                    },
                )
            }
        }
        // 配对的伴侣 APK 是同一份插件能力的原生界面，作为子组件列在这里。
        companions.forEach { companion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCompanion(companion) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BundleArtwork(bundle = bundle, frameSize = 34.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(companion.name, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.bgModulePlatform)
                                .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(stringResource(R.string.plugins_native_badge), color = colors.labelSecondary, fontSize = 10.sp, lineHeight = 12.sp)
                        }
                    }
                    Text(companion.description, color = colors.labelTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(companion.pluginId, color = colors.labelTertiary, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                }
                DshSwitch(
                    checked = companionEnabled(companion),
                    onCheckedChange = { enabled -> onToggleCompanion(companion, enabled) },
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onUninstallCompanion(companion) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_trash_outline),
                        contentDescription = stringResource(R.string.plugins_uninstall_companion),
                        tint = colors.stateErrorPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.plugins_source_info), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        // 代码来源：DSH 自己的包（含 `@deepseek-ai/dsh-experimental-*` 这些可选实验
        // 包）都随安装提供，标「内置」；第三方包才显示包名，用户据此判断来源。
        val builtIn = isBuiltInBundle(bundle.packageName)
        DetailFactRow(
            label = stringResource(R.string.plugins_code_source),
            value = if (builtIn) stringResource(R.string.plugin_builtin) else bundle.packageName,
            monospace = !builtIn,
        )
        DetailFactRow(label = stringResource(R.string.plugin_current_version), value = bundle.version ?: "—")
        if (bundle.removable && !bundle.locked) {
            Spacer(Modifier.height(16.dp))
            UninstallButton(onClick = onUninstall)
        }
    }
}

/**
 * 一个插件包或 row 的图标。
 *
 * Host 的 `meta.icon` 有两种形态：位图 data URL 与 SVG data URL。规范里实验性
 * 插件的图标都是 SVG，只按位图解码会让整列回落成占位图标，所以两种都认。
 */

/**
 * 把 SVG 的 data URL 解成可以交给 [DshSvgIcon] 的文本。
 *
 * 支持 `data:image/svg+xml;base64,...` 与 `data:image/svg+xml;utf8,...`/裸文本；
 * 不是 SVG 的 data URL 返回 null，调用方继续按位图处理。
 * @param dataUrl Host 给出的 `meta.icon`。
 * @returns SVG 文本，或 null。
 */
private fun decodeSvgDataUrl(dataUrl: String): String? {
    if (!dataUrl.startsWith("data:image/svg+xml", ignoreCase = true)) return null
    val payload = dataUrl.substringAfter(",", missingDelimiterValue = "")
    if (payload.isEmpty()) return null
    if (dataUrl.substringBefore(",").contains("base64", ignoreCase = true)) {
        return runCatching {
            String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.contains("<svg", ignoreCase = true) }
    }
    return runCatching { java.net.URLDecoder.decode(payload, "UTF-8") }
        .getOrNull()
        ?.takeIf { it.contains("<svg", ignoreCase = true) }
}


/**
 * 一个 bundle 的图形：有 Host 给的 `meta.icon` 就用它，没有才回落到通用图标。
 *
 * 实验性插件的图标是 SVG data URL，[PluginMetaIcon] 两种编码都认；这里只负责
 * 选出用哪个，以及在没有图标时保持版式不塌。
 * @param bundle 要画的 bundle。
 * @param frameSize 外框边长。
 */
@Composable
private fun BundleArtwork(bundle: PluginBundleItem, frameSize: Dp = 48.dp) {
    if (bundle.iconData.isNullOrBlank()) {
        PluginsIconFrame(
            icon = Icons.Default.Build,
            frameSize = frameSize,
            frameRadius = if (frameSize >= 44.dp) 16.dp else 10.dp,
            glyphSize = if (frameSize >= 44.dp) 22.dp else 16.dp,
        )
        return
    }
    Box(
        modifier = Modifier
            .size(frameSize)
            .clip(RoundedCornerShape(if (frameSize >= 44.dp) 16.dp else 10.dp))
            .background(LocalDshPanelColors.current.bgModulePlatform),
        contentAlignment = Alignment.Center,
    ) {
        PluginMetaIcon(
            iconData = bundle.iconData,
            size = frameSize * 0.62f,
            radius = 0.dp,
            glyphSize = 16.dp,
        )
    }
}

@Composable
private fun PluginMetaIcon(iconData: String?, size: Dp, radius: Dp, glyphSize: Dp) {
    val svgText = remember(iconData) { iconData?.let(::decodeSvgDataUrl) }
    val bitmap = remember(iconData) {
        if (svgText != null || iconData.isNullOrBlank() || !iconData.contains(",")) null else runCatching {
            val bytes = Base64.decode(iconData.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (svgText != null) {
        DshSvgIcon(
            svg = svgText,
            size = size,
            tint = LocalDshPanelColors.current.labelPrimary,
            modifier = Modifier.size(size),
        )
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(radius)),
        )
    } else {
        PluginsIconFrame(
            icon = Icons.Default.Build,
            frameSize = size,
            frameRadius = radius,
            glyphSize = glyphSize,
        )
    }
}

/** 一个官方 `plugins.item` 分类之外的设置入口 id。 */
private const val OTHER_SETTINGS_ROW_ID = "__other_settings__"

/**
 * 官方客户端 bundle 里的插槽目录示例包。
 *
 * 它把文档里的示例注册（`id: my-entry / label: My entry`）也当成了真实注册，
 * 每个 slot 都有一条；那不是插件页面，列出来只会在设置里多一行打不开的
 * 「My entry」，所以在装配「内置插件配置」时整体排除。
 */
@Composable
private fun OfficialConfigRow(
    item: ConfigEntryRow,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, colors.borderL3, RoundedCornerShape(12.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item.iconSvg != null) {
                    // 官方图形由 bridge 渲染成 SVG 后传下来，客户端直接画矢量。
                    DshSvgIcon(
                        svg = item.iconSvg,
                        size = 28.dp,
                        tint = colors.labelPrimary,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 没有图标（Host 设置页条目本来就没有图标字段）：画默认插件图标。
                    // 这是视觉占位，不是掩盖——painterResource(0) 才是要避开的崩溃。
                    Icon(
                        imageVector = Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = colors.labelPrimary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, color = colors.labelPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                if (item.description.isNotBlank()) {
                    Text(item.description, color = colors.labelTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("›", color = colors.labelTertiary, fontSize = 18.sp)
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.borderL4))
        }
    }
}

/**
 * 已安装的第三方「行插件」：标题/说明 + 真实开关 + 卸载。
 *
 * 这类插件不是 bundle，Host 用 `setPluginEnabled` 直接重载这一行；开关状态
 * 跟着 `pluginManager/listPlugins` 报的 `enabled` 走。
 */
@Composable
private fun InstalledPluginRowItem(
    row: PluginRowInfo,
    showDivider: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginMetaIcon(iconData = row.iconData, size = 40.dp, radius = 12.dp, glyphSize = 18.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = officialPluginTitle(row.title.ifBlank { row.moduleName }),
                    color = colors.labelPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = officialPluginDescription(row.title, row.description.ifBlank { row.moduleName }),
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DshSwitch(checked = row.enabled, onCheckedChange = onToggle)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onUninstall),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_trash_outline),
                    contentDescription = stringResource(R.string.plugins_uninstall),
                    tint = colors.stateErrorPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.borderL4))
        }
    }
}

@Composable
private fun PluginConfigRow(row: PluginRowInfo, showDivider: Boolean) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginMetaIcon(iconData = row.iconData, size = 40.dp, radius = 12.dp, glyphSize = 18.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(officialPluginTitle(row.title), color = colors.labelPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(officialPluginDescription(row.title, row.description.ifBlank { row.moduleName }), color = colors.labelTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = colors.labelTertiary, fontSize = 18.sp)
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.borderL4))
        }
    }
}

@Composable
private fun InstalledHostBundleRow(
    bundle: PluginBundleItem,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onOpen: () -> Unit = {},
    /** 必需的基础设施插件：只能查看，不能停用或卸载。 */
    locked: Boolean = false,
    /** 设备上装不上的包（`availability = missing`）：开关没有意义，禁用。 */
    toggleEnabled: Boolean = true,
) {
    val colors = LocalDshPanelColors.current
    val rowInteraction = remember { MutableInteractionSource() }
    val rowPressed by rowInteraction.collectIsPressedAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (rowPressed) colors.interactiveHover else Color.Transparent)
                .clickable(onClick = onOpen)
                .padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginMetaIcon(iconData = bundle.iconData, size = 40.dp, radius = 12.dp, glyphSize = 18.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = bundle.title,
                        color = colors.labelPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 先压缩标题再压缩药丸：否则「Plugin not installed」会被挤成两行。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 与官方页面一致：包不在设备上时标「未安装」，而不是假装可用。
                    if (bundle.availability == "missing") {
                        // 药丸尺寸由 padding 决定，避免固定高度把 9sp 文本挤到上沿：
                        // 关掉字体额外行距后，上下 padding 对称即可真正居中。
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bgModulePlatform)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_not_installed),
                                color = colors.labelSecondary,
                                maxLines = 1,
                                softWrap = false,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false,
                                    ),
                                ),
                            )
                        }
                    }
                }
                Text(
                    text = bundle.description.ifEmpty { bundle.packageName },
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked) {
                // bridge 是客户端的契约对端：停用它会让 App 失去官方分类与图标，
                // 卸载它则让整页退化成原生清单。因此只呈现状态，不给开关。
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.bgModulePlatform)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.plugin_required),
                        color = colors.labelTertiary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            } else {
                DshSwitch(
                    checked = bundle.enabled,
                    onCheckedChange = onToggle,
                    enabled = toggleEnabled,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onUninstall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_trash_outline),
                        contentDescription = stringResource(R.string.plugins_uninstall),
                        tint = colors.stateErrorPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.borderL2)
            )
        }
    }
}

/**
 * 已安装组件行：`.row`（padding 12/2 + 0.5dp border-l2 下划线，末行无）。
 * 40dp 图标框 + 13.5/20 500 行名 + 11.5/16 等宽 slot 说明 + 尾部 sm 描边按钮。
 */
@Composable
private fun InstalledSurfaceRow(
    item: SurfaceItem,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    val isOverlay = item.kind == SurfaceKind.OVERLAY
    val rowInteraction = remember { MutableInteractionSource() }
    val rowPressed by rowInteraction.collectIsPressedAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (rowPressed) colors.interactiveHover else Color.Transparent)
                .clickable(
                    interactionSource = rowInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onOpenDetail,
                )
                .padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            // .rowLine 间距 16px；手机端收为 12dp，为行名与 slot 说明留出宽度。
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginsIconFrame(
                icon = if (isOverlay) Icons.Default.Widgets else Icons.Default.Build,
                frameSize = 40.dp,
                frameRadius = 12.dp,
                glyphSize = 18.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title ?: item.pluginId,
                    color = colors.labelPrimary,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Slot: ${item.slot} · ${item.kind}",
                    color = colors.labelTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PluginsButton(
                text = if (isOverlay) stringResource(R.string.plugin_open_overlay) else stringResource(R.string.plugin_open),
                onClick = onOpen,
                primary = false,
                height = 28.dp,
                horizontalPadding = 10.dp,
                radius = 8.dp,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.plugin_view_details),
                tint = colors.labelTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.borderL2)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 插件详情（点击卡片后的官方 ui-plugin-manager 详情页）
// ---------------------------------------------------------------------------

/** 官方插件详情页：面包屑、图标 + 卸载/开关、名称/版本/包名/说明、组件、来源信息。 */
@Composable
private fun PluginDetailContent(
    item: SurfaceItem,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = PluginsDimens.PageHorizontal,
                end = PluginsDimens.PageHorizontal,
                top = PluginsDimens.PageTop,
                bottom = PluginsDimens.PageBottom,
            ),
    ) {
        // 返回不放这里：App 顶栏的 [PageBackBar] 已经画了返回，详情页再画一条
        // 面包屑就是第二条返回。用户明确要求复用顶栏。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PluginsIconFrame(
                icon = pluginIcon(item),
                frameSize = 48.dp,
                frameRadius = 16.dp,
                glyphSize = 20.dp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UninstallButton(onClick = onUninstall)
                DshSwitch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title ?: item.pluginId,
                    color = colors.labelPrimary,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = item.pluginId,
                color = colors.labelTertiary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_components),
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.plugin_one_component),
                color = colors.labelSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        PluginComponentRow(
            item = item,
            enabled = enabled,
            onEnabledChange = onEnabledChange,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.plugins_source_info),
            color = colors.labelPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        DetailFactRow(
            label = stringResource(R.string.plugin_id),
            value = item.pluginId,
            monospace = true,
        )
        DetailFactRow(label = stringResource(R.string.plugin_mount_slot), value = item.slot)
    }
}

/** 详情页组件行：图标框 + 名称/说明/模块 + 状态点、等待依赖与开关。 */
@Composable
private fun PluginComponentRow(
    item: SurfaceItem,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PluginsIconFrame(
            icon = pluginIcon(item),
            frameSize = 40.dp,
            frameRadius = 12.dp,
            glyphSize = 18.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title ?: item.pluginId,
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.pluginId,
                color = colors.labelTertiary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.labelTertiary),
                )
                Text(
                    text = stringResource(R.string.plugin_waiting_dependencies),
                    color = colors.labelSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                )
            }
            DshSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

/** 详情页来源信息行：44dp 最小高 + 8/2 内距 + 0.5dp border-l2 下划线；值右对齐。 */
@Composable
internal fun DetailFactRow(label: String, value: String, monospace: Boolean = false) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = label,
                color = colors.labelSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f),
            )
            if (monospace) {
                Text(
                    text = value,
                    color = colors.labelPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.bgModulePlatform)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    text = value,
                    color = colors.labelPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.borderL2),
        )
    }
}

/** 版本胶囊：18dp 高 / 10sp-14 500 / bg-module-platform。 */
@Composable
private fun VersionTag(version: String) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .height(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.bgModulePlatform)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "v$version",
            color = colors.labelSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 官方危险描边按钮：28dp 高 / R8 / 0.5dp error 30% 描边 / 错误色图标与文字。 */
@Composable
private fun UninstallButton(onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(if (pressed) colors.interactiveHoverDanger else Color.Transparent)
            .border(0.5.dp, colors.stateErrorPrimary.copy(alpha = 0.3f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_trash_outline),
            contentDescription = null,
            tint = colors.stateErrorPrimary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.plugins_uninstall),
            color = colors.stateErrorPrimary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

/** 详情页图标（按 Surface 类型选择）。 */
private fun pluginIcon(item: SurfaceItem): ImageVector =
    if (item.kind == SurfaceKind.OVERLAY) Icons.Default.Widgets else Icons.Default.Build

/** 图标框：0.5dp border-l3 细线框 + 二级色描边图标（卡片头 48/R16，组件行 40/R12）。 */
@Composable
private fun PluginsIconFrame(
    icon: ImageVector,
    frameSize: Dp,
    frameRadius: Dp,
    glyphSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    val shape = RoundedCornerShape(frameRadius)
    Box(
        modifier = modifier
            .size(frameSize)
            .clip(shape)
            .border(0.5.dp, colors.borderL3, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(glyphSize),
        )
    }
}

/**
 * 按钮（ui-primitives Button）：sm 高 28 / 12-18 / R8 / 内距 10，
 * 插件页「添加插件」为高 32 / 13-20 / R12 / 内距 12；主按钮用 button-primary-fill，
 * 描边按钮用 0.5dp border-l3，按下态分别走 button-primary-hover / interactive-hover。
 */
@Composable
private fun PluginsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    height: Dp = 28.dp,
    horizontalPadding: Dp = 10.dp,
    radius: Dp = 8.dp,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = 18.sp,
    icon: ImageVector? = null,
    iconSize: Dp = 13.dp,
) {
    val colors = LocalDshPanelColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(radius)
    val fill = when {
        primary && pressed -> colors.buttonPrimaryHover
        primary -> colors.buttonPrimaryFill
        pressed -> colors.interactiveHover
        else -> Color.Transparent
    }
    val ink = if (primary) colors.labelPrimaryForeground else colors.labelPrimary
    Row(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(fill)
            .then(if (primary) Modifier else Modifier.border(0.5.dp, colors.borderL3, shape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = text,
            color = ink,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 「添加插件」对话框：installTitle/installDescription 文案，
 * Modal 材质（[DshDialog]）+ 40dp 输入（[DshTextInput]）+ 整宽 40dp 主按钮。
 * 行为与原 PluginsListScreen 一致：输入包名或地址，可「填入示例」，安装后回显状态并清空输入。
 *
 * 安装耗时以秒计，所以 busy 时把过程画出来：6dp 进度条 + 步骤文案 + 百分比 + 「请保持打开」提示；
 * 同时锁住输入、主按钮与关闭入口（[onDismiss] 由调用方按 busy 拦截），避免重复提交和误关。
 *
 * @param statusMessage 上一次安装的结论（成功 / 失败），安装中清空。
 * @param busy 是否正在安装。
 * @param progress 安装进度 0..1。
 * @param step 当前步骤文案（安装器回调给出）。
 */
@Composable
private fun PluginsInstallDialog(
    packageInput: String,
    onPackageInputChange: (String) -> Unit,
    statusMessage: String?,
    busy: Boolean,
    progress: Float,
    step: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    DshDialog(
        // 安装中允许收起（安装继续在后台跑）：卡住的安装不能把界面锁死在弹层里。
        onDismiss = onDismiss,
        title = stringResource(R.string.action_add_plugin),
        closeLabel = stringResource(R.string.action_close),
        description = stringResource(R.string.plugin_add_desc),
        width = 560.dp,
        content = {
            DshTextInput(
                value = packageInput,
                // busy 时输入已属于「上一次安装」，改它只会让用户以为改的是本次安装。
                onValueChange = { if (!busy) onPackageInputChange(it) },
                placeholder = stringResource(R.string.plugin_add_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            if (busy) {
                // 百分比与步骤文案先算好，避免在布局 lambda 里做格式化。
                val fraction = progress.coerceIn(0f, 1f)
                val stepText = if (step.isBlank()) {
                    stringResource(R.string.plugins_install_preparing)
                } else {
                    step
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.buttonPrimaryFill,
                    trackColor = colors.bgLayer2,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stepText,
                        color = colors.labelSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        color = colors.labelTertiary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.plugins_install_busy_hint),
                    color = colors.labelTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.plugin_source_note),
                    color = colors.labelTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            if (statusMessage != null && !busy) {
                Text(
                    text = statusMessage,
                    color = colors.labelSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        footer = {
            DshDialogButton(
                // busy 时标签切到「正在安装插件」，既说明状态也解释了为什么点不动。
                text = stringResource(
                    if (busy) R.string.plugins_install_progress_title else R.string.plugins_store_install,
                ),
                primary = true,
                enabled = !busy,
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                height = 40.dp,
            )
        },
    )
}

/**
 * 「更多插件」页面：实验性分组头部的「更多 ›」进入，列全部默认收起的实验性插件。
 * 与主列表同一套行样式。
 *
 * 这一页**不画返回箭头**：返回由 App 顶栏的 [PageBackBar] 提供（见 PluginsScreen 里的
 * `backSignal` 处理）。同一个动作画两次会让页面顶部出现两条返回。
 */
@Composable
private fun MorePluginsScreen(
    bundles: List<PluginBundleItem>,
    onOpen: (PluginBundleItem) -> Unit,
    onToggle: (PluginBundleItem, Boolean) -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // 只留标题（说明当前在哪个子页），返回箭头交给顶栏。
        PluginsGroupTitle(
            text = stringResource(R.string.plugins_experimental),
            modifier = Modifier.padding(
                start = PluginsDimens.PageHorizontal,
                end = PluginsDimens.PageHorizontal,
                top = PluginsDimens.PageTop,
                bottom = PluginsDimens.GroupTitleGap,
            ),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = PluginsDimens.PageHorizontal,
                end = PluginsDimens.PageHorizontal,
                bottom = PluginsDimens.PageBottom,
            ),
        ) {
            itemsIndexed(
                items = bundles,
                key = { index, bundle -> "more_page_${index}_${bundle.packageName}" },
            ) { index, bundle ->
                InstalledHostBundleRow(
                    bundle = bundle,
                    showDivider = index != bundles.lastIndex,
                    onOpen = { onOpen(bundle) },
                    onToggle = { enabled -> onToggle(bundle, enabled) },
                    onUninstall = { },
                    locked = bundle.locked,
                    // 设备上装不上的包（`availability = missing`）没有可拨的状态。
                    toggleEnabled = bundle.availability != "missing",
                )
            }
        }
    }
}










