package com.deepseek.dsh.feature.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.feature.approval.R

/**
 * v4.0 设计文档 §6.5: 审批与提问状态机模型
 * Pending -> Approve/Deny -> Submitting -> Confirmed/Failed
 */
enum class ApprovalStatus(val labelResId: Int) {
    PENDING(R.string.approval_status_pending),
    SUBMITTING(R.string.approval_status_submitting),
    CONFIRMED(R.string.approval_status_confirmed),
    REJECTED(R.string.approval_status_rejected)
}

data class ApprovalItem(
    val id: String,
    val title: String,
    val command: String,
    val dangerReason: String? = null,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

@Composable
fun ApprovalScreen(
    approvals: List<ApprovalItem>,
    onApprove: (ApprovalItem) -> Unit,
    onDeny: (ApprovalItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.approval_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.approval_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (approvals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.approval_empty),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(approvals, key = { it.id }) { item ->
                    ApprovalCard(
                        item = item,
                        onApprove = { onApprove(item) },
                        onDeny = { onDeny(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun ApprovalCard(
    item: ApprovalItem,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (item.status) {
                        ApprovalStatus.PENDING -> MaterialTheme.colorScheme.errorContainer
                        ApprovalStatus.SUBMITTING -> MaterialTheme.colorScheme.primaryContainer
                        ApprovalStatus.CONFIRMED -> Color(0xFFE8F5E9)
                        ApprovalStatus.REJECTED -> MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = stringResource(item.status.labelResId),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            item.dangerReason?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (item.status == ApprovalStatus.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDeny) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_deny))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onApprove) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_approve))
                    }
                }
            } else if (item.status == ApprovalStatus.SUBMITTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.approval_status_submitting), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
