package com.deepseek.dsh.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * 账号与余额（右侧面板 AccountSection）。
 *
 * 结构：账号身份卡片 + 余额卡片（充值余额 / 赠金余额 / 更多操作），
 * 卡片为 R20 + 0.5dp 描边的 settings card 材质；金额 18/28，链接按钮 H36/R12。
 */
@Composable
internal fun SettingsAccountPane(
    accountName: String,
    accountBalance: String,
    accountPhone: String,
    isSignedIn: Boolean = false,
    avatarUrl: String? = null,
    onManageAccount: () -> Unit,
    onOpenUpdate: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalSettingsPalette.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsPaneDimens.Horizontal,
            end = SettingsPaneDimens.Horizontal,
            top = SettingsPaneDimens.Top,
            bottom = SettingsPaneDimens.Bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!isSignedIn) {
            // 官方未登录态：仅一张提示卡 + 主按钮「登录」，不展示身份/余额数据。
            item(key = "account_signed_out") {
                SettingsCard(horizontalPadding = 18.dp, verticalPadding = 16.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_misc_account_signed_out_title),
                            style = SettingsText.RowTitleMedium,
                            color = palette.labelPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_misc_account_signed_out_desc),
                            style = SettingsText.AccountBody,
                            color = palette.labelTertiary,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SettingsButton(
                            text = stringResource(R.string.settings_misc_login),
                            onClick = onSignIn,
                            variant = SettingsButtonVariant.Primary,
                        )
                    }
                }
            }
        } else {
            // 1. 身份卡片：32dp 头像 + 名称 14/500 + 状态 13，右侧「更多账号信息 ↗」。
            item(key = "account_identity") {
                SettingsCard(horizontalPadding = 18.dp, verticalPadding = 14.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteSettingsAvatar(url = avatarUrl, size = 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = accountName,
                                style = SettingsText.RowTitleMedium,
                                color = palette.labelPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = accountPhone,
                                style = SettingsText.AccountBody,
                                color = palette.labelTertiary,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        SettingsTextLink(text = stringResource(R.string.settings_misc_more_account_info), onClick = onManageAccount) {
                            Icon(
                                imageVector = Icons.Filled.NorthEast,
                                contentDescription = null,
                                tint = palette.labelPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // 2. 余额卡片：行高 40、0.5dp 分隔线，末行右侧两个 H36 链接按钮。
            item(key = "account_balance") {
                SettingsCard(horizontalPadding = 18.dp, verticalPadding = 14.dp) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BalanceRow(label = stringResource(R.string.settings_misc_balance_recharge), amount = accountBalance)
                        SettingsCardDivider()
                        BalanceRow(label = stringResource(R.string.settings_misc_balance_bonus), amount = "¥0.00")
                        SettingsCardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_misc_more),
                                style = SettingsText.AccountBody,
                                color = palette.labelTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            SettingsButton(
                                text = stringResource(R.string.settings_misc_usage),
                                onClick = onManageAccount,
                                variant = SettingsButtonVariant.Outline,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            SettingsButton(
                                text = stringResource(R.string.settings_misc_top_up),
                                onClick = onManageAccount,
                                variant = SettingsButtonVariant.Primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 余额行：13/22 标签 + 18/28 金额。 */
@Composable
private fun BalanceRow(label: String, amount: String) {
    val palette = LocalSettingsPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SettingsText.AccountBody,
            color = palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(16.dp))
        val formattedAmount = if (amount.startsWith("¥")) {
            val num = amount.removePrefix("¥").toDoubleOrNull()
            if (num != null) String.format(java.util.Locale.US, "¥%.2f", num) else amount
        } else {
            val num = amount.toDoubleOrNull()
            if (num != null) String.format(java.util.Locale.US, "¥%.2f", num) else amount
        }
        Text(
            text = formattedAmount,
            style = SettingsText.Amount,
            color = palette.labelPrimary,
        )
    }
}

@Composable
private fun RemoteSettingsAvatar(url: String?, size: androidx.compose.ui.unit.Dp) {
    var image by androidx.compose.runtime.remember(url) {
        androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                java.net.URL(url).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }?.let { it.asImageBitmap() }
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = "🐱", fontSize = 15.sp)
        }
    }
}



