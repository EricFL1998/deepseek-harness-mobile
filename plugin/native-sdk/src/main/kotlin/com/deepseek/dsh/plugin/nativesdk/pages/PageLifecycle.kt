package com.deepseek.dsh.plugin.nativesdk.pages

import androidx.compose.runtime.staticCompositionLocalOf

/** Prevents a late Host-RPC result from updating a page after the user navigated away. */
object PageLifecycle {
    val LocalIsActive = staticCompositionLocalOf<() -> Boolean> { { false } }
}
