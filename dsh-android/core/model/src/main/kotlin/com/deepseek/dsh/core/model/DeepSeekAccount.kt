package com.deepseek.dsh.core.model

import kotlinx.serialization.Serializable

/**
 * 官方 DeepSeek 账号体系 (来源: @deepseek-ai/dsh-deepseek-account)
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
    val platformUrl: String = "https://platform.deepseek.com"
)
