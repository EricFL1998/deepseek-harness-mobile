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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    /** 安装一个插件 spec（包名 / Git 地址 / 本地绝对路径）。挂起：Host 安装耗时以秒计。 */
    onInstallPlugin: suspend (String) -> Result<String>,
    onToggleBundle: (String, Boolean) -> Unit = { _, _ -> },
    /** 启停一条 Loader 行插件（第三方插件，非 bundle）：参数为 entryId 与目标状态。 */
    onTogglePluginRow: (String, Boolean) -> Unit = { _, _ -> },
    /** 启停 bundle 里的一个子组件（bundle patch 插进来的一行 Loader entry）。 */
    onToggleComponent: (String, Boolean) -> Unit = { _, _ -> },
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
    /** 外层 App 返回按钮的点击计数：详情页打开时自增，由本页关掉详情。 */
    backSignal: Int = 0,
    /** 详情页开关状态上报：外层据此决定返回是关详情还是离开插件页。 */
    onDetailOpenChange: (Boolean) -> Unit = {},
    /** 「原生插件」tab 的商店列表项（可装伴侣 APK）。 */
    nativePluginItems: List<com.deepseek.dsh.plugin.nativehost.NativePluginItem> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showInstallDialog by remember { mutableStateOf(false) }
    var pluginPackageInput by remember { mutableStateOf("") }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var selectedPlugin by remember { mutableStateOf<SurfaceItem?>(null) }
    var selectedBundle by remember { mutableStateOf<PluginBundleItem?>(null) }
    // 原生伴侣：详情页 + 停用集合 + 已装列表（merge 进已安装分组）。
    var selectedNativePlugin by remember {
        mutableStateOf<com.deepseek.dsh.plugin.nativehost.NativePluginItem?>(null)
    }
    var disabledNativePlugins by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 插件页分段页签：0 = 插件（现有列表），1 = 原生插件商店。
    var pluginsTabIndex by remember { mutableStateOf(0) }
    val installedNativePlugins = nativePluginItems.filter { it.installed }
    // 官方插件分类只有一个来源：bridge 的 `client-slots/plugins.item` 注册。
    // 文案、顺序与图形都是官方客户端自己的资源。bridge 不可用时这个分组直接
    // 不出现 —— 宁可少一行，也不拿写死的分类表冒充官方数据。
    // 官方设置条目的**来源**是 Host：凡是声明了设置页的插件，Host 都会为它生成命名空间，
    // bridge 的 `host-slots` 把这些位置报上来。只有 Host 没报任何条目时，才回落到
    // 客户端 bundle 的 `plugins.item` 注册（那条路在预构建 web 客户端上通常为空）。
    val officialConfigItems = remember(bridgeCatalog, hostSettingsPages) {
        if (hostSettingsPages.isNotEmpty()) {
            hostSettingsPages.map { page ->
                OfficialConfigItem(
                    id = page.id,
                    title = page.title,
                    description = page.description.orEmpty(),
                    iconRes = 0,
                    iconSvg = null,
                )
            }
        } else {
            bridgeCatalog.categories.map { category ->
                OfficialConfigItem(
                    id = category.id,
                    title = category.label,
                    description = "",
                    iconRes = 0,
                    iconSvg = category.iconSvg,
                )
            }
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
            .filter { it.optional }
            .map { it.toPluginBundleItem() }
            .filter(::pinnedToMainList)
            .sortedBy { optionalBundleRank(it.packageName) }
        fromBridge.ifEmpty { pluginManagerSnapshot?.experimentalBundles.orEmpty() }
    }
    /** 默认收起、只有「更多插件」里才出现的实验性插件；启用后会升进上面的主列表。 */
    val derivedMoreBundles = remember(bridgeCatalog, pluginManagerSnapshot) {
        bridgeCatalog.bundles
            .filter { it.optional }
            .map { it.toPluginBundleItem() }
            .filterNot(::pinnedToMainList)
            .sortedBy { optionalBundleRank(it.packageName) }
    }
    val derivedInstalledBundles = remember(bridgeCatalog, pluginManagerSnapshot) {
        val fromBridge = bridgeCatalog.bundles
            .filter { it.installed && !it.official && !it.optional }
            .filterNot { BUILTIN_PROFILE_BUNDLES.contains(it.name) }
            .map { it.toPluginBundleItem() }
        fromBridge.ifEmpty { pluginManagerSnapshot?.installedBundles.orEmpty() }
    }

    fun keyOf(item: SurfaceItem): String = "${item.pluginId}::${item.slot}"
    fun enabledOf(item: SurfaceItem): Boolean = keyOf(item) !in disabledPlugins

    val bundleDetail = selectedBundle
    if (bundleDetail != null) {
        CompositionLocalProvider(LocalDshPanelColors provides colors) {
            Box(modifier = modifier.fillMaxSize().background(colors.bgBase)) {
                BundleDetailContent(
                    bundle = bundleDetail,
                    onBack = { selectedBundle = null },
                    onToggle = { enabled -> onToggleBundle(bundleDetail.packageName, enabled) },
                    onUninstall = { onUninstall(SurfaceItem(pluginId = bundleDetail.packageName, slot = "host.bundle", title = bundleDetail.title)) },
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
            if (nativeDetail != null) {
                NativePluginDetailPage(
                    item = nativeDetail,
                    onBack = { selectedNativePlugin = null },
                    onUninstall = { selectedNativePlugin = null },
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
                    // 分段页签永远在最顶（添加插件按钮之上），两个 tab 都有它。
                    PluginsNativeTabs(
                        selectedIndex = pluginsTabIndex,
                        onSelect = { pluginsTabIndex = it },
                        modifier = Modifier.padding(
                            start = PluginsDimens.PageHorizontal,
                            end = PluginsDimens.PageHorizontal,
                            top = PluginsDimens.PageTop,
                            bottom = 16.dp,
                        ),
                    )
                    if (pluginsTabIndex == 1) {
                        // 「原生插件」tab = 商店：浏览/发现/安装可装的伴侣 APK。
                        NativeStoreTab(
                            items = nativePluginItems,
                            onOpenDetail = { selectedNativePlugin = it },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PluginsDimens.PageHorizontal,
                        end = PluginsDimens.PageHorizontal,
                        bottom = PluginsDimens.PageBottom,
                    ),
                ) {
                    item(key = "page_head") {
                        PluginsPageHead(
                            onAddPluginClick = { showInstallDialog = true },
                            addMenuOpen = addMenuOpen,
                            onToggleAddMenu = { addMenuOpen = !addMenuOpen },
                            onDismissAddMenu = { addMenuOpen = false },
                            onRefresh = { /* 本地目录刷新由 Host 推送；此处保持官方按钮形态 */ },
                            onCreatePlugin = onCreatePlugin,
                        )
                    }

                    if (officialConfigItems.isNotEmpty()) {
                        item(key = "builtin_config_head") {
                            PluginsGroupTitle(
                                text = "内置插件配置",
                                modifier = Modifier.padding(
                                    top = PluginsDimens.SectionGap,
                                    bottom = PluginsDimens.GroupTitleGap,
                                ),
                            )
                        }
                        itemsIndexed(officialConfigItems, key = { index, item -> "official_config_${index}_${item.id}" }) { index, item ->
                            OfficialConfigRow(item = item, showDivider = index != officialConfigItems.lastIndex)
                        }
                    }

                    // bridge 报告的非致命问题（例如某个 bundle 无法求值、图标缺失）。
                    // 展示出来而不是隐藏，是因为它解释了「为什么这里少了东西」。
                    if (bridgeWarnings.isNotEmpty()) {
                        item(key = "bridge_warnings") {
                            Text(
                                text = "Host 兼容层提示：" + bridgeWarnings.joinToString("；"),
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
                            PluginsGroupTitle(
                                text = "实验性插件",
                                modifier = Modifier.padding(
                                    top = PluginsDimens.SectionGap,
                                    bottom = PluginsDimens.GroupTitleGap,
                                ),
                            )
                        }
                        itemsIndexed(experimentalBundles, key = { index, bundle -> "experimental_${index}_${bundle.packageName}" }) { index, bundle ->
                            InstalledHostBundleRow(
                                bundle = bundle,
                                showDivider = index != experimentalBundles.lastIndex,
                                onOpen = { selectedBundle = bundle },
                                onToggle = { enabled -> onToggleBundle(bundle.packageName, enabled) },
                                onUninstall = { },
                                locked = isLockedBundle(bundle.packageName),
                            )
                        }
                    }

                    // 默认收起的实验性插件：与官方页面一致，放在主列表之外的「更多插件」。
                    if (derivedMoreBundles.isNotEmpty()) {
                        item(key = "more_group_head") {
                            PluginsGroupTitle(
                                text = "更多插件",
                                modifier = Modifier.padding(
                                    top = PluginsDimens.SectionGap,
                                    bottom = PluginsDimens.GroupTitleGap,
                                ),
                            )
                        }
                        itemsIndexed(
                            items = derivedMoreBundles,
                            key = { index, bundle -> "more_${index}_${bundle.packageName}" },
                        ) { index, bundle ->
                            InstalledHostBundleRow(
                                bundle = bundle,
                                showDivider = index != derivedMoreBundles.lastIndex,
                                onOpen = { selectedBundle = bundle },
                                onToggle = { enabled -> onToggleBundle(bundle.packageName, enabled) },
                                onUninstall = { },
                                locked = isLockedBundle(bundle.packageName),
                            )
                        }
                    }

                    // 插件声明的行级配置入口：与设置页同一套机制，按 slot 分派到插件页。
                    if (rowConfigSurfaces.isNotEmpty()) {
                        item(key = "row_config_head") {
                            PluginsGroupTitle(
                                text = "插件配置",
                                modifier = Modifier.padding(
                                    top = PluginsDimens.SectionGap,
                                    bottom = PluginsDimens.GroupTitleGap,
                                ),
                            )
                        }
                        itemsIndexed(
                            items = rowConfigSurfaces,
                            key = { index, surface -> "row_config_${index}_${surface.id}" },
                        ) { index, surface ->
                            RowConfigRow(
                                surface = surface,
                                showDivider = index != rowConfigSurfaces.lastIndex,
                                onClick = {
                                    // id 形如 <bundle>#<rowId>：打开所属 bundle 的详情页。
                                    val bundleName = surface.id.substringBefore("#")
                                    val target = (derivedExperimentalBundles + derivedMoreBundles +
                                        derivedInstalledBundles).firstOrNull {
                                        it.packageName == bundleName
                                    }
                                    if (target != null) selectedBundle = target
                                },
                            )
                        }
                    }

                    // 1. 已安装插件：只列第三方（官方与实验性已各自成组）
                    item(key = "installed_group_head") {
                        PluginsGroupTitle(
                            text = "已安装插件",
                            modifier = Modifier.padding(
                                top = PluginsDimens.SectionGap,
                                bottom = PluginsDimens.GroupTitleGap,
                            ),
                        )
                    }

                    val hostBundles = derivedInstalledBundles
                    if (surfaces.isEmpty() && hostBundles.isEmpty()) {
                        item(key = "installed_empty") {
                            Text(
                                text = "还没有安装任何插件。",
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
                                    showDivider = index != hostBundles.lastIndex || surfaces.isNotEmpty(),
                                    onOpen = { selectedBundle = bundle },
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
                                    locked = isLockedBundle(bundle.packageName),
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
                                    showDivider = index != surfaces.lastIndex || installedNativePlugins.isNotEmpty(),
                                    onOpen = { onOpenSurface(item) },
                                    onOpenDetail = { selectedPlugin = item },
                                )
                            }
                        }

                        // 1.3 已装的原生伴侣插件：同行样式 + 「原生」标记 + 开关。
                        if (installedNativePlugins.isNotEmpty()) {
                            itemsIndexed(
                                items = installedNativePlugins,
                                key = { index, p -> "native_${index}_${p.pluginId}" },
                            ) { index, plugin ->
                                NativeInstalledRow(
                                    plugin = plugin,
                                    showDivider = index != installedNativePlugins.lastIndex,
                                    enabled = plugin.pluginId !in disabledNativePlugins,
                                    onToggle = { enabled ->
                                        disabledNativePlugins =
                                            if (enabled) disabledNativePlugins - plugin.pluginId
                                            else disabledNativePlugins + plugin.pluginId
                                    },
                                    onClick = { selectedNativePlugin = plugin },
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
                    onInstall = {
                    val spec = pluginPackageInput.trim()
                    if (spec.isNotEmpty()) {
                        // 安装是 suspend（Host 安装耗时以秒计），包一层协程。
                        scope.launch {
                            val result = runCatching { onInstallPlugin(spec) }
                            installStatusMessage = result.fold(
                                onSuccess = { "OK: $spec" },
                                onFailure = { "失败：${it.message ?: "未知错误"}" },
                            )
                            if (result.isSuccess) pluginPackageInput = ""
                        }
                    }
                },
                onDismiss = {
                    showInstallDialog = false
                    installStatusMessage = null
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
    rows = rows.map { row ->
        PluginComponentInfo(
            rowId = row.rowId,
            moduleName = row.moduleName,
            title = row.title,
            description = row.description.orEmpty(),
            enabled = true,
        )
    },
)

private const val BRIDGE_PLUGIN_PACKAGE = "dsh-mobile-bridge-plugin"



/**
 * 官方插件页的分组规则，逐条对应 `ui-plugin-manager` 的实现。
 *
 * 这些包名是官方页面的固定清单，不是内容数据：它们决定「哪些默认显示、哪些先收起来、
 * 哪些开关默认是开的」。内容（标题/描述/图标/开关状态）全部来自 Host。
 */
/** 基础层 bundle：`/plugins` 页面永远不列它们，即使 profile 声明为依赖。 */
private val BUILTIN_PROFILE_BUNDLES = setOf(
    "@deepseek-ai/dsh-base",
    "@deepseek-ai/dsh-web-app",
    "@deepseek-ai/dsh-headless",
    "@deepseek-ai/dsh-sdk-app",
    "@deepseek-ai/dsh-acp-app",
    "@deepseek-ai/dsh-sdk-minimal",
)

/** 实验性插件里常驻主列表的四个，顺序即显示顺序。 */
private val EXTENSION_BUNDLES = listOf(
    "@deepseek-ai/dsh-experimental-agent-team-profile",
    "@deepseek-ai/dsh-experimental-voice-input-bundle",
    "@deepseek-ai/dsh-experimental-auto-review",
    "@deepseek-ai/dsh-experimental-inspector-profile",
)

/** 其余实验性插件：默认收在「更多插件」里，一旦启用就升进主列表。 */
private val MORE_BUNDLES = listOf(
    "@deepseek-ai/dsh-experimental-tool-worktree",
    "@deepseek-ai/dsh-experimental-cot-translation-bundle",
    "@deepseek-ai/dsh-experimental-terminal-bundle",
    "@deepseek-ai/dsh-experimental-session-search",
    "@deepseek-ai/dsh-experimental-session-titles-bundle",
    "@deepseek-ai/dsh-experimental-badge-skill-bundle",
    "@deepseek-ai/dsh-experimental-ralph-bundle",
    "@deepseek-ai/dsh-experimental-plugin-market-bundle",
    "@deepseek-ai/dsh-subagent-codex",
    "@deepseek-ai/dsh-subagent-claude-code",
)

/**
 * 官方页面的排序：常驻的按固定顺序，不认识的排在其后，被收起的按「更多」顺序。
 * @param name 包名。
 * @returns 排序键。
 */
private fun optionalBundleRank(name: String): Int {
    val alwaysHeld = EXTENSION_BUNDLES.indexOf(name)
    if (alwaysHeld >= 0) return alwaysHeld
    val heldBack = MORE_BUNDLES.indexOf(name)
    return if (heldBack < 0) EXTENSION_BUNDLES.size else EXTENSION_BUNDLES.size + 1 + heldBack
}

/**
 * 一个实验性插件是否进主列表：常驻的那四个总是进，被收起的只有启用后才进。
 * @param bundle 实验性 bundle。
 * @returns 是否在主列表显示。
 */
private fun pinnedToMainList(bundle: PluginBundleItem): Boolean =
    !MORE_BUNDLES.contains(bundle.packageName) || bundle.enabled


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
private fun isLockedBundle(packageName: String): Boolean = packageName == BRIDGE_PLUGIN_PACKAGE

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
    onAddPluginClick: () -> Unit,
    addMenuOpen: Boolean,
    onToggleAddMenu: () -> Unit,
    onDismissAddMenu: () -> Unit,
    onRefresh: () -> Unit,
    onCreatePlugin: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = "插件",
                color = colors.labelPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "安装、启用和配置插件",
                color = colors.labelSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp),
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
                    contentDescription = "刷新",
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box {
                PluginsSplitAddButton(
                    expanded = addMenuOpen,
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
        }
    }
}

/** 官方拆分主按钮：左「+ 添加插件」，右侧 32dp 下拉段，中间 0.5dp 前景色分隔线。 */
@Composable
private fun PluginsSplitAddButton(
    expanded: Boolean,
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
                text = "添加插件",
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
                contentDescription = "添加插件方式",
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
            title = "安装第三方插件",
            description = "支持 npm 包、Git 仓库和本地目录",
            onClick = onInstallExternal,
        )
        AddPluginMenuItem(
            iconResId = com.deepseek.dsh.core.designsystem.R.drawable.ic_agent_preset_outline,
            title = "让 Agent 创建插件",
            description = "用创造模式描述能力，由 Agent 编写并安装插件",
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
                text = "SDUI 声明式原生插件",
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "JSON layout 声明 -> 客户端纯原生 Compose 渲染",
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PluginsButton(
            text = "体验",
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
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PluginsDimens.PageHorizontal, vertical = PluginsDimens.PageTop),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = colors.labelPrimary, fontSize = 26.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text("插件", color = colors.labelSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
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
                    Text("实验性", color = colors.labelSecondary, fontSize = 11.sp)
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
                    Text("异常：${bundle.error}", color = colors.stateErrorPrimary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (isLockedBundle(bundle.packageName)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgModulePlatform)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (bundle.enabled) "必需 · 已启用" else "必需",
                        color = colors.labelTertiary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                DshSwitch(checked = bundle.enabled, onCheckedChange = onToggle)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "包含的组件 共 ${bundle.rows.size} 个 · ${bundle.rows.count { it.enabled }} 运行中",
            color = colors.labelPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        bundle.rows.forEach { component ->
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
                Text("运行中", color = colors.labelSecondary, fontSize = 11.sp)
                DshSwitch(checked = component.enabled, onCheckedChange = { /* 组件启停暂由 bundle 控制 */ })
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("来源信息", color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        // 代码来源：DSH 自己的包（含 `@deepseek-ai/dsh-experimental-*` 这些可选实验
        // 包）都随安装提供，标「内置」；第三方包才显示包名，用户据此判断来源。
        val builtIn = isBuiltInBundle(bundle.packageName)
        DetailFactRow(
            label = "代码来源",
            value = if (builtIn) "内置" else bundle.packageName,
            monospace = !builtIn,
        )
        DetailFactRow(label = "当前版本", value = bundle.version ?: "—")
        if (bundle.removable && !isLockedBundle(bundle.packageName)) {
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

private data class OfficialConfigItem(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    /** bridge 提供的官方 SVG 图形；非空时优先于 [iconRes]。 */
    val iconSvg: String? = null,
)

@Composable
private fun OfficialConfigRow(item: OfficialConfigItem, showDivider: Boolean) {
    val colors = LocalDshPanelColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                } else if (item.iconRes != 0) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
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
                Text(row.title, color = colors.labelPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                Text(row.description.ifBlank { row.moduleName }, color = colors.labelTertiary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    )
                    // 与官方页面一致：包不在设备上时标「未安装」，而不是假装可用。
                    if (bundle.availability == "missing") {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.bgModulePlatform)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "未安装",
                                color = colors.labelSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
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
                        text = "必需",
                        color = colors.labelTertiary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            } else {
                DshSwitch(
                    checked = bundle.enabled,
                    onCheckedChange = onToggle
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
                        contentDescription = "卸载",
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
                text = if (isOverlay) "浮层打开" else "打开",
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
                contentDescription = "查看插件详情",
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
        // 面包屑：‹ 插件
        val crumbInteraction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = crumbInteraction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_chevron_left),
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "插件",
                color = colors.labelTertiary,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
            )
        }

        Spacer(Modifier.height(32.dp))

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
                text = "包含的组件",
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "共 1 个",
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
            text = "来源信息",
            color = colors.labelPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(12.dp))
        DetailFactRow(
            label = "插件 ID",
            value = item.pluginId,
            monospace = true,
        )
        DetailFactRow(label = "挂载位置", value = item.slot)
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
                    text = "等待依赖",
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
private fun DetailFactRow(label: String, value: String, monospace: Boolean = false) {
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
            text = "卸载",
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
 */
@Composable
private fun PluginsInstallDialog(
    packageInput: String,
    onPackageInputChange: (String) -> Unit,
    statusMessage: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalDshPanelColors.current
    DshDialog(
        onDismiss = onDismiss,
        title = "添加插件",
        closeLabel = "关闭",
        description = "输入插件的包名、GitHub 仓库地址或本地目录路径。",
        width = 560.dp,
        content = {
            DshTextInput(
                value = packageInput,
                onValueChange = onPackageInputChange,
                placeholder = "npm 包名 / Git 地址 / 本地目录",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "来源: 官方 npm / 国内镜像",
                    color = colors.labelTertiary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            if (statusMessage != null) {
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
                text = "安装",
                primary = true,
                enabled = true,
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                height = 40.dp,
            )
        },
    )
}










