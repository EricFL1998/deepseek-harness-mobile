package com.deepseek.dsh.feature.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.feature.chat.R

/**
 * `conversation.composer.dock` 底栏。
 *
 * 对齐 composer dock：一行无底色、无边框的读数，按顺序为
 * 推理速率（tok/s）、缓存命中率、上下文占用环形计量。
 */
@Composable
fun ComposerDockBar(
    stats: ComposerStats = ComposerStats(),
    showStats: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showStats) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. 推理速率（IconGaugeOutline + "<speed> tok/s"）
        StatReading(
            label = stringResource(R.string.stat_tok_per_sec, stats.outputSpeedTokensPerSec),
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_stat_gauge),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }

        // 2. 缓存命中率（IconDatabaseOutline + "缓存命中 <percent>%"）
        StatReading(
            label = stringResource(R.string.stat_cache_hit, stats.cacheHitPercent),
        ) {
            Icon(
                painter = painterResource(id = com.deepseek.dsh.core.designsystem.R.drawable.ic_stat_database),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }

        // 3. 上下文占用（14dp 环形计量 + 百分比）
        val contextAria = stringResource(R.string.stat_context_aria, stats.contextPercent)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .semantics { contentDescription = contextAria }
                .padding(horizontal = 8.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContextRing(percent = stats.contextPercent)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${stats.contextPercent}%",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 读数样式：14px 图标 + 6dp 间距 + 三级文字色，无底色无边框。 */
@Composable
private fun StatReading(
    label: String,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}

/** 上下文环形计量：14dp 视口、2dp 描边，从 12 点方向顺时针填充。 */
@Composable
private fun ContextRing(percent: Int) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(14.dp)) {
        val strokeWidth = 2.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(
            x = (size.width - radius * 2) / 2f,
            y = (size.height - radius * 2) / 2f,
        )
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = fillColor,
            startAngle = -90f,
            sweepAngle = 360f * percent.coerceIn(0, 100) / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}



