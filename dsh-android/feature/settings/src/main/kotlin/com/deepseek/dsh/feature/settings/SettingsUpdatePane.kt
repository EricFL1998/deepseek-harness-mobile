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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_REPO = "EricFL1998/deepseek-harness-mobile"
private const val RELEASES_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
private const val RELEASES_PAGE = "https://github.com/$GITHUB_REPO/releases"

private data class UpdateRelease(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
)

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Latest(val current: String, val tag: String) : UpdateState
    data class Available(val current: String, val release: UpdateRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
internal fun SettingsUpdatePane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0.0" }
    }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun check() {
        state = UpdateState.Checking
        scope.launch {
            state = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "dsh-android")
                    }
                    try {
                        if (connection.responseCode !in 200..299) {
                            return@runCatching UpdateState.Failed("GitHub 返回 HTTP ${connection.responseCode}")
                        }
                        val root = JSONObject(connection.inputStream.bufferedReader().readText())
                        val release = UpdateRelease(
                            tag = root.optString("tag_name").removePrefix("v"),
                            name = root.optString("name").ifBlank { root.optString("tag_name") },
                            body = root.optString("body"),
                            htmlUrl = root.optString("html_url", RELEASES_PAGE),
                        )
                        if (release.tag.isBlank()) UpdateState.Failed("Release 缺少版本号")
                        else if (release.tag == currentVersion) UpdateState.Latest(currentVersion, release.tag)
                        else UpdateState.Available(currentVersion, release)
                    } finally {
                        connection.disconnect()
                    }
                }.getOrElse { UpdateState.Failed(it.message ?: "检查更新失败") }
            }
        }
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
                    when (val current = state) {
                        is UpdateState.Idle -> Text(
                            "从 GitHub Releases 检查新版本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在检查更新…", style = MaterialTheme.typography.bodyMedium)
                        }
                        is UpdateState.Latest -> Text(
                            "已是最新版本（${current.current}）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        is UpdateState.Available -> Column {
                            Text("发现新版本", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(current.release.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "当前 ${current.current} → ${current.release.tag}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (current.release.body.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    current.release.body.take(2000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { openUrl(current.release.htmlUrl) }) { Text("前往下载") }
                                OutlinedButton(onClick = { openUrl(RELEASES_PAGE) }) { Text("全部版本") }
                            }
                        }
                        is UpdateState.Failed -> Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state !is UpdateState.Checking) {
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { check() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state is UpdateState.Failed) "重试" else "检查更新")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { openUrl("https://github.com/$GITHUB_REPO") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在 GitHub 查看项目")
            }
        }
        item {
            Text(
                "更新来源：github.com/$GITHUB_REPO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
