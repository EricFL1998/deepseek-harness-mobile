package com.deepseek.dsh.core.data

import com.deepseek.dsh.core.model.AppSettings
import com.deepseek.dsh.core.model.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class DefaultSettingsRepository(
    private val dao: DshDao,
    private val scope: CoroutineScope
) : SettingsRepository {

    val settingsFlow: StateFlow<AppSettings> = dao.getAppSettings()
        .map { entity ->
            if (entity != null) {
                AppSettings(
                    defaultModel = entity.defaultModel,
                    defaultReasoningLevel = entity.defaultReasoningLevel,
                    idleStopMinutes = entity.idleStopMinutes,
                    keepAliveOnTask = entity.keepAliveOnTask,
                    autoBootRecovery = entity.autoBootRecovery,
                    highRiskConfirm = entity.highRiskConfirm,
                    showCodingView = entity.showCodingView,
                    transcriptView = entity.transcriptView,
                    enableSoundNotice = entity.enableSoundNotice,
                    notificationsEnabled = entity.notificationsEnabled,
                    notifyOnTurnComplete = entity.notifyOnTurnComplete,
                    notifyOnError = entity.notifyOnError,
                    remoteHostUrl = entity.remoteHostUrl,
                    remoteHostToken = entity.remoteHostToken,
                    workspacePath = entity.workspacePath
                )
            } else {
                AppSettings()
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppSettings()
        )

    override suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        settingsFlow.value
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
        val updated = transform(settingsFlow.value)
        dao.saveAppSettings(
            AppSettingsEntity(
                id = "default",
                defaultModel = updated.defaultModel,
                defaultReasoningLevel = updated.defaultReasoningLevel,
                idleStopMinutes = updated.idleStopMinutes,
                keepAliveOnTask = updated.keepAliveOnTask,
                autoBootRecovery = updated.autoBootRecovery,
                highRiskConfirm = updated.highRiskConfirm,
                showCodingView = updated.showCodingView,
                transcriptView = updated.transcriptView,
                enableSoundNotice = updated.enableSoundNotice,
                notificationsEnabled = updated.notificationsEnabled,
                notifyOnTurnComplete = updated.notifyOnTurnComplete,
                notifyOnError = updated.notifyOnError,
                remoteHostUrl = updated.remoteHostUrl,
                remoteHostToken = updated.remoteHostToken,
                workspacePath = updated.workspacePath
            )
        )
    }
}

