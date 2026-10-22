package com.deepseek.dsh

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * App 界面语言：由用户在「设置 → 通用 → 语言」里选择，选择同时写给 Host。
 *
 * - `system` 跟随系统；
 * - `zh` / `en` 是 Host 的 `locale.preference` 取值，也是 App 的 per-app locale。
 *
 * 写入走平台的 per-app locale（Android 13+），系统会重建 Activity，资源随之切换；
 * 更老的系统没有 per-app locale，保持跟随系统，只同步 Host 语言。
 */
object AppLanguage {
    private const val PREFS = "app_locale"
    private const val KEY = "selection"

    /** 用户选择的原始值：`system` / `zh` / `en`。 */
    fun selection(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system")
            ?.takeIf { it in setOf("system", "zh", "en") } ?: "system"

    /** 生效的语言标签（BCP-47），用于 Host 语言同步。 */
    fun effectiveTag(context: Context): String {
        val selection = selection(context)
        if (selection != "system") return selection
        return java.util.Locale.getDefault().toLanguageTag()
    }

    /** 启动时套用已保存的选择（幂等）。 */
    fun applySaved(context: Context) = apply(context, selection(context))

    /** 套用一次选择：更新 per-app locale 并持久化。 */
    fun apply(context: Context, selection: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, selection).apply()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales = when (selection) {
            "system" -> LocaleList.getEmptyLocaleList()
            else -> LocaleList.forLanguageTags(selection)
        }
    }
}
