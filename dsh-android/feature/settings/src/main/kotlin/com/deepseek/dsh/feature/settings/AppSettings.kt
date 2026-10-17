package com.deepseek.dsh.feature.settings

import kotlinx.serialization.Serializable

/**
 * v4.0 设计文档 §13.2:
 * 手机端用户偏好与设置数据模型
 */
@Serializable
data class AppSettings(
    val defaultModel: String = "DeepSeek-V3",
    val defaultReasoningLevel: String = "FAST",
    val idleStopMinutes: Int = 5,           // 空闲 5 分钟后停止容器 (0 = 常驻)
    val keepAliveOnTask: Boolean = true,    // 后台有任务时保持运行
    val autoBootRecovery: Boolean = false,  // 开机恢复
    val highRiskConfirm: Boolean = true,    // 高危操作强确认
    val enableSoundNotice: Boolean = true,  // 任务完成语音/提示音
    val workspacePath: String = "/storage/emulated/0/Documents/DSH-Workspace"
)

interface SettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}
