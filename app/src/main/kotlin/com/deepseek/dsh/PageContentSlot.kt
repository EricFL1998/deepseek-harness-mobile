package com.deepseek.dsh

import androidx.compose.runtime.Composable

/**
 * 页面内容槽：把「某个页面的整段组合代码」装进一个非 inline 的 composable
 * lambda。
 *
 * Compose 会把传进来的 lambda 编译成独立方法，因此各页面分支的指令不再计入
 * `MainAppHost` 这个巨型 Composable 的方法体——后者曾经贴着 ART 校验上限，
 * 一加功能就 VerifyError 崩溃。
 */
@Composable
internal fun PageContentSlot(content: @Composable () -> Unit) {
    content()
}
