package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.DeepSeekAccountState
import com.deepseek.dsh.core.model.DeepSeekUserProfile
import com.deepseek.dsh.core.model.DeepSeekWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DeepSeek 账号 OAuth 鉴权与状态中心
 * 默认账号直接对齐 DeepSeek Platform OAuth
 */
class DeepSeekOAuthManager {

    // 与官方一致：没有已保存凭据时处于未登录状态，不预置演示账号/余额。
    private val _accountFlow = MutableStateFlow(
        DeepSeekAccountState(
            isSignedIn = false,
            profile = null,
            wallets = emptyList()
        )
    )
    val accountFlow: StateFlow<DeepSeekAccountState> = _accountFlow.asStateFlow()

    fun buildOAuthAuthorizeUrl(theme: String = "light"): String {
        return "https://platform.deepseek.com/dsh/authorize?theme=$theme"
    }

    fun signOut() {
        // 退出账号不清除已配置的 API Key（官方两种凭据相互独立）。
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = false,
            profile = null,
            wallets = emptyList(),
            hasApiKey = _accountFlow.value.hasApiKey
        )
    }

    /**
     * API Key 登录：官方 SignInDialog 的「添加 API Key」路径。
     * 只记录「已配置 API Key」，账号仍为未登录态；Key 本身不落盘。
     */
    fun signInWithApiKey(apiKey: String) {
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = false,
            profile = null,
            wallets = emptyList(),
            hasApiKey = apiKey.isNotBlank()
        )
    }

    fun signInSuccess(profile: DeepSeekUserProfile) {
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = true,
            profile = profile,
            wallets = listOf(DeepSeekWallet(currency = "CNY", balance = "100.00")),
            hasApiKey = _accountFlow.value.hasApiKey
        )
    }

    /** Host account/watch 报告 credential-stored 后，用真实资料与余额更新登录态。 */
    fun applyHostAccount(profile: AccountProfileInfo?, balance: AccountBalanceInfo?) {
        _accountFlow.value = DeepSeekAccountState(
            isSignedIn = true,
            profile = DeepSeekUserProfile(
                id = profile?.id ?: "deepseek-account",
                name = profile?.name ?: "已登录 DeepSeek",
                contact = profile?.contact,
                avatarUrl = profile?.avatarUrl,
            ),
            wallets = balance?.wallets ?: emptyList(),
            bonusWallets = balance?.bonusWallets ?: emptyList(),
            hasApiKey = _accountFlow.value.hasApiKey,
        )
    }
}
