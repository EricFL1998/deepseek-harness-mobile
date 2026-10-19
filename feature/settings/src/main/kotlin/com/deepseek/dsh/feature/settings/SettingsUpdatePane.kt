package com.deepseek.dsh.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 官方更新面板：展示 APK、Rootfs 与 DSH 版本号，并检查 GitHub Release 更新。
 */
@Composable
internal fun SettingsUpdatePane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val update by UpdateCenter.update.collectAsState()
    val checking by UpdateCenter.checking.collectAsState()
    val error by UpdateCenter.error.collectAsState()
    val apkVersion = UpdateCenter.currentVersion(context)
    val available = update?.takeIf { UpdateCenter.hasUpdate(context) }

    var rootfsInstalledVersion by remember { mutableStateOf<String?>("检查中…") }
    var dshInstalledVersion by remember { mutableStateOf<String?>("检查中…") }

    LaunchedEffect(Unit) {
        UpdateCenter.check(context)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val runtimeMgr = com.deepseek.dsh.runtime.host.HostRuntimeHolder.manager
            val installed = runtimeMgr?.installedRootfsVersion()
            val available = runtimeMgr?.availableRootfsVersion()
            val rawManifest = runCatching {
                context.assets.open("rootfs-manifest.json").bufferedReader().use { it.readText() }
            }.getOrNull().orEmpty()

            val runtimeVer = installed ?: available ?: runCatching {
                org.json.JSONObject(rawManifest).optString("runtimeVersion")
            }.getOrNull().orEmpty()

            // runtimeVersion 格式示例: 1.0.0-dsh0.2.1-rc.2
            if (runtimeVer.isNotBlank()) {
                val parts = runtimeVer.split("-dsh")
                val rfVer = parts.firstOrNull().orEmpty().ifBlank { runtimeVer }
                val dshVer = if (parts.size > 1) parts[1] else {
                    runCatching {
                        val obj = org.json.JSONObject(rawManifest)
                        val arr = obj.optJSONArray("bundledPackages")
                        (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optString(it) }
                            .firstOrNull { it.startsWith("@deepseek-ai/dsh@") }
                            ?.removePrefix("@deepseek-ai/dsh@")
                    }.getOrNull() ?: "0.2.1-rc.2"
                }
                rootfsInstalledVersion = rfVer
                dshInstalledVersion = dshVer
            } else {
                rootfsInstalledVersion = "未安装"
                dshInstalledVersion = "未就绪"
            }
        }
    }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("软件更新", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                "客户端及底层运行时状态",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        item {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "版本信息",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    VersionItemRow(label = "APK 客户端版本", value = "v$apkVersion")
                    VersionItemRow(label = "Rootfs 镜像版本", value = "v$rootfsInstalledVersion")
                    VersionItemRow(label = "官方 DSH 引擎", value = "$dshInstalledVersion")
                }
            }
        }

        item {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when {
                        checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在检查更新…", style = MaterialTheme.typography.bodyMedium)
                        }
                        available != null -> Column {
                            Text(
                                "新版本 ${available.tag}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(available.name, style = MaterialTheme.typography.bodyMedium)
                            if (available.body.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    available.body.take(2000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(
                            "已是最新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/$DSH_GITHUB_REPO") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在 GitHub 查看客户端项目")
            }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/EricFL1998/deepseek-harness-mobile-rootfs") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在 GitHub 查看 Rootfs 项目")
            }
        }
        item {
            Text(
                "更新来源：github.com/$DSH_GITHUB_REPO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun VersionItemRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

