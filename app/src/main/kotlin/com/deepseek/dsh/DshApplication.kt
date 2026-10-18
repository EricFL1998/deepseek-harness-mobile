package com.deepseek.dsh

import android.app.Application
import com.deepseek.dsh.runtime.host.DefaultHostRuntimeManager
import com.deepseek.dsh.runtime.host.HostProcessRunner
import com.deepseek.dsh.runtime.host.HostRuntimeHolder
import com.deepseek.dsh.runtime.host.HostRuntimeManager
import com.deepseek.dsh.runtime.host.RuntimeLayout
import androidx.room.Room
import com.deepseek.dsh.core.data.DefaultSettingsRepository
import com.deepseek.dsh.core.data.DshDatabase
import com.deepseek.dsh.core.data.DshRepository
import com.deepseek.dsh.core.protocol.HostAuthenticator
import com.deepseek.dsh.core.protocol.HostCookieJar
import com.deepseek.dsh.core.protocol.RemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DshApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var runtimeLayout: RuntimeLayout
        private set

    val hostRunner = HostProcessRunner()

    lateinit var runtimeManager: HostRuntimeManager
        private set

    val database: DshDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            DshDatabase::class.java,
            "dsh_app.db"
        ).fallbackToDestructiveMigration().build()
    }

    val cookieJar by lazy { HostCookieJar() }

    val okHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createRemoteClient(baseUrl: String): RemoteClient {
        return RemoteClient(baseUrl = baseUrl, client = okHttpClient)
    }

    val defaultClient: RemoteClient by lazy {
        createRemoteClient("http://127.0.0.1:1")
    }

    /**
     * 将仓库客户端绑定到本地 Host 运行时实际报告的 loopback 端口，并用启动
     * token 换取访问 cookie；UI 的所有数据请求随后都走这个真实地址。
     */
    suspend fun bindLocalHost(port: Int, token: String?) {
        val baseUrl = "http://127.0.0.1:$port"
        defaultClient.updateBaseUrl(baseUrl)
        if (!token.isNullOrBlank()) {
            defaultClient.authenticate(baseUrl, token)
        }
    }

    fun unbindLocalHost() {
        defaultClient.updateBaseUrl("http://127.0.0.1:1")
    }

    val dshRepository: DshRepository by lazy {
        DshRepository(
            dao = database.dshDao(),
            client = defaultClient,
            scope = applicationScope
        )
    }

    val settingsRepository: DefaultSettingsRepository by lazy {
        DefaultSettingsRepository(
            dao = database.dshDao(),
            scope = applicationScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        runtimeLayout = RuntimeLayout(
            baseDir = filesDir,
            nativeLibraryDir = java.io.File(applicationInfo.nativeLibraryDir)
        )
        runtimeManager = DefaultHostRuntimeManager(
            context = applicationContext,
            layout = runtimeLayout,
            runner = hostRunner
        )
        HostRuntimeHolder.manager = runtimeManager
        // App 打开即开始预热点 Host（Activity/Compose 之前）；不常驻后台。
        if (java.io.File(filesDir, "profileInstalled").exists()) {
            val workspace = java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Documents/DSH-Workspace"
            )
            applicationScope.launch(Dispatchers.IO) {
                runCatching { runtimeManager.start(workspace.absolutePath) }
            }
        }
    }
}



