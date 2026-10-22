package com.deepseek.dsh.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.model.MessageFeedbackInfo

/**
 * chat 模块与宿主之间的消息反馈桥。
 *
 * 为什么用桥而不是给 ChatScreen 加参数：MainActivity 里的 `MainAppHost` 是一个
 * 约 1.9 万条 DEX 指令的巨型 composable，任何新增参数/内联 lambda 都会把它推到
 * ART verifier 拒绝的边界（启动即 VerifyError）。桥只保留可以随时替换的函数引用，
 * 宿主在已有的会话 effect 里赋值，不再扩大 `MainAppHost`。
 */
object MessageFeedbackBridge {
    /** 当前会话的反馈，宿主刷新后写入；ChatScreen 直接读它并自动重组。 */
    var items by mutableStateOf<Map<String, MessageFeedbackInfo>>(emptyMap())

    /** 拉取当前会话反馈（宿主注入）。 */
    var refresh: (suspend () -> Map<String, MessageFeedbackInfo>)? = null

    /** 提交一次反馈；rating 为 null 表示删除（宿主注入）。 */
    var submit: ((messageId: String, rating: String?, note: String?) -> Unit)? = null

    /** 会话切换时清空，避免上一会话的反馈残留到新会话消息上。 */
    fun clear() {
        items = emptyMap()
    }
}
