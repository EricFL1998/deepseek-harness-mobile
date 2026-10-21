package com.deepseek.dsh.plugin.runtimeweb

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * v4.0 设计文档 §12.3: WebView 安全加固与 Web 岛容器
 * - JS 开，File/Content 关，Mixed Content 拒，第三方 Cookie 仅 host
 * - shouldOverrideUrlLoading 白名单
 * - onRenderProcessGone 恢复
 *
 * 除了安全边界，这里还负责把**加载状态**告诉宿主界面：插件界面用
 * [onLoadingChanged] / [onFailed] 画加载指示与失败重试，而不是让用户盯着一块
 * 空白 WebView 猜它是没加载、加载失败、还是根本没有内容。
 *
 * @param onLoadingChanged 主框架开始/结束加载时回调（true = 正在加载）。
 * @param onFailed 主框架加载失败或收到 HTTP 错误时回调，参数是给用户看的原因。
 */
class WebIslandContainer(
    context: Context,
    private val allowedHost: String = "127.0.0.1",
    private val allowedPort: Int = 8080,
    private val onBridgeMessage: ((String, String) -> Unit)? = null,
    private val onLoadingChanged: ((Boolean) -> Unit)? = null,
    private val onFailed: ((String) -> Unit)? = null,
) : WebView(context) {

    @Serializable
    private data class BridgeEnvelope(val type: String, val payload: kotlinx.serialization.json.JsonElement? = null)

    /** 最后一次成功的导航地址，供 [reloadLast] 使用。 */
    private var lastUrl: String? = null

    /** 页面是否已经加载完成；焦点脚本要等它完成后再跑。 */
    private var pageFinished = false

    /** 需要自动打开的设置分区标签（插件自己的 `settings.section` 文案）。 */
    private var focusLabels: List<String> = emptyList()

    /** 需要打开的 `settings.section` 注册 id（与 Web 客户端里的分区 id 一致）。 */
    private var focusSectionId: String? = null

    /**
     * 等 WebView 拿到非零尺寸后再真正开始导航。
     *
     * Android WebView 在 0×0 尺寸下开始导航时，会把布局视口（initial containing
     * block）固定成 0 高；之后再测量也不会重算，结果页面里 `100vh` 全是 0，
     * 设置面板这类按视口高度布局的界面高度算成 0——用户看到的是一整块灰蒙层。
     * 所以首次导航必须放到视图已经测量之后。
     */
    private var pendingLoad: (() -> Unit)? = null

    init {
        setupSecuritySettings()
        setupClients()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSecuritySettings() {
        settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = true
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // Web 客户端按 CSS 视口渲染；默认的宽视口会让手机看到桌面布局。
            useWideViewPort = false
            loadWithOverviewMode = false
            // 页面里的 fetch 走的是本机 loopback，不应带浏览器缓存造成旧 HTML。
            // 但静态资源仍要走缓存，所以这里不整体禁缓存，交给 Host 的响应头。
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@WebIslandContainer, false)
        }

        // 注册安全 JSBridge
        addJavascriptInterface(JsBridge(), "DshNativeBridge")

        // 调试构建允许 chrome://inspect 观察这个 WebView（与 Host 的联调需要）。
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
        }
    }

    private fun setupClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                pageFinished = false
                onLoadingChanged?.invoke(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageFinished = true
                onLoadingChanged?.invoke(false)
                Log.d(TAG, "onPageFinished $url viewport=${view?.width}x${view?.height}")
                // 视口高度为 0 时先修一次，再驱动 Web 客户端自己的界面——设置面板按
                // `100vh` 布局，视口坏着的话它会被算成 0 高，只剩一层灰色蒙版。
                repairViewportIfNeeded()
                // Web 客户端把自己的启动失败画在页面里；从这里读一眼，让宿主知道
                // 这块 WebView 实际没有进入应用界面。
                view?.evaluateJavascript(BOOT_FAILURE_PROBE) { result ->
                    val text = result?.trim()
                    if (text != null && text.length > 2 && text != "null") {
                        val message = text.removeSurrounding("\"")
                        if (message.isNotBlank()) onFailed?.invoke(message)
                    }
                }
                // 打开插件自己的设置分区：Web 客户端没有 URL 路由，只能驱动它自己的
                // 界面（侧边栏 → 账号菜单 → 设置 → 分区行）。脚本在页面里轮询等待
                // React 挂载，失败时保持普通界面，不影响浏览。
                if (focusLabels.isNotEmpty()) {
                    view?.evaluateJavascript(focusScript(focusLabels, focusSectionId)) { result ->
                        Log.d(TAG, "focus script (page finished): $result")
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val host = uri.host
                val port = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80

                // 仅允许本应用关联的 local loopback 或受信域名
                if (host == allowedHost && (port == allowedPort || allowedPort == 0)) {
                    return false
                }
                // 外链不允许直接在岛内导航
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    onLoadingChanged?.invoke(false)
                    val description = error?.description?.toString()?.takeIf { it.isNotBlank() }
                        ?: "网络错误"
                    onFailed?.invoke(description)
                }
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    onLoadingChanged?.invoke(false)
                    onFailed?.invoke("Host 返回 HTTP ${errorResponse?.statusCode ?: 0}")
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                // 崩溃安全兜底
                destroy()
                return true
            }
        }
        // 页面 console 转发到 logcat：Web 半边的启动失败只有在这里能看到完整原因。
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                message ?: return false
                Log.d("DshWebIsland", "${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                return false
            }
        }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun postMessage(jsonString: String) {
            try {
                val envelope = Json.decodeFromString<BridgeEnvelope>(jsonString)
                onBridgeMessage?.invoke(envelope.type, envelope.payload?.toString() ?: "")
            } catch (e: Exception) {
                // Ignore parse errors from untrusted web
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(
            TAG,
            "onSizeChanged ${oldw}x${oldh} -> ${w}x${h} pendingLoad=${pendingLoad != null}",
        )
        if (w > 0 && h > 0) {
            val run = pendingLoad
            pendingLoad = null
            run?.invoke()
        }
    }

    /**
     * 触发一次真实的尺寸变化，让 Chromium 重算布局视口。
     *
     * 这台设备上的 WebView 在页面首次布局时若布局视口高度为 0，之后即便视图尺寸
     * 变成 1280×2448，CSS 的 `100vh` 仍然解析成 0：`window.innerHeight` 是对的，
     * 但以视口高度布局的界面（Web 客户端的设置面板）会被算成 0 高。给视图一次
     * 1px 的高度变化再还原，会走一遍真实的 layout/resize，视口随之更新。
     */
    private fun nudgeViewport() {
        val params = layoutParams as? android.view.ViewGroup.LayoutParams ?: return
        if (width <= 0 || height <= 0) return
        val originalHeight = params.height
        Log.d(TAG, "nudgeViewport on ${width}x${height} params=$originalHeight")
        params.height = height + 1
        requestLayout()
        postDelayed({
            params.height = originalHeight
            requestLayout()
        }, 80)
    }

    /** 布局视口高度是不是坏的（`100vh` = 0），坏就修正；最多重试两次。 */
    private fun repairViewportIfNeeded(attempt: Int = 0) {
        evaluateJavascript(VIEWPORT_PROBE) { result ->
            val ok = result?.contains("ok") == true
            Log.d(TAG, "viewport probe (attempt $attempt): $result")
            if (ok || attempt >= 2) return@evaluateJavascript
            nudgeViewport()
            postDelayed({ repairViewportIfNeeded(attempt + 1) }, 400)
        }
    }

    /** 重新加载最后一次导航；没有加载过时无操作。 */
    fun reloadLast() {
        val target = lastUrl ?: return
        onLoadingChanged?.invoke(true)
        loadUrl(target)
    }

    /**
     * 自动打开插件自己的设置分区。
     *
     * Web 客户端是一个没有 URL 路由的 SPA（`/` 永远是同一个壳），所以「完整界面」
     * 只能加载整个客户端，再驱动它自己的界面走到插件分区。优先直接调用设置面板
     * store 的 `openSection(sectionId)`（不依赖侧边栏/菜单的展开状态），失败再退回
     * 点击路径；脚本在页面里持续重试一段时间，覆盖 Web 客户端启动慢、预览提示
     * 后出现、onboarding 把面板关掉等时序。
     *
     * 失败时什么也不做：界面停在 Web 客户端本身，仍可手动浏览。
     *
     * @param labels 候选标签（本地化 label、注册 id）。
     * @param sectionId 该插件 `settings.section` 的注册 id（Web 客户端里同一身份）。
     */
    fun focusSettingsSection(labels: List<String>, sectionId: String? = null) {
        focusLabels = labels.filter { it.isNotBlank() }
        focusSectionId = sectionId?.takeIf { it.isNotBlank() }
        if (!pageFinished || focusLabels.isEmpty()) return
        evaluateJavascript(focusScript(focusLabels, focusSectionId)) { result ->
            Log.d(TAG, "focus script: $result")
        }
    }

    /** 把标签数组编码成 JS 字面量。 */
    private fun labelsLiteral(labels: List<String>): String =
        labels.joinToString(prefix = "[", postfix = "]") { "\"" + escapeJson(it) + "\"" }

    private fun escapeJson(value: String): String {
        val out = StringBuilder(value.length + 2)
        for (char in value) {
            when (char) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(char)
            }
        }
        return out.toString()
    }

    /**
     * 驱动 Web 客户端走到目标分区的脚本。
     *
     * 优先用设置面板 store 的 `openSection(sectionId)`（由 React fiber 的 props 找到），
     * 它不依赖侧边栏是否展开、账号菜单是否被 onboarding 关掉；随后用 DOM 校验当前
     * 分区行是否 `aria-current`。失败再退回点击路径（launcher → 菜单项 → 分区行）。
     *
     * 整个脚本以 250ms 为节拍持续尝试约 30 秒：Web 客户端启动快慢、首启预览提示、
     * 空白会话的 onboarding 都可能在任意时刻改变界面状态。成功即返回。
     *
     * @param labels 候选分区标签（本地化文案）。
     * @param sectionId `settings.section` 注册 id，可为 null（只按标签点）。
     */
    private fun focusScript(labels: List<String>, sectionId: String?): String = """
        (async () => {
          const labels = ${labelsLiteral(labels)};
          const sectionId = ${sectionId?.let { "\"" + escapeJson(it) + "\"" } ?: "null"};
          const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
          const visible = (el) => !!el && el.offsetParent !== null;
          const buttons = () => Array.from(document.querySelectorAll('button')).filter(visible);
          const byText = (text) => buttons().find((b) => ((b.innerText || '').trim()) === text);
          const byAria = (aria) => buttons().find((b) => b.getAttribute('aria-label') === aria);
          const waitFor = async (fn, timeout) => {
            const end = Date.now() + timeout;
            while (Date.now() < end) {
              const value = fn();
              if (value) return value;
              await sleep(120);
            }
            return null;
          };
          const clickElement = (el) => {
            if (!el) return;
            try { el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (error) { /* noop */ }
            try { el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerType: 'mouse' })); } catch (error) { /* noop */ }
            try { el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true })); } catch (error) { /* noop */ }
            try { el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true })); } catch (error) { /* noop */ }
            try { el.click(); } catch (error) { /* noop */ }
          };
          const settingsDialog = () =>
            document.querySelector('[role="dialog"][aria-modal="true"]:has(> nav)');
          const activeNav = () => {
            const dialog = settingsDialog();
            if (!dialog) return null;
            return dialog.querySelector('nav button[aria-current="true"]')
              || Array.from(dialog.querySelectorAll('nav button')).find((b) => {
                const cls = typeof b.className === 'string' ? b.className : '';
                return /(^|[ _-])active($|[ _-])/i.test(cls);
              }) || null;
          };
          const isNav = (b) => labels.some(
            (label) => label.toLowerCase() === (((b.innerText || '').trim()).toLowerCase()),
          );
          const navButton = () => {
            const dialog = settingsDialog();
            if (!dialog) return null;
            const inDialog = Array.from(dialog.querySelectorAll('button')).filter(visible);
            return inDialog.find(isNav) || null;
          };
          // Web 客户端是桌面优先的：手机宽度下设置面板仍是「188px 导航栏 + 内容」
          // 的横向分栏，内容列只剩不到 200px，中文会一字一行。补一条窄屏适配，
          // 让同一份真实界面在手机上可读：导航变成顶部横向滚动条，内容占满宽度。
          const responsiveCss = [
            '@media (max-width: 700px) {',
            '  [role="dialog"][aria-modal="true"]:has(> nav) { flex-direction: column !important; width: calc(100vw - 16px) !important; max-width: calc(100vw - 16px) !important; height: calc(100vh - 40px) !important; }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) > nav { width: 100% !important; padding: 10px 12px 6px !important; gap: 8px !important; flex-direction: row !important; overflow-x: auto !important; flex: none !important; border-bottom: 1px solid rgba(0,0,0,0.08); }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) > nav > div:first-child { display: none !important; }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) > nav > div { display: flex !important; flex-direction: row !important; gap: 8px !important; }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) > nav > div > div { display: flex !important; flex-direction: row !important; gap: 6px !important; flex-wrap: nowrap !important; }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) > div { min-height: 0 !important; flex: 1 1 auto !important; }',
            '  [role="dialog"][aria-modal="true"]:has(> nav) button { white-space: nowrap; }',
            '}',
          ].join('\n');
          const ensureResponsiveStyle = () => {
            let style = document.getElementById('dsh-mobile-responsive');
            if (style === null) {
              style = document.createElement('style');
              style.id = 'dsh-mobile-responsive';
              (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = responsiveCss;
          };
          const dismissPreview = () => {
            if (!(document.body.innerText || '').includes('预览版说明')) return false;
            const cont = byText('继续') || byText('Continue');
            if (!cont) return false;
            clickElement(cont);
            return true;
          };
          /**
           * 从 settings launcher 的 React fiber 向上找设置面板的 actions。
           * SettingsRoot 把 store 的 actions 当 props 传给内部组件，fiber 返回链上
           * 一定能找到带 openSection 的那一层；这是 Web 客户端自己的 API，不是伪造状态。
           */
          const settingsActions = () => {
            const candidates = Array.from(document.querySelectorAll(
              '[data-slot="sidebar.settings"] button, [data-slot="settings.launcher"] button, button[aria-haspopup="dialog"]',
            ));
            for (const element of candidates) {
              const key = Object.keys(element).find(
                (name) => name.startsWith('__reactFiber$') || name.startsWith('__reactInternalInstance$'),
              );
              if (!key) continue;
              let fiber = element[key];
              let guard = 0;
              while (fiber && guard < 120) {
                const props = fiber.memoizedProps;
                const actions = props && props.actions;
                if (actions && typeof actions.openSection === 'function') return actions;
                fiber = fiber.return;
                guard += 1;
              }
            }
            return null;
          };
          const deadline = Date.now() + 45000;
          let dispatched = false;
          // 已经打开面板但还没等到 active 行时，需要连续几次确认面板没被
          // onboarding 关掉才算成功；否则会「打开→被关→剩余时间不足」。
          let stableChecks = 0;
          let lastNote = '';
          ensureResponsiveStyle();
          while (Date.now() < deadline) {
            try {
              if (dismissPreview()) {
                await sleep(450);
                continue;
              }
              let dialog = settingsDialog();
              let active = activeNav();
              if (dialog && active && isNav(active)) {
                ensureResponsiveStyle();
                return 'focused:' + ((active.innerText || '').trim() || sectionId || 'section');
              }
              // 用 id 打开过、面板确实存在且有 active 行：再连续确认几拍防止
              // onboarding 把面板刚打开就关掉（关掉后 dialog 会消失，计数归零）。
              if (dialog && active && dispatched) {
                stableChecks += 1;
                if (stableChecks >= 3) {
                  ensureResponsiveStyle();
                  return 'focused-id:' + ((active.innerText || '').trim() || sectionId || 'section');
                }
              } else {
                stableChecks = 0;
              }
              // ① 直接调 store：不经过侧边栏/账号菜单的展开状态。
              if (sectionId !== null) {
                let actions = null;
                try { actions = settingsActions(); } catch (error) { lastNote = 'fiber:' + error; }
                if (actions) {
                  try {
                    actions.openSection(sectionId);
                    dispatched = true;
                  } catch (error) {
                    lastNote = 'openSection:' + error;
                  }
                  await sleep(400);
                  ensureResponsiveStyle();
                  dialog = settingsDialog();
                  if (dialog) {
                    const target = navButton();
                    if (target) {
                      clickElement(target);
                      await sleep(300);
                    }
                  }
                  await sleep(250);
                  continue;
                }
                lastNote = 'settings actions not found';
              }
              // ② 退回点击路径：设置 launcher → 菜单项“设置” → 分区行。
              const existing = navButton();
              if (existing) {
                clickElement(existing);
                await sleep(500);
                continue;
              }
              if (!dialog) {
                const trigger = (document.querySelector('[data-slot="sidebar.settings"] button')
                  || document.querySelector('[data-slot="settings.launcher"] button')
                  || byAria('账号菜单') || byAria('设置'));
                if (trigger) {
                  clickElement(trigger);
                  await sleep(350);
                }
                const menuItem = await waitFor(
                  () => Array.from(document.querySelectorAll('button[role="menuitem"]'))
                    .find((b) => /^\s*(设置|Settings)\s*$/.test(b.innerText || '')),
                  1200,
                );
                if (menuItem) {
                  clickElement(menuItem);
                  await sleep(500);
                }
                const target = await waitFor(navButton, 1500);
                if (target) {
                  clickElement(target);
                  await sleep(500);
                }
              }
            } catch (error) {
              lastNote = String(error);
            }
            await sleep(300);
          }
          return 'not-found:' + lastNote;
        })()
    """.trimIndent()

    fun loadSurface(surfaceSlot: String, pluginId: String, isolated: Boolean = true, token: String? = null) {
        val run = { performLoad(surfaceSlot, pluginId, isolated, token) }
        // 0×0 时开始导航会把布局视口固定成 0 高（见 [pendingLoad]），等测量完再走。
        if (width > 0 && height > 0) {
            pendingLoad = null
            run()
        } else {
            pendingLoad = run
        }
    }

    private fun performLoad(surfaceSlot: String, pluginId: String, isolated: Boolean, token: String?) {
        val base = "http://$allowedHost:$allowedPort"
        val queryParts = mutableListOf<String>()
        if (isolated) {
            queryParts.add("__dsh_surface=${Uri.encode(surfaceSlot)}")
            queryParts.add("__dsh_plugin=${Uri.encode(pluginId)}")
            queryParts.add("__dsh_shell=none")
        }
        val query = if (queryParts.isNotEmpty()) {
            "?" + queryParts.joinToString("&")
        } else {
            ""
        }
        val targetUrl = "$base/$query"

        // 启动 token 是**页面级**凭据：Host 对带 token 的请求返回完整 HTML（含 boot 清单），
        // 但不在响应里下发 cookie —— 换取由页面自己的 JS 完成。所以带 token 时要停在
        // 这个 URL 上，不能抢先跳到不带 token 的地址：那样拿到的是一份降级 HTML，
        // 客户端会停在「Failed to load plugins」。cookie 建好之后的加载才用目标地址。
        if (!token.isNullOrBlank()) {
            val cm = CookieManager.getInstance()
            val existing = cm.getCookie(base)
            if (existing.isNullOrBlank() || !existing.contains("dsh-web-session")) {
                val separator = if (query.isEmpty()) "?" else "&"
                val url = "$base/$query${separator}token=${Uri.encode(token)}"
                lastUrl = url
                onLoadingChanged?.invoke(true)
                loadUrl(url)
                return
            }
        }

        lastUrl = targetUrl
        onLoadingChanged?.invoke(true)
        loadUrl(targetUrl)
    }

    private companion object {
        /** logcat 标签：Web 岛加载与视口修复的诊断。 */
        const val TAG = "DshWebIsland"

        /**
         * 判断布局视口高度是否可用：`height: 100vh` 能解析出非零值才算正常。
         * 返回 `ok` / `broken:<值>`。
         */
        const val VIEWPORT_PROBE = """
            (() => {
              try {
                const probe = document.createElement('div')
                probe.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:100vh;pointer-events:none'
                document.body.appendChild(probe)
                const height = probe.getBoundingClientRect().height
                probe.remove()
                return height > 0 ? 'ok' : ('broken:' + height)
              } catch (e) { return 'broken:error' }
            })()
        """

        /**
         * 读出 Web 客户端启动页上的失败文案，交给宿主显示。
         *
         * 页面本身把失败画出来（`Failed to load plugins`），但 WebView 的
         * `onPageFinished` 仍算“成功”，宿主看不到失败。这里只读文本，不修改页面。
         */
        const val BOOT_FAILURE_PROBE = """
            (() => {
              try {
                const el = document.querySelector('[class*="failed"], [class*="failedTitle"]')
                if (!el) return null
                const text = (el.textContent || '').replace(/\s+/g, ' ').trim()
                return text.length > 0 ? text.slice(0, 240) : null
              } catch (e) { return null }
            })()
        """
    }
}
