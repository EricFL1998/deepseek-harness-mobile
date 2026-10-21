package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshPanelColors
import com.deepseek.dsh.core.designsystem.DshSwitch
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.plugin.nativehost.NativePluginItem

/**
 * 插件页的「原生插件」分区：商店 tab 内容 + 已安装分组里的原生伴侣行。
 *
 * 独立成文件，避免反复改 PluginsScreen 这个大文件。PluginsScreen 只调这里的
 * 几个组件。
 *
 * 语义：
 * - 「插件」tab 的已安装分组：装了伴侣 APK 就出现（带「原生」标记 + 开关）；
 * - 「原生插件」tab 是商店：浏览/发现/安装可装的伴侣 APK。
 */

/** 插件页顶部的分段页签（对齐 ui-primitives SegmentedTabs）：插件 / 原生插件。 */
@Composable
fun PluginsNativeTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    val colors = LocalDshPanelColors.current
    val containerShape = RoundedCornerShape(16.dp)
    val indicatorShape = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(colors.bgModulePlatform)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / labels.size.toFloat()
        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
            targetValue = tabWidth * selectedIndex.toFloat(),
            label = "plugins-native-tab",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(34.dp)
                .clip(indicatorShape)
                .background(colors.bgLayer3)
                .border(0.5.dp, colors.borderL3, indicatorShape),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
        ) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (index == selectedIndex) colors.labelPrimary else colors.labelSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}


/** 「原生插件」商店 tab 的根视图：顶部搜索 + 可装推荐列表。 */
@Composable
fun NativeStoreTab(
    items: List<NativePluginItem>,
    onOpenDetail: (NativePluginItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.pluginId.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        NativeSearchField(
            value = query,
            onValueChange = { query = it },
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank()) stringResource(R.string.plugins_store_empty)
                    else stringResource(R.string.plugins_store_no_match, query),
                    color = colors.labelSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filtered, key = { it.pluginId }) { item ->
                NativeStoreRow(item = item, colors = colors, onClick = { onOpenDetail(item) })
            }
        }
    }
}

/** 商店里一行可装的原生插件：图标 + 名称/说明 + 「可安装」标记。 */
@Composable
private fun NativeStoreRow(item: NativePluginItem, colors: DshPanelColors, onClick: () -> Unit) {
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
            Text(item.name.take(1), color = colors.labelPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, color = colors.labelPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Text(item.description, color = colors.labelTertiary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
        }
        NativeChip(
            text = if (item.installed) stringResource(R.string.plugins_native_installed)
            else stringResource(R.string.plugins_native_available),
            colors = colors,
        )
    }
}

/** 已安装分组里的一行原生伴侣：图标 + 名称/包名 + 「原生」标记 + 开关。 */
@Composable
fun NativeInstalledRow(
    plugin: NativePluginItem,
    showDivider: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 额外状态胶囊，例如「插件未安装」：伴侣在、配对的 JS 插件不在。 */
    note: String? = null,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = modifier.fillMaxWidth()) {
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
                    .background(colors.bgModulePlatform)
                    .border(0.5.dp, colors.borderL3, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(plugin.name.take(1), color = colors.labelPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = plugin.name,
                        color = if (enabled) colors.labelPrimary else colors.labelTertiary,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    NativeChip(text = stringResource(R.string.plugins_native_badge), colors = colors)
                    if (note != null) {
                        NativeChip(text = note, colors = colors)
                    }
                }
                Text(
                    text = plugin.description,
                    color = colors.labelTertiary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                )
            }
            DshSwitch(checked = enabled, onCheckedChange = onToggle)
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.borderL2,
                modifier = Modifier.padding(start = 52.dp),
            )
        }
    }
}

/** 原生伴侣详情页：图标 + 名称/版本/包名 + 能力 + 签名指纹 + 卸载。 */
@Composable
fun NativePluginDetailPage(
    item: NativePluginItem,
    onBack: () -> Unit,
    onUninstall: (NativePluginItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDshPanelColors.current
    Column(modifier = modifier.fillMaxSize()) {
        // 返回不放这里：App 顶栏已经有返回条，页面内再画一条就是第二条返回。
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.bgModulePlatform)
                            .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.name.take(1), color = colors.labelPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(item.name, color = colors.labelPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("${item.pluginId} · v${item.version}", color = colors.labelTertiary, fontSize = 12.sp)
                    }
                }
            }
            if (item.description.isNotBlank()) {
                item { Text(item.description, color = colors.labelSecondary, fontSize = 13.sp, lineHeight = 20.sp) }
            }
            if (item.capabilities.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.plugins_capabilities), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        for (cap in item.capabilities) Text("· $cap", color = colors.labelSecondary, fontSize = 13.sp)
                    }
                }
            }
            item.signatureFingerprint?.let { fp ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.plugins_signature), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(fp.take(16) + "…", color = colors.labelTertiary, fontSize = 12.sp)
                    }
                }
            }
            if (item.installed) {
                item {
                    Text(
                        text = stringResource(R.string.plugins_uninstall),
                        color = colors.stateErrorPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onUninstall(item) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** 状态胶囊。 */
@Composable
private fun NativeChip(text: String, colors: DshPanelColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.bgModulePlatform)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = colors.labelSecondary, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

/** 搜索框。 */
@Composable
private fun NativeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: DshPanelColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgModulePlatform)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(text = stringResource(R.string.plugins_store_search), color = colors.labelTertiary, fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = colors.labelPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(colors.labelPrimary),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
