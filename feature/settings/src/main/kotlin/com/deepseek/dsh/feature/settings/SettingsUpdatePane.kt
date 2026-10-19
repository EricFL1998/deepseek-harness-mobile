package com.deepseek.dsh.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.RootfsVersion
import com.deepseek.dsh.runtime.host.UpdateAssessment
import com.deepseek.dsh.runtime.host.UpdateNeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新面板：APK、bridge 插件、rootfs 镜像三块，各自的更新性质不同。
 *
 * - **APK**：装了新版本才提示，走 GitHub Release 下载页。
 * - **bridge 插件**：必需更新。它是客户端的契约对端，Host 与 App 必须说同一个
 *   协议；但代价极小，所以在 rootfs 内就地安装，不重下镜像。
 * - **rootfs 镜像**：可选更新。镜像重建只为跟随 DSH 引擎；旧镜像能跑，
 *   重下几百 MB 不该被强加给用户。
 *
 * 三块都只在这里提示。启动流程不会因为任何一项而打断用户。
 */
@Composable
internal fun SettingsUpdatePane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by UpdateCenter.update.collectAsState()
    val checking by UpdateCenter.checking.collectAsState()
    val error by UpdateCenter.error.collectAsState()
    val apkVersion = UpdateCenter.currentVersion(context)
    val available = update?.takeIf { UpdateCenter.hasUpdate(context) }

    var assessment by remember { mutableStateOf(UpdateAssessment()) }
    var assessmentLoaded by remember { mutableStateOf(false) }
    var pluginBusy by remember { mutableStateOf(false) }
    var pluginStatus by remember { mutableStateOf<String?>(null) }
    var pluginError by remember { mutableStateOf<String?>(null) }
    var rootfsBusy by remember { mutableStateOf(false) }
    var rootfsStatus by remember { mutableStateOf<String?>(null) }

    /** 重新读一次版本与更新结论。 */
    suspend fun refreshAssessment() {
        val manager = HostRuntimeHolder.manager
        if (manager == null) {
            assessmentLoaded = true
            return
        }
        assessment = manager.assessUpdates()
        assessmentLoaded = true
    }

    LaunchedEffect(Unit) {
        UpdateCenter.check(context)
        refreshAssessment()
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    val installed = RootfsVersion.parse(assessment.installed)
    val packaged = RootfsVersion.parse(assessment.available)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("软件更新", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "客户端、兼容层插件与底层运行时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        // ── 版本信息 ─────────────────────────────────────────────────────────
        item {
            UpdateCard {
                Text(
                    "版本信息",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                VersionItemRow("APK 客户端版本", "v$apkVersion")
                VersionItemRow(
                    "Bridge 插件版本",
                    assessment.installedPlugin?.let { "v$it" } ?: "未知",
                )
                VersionItemRow(
                    "Rootfs 镜像版本",
                    installed?.rootfs?.let { "v$it" } ?: "未安装",
                )
                VersionItemRow(
                    "官方 DSH 引擎",
                    installed?.dsh ?: "未就绪",
                )
                // 打包版本只在它与已装版本不同时才有信息量，避免噪声。
                packaged?.bridge?.takeIf { it != assessment.installedPlugin }?.let { packagedBridge ->
                    VersionItemRow("镜像内打包插件", "v$packagedBridge")
                }
            }
        }

        // ── bridge 插件：必需更新 ────────────────────────────────────────────
        item {
            UpdateCard {
                SectionTitle(
                    "Bridge 兼容层插件",
                    badge = if (assessment.need == UpdateNeed.PLUGIN_REQUIRED) "必需更新" else null,
                    badgeUrgent = true,
                )
                when {
                    !assessmentLoaded -> LoadingRow("正在检查插件版本…")
                    assessment.need == UpdateNeed.PLUGIN_REQUIRED -> {
                        Text(
                            "客户端与 Host 通过 bridge/v1 通信，两者必须说同一个协议版本。" +
                                "插件更新只需在运行时内安装，不会重新下载镜像。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "已安装 ${assessment.installedPlugin ?: "未知"} → 最新 ${assessment.latestPlugin ?: "未知"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    assessment.latestPlugin == null -> Text(
                        "无法读取插件最新版本（可能离线）。已安装 ${assessment.installedPlugin ?: "未知"}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    else -> Text(
                        "已是最新版本 ${assessment.latestPlugin}。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                pluginStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                pluginError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !pluginBusy && assessmentLoaded,
                        onClick = {
                            scope.launch {
                                pluginBusy = true
                                pluginError = null
                                pluginStatus = "准备更新…"
                                val manager = HostRuntimeHolder.manager
                                if (manager == null) {
                                    pluginError = "运行时不可用"
                                    pluginBusy = false
                                    return@launch
                                }
                                val result = manager.updateBridgePlugin { _, step ->
                                    pluginStatus = step
                                }
                                pluginBusy = false
                                result.fold(
                                    onSuccess = { version ->
                                        pluginStatus = "插件已更新到 $version"
                                        // 装完重读一次：结论应立刻从「必需」变回「最新」。
                                        refreshAssessment()
                                    },
                                    onFailure = { failure ->
                                        pluginError = failure.message ?: "插件更新失败"
                                    },
                                )
                            }
                        },
                    ) {
                        if (pluginBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(14.dp).height(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (pluginBusy) "正在更新…" else "更新插件")
                    }
                    OutlinedButton(
                        enabled = !pluginBusy && !rootfsBusy,
                        onClick = { scope.launch { refreshAssessment() } },
                    ) { Text("重新检查") }
                }
            }
        }

        // ── rootfs 镜像：可选更新 ────────────────────────────────────────────
        item {
            UpdateCard {
                SectionTitle(
                    "Rootfs 运行时镜像",
                    badge = if (assessment.need == UpdateNeed.ROOTFS_OPTIONAL) "可选更新" else null,
                    badgeUrgent = false,
                )
                when {
                    !assessmentLoaded -> LoadingRow("正在检查镜像版本…")
                    assessment.need == UpdateNeed.ROOTFS_OPTIONAL -> Text(
                        "镜像重建只为跟随新的 DSH 引擎。当前镜像仍然可用；" +
                            "更新会重新下载运行时，并保留你的账号、会话与工作区。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        "镜像与当前发布一致。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                assessment.available?.takeIf { assessment.need == UpdateNeed.ROOTFS_OPTIONAL }?.let { target ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已安装 ${assessment.installed ?: "未安装"} → 可用 $target",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                rootfsStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    enabled = !rootfsBusy && !pluginBusy && assessment.need == UpdateNeed.ROOTFS_OPTIONAL,
                    onClick = {
                        scope.launch {
                            rootfsBusy = true
                            rootfsStatus = "准备重新安装镜像…"
                            val manager = HostRuntimeHolder.manager
                            if (manager == null) {
                                rootfsStatus = "运行时不可用"
                                rootfsBusy = false
                                return@launch
                            }
                            // 镜像替换会重启 Host，因此先停掉再装。
                            withContext(Dispatchers.IO) { manager.stop() }
                            val result = manager.installRootfs { _, step -> rootfsStatus = step }
                            rootfsBusy = false
                            result.fold(
                                onSuccess = {
                                    rootfsStatus = "镜像已更新"
                                    refreshAssessment()
                                },
                                onFailure = { failure ->
                                    rootfsStatus = "镜像更新失败：${failure.message}"
                                },
                            )
                        }
                    },
                ) { Text(if (rootfsBusy) "正在安装…" else "更新镜像（可选）") }
            }
        }

        // ── APK 自身 ─────────────────────────────────────────────────────────
        item {
            UpdateCard {
                SectionTitle("APK 客户端", badge = null, badgeUrgent = false)
                when {
                    checking -> LoadingRow("正在检查更新…")
                    available != null -> {
                        Text(
                            "新版本 ${available.tag}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(available.name, style = MaterialTheme.typography.bodyMedium)
                        if (available.body.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                available.body.take(2000),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openUrl(available.htmlUrl) }) { Text("前往下载") }
                            OutlinedButton(onClick = { openUrl(DSH_RELEASES_PAGE) }) { Text("全部版本") }
                        }
                    }
                    error != null -> Text(
                        error ?: "检查更新失败",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        "已是最新版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/$DSH_GITHUB_REPO") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("在 GitHub 查看客户端项目") }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/EricFL1998/deepseek-harness-mobile-rootfs") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("在 GitHub 查看 Rootfs 项目") }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/EricFL1998/deepseek-harness-mobile-bridge-plugin") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("在 GitHub 查看 Bridge 插件项目") }
        }
        item {
            Text(
                "更新来源：github.com/$DSH_GITHUB_REPO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 统一的面板卡片外观。 */
@Composable
private fun UpdateCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/** 区块标题，可选一个右侧标记。 */
@Composable
private fun SectionTitle(title: String, badge: String?, badgeUrgent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (badgeUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                fontWeight = if (badgeUrgent) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** 一行「检查中」。 */
@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VersionItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
    }
}
