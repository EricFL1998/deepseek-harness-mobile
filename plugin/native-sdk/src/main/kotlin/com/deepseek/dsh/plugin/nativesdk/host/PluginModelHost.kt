package com.deepseek.dsh.plugin.nativesdk.host

/**
 * Optional native capability for slot contributions that need to change the
 * session's model / reasoning rung (e.g. the subscription plugin's fast-mode
 * button). Like [PluginRpcHost], the App owns the implementation; the plugin
 * APK only requests a selection and never talks to Host model services directly.
 */
interface PluginModelHost {
    /**
     * Select one model and optional reasoning effort for the active session.
     *
     * @param provider provider route id (e.g. `kimi`, `grok`, `deepseek-account`).
     * @param model model id (e.g. `kimi-for-coding`).
     * @param reasoningEffort provider rung (e.g. `highspeed`); null restores the
     *   model's own default.
     * @param onResult receives success or a readable failure.
     */
    fun selectModel(
        provider: String,
        model: String,
        reasoningEffort: String?,
        onResult: (Result<Unit>) -> Unit,
    )
}
