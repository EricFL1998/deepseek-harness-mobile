package com.deepseek.dsh.core.model

import kotlinx.serialization.Serializable

/**
 * DeepSeek 账号体系 (来源: @deepseek-ai/dsh-deepseek-account)
 */
@Serializable
data class DeepSeekUserProfile(
    val id: String,
    val name: String? = null,
    val contact: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class DeepSeekWallet(
    val currency: String = "CNY",
    val balance: String = "0.00"
)

@Serializable
data class DeepSeekAccountState(
    val isSignedIn: Boolean = false,
    val profile: DeepSeekUserProfile? = null,
    val wallets: List<DeepSeekWallet> = emptyList(),
    val bonusWallets: List<DeepSeekWallet> = emptyList(),
    /** 是否已配置 API Key（官方允许「有 API Key、未登录账号」的独立状态）。 */
    val hasApiKey: Boolean = false,
    val platformUrl: String = "https://platform.deepseek.com"
)

/** Host `account/getState` 的安全投影（不含凭据）。 */
data class AccountViewInfo(
    val status: String = "signed-out",
    val attemptId: String? = null,
    val attemptPhase: String? = null,
    val authorizeUrl: String? = null
)

/** Host `account/getProfile` 的安全资料。 */
data class AccountProfileInfo(
    val id: String? = null,
    val name: String? = null,
    val contact: String? = null,
    val avatarUrl: String? = null
)

/** Host `account/getBalance` 的余额。 */
data class AccountBalanceInfo(
    val wallets: List<DeepSeekWallet> = emptyList(),
    val bonusWallets: List<DeepSeekWallet> = emptyList()
)
