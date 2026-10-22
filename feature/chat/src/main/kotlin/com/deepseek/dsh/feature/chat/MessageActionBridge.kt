package com.deepseek.dsh.feature.chat

/**
 * 消息级操作的桥（宿主注入）。
 *
 * 目前只有「从这条消息分叉」需要访问 Host（`session/fork`，带 `atSeq`）。
 * 复制走本地剪贴板，不需要宿主能力。
 */
object MessageActionBridge {
    /** 从某个 seq 分叉新会话（宿主注入）。 */
    var forkAt: ((seq: Int) -> Unit)? = null

    /** 下载当前会话日志 ZIP（宿主注入）。 */
    var exportSession: (() -> Unit)? = null
}
