package com.deepseek.dsh.plugin.nativehost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「原生插件」tab 的内容：顶部搜索 + 推荐插件列表 + 点进详情。
 *
 * 这是原生插件商店在手机端的门面。列表数据来自两个来源的并集：
 * - **已装**：App 的 [NativePluginDiscovery] 扫到的伴侣 APK（本机 PackageManager）；
 * - **可装**：bridge `native-plugins` provider 报告的、已装 JS 插件的原生伴侣声明。
 *
 * 视觉对齐插件页（PluginsScreen）的 panel token 体系，不引入 Material 配色。
 */

/** 列表里的一项原生插件（已装或可装统一成一行）。 */
data class NativePluginItem(
    val pluginId: String,
    val name: String,
    val description: String,
    val version: String,
    val capabilities: List<String>,
    /** 已装 true / 可装（商店推荐）false。 */
    val installed: Boolean,
    /** 已装项的状态文案（正常/待更新/身份不符）。 */
    val statusLabel: String? = null,
    val signatureFingerprint: String? = null,
    /**
     * 伴侣 APK 的 Android 包名。
     *
     * 卸载要用它（`package:<包名>`），`pluginId` 是作者声明的插件身份，两者不同。
     */
    val packageName: String? = null,
    /**
     * APK manifest 声明的配对 JS 插件（`dsh.plugin.jsPackage`）。
     *
     * 插件页据此把伴侣 APK 挂到对应 JS 插件的详情里，作为那个插件的子组件；
     * 没有声明时为 null，伴侣只能独立出现在列表里。
     */
    val jsPackage: String? = null,
    /** APK 更新时间（毫秒），插件商店据此提示更新。 */
    val lastUpdateTime: Long = 0L,
)

/**
 * 「原生插件」tab 的根视图。
 *
 * @param items 全部原生插件项（已装 + 可装），由调用方合并两个来源。
 * @param onOpenDetail 点进详情。
 * @param colors 面板配色（插件页同一套 token）。
 */
@Composable
fun NativePluginsTab(
    items: List<NativePluginItem>,
    onOpenDetail: (NativePluginItem) -> Unit,
    colors: NativePanelColors,
    modifier: Modifier = Modifier,
) {
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
        // 顶部搜索框
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
                    text = if (query.isBlank()) "还没有原生插件。安装带原生界面的插件后会出现在这里。"
                    else "没有匹配「$query」的原生插件。",
                    color = colors.labelSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filtered, key = { it.pluginId }) { item ->
                NativePluginRow(item = item, colors = colors, onClick = { onOpenDetail(item) })
            }
        }
    }
}

/** 搜索框：40dp 高、圆角、面板底色。 */
@Composable
private fun NativeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: NativePanelColors,
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
            Text(text = "搜索原生插件", color = colors.labelTertiary, fontSize = 13.sp)
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

/** 一行原生插件：48dp 图标框 + 名称/说明 + 状态胶囊。 */
@Composable
private fun NativePluginRow(
    item: NativePluginItem,
    colors: NativePanelColors,
    onClick: () -> Unit,
) {
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
        // 图标框
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgModulePlatform)
                .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.name.take(1),
                color = colors.labelPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = colors.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.description,
                color = colors.labelTertiary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
        // 状态胶囊：已装 / 可装 / 待更新
        NativeStatusChip(
            text = item.statusLabel ?: if (item.installed) "已安装" else "可安装",
            colors = colors,
            emphasized = item.installed,
        )
    }
}

/** 状态胶囊：高 18 / 内距 7 / 10sp。 */
@Composable
private fun NativeStatusChip(text: String, colors: NativePanelColors, emphasized: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (emphasized) colors.bgModulePlatform else colors.bgBase)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(9.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = colors.labelSecondary, fontSize = 10.sp, lineHeight = 12.sp)
    }
}

/**
 * 「原生插件」详情页：图标 + 名称/版本/包名 + 能力清单 + 签名指纹 + 状态。
 *
 * @param item 被打开的插件。
 * @param onBack 返回列表。
 * @param onUninstall 卸载该伴侣 APK（跳系统卸载）。
 */
@Composable
fun NativePluginDetail(
    item: NativePluginItem,
    onBack: () -> Unit,
    onUninstall: (NativePluginItem) -> Unit,
    colors: NativePanelColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 返回条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ 原生插件",
                color = colors.labelPrimary,
                fontSize = 15.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.bgModulePlatform)
                            .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.name.take(1),
                            color = colors.labelPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column {
                        Text(
                            text = item.name,
                            color = colors.labelPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${item.pluginId} · v${item.version}",
                            color = colors.labelTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (item.description.isNotBlank()) {
                item {
                    Text(
                        text = item.description,
                        color = colors.labelSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            if (item.capabilities.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "声明的能力",
                            color = colors.labelPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        for (cap in item.capabilities) {
                            Text(
                                text = "· $cap",
                                color = colors.labelSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            item.signatureFingerprint?.let { fp ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "签名指纹",
                            color = colors.labelPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = fp.take(16) + "…",
                            color = colors.labelTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (item.installed) {
                item {
                    Text(
                        text = "卸载",
                        color = colors.error,
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

/** 面板配色快照（从插件页的 dshPanelColors 适配，避免 native-host 反向依赖 app）。 */
data class NativePanelColors(
    val labelPrimary: androidx.compose.ui.graphics.Color,
    val labelSecondary: androidx.compose.ui.graphics.Color,
    val labelTertiary: androidx.compose.ui.graphics.Color,
    val bgBase: androidx.compose.ui.graphics.Color,
    val bgLayer3: androidx.compose.ui.graphics.Color,
    val bgModulePlatform: androidx.compose.ui.graphics.Color,
    val borderL2: androidx.compose.ui.graphics.Color,
    val borderL3: androidx.compose.ui.graphics.Color,
    val error: androidx.compose.ui.graphics.Color,
)
