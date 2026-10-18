package com.deepseek.dsh.feature.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 官方更新面板：只显示当前版本与“检测到的新版本”；没有常驻“检查更新”按钮。
 */
@Composable
internal fun SettingsUpdatePane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val update by UpdateCenter.update.collectAsState()
    val checking by UpdateCenter.checking.collectAsState()
    val error by UpdateCenter.error.collectAsState()
    val currentVersion = UpdateCenter.currentVersion(context)
    val available = update?.takeIf { it.tag != currentVersion }

    LaunchedEffect(Unit) { UpdateCenter.check(context) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("软件更新", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text(
                "当前版本：$currentVersion",
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
                Text("在 GitHub 查看项目")
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
