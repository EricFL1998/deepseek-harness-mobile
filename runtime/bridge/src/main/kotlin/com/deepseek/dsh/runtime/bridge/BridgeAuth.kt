package com.deepseek.dsh.runtime.bridge

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * 设备桥的本地鉴权。
 *
 * 桥监听在 127.0.0.1，但 Android 上 loopback 不是「只有本应用能连」：同机任意
 * 应用都可以连到 127.0.0.1:<随机端口>，随机端口只是降低命中率，不能当作鉴权。
 *
 * Host 侧（proot 内的 bash / fs-search 补丁）从 guest 的
 * [TOKEN_PATH_IN_GUEST] 读取 token，并在每个请求里携带；App 每次启动 Host 时
 * 轮换 token。该文件位于应用私有目录，其他 Android 应用读不到，因此拿不到
 * token 就无法调用桥。
 */
object BridgeAuth {

    /** 桥 token 在 guest 内的固定路径，对应 rootfs 的 `/root/.dsh/.bridge_token`。 */
    const val TOKEN_PATH_IN_GUEST: String = "/root/.dsh/.bridge_token"

    /** token 文件在 [java.io.File] 视角下的名字。 */
    const val TOKEN_FILE_NAME: String = ".bridge_token"

    /** JSON-RPC 错误码：请求未通过本地鉴权。 */
    const val UNAUTHORIZED_CODE: Int = -32001

    /** 一次 token 的随机字节数（256 位）。 */
    private const val TOKEN_BYTES: Int = 32

    /**
     * 生成一枚新的桥 token。
     * @param random 随机源；默认使用 [SecureRandom]。
     * @returns URL-safe、无填充的 256 位 token。
     */
    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * 以常量时间比较预期 token 与请求携带的 token。
     * @param expected 当前进程的 token；null 或空表示未配置，按失败处理。
     * @param presented 请求里的 token；null 或空按失败处理。
     * @returns 两者完全一致时为 true。
     */
    fun verify(expected: String?, presented: String?): Boolean {
        if (expected.isNullOrEmpty() || presented.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            presented.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * 把 token 写到 guest 可读的文件，并收紧为仅属主可读写。
     *
     * 先写同目录临时文件再 rename：Host 可能在写入的同时读取，半截文件会拿到
     * 错误的 token。Unix 上 rename 覆盖是原子的，读到旧 token 的请求会收到
     * 鉴权失败，而不是执行半次操作。
     * @param file 目标文件（rootfs 内的 `root/.dsh/.bridge_token`）。
     * @param token 要写入的 token。
     */
    fun writeTokenFile(file: File, token: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        try {
            temp.writeText(token)
            temp.setReadable(false, false)
            temp.setWritable(false, false)
            temp.setReadable(true, true)
            temp.setWritable(true, true)
            if (!temp.renameTo(file)) {
                file.writeText(token)
            }
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
