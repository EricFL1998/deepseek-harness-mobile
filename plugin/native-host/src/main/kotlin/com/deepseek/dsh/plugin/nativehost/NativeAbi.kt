package com.deepseek.dsh.plugin.nativehost

/**
 * native-SDK 的 ABI 版本。伴侣 APK 编译时针对某个版本；App 升级 SDK 破坏契约时
 * 递增。门禁用：伴侣的 `apiVersion` 高于此值即拒载（待更新），低于或等于则按
 * 能力检测降级。
 */
object NativeAbi {
    /** 当前 App 内置 native-SDK 的 ABI 版本。 */
    const val CURRENT: Int = 1
}
