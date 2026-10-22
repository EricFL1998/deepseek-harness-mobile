package com.deepseek.dsh

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.deepseek.dsh.feature.chat.ChatAttachment
import java.io.File

/**
 * 把系统文件选择器返回的 Uri 复制进工作区 `attachments/` 子目录。
 *
 * agent 在 proot 里只能看到工作区（/workspace/...），所以附件必须先落到
 * 工作区内真实路径，消息里再以 `@attachments/<name>` 引用。复制是流式的，
 * 不把整个文件读进内存；同名文件自动加序号，避免覆盖。
 */
object AttachmentImporter {

    data class Result(
        val added: List<ChatAttachment>,
        /** 无法读取/复制的文件 display name（用于 toast 提示）。 */
        val skipped: List<String>,
    )

    fun import(context: Context, workspaceDir: File, uris: List<Uri>): Result {
        val targetDir = File(workspaceDir, ATTACHMENTS_DIR).apply { mkdirs() }
        val added = mutableListOf<ChatAttachment>()
        val skipped = mutableListOf<String>()

        for (uri in uris) {
            val meta = queryMeta(context, uri)
            val displayName = meta?.first ?: "file-${System.currentTimeMillis()}"
            val sizeBytes = meta?.second ?: 0L
            val target = uniqueFile(targetDir, displayName)
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("openInputStream returned null")
            }
            if (copied.isSuccess) {
                added.add(
                    ChatAttachment(
                        displayName = target.name,
                        workspaceRelativePath = "$ATTACHMENTS_DIR/${target.name}",
                        sizeBytes = target.length(),
                    ),
                )
            } else {
                runCatching { target.delete() }
                skipped.add(displayName)
            }
        }
        return Result(added = added, skipped = skipped)
    }

    /** 中转导入：把 SAF 选中的任意文件复制进指定工作区目录（Host 可见）。 */
    fun importInto(context: Context, targetDir: File, uris: List<Uri>): Result {
        targetDir.mkdirs()
        val added = mutableListOf<ChatAttachment>()
        val skipped = mutableListOf<String>()
        for (uri in uris) {
            val meta = queryMeta(context, uri)
            val displayName = meta?.first ?: "file-${System.currentTimeMillis()}"
            val target = uniqueFile(targetDir, displayName)
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("openInputStream returned null")
            }
            if (copied.isSuccess) {
                added.add(
                    ChatAttachment(
                        displayName = target.name,
                        workspaceRelativePath = target.absolutePath,
                        sizeBytes = target.length(),
                    ),
                )
            } else {
                runCatching { target.delete() }
                skipped.add(displayName)
            }
        }
        return Result(added = added, skipped = skipped)
    }

    /** 从 SAF 取文件名与大小；失败时返回 null（用回退名）。 */
    private fun queryMeta(context: Context, uri: Uri): Pair<String, Long>? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
            if (name.isNullOrBlank()) null else name to size
        }
    }.getOrNull()

    /** 不覆盖同名文件：name.png → name-1.png → name-2.png。 */
    private fun uniqueFile(dir: File, name: String): File {
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "attachment" }
        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem-$index$ext")
            index += 1
        }
        return candidate
    }

    private const val ATTACHMENTS_DIR = "attachments"
}
