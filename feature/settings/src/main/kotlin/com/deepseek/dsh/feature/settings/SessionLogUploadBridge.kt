package com.deepseek.dsh.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.deepseek.dsh.core.data.DshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Session log 上传开关的桥（官方 `ui-settings-session-log` 的 General 设置项）。
 *
 * Host 侧命名空间 `session-log-deepseek`，字段 `enabled`。显示状态来自
 * `settings/describe`，写入走 `settings/mutate`（带 revision 的 CAS）。宿主只在
 * 会话 effect 里调用一次 [wire]，设置页直接读写这里的状态。
 */
object SessionLogUploadBridge {
    /** null 表示还没读到（不渲染开关，避免闪一下默认值）。 */
    var enabled by mutableStateOf<Boolean?>(null)
        private set

    private var writer: ((Boolean) -> Unit)? = null
    private var reader: (() -> Unit)? = null

    /** 宿主注入读取与写入能力。 */
    fun wire(repository: DshRepository, hostId: String, scope: CoroutineScope) {
        reader = {
            scope.launch {
                enabled = runCatching { repository.fetchSessionLogUploadEnabled(hostId) }.getOrNull()
            }
        }
        writer = { value ->
            scope.launch {
                runCatching { repository.setSessionLogUploadEnabled(hostId, value) }
                enabled = runCatching { repository.fetchSessionLogUploadEnabled(hostId) }.getOrNull()
            }
        }
        reader?.invoke()
    }

    /** 设置页首次出现时补一次读取。 */
    fun refresh() {
        reader?.invoke()
    }

    /** 写入开关（写入后回读 Host 的真实值）。 */
    fun set(value: Boolean) {
        writer?.invoke(value)
    }
}
