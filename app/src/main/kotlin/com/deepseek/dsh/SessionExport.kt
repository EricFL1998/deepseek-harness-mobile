package com.deepseek.dsh

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import com.deepseek.dsh.core.data.DshRepository

/**
 * 下载当前会话日志并保存到系统 Download 目录（官方 `/api/session.export`）。
 *
 * 放在独立文件里，是为了让 `MainAppHost` 只保留一行桥接赋值——它是接近 ART
 * verifier 上限的巨型 composable，所有上下文逻辑都不应该内联进去。
 *
 * @param context 用于 ContentResolver 与 Toast。
 * @param repository 数据层，负责带鉴权地 GET 导出路由。
 * @param hostId 当前 Host。
 * @param sessionId 要导出的会话。
 */
suspend fun performSessionExport(
    context: Context,
    repository: DshRepository,
    hostId: String,
    sessionId: String,
) {
    val bytes = runCatching { repository.exportSessionLog(hostId, sessionId) }.getOrNull()
    if (bytes == null || bytes.isEmpty()) {
        Toast.makeText(
            context,
            context.getString(R.string.session_export_failed),
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    val saved = runCatching { saveZip(context, sessionId, bytes) }.getOrDefault(false)
    Toast.makeText(
        context,
        context.getString(
            if (saved) R.string.session_export_saved else R.string.session_export_failed,
        ),
        Toast.LENGTH_LONG,
    ).show()
}

/** 通过 MediaStore 写入 Download 目录；返回是否成功拿到 uri。 */
private fun saveZip(context: Context, sessionId: String, bytes: ByteArray): Boolean {
    val name = "dsh-session-" + sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".zip"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return false
    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return false
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    return true
}
