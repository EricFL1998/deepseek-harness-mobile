package com.deepseek.dsh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.runtime.host.DefaultHostRuntimeManager
import com.deepseek.dsh.runtime.host.HostProcessRunner
import com.deepseek.dsh.runtime.host.HostRuntimeManager
import com.deepseek.dsh.runtime.host.RuntimeLayout
import com.deepseek.dsh.runtime.host.RuntimePhase
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var runtimeManager: HostRuntimeManager
    private val runner = HostProcessRunner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = RuntimeLayout(
            baseDir = filesDir,
            nativeLibraryDir = File(applicationInfo.nativeLibraryDir)
        )
        runtimeManager = DefaultHostRuntimeManager(
            context = applicationContext,
            layout = layout,
            runner = runner
        )

        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    MainScreen(
                        runtimeManager = runtimeManager,
                        runner = runner,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    runtimeManager: HostRuntimeManager,
    runner: HostProcessRunner,
    modifier: Modifier = Modifier
) {
    val status by runner.statusFlow.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DSH 手机端 v4.0",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "R0 阶段: 本地 Linux 运行时 Spike",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "运行阶段: ${status.phase}")
                Text(text = "端口: ${status.port ?: "未分配"}")
                Text(text = "Token: ${status.token?.take(10)?.plus("...") ?: "无"}")
                Text(text = "PID: ${status.pid ?: "无"}")
                status.lastError?.let {
                    Text(text = "错误: $it", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (status.phase == RuntimePhase.STOPPED || status.phase == RuntimePhase.UNINSTALLED || status.phase == RuntimePhase.ERROR) {
            Button(onClick = {
                scope.launch {
                    val defaultWorkspace = File(context.filesDir, "workspace").apply { mkdirs() }.absolutePath
                    runtimeManager.start(defaultWorkspace)
                }
            }) {
                Text(text = "启动本地 DSH 运行时")
            }
        } else {
            Button(onClick = {
                scope.launch {
                    runtimeManager.stop()
                }
            }) {
                Text(text = "停止本地 DSH 运行时")
            }
        }
    }
}
