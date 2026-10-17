package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.DeepSeekAccountState
import com.deepseek.dsh.core.model.DeepSeekUserProfile
import com.deepseek.dsh.core.model.DeepSeekWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 官方 DeepSeek 账号 OAuth 鉴权与状态中心
 * 默认账号直接对齐官方 DeepSeek Platform OAuth
 */
class DeepSeekOAuthManager {

    private val _accountFlow = MutableStateFlow(
        DeepSeekAccountState(
            isSignedIn = true,
            profile = DeepSeekUserProfile(
                id = "dsh_user_official",
                name = "DeepSeek 用户",
                contact = "138****0000"
            ),
            wallets = listOf(
                DeepSeekWallet(currency = "CNY", balance = "50.00")
            ),
            bonusWallets = listOf(
                DeepSeekWallet(currency = "CNY", balance = "10.00")
            )
        )
    )
    val accountFlow: StateFlow<DeepSeekAccountState> = _accountFlow.asStateFlow()

    fun buildOAuthAuthorizeUrl(theme: String = "light"): String {
        return "https://platform.deepseek.com/dsh/authorize?theme=$theme"
    }

    fun signOut() {
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = false,
            profile = null,
            wallets = emptyList()
        )
    }

    fun signInSuccess(profile: DeepSeekUserProfile) {
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = true,
            profile = profile,
            wallets = listOf(DeepSeekWallet(currency = "CNY", balance = "100.00"))
        )
    }
}
