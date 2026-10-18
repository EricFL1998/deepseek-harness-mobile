package com.deepseek.dsh.feature.runtime

import android.content.Context

/**
 * Work scenarios offered by the onboarding purpose page.
 *
 * Mirrors the `OnboardingPurpose` union: office, development, or both
 * when the user selects the two independent cards.
 */
enum class OnboardingPurpose(val storageValue: String) {
    OFFICE("office"),
    DEVELOPMENT("development"),
    BOTH("both"),
}

/**
 * Work-detail preference selected on the onboarding process page.
 *
 * Mirrors the `OnboardingProcess` union. It is applied to the Chat
 * work-step presentation when the introduction completes.
 */
enum class OnboardingProcess(val storageValue: String) {
    COMPACT("compact"),
    STANDARD("standard"),
    DETAILED("detailed"),
}

/**
 * Terminal outcome recorded for the introduction.
 *
 * Mirrors the completion values `completed` and `skipped`.
 */
enum class OnboardingCompletion(val storageValue: String) {
    COMPLETED("completed"),
    SKIPPED("skipped"),
}

/**
 * One installation's resolved onboarding result.
 *
 * The factory [of] applies the completion semantics: an office-only
 * purpose finishes with the compact preference, a development purpose keeps the
 * selected process and enables developer tools, and an explicit skip records the
 * standard/default preferences.
 *
 * @property purpose selected work scenario, or null when skipped from welcome.
 * @property process work-detail preference to apply to Chat.
 * @property completion whether the user finished or skipped the introduction.
 * @property usage performance/usage presentation; `compact` or `detailed`.
 * @property developerTools whether the Coding Tools / developer view is enabled.
 */
data class OnboardingResult(
    val purpose: OnboardingPurpose?,
    val process: OnboardingProcess,
    val completion: OnboardingCompletion,
    val usage: String,
    val developerTools: Boolean,
) {
    companion object {
        /**
         * Resolve the persisted result from the user's choices.
         *
         * @param purpose selected purpose, if any.
         * @param process selected process, when the process page was reached.
         * @param skipped whether the user completed or explicitly skipped the flow.
         * @returns the result with preference mapping applied.
         */
        fun of(
            purpose: OnboardingPurpose?,
            process: OnboardingProcess?,
            skipped: Boolean,
        ): OnboardingResult {
            val office = !skipped && purpose == OnboardingPurpose.OFFICE
            val development = !skipped && !office
            val resolvedProcess = when {
                office -> OnboardingProcess.COMPACT
                skipped -> OnboardingProcess.STANDARD
                else -> process ?: OnboardingProcess.STANDARD
            }
            return OnboardingResult(
                purpose = purpose,
                process = resolvedProcess,
                completion = if (skipped) OnboardingCompletion.SKIPPED else OnboardingCompletion.COMPLETED,
                usage = if (development) "detailed" else "compact",
                developerTools = development,
            )
        }
    }
}

/**
 * Device-local first-run onboarding progress.
 *
 * The client persists progress in the Host settings document; the phone
 * app keeps the equivalent completion marker and applied preferences in private
 * SharedPreferences so the introduction stays a once-per-installation flow.
 *
 * @param context application context used to resolve the private preferences file.
 */
class OnboardingPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Whether this installation has already finished or skipped the introduction. */
    val isCompleted: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)

    /** Previously applied work-detail preference, defaulting to the default. */
    val process: OnboardingProcess
        get() = OnboardingProcess.entries.firstOrNull {
            it.storageValue == prefs.getString(KEY_PROCESS, null)
        } ?: OnboardingProcess.STANDARD

    /** Previously applied developer-tools preference; enabled by default like the app baseline. */
    val developerTools: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER_TOOLS, true)

    /**
     * Persist the resolved choices and the completion marker.
     *
     * @param result resolved onboarding result.
     */
    fun save(result: OnboardingResult) {
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_COMPLETION, result.completion.storageValue)
            .putString(KEY_PURPOSE, result.purpose?.storageValue)
            .putString(KEY_PROCESS, result.process.storageValue)
            .putString(KEY_USAGE, result.usage)
            .putBoolean(KEY_DEVELOPER_TOOLS, result.developerTools)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "dsh_onboarding"
        const val KEY_COMPLETED = "completed"
        const val KEY_COMPLETION = "completion"
        const val KEY_PURPOSE = "purpose"
        const val KEY_PROCESS = "process"
        const val KEY_USAGE = "usage"
        const val KEY_DEVELOPER_TOOLS = "developer_tools"
    }
}
