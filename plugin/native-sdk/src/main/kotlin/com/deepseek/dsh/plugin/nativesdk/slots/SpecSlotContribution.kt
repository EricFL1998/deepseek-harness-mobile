package com.deepseek.dsh.plugin.nativesdk.slots

import androidx.compose.runtime.Composable
import com.deepseek.dsh.plugin.nativesdk.host.PluginHostApi
import com.deepseek.dsh.plugin.nativesdk.render.DshUiSpecParser
import com.deepseek.dsh.plugin.nativesdk.render.SpecRenderer
import com.deepseek.dsh.plugin.nativesdk.theme.PluginTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通用落点贡献：把 slot spec 里的 `ui` 字段当作一份 `dsh-ui` spec，用
 * [SpecRenderer] 直接画成原生 UI。
 *
 * 这是“通用解法”的运行时半边：新增一个插件的 dock/卡片/设置块时，不需要再写
 * Kotlin Compose，只要 Host 推一份 dsh-ui spec（text/card/row/meter/button…），
 * 原生端就能渲染大部分信息型 UI。业务动作仍经 [PluginHostApi.dispatchEvent]
 * 回传 Host。
 *
 * 官方 UI / 各插件都可以注册自己的子类，或直接实例化：
 *
 * ```kotlin
 * SlotRegistry.register(SpecSlotContribution(NativeSlot.COMPOSER_DOCK))
 * ```
 *
 * spec 形状：
 * ```json
 * {
 *   "ui": {
 *     "title": "DeepSeek Account 额度",
 *     "items": [
 *       { "type": "keyvalue", "pairs": [{ "key": "CNY", "value": "¥24.80" }] }
 *     ]
 *   }
 * }
 * ```
 */
open class SpecSlotContribution(
    override val slot: NativeSlot,
    override val order: Int = 50,
    /** spec 中承载 dsh-ui 的字段名；默认 `ui`。 */
    private val specKey: String = "ui",
) : SlotContribution() {
    override val supportedTypes: Set<String> = setOf("dsh-ui")
    override val isOfficial: Boolean = false

    @Composable
    override fun Render(spec: JsonObject, theme: PluginTheme, host: PluginHostApi) {
        val raw = (spec[specKey] as? JsonObject) ?: return
        SpecRenderer.Render(
            spec = DshUiSpecParser.fromJson(raw),
            theme = theme,
            host = host,
        )
    }
}

/** 把 `ui` 字段解析成 dsh-ui spec 的便捷函数（诊断/测试用）。 */
fun JsonObject.dshUiSpec(key: String = "ui") =
    (this[key] as? JsonObject)?.let(DshUiSpecParser::fromJson)
