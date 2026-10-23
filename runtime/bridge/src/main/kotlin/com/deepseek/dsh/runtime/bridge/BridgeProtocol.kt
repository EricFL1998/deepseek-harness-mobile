package com.deepseek.dsh.runtime.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * v4.0 设计文档 §10.2: 设备能力桥 JSON-RPC 协议与能力模型
 */
@Serializable
data class BridgeRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null,
    /**
     * 本地鉴权 token。
     *
     * 桥监听在 127.0.0.1，但 Android 上 loopback 不是「只有本应用能连」，
     * 端口随机只能降低命中率；Host 侧补丁从 guest 的
     * `/root/.dsh/.bridge_token` 读取并在每个请求里携带。缺失或错误的
     * token 会被拒绝，不执行任何设备操作。
     */
    val auth: String? = null
)

@Serializable
data class BridgeResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: JsonElement? = null,
    val error: BridgeError? = null
)

@Serializable
data class BridgeError(
    val code: Int,
    val message: String
)

enum class BridgeMethod(val methodName: String) {
    GET_DEVICE_INFO("android_device_info"),
    GET_CLIPBOARD("android_clipboard_get"),
    SET_CLIPBOARD("android_clipboard_set"),
    SHOW_NOTIFICATION("android_notify"),
    SPEAK_TTS("android_tts"),
    PICK_FILE("android_pick_file"),
    SHARE_TEXT("android_share"),
    EXEC_SHELL("android_exec_shell"),
    EXEC_PROOT("android_exec_proot")
}


