package com.deepseek.dsh

import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onInstallPlugin: (String) -> Unit,
    onToggleBundle: (String, Boolean) -> Unit = { _, _ -> },
    onUninstall: (SurfaceItem) -> Unit = {},
    onCreatePlugin: () -> Unit = {},
    pluginRows: List<PluginRowInfo> = emptyList(),
    bridgeCatalog: BridgePluginCatalogData = BridgePluginCatalogData(BridgePluginCatalogSource.NONE),
    bridgeWarnings: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = dshPanelColors()
    val context = LocalContext.current
    var showInstallDialog by remember { mutableStateOf(false) }
    var pluginPackageInput by remember { mutableStateOf("") }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var selectedPlugin by remember { mutableStateOf<SurfaceItem?>(null) }
    var selectedBundle by remember { mutableStateOf<PluginBundleItem?>(null) }
    // 官方插件分类只有一个来源：bridge 的 `client-slots/plugins.item` 注册。
    // 文案、顺序与图形都是官方客户端自己的资源。bridge 不可用时这个分组直接
    // 不出现 —— 宁可少一行，也不拿写死的分类表冒充官方数据。
    val officialConfigItems = remember(bridgeCatalog) {
        bridgeCatalog.categories.map { category ->
            OfficialConfigItem(
                id = category.id,
                title = category.label,
                // 官方卡片的 summary 由组件渲染；这里只保留一个中性的空说明。
                description = "",
                iconRes = 0,
                iconSvg = category.iconSvg,
            )
        }
    }
    var disabledPlugins by remember { mutableStateOf<Set<String>>(emptySet()) }

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
            if (detail != null) {
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PluginsDimens.PageHorizontal,
                        end = PluginsDimens.PageHorizontal,
                        top = PluginsDimens.PageTop,
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

                    val experimentalBundles = pluginManagerSnapshot?.experimentalBundles ?: emptyList()
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

                    // 1. 已安装插件（首页即官方插件管理页的唯一列表；不预置演示/测试插件）
                    item(key = "installed_group_head") {
                        PluginsGroupTitle(
                            text = "已安装插件",
                            modifier = Modifier.padding(
                                top = PluginsDimens.SectionGap,
                                bottom = PluginsDimens.GroupTitleGap,
                            ),
                        )
                    }

                    val hostBundles = pluginManagerSnapshot?.installedBundles ?: emptyList()
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
                                    showDivider = index != surfaces.lastIndex,
                                    onOpen = { onOpenSurface(item) },
                                    onOpenDetail = { selectedPlugin = item },
                                )
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
                        onInstallPlugin(spec)
                        installStatusMessage = "OK: $spec"
                        pluginPackageInput = ""
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
private const val BRIDGE_PLUGIN_PACKAGE = "dsh-mobile-bridge-plugin"

/**
 * 这个 bundle 是否是客户端必需的基础设施。
 *
 * bridge 是 App 与 Host 的契约对端：停用它，插件页会失去官方分类与图标，
 * 整页退化成原生清单。因此它只能在设置里升级，不能在插件页关掉。
 * @param packageName bundle 的包名。
 */
private fun isLockedBundle(packageName: String): Boolean = packageName == BRIDGE_PLUGIN_PACKAGE

/** 页面与卡片的间距/圆角常量（PluginManagerPage.module.css + ui-primitives）。 */
private object PluginsDimens {    /** 手机页面左右安全边距（clamp(24px, 4vw, 48px) 的下限）。 */
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
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PluginsIconFrame(
                icon = Icons.Default.Build,
                frameSize = 48.dp,
                frameRadius = 16.dp,
                glyphSize = 22.dp,
            )
            Spacer(Modifier.width(12.dp))
            if (isLockedBundle(bundle.packageName)) {
                // 与列表行一致：必需插件只呈现状态，不提供开关。
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
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bundle.title,
                color = colors.labelPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!bundle.version.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Text("v${bundle.version}", color = colors.labelTertiary, fontSize = 12.sp)
            }
            if (bundle.isExperimental) {
                Spacer(Modifier.width(8.dp))
                Text("实验性", color = colors.labelSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(bundle.packageName, color = colors.labelTertiary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        if (bundle.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(bundle.description, color = colors.labelSecondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
        if (bundle.error != null) {
            Spacer(Modifier.height(8.dp))
            Text("异常：${bundle.error}", color = colors.stateErrorPrimary, fontSize = 12.sp)
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
                PluginsIconFrame(
                    icon = Icons.Default.Build,
                    frameSize = 36.dp,
                    frameRadius = 10.dp,
                    glyphSize = 16.dp,
                )
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
        DetailFactRow(label = "代码来源", value = if (bundle.official) "内置" else bundle.packageName, monospace = true)
        DetailFactRow(label = "当前版本", value = bundle.version ?: "—")
        if (bundle.removable && !isLockedBundle(bundle.packageName)) {
            Spacer(Modifier.height(16.dp))
            UninstallButton(onClick = onUninstall)
        }
    }
}

@Composable
private fun PluginMetaIcon(iconData: String?, size: Dp, radius: Dp, glyphSize: Dp) {
    val bitmap = remember(iconData) {
        if (iconData.isNullOrBlank() || !iconData.contains(",")) null else runCatching {
            val bytes = Base64.decode(iconData.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
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
                } else {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = null,
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
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.bgModulePlatform)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Host",
                            color = colors.labelSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
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










