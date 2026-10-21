package com.deepseek.dsh

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 「思考过程机器翻译」插件的原生界面。
 *
 * 官方客户端里，这个插件贡献两块 UI：`plugins.bundle.config` 的翻译偏好面板，
 * 以及展开思考内容时把译文/原文切换画在 `conversation.chat.reasoning.body`
 * 里。这里先实现偏好面板（官方 `TranslationPreferencesForm`）：翻译服务 +
 * 目标语言，保存写 `cot-translation` 命名空间。
 *
 * 文案逐条取自 `packages/experimental/client-ui-cot-translation/src/client/locales.ts`
 * 的 `zh` 字典（provider/paidNotice/privacy/targetLanguage/targetLanguageHint）。
 */
internal object CotTranslationPluginUi : PluginUi {
    override val bundle: String = "@deepseek-ai/dsh-experimental-cot-translation-bundle"

    @Composable
    override fun Section(hooks: PluginUiHooks) {
        PluginSettingsSection(
            namespace = "cot-translation",
            hooks = hooks,
            fields = listOf(
                PluginField(
                    key = "provider",
                    label = stringResource(R.string.cot_service),
                    kind = PluginFieldKind.CHOICE,
                    choices = listOf(
                        "bing" to "Bing",
                        "google" to "Google",
                        "deepseek-account" to stringResource(R.string.cot_provider_account),
                        "deepseek-official" to stringResource(R.string.cot_provider_official),
                    ),
                ),
                PluginField(
                    key = "targetLanguage",
                    label = stringResource(R.string.cot_target_language),
                    hint = stringResource(R.string.cot_target_language_hint),
                ),
            ),
            notices = listOf(
                stringResource(R.string.cot_privacy_note),
                stringResource(R.string.cot_billing_note),
                stringResource(R.string.cot_free_note),
            ),
            emptyHint = stringResource(R.string.cot_unavailable),
        )
    }
}
