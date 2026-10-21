package com.deepseek.dsh

import androidx.compose.runtime.Composable

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

    /** 翻译服务的选择顺序与官方 `PROVIDER_PREFERENCES` 一致。 */
    private val providers = listOf(
        "bing" to "Bing",
        "google" to "Google",
        "deepseek-account" to "DeepSeek Flash · 账号（付费）",
        "deepseek-official" to "DeepSeek Flash · 官方 API（付费）",
    )

    @Composable
    override fun Section(hooks: PluginUiHooks) {
        PluginSettingsSection(
            namespace = "cot-translation",
            hooks = hooks,
            fields = listOf(
                PluginField(
                    key = "provider",
                    label = "翻译服务",
                    kind = PluginFieldKind.CHOICE,
                    choices = providers,
                ),
                PluginField(
                    key = "targetLanguage",
                    label = "目标语言",
                    hint = "auto 使用界面语言；也可填写语言代码，例如 zh、en、ja",
                ),
            ),
            notices = listOf(
                "展开的思考内容会发送给所选翻译服务，可能包含私有代码或对话细节。",
                "每个未缓存片段都会通过所选账号或官方 API 凭据单独发送一次付费的 DeepSeek Flash 请求；" +
                    "重新展开会复用已保存的译文，更改服务、语言、原文或请求设置可能发送新的付费请求",
                "Bing 和 Google 翻译无需额外登录或 API key，但匿名接口可能拒绝或限制请求",
            ),
            emptyHint = "翻译设置暂不可用",
        )
    }
}
