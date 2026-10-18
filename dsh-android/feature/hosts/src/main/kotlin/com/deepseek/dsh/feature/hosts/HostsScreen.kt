package com.deepseek.dsh.feature.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.model.HostDescriptor
import com.deepseek.dsh.core.model.HostKind
import com.deepseek.dsh.feature.hosts.R

/**
 * v4.0 设计文档 §11.1 & §11.3:
 * 多主机（本机 Linux + 远程网关）管理面板，对齐深色调
 */
@Composable
fun HostsScreen(
    hosts: List<HostDescriptor>,
    activeHostId: String,
    onSelectHost: (HostDescriptor) -> Unit,
    onScanPairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkBg = Color(0xFF161719)
    val cardBg = Color(0xFF212327)
    val textPrimary = Color(0xFFEEEEEE)
    val textSecondary = Color(0xFF9E9EA4)
    val textMuted = Color(0xFF6E7077)
    val accentBlue = Color(0xFF3875F6)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hosts_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary
                )
                Text(
                    text = stringResource(R.string.hosts_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = textMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF20283A),
                onClick = onScanPairClick,
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        tint = accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_scan_pair),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hosts, key = { it.id }) { host ->
                val isActive = host.id == activeHostId
                val isLocal = host.kind == HostKind.LOCAL

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) Color(0xFF2A2E38) else cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectHost(host) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLocal) accentBlue else Color(0xFF2E3340)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (isLocal) Color.White else accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = host.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = textPrimary
                                )
                                if (isLocal) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF20283A)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.host_tag_local),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = accentBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = when (host) {
                                    is HostDescriptor.Local -> stringResource(R.string.host_desc_local)
                                    is HostDescriptor.Remote -> host.baseUrl
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = textMuted
                            )
                        }

                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = accentBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

