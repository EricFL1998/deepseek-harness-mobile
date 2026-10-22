package com.deepseek.dsh.feature.chat

/**
 * 用户通过系统文件选择器选中的一个附件。
 *
 * 文件已被复制进当前工作区的 `attachments/` 子目录；消息里以
 * `@<工作区相对路径>` 引用，agent 可以直接读到真实文件内容。
 */
data class ChatAttachment(
    /** 展示用的原始文件名（来自选择器返回的 display name）。 */
    val displayName: String,
    /** 复制进工作区后的相对路径（如 `attachments/photo.png`）。 */
    val workspaceRelativePath: String,
    /** 文件大小（字节）。 */
    val sizeBytes: Long,
)

/**
 * 消息里给 agent 的附件引用格式：`@attachments/photo.png`。
 * 与官方客户端 `@ 文件或对话` 的引用方式一致：模型把 @ 后面的路径
 * 当成工作区内的文件来读。
 */
fun ChatAttachment.asMessageReference(): String = "@$workspaceRelativePath"

/** 给输入卡片附件栏展示的大小文案。 */
fun ChatAttachment.formattedSize(): String {
    if (sizeBytes <= 0) return ""
    val kb = sizeBytes / 1024.0
    return when {
        kb < 1024 -> "%.0f KB".format(kb)
        else -> "%.1f MB".format(kb / 1024.0)
    }
}
