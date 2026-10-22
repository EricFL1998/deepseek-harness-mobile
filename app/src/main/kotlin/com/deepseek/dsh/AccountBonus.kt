package com.deepseek.dsh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.data.DshRepository
import com.deepseek.dsh.core.model.AccountBonusInfo

/**
 * 未读奖励提示（官方 `account/getUnnotifiedBonuses`）。
 *
 * 官方 Web 客户端在打开时查询未展示的奖励并弹窗；这里同逻辑：首次组合时拉一次，
 * 有奖励就展示，点「知道了」逐条 `ackBonusNotified`。逻辑放在独立 composable，
 * `MainAppHost` 只留一行调用。
 */
@Composable
fun AccountBonusHost(
    repository: DshRepository,
    hostId: String,
    hostBound: Boolean,
) {
    var accountId by remember { mutableStateOf<String?>(null) }
    var bonuses by remember { mutableStateOf<List<AccountBonusInfo>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(hostId, hostBound) {
        if (!hostBound) return@LaunchedEffect
        val result = runCatching { repository.fetchUnnotifiedBonuses(hostId) }.getOrNull()
        if (result != null) {
            accountId = result.first
            bonuses = result.second
        }
    }
    if (bonuses.isEmpty()) return
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.account_bonus_title)) },
        text = {
            Column {
                bonuses.forEach { bonus ->
                    Text(
                        text = bonus.title ?: bonus.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    if (!bonus.title.isNullOrBlank() && bonus.message.isNotBlank()) {
                        Text(
                            text = bonus.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = accountId
                val pending = bonuses
                bonuses = emptyList()
                if (id != null) {
                    scope.launch {
                        pending.forEach { bonus ->
                            runCatching { repository.ackBonusNotified(hostId, id, bonus.orderId) }
                        }
                    }
                }
            }) {
                Text(stringResource(R.string.account_bonus_ok))
            }
        },
    )
}
