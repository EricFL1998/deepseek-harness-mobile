package com.deepseek.dsh.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.protocol.AttachmentImageRef

/**
 * 用户消息图片能力的桥（宿主注入）。
 *
 * `items` 由宿主在组合期间写入当前会话的用户图片引用；`load` 返回图片字节。
 * 与反馈/轨迹一样走桥，避免给 `MainAppHost` 增加参数。
 */
object MessageMediaBridge {
    var items by mutableStateOf<Map<String, List<AttachmentImageRef>>>(emptyMap())
    /** 当前会话图片引用的快照提供者（宿主注入）。 */
    var provider: (() -> Map<String, List<AttachmentImageRef>>)? = null
    var load: (suspend (AttachmentImageRef) -> ByteArray?)? = null
}

/**
 * 消息里的图片缩略图（官方客户端把用户发的图渲染成圆角缩略图）。
 *
 * 图片字节按需通过 `MessageMediaBridge.load` 拉取并缓存；宽度受限、保持比例，
 * 多图横向排列。
 */
@Composable
fun MessageImagesRow(
    images: List<AttachmentImageRef>,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.take(3).forEach { ref ->
            Thumbnail(ref)
        }
    }
}

@Composable
private fun Thumbnail(ref: AttachmentImageRef) {
    var bitmap by remember(ref.attachmentId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ref.attachmentId) {
        val loader = MessageMediaBridge.load ?: return@LaunchedEffect
        val bytes = runCatching { loader(ref) }.getOrNull() ?: return@LaunchedEffect
        bitmap = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(width = 132.dp, height = 132.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = ref.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(132.dp)
                    .background(MaterialTheme.colorScheme.surface, shape),
            )
        }
    }
}
