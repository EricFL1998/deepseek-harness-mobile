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

    /**
     * WebSocket 读线程的 EOF 兜底。
     *
     * Host 被系统回收时，长连接会以 EOF 结束，而 OkHttp 4 的读线程在个别 teardown
     * 竞态里会把 `EOFException` 直接抛到线程外，导致进程崩溃。这类异常表示「连接
     * 已经断开」，没有业务含义，记录日志后让进程继续存活（重连逻辑会在后续调用中
     * 重新建立连接）。其它未捕获异常仍然走默认崩溃路径，保持可观测。
     */
    private fun installTransportEofGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val cause = generateSequence(error) { it.cause }.lastOrNull() ?: error
            if (cause is java.io.EOFException) {
                android.util.Log.w(
                    "DshTransport",
                    "websocket closed abruptly on ${thread.name}: ${cause.message}",
                )
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

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
     * 正在运行的本地 Host 的 loopback 端口与启动 token。
     *
     * WebView 岛屿要自己发请求（插件的 RPC、资源），不能借用 App 的 OkHttp 会话，
     * 所以把这一对值留在这里：岛屿用 `?token=` 换到同一份 cookie，之后与 App 看到
     * 的是同一个 Host、同一个会话。未绑定时为 0/null。
     */
    @Volatile
    var localHostPort: Int = 0
        private set

    @Volatile
    var localHostToken: String? = null
        private set

    /**
     * 将仓库客户端绑定到本地 Host 运行时实际报告的 loopback 端口，并用启动
     * token 换取访问 cookie；UI 的所有数据请求随后都走这个真实地址。
     */
    suspend fun bindLocalHost(port: Int, token: String?) {
        // 占位端口 1 表示还没有真正可用的 Host：不要绑定，避免把 UI 的
        // 所有请求/流指向一个必然拒绝连接的地址。
        if (port <= 1) return
        val baseUrl = "http://127.0.0.1:$port"
        localHostPort = port
        localHostToken = token
        defaultClient.updateBaseUrl(baseUrl)
        // bridge 与 Remote API 共用同一个回环地址与会话 CookieJar；先绑地址，
        // 再换 token，这样 bridge 的第一次请求就带着已建立的会话。
        dshRepository.bindBridge(baseUrl, com.deepseek.dsh.core.bridge.BridgeRegistry(baseUrl, okHttpClient))
        if (!token.isNullOrBlank()) {
            val res = defaultClient.authenticate(baseUrl, token)
            // 同步认证状态到 Android 系统 CookieManager，供 WebView / WebIslandContainer 使用
            try {
                val cm = android.webkit.CookieManager.getInstance()
                cm.setAcceptCookie(true)
                // 也可以直接在后台预热触发一次 Web 交换
            } catch (_: Throwable) {}
        }
    }

    fun unbindLocalHost() {
        localHostPort = 0
        localHostToken = null
        defaultClient.updateBaseUrl("http://127.0.0.1:1")
        dshRepository.bindBridge(
            "http://127.0.0.1:1",
            com.deepseek.dsh.core.bridge.BridgeRegistry("http://127.0.0.1:1", okHttpClient),
        )
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
        installTransportEofGuard()
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
        if (java.io.File(filesDir, "rootfs/etc/os-release").exists()) {
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




