package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.model.SurfaceItem
import com.deepseek.dsh.feature.chat.R

/**
 * DSH 官方规范:
 * 承载 conversation.composer.dock 插槽的底栏 dock 容器与官方 stats pills (Activity & Usage)
 * 允许第三方插件（如表情、上下文计量、快捷操作）在此注册扩展，保证插件不损坏
 */
data class ComposerStats(
    val outputSpeedTokensPerSec: Double = 42.5,
    val contextPercent: Int = 12,
    val cacheHitPercent: Int = 85
)

@Composable
fun ComposerDockBar(
    stats: ComposerStats = ComposerStats(),
    dockPluginSurfaces: List<SurfaceItem> = emptyList(),
    onPluginClick: (SurfaceItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. DSH 官方 Activity Pill: 推理速率 (tok/s)
        DshStatPill(
            icon = Icons.Default.Speed,
            label = stringResource(R.string.stat_tok_per_sec, stats.outputSpeedTokensPerSec),
            onClick = {}
        )

        // 2. DSH 官方 Usage Pill: 上下文占用率与缓存命中率 (上下文 % · 缓存 %)
        DshStatPill(
            icon = Icons.Default.DataUsage,
            label = stringResource(R.string.stat_context_cache, stats.contextPercent, stats.cacheHitPercent),
            onClick = {}
        )

        // 3. 插件动态注入的 conversation.composer.dock 项 (保留插件生态扩展位)
        dockPluginSurfaces.forEach { item ->
            DshStatPill(
                icon = Icons.Default.Widgets,
                label = item.title ?: item.pluginId,
                onClick = { onPluginClick(item) },
                isPlugin = true
            )
        }
    }
}

@Composable
fun DshStatPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isPlugin: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isPlugin) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        onClick = onClick,
        modifier = Modifier.height(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (isPlugin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlugin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

