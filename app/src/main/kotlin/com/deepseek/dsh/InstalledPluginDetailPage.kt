package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.designsystem.DshSwitch
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import com.deepseek.dsh.core.model.PluginRowInfo
import com.deepseek.dsh.plugin.nativehost.NativePluginItem

/**
 * 已安装的第三方「行插件」详情页。
 *
 * 行插件没有 bundle 记录，它的内容是 Host 的一条 Loader 行 + 可选的伴侣 APK。
 * 伴侣 APK 在这里作为子组件出现（作者在 manifest 里用 `dsh.plugin.jsPackage`
 * 声明配对），而不是在「已安装插件」里再单独占一行。
 *
 * @param plugin 这一行的插件。
 * @param companions 配对的伴侣 APK（可能为空）。
 * @param companionEnabled 某个伴侣是否启用（App 侧开关）。
 * @param onToggleCompanion 拨动伴侣开关。
 * @param onOpenCompanion 打开伴侣详情。
 * @param onToggle 启停这个插件（Host 的 Loader 行）。
 * @param onUninstall 卸载这个插件。
 */
@Composable
internal fun InstalledPluginDetailPage(
    plugin: PluginRowInfo,
    companions: List<NativePluginItem>,
    companionEnabled: (NativePluginItem) -> Boolean,
    onToggleCompanion: (NativePluginItem, Boolean) -> Unit,
    onOpenCompanion: (NativePluginItem) -> Unit,
    /** 卸载一个伴侣 APK（系统确认框由外层发起）。 */
    onUninstallCompanion: (NativePluginItem) -> Unit,
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
        // 头部：图标 + 名称 / 描述 / 模块名，右侧开关与卸载。
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.bgModulePlatform)
                    .border(0.5.dp, colors.borderL3, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = plugin.title.ifBlank { plugin.moduleName }.take(1),
                    color = colors.labelPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.title.ifBlank { plugin.moduleName },
                    color = colors.labelPrimary,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = plugin.moduleName,
                    color = colors.labelTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plugin.description.isNotBlank() && plugin.description != plugin.moduleName) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = plugin.description,
                        color = colors.labelSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DshSwitch(checked = plugin.enabled, onCheckedChange = onToggle)
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
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.plugins_components_count, companions.size),
            color = colors.labelPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        if (companions.isEmpty()) {
            Text(
                text = stringResource(R.string.plugins_no_companion),
                color = colors.labelTertiary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            companions.forEach { companion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenCompanion(companion) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bgModulePlatform)
                            .border(0.5.dp, colors.borderL3, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = companion.name.take(1),
                            color = colors.labelPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = companion.name,
                                color = colors.labelPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                        Text(
                            text = companion.description,
                            color = colors.labelTertiary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.borderL4),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.plugins_source_info), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        DetailFactRow(label = stringResource(R.string.plugins_code_source), value = plugin.moduleName, monospace = true)
        DetailFactRow(
            label = stringResource(R.string.plugins_status),
            value = if (plugin.enabled) stringResource(R.string.plugins_status_running)
            else stringResource(R.string.plugins_status_stopped),
        )
    }
}
