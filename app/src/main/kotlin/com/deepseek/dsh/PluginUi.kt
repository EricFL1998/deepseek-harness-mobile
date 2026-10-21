package com.deepseek.dsh

import androidx.compose.runtime.Composable
import com.deepseek.dsh.core.data.DshRepository
import com.deepseek.dsh.core.model.PluginSettingsDocument
import kotlinx.serialization.json.JsonObject

/**
 * 插件原生 UI 用得到的 Host 能力。
 *
 * 官方客户端的插件 UI 主要读两类东西：设置文档（`configForms.get(ns)`）与凭据
 * （`remote.credentials`）。原生页用同一对接口，所以行为与网页版一致。
 */
internal data class PluginUiHooks(
    /** 读取一个设置命名空间的表单与当前值。 */
    val loadDocument: suspend (String) -> PluginSettingsDocument?,
    /** 回写一个设置命名空间（带 revision，冲突时拒绝覆盖）。 */
    val saveDocument: suspend (String, JsonObject, Int) -> Boolean,
    /** 读取一个凭据引用的状态（不返回值，只回已配置/可写）。 */
    val loadCredential: suspend (String) -> DshRepository.CredentialStatus?,
    /** 写入一个凭据引用。 */
    val saveCredential: suspend (String, String) -> Boolean,
    /** 语音识别的 Host `speech` 接口；语音插件之外为 null。 */
    val speech: SpeechHooks? = null,
)

/**
 * 一个实验性插件贡献的原生 UI。
 *
 * 约定：**每个插件一个 `.kt` 文件**，文件里实现本接口并返回单例，然后在
 * [PluginUis] 里登记。bundle 详情页把登记过的 [Section] 画在「包含的组件」
 * 下面；没有 UI 的插件不登记。
 */
internal interface PluginUi {
    /** 这个 UI 属于哪个 bundle（Host 的 bundle 包名，例如 `@deepseek-ai/dsh-experimental-cot-translation-bundle`）。 */
    val bundle: String

    /** bundle 详情页里的区块。 */
    @Composable
    fun Section(hooks: PluginUiHooks)
}

/** 已登记的原生插件 UI。 */
internal object PluginUis {
    private val entries: List<PluginUi> = listOf(
        // 新插件在这里登记（每个插件一个 .kt 文件）。
        CotTranslationPluginUi,
        VoiceInputPluginUi,
    )

    /** @param bundle bundle 包名。 @returns 该 bundle 的原生 UI，没有时 null。 */
    fun forBundle(bundle: String): PluginUi? = entries.firstOrNull { it.bundle == bundle }

    /** 所有已登记 UI 对应的 bundle 包名，供列表页标记「此插件有原生配置」。 */
    val bundles: Set<String>
        get() = entries.map { it.bundle }.toSet()
}
