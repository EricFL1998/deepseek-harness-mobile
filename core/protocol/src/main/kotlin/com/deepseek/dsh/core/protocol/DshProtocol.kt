package com.deepseek.dsh.core.protocol

import com.deepseek.dsh.core.model.AccountBalanceInfo
import com.deepseek.dsh.core.model.AccountProfileInfo
import com.deepseek.dsh.core.model.AccountViewInfo
import com.deepseek.dsh.core.model.ApprovalItemModel
import com.deepseek.dsh.core.model.AutomationTaskItem
import com.deepseek.dsh.core.model.BuiltInPluginInfo
import com.deepseek.dsh.core.model.ChatMessage
import com.deepseek.dsh.core.model.ComposerStats
import com.deepseek.dsh.core.model.ModelCatalogInfo
import com.deepseek.dsh.core.model.PluginBundleItem
import com.deepseek.dsh.core.model.PluginConfigItem
import com.deepseek.dsh.core.model.PluginInventorySnapshotInfo
import com.deepseek.dsh.core.model.PluginManagerSnapshot
import com.deepseek.dsh.core.model.SessionInfo
import com.deepseek.dsh.core.model.TeamTaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
sealed interface RemoteResult<out T> {
    @Serializable
    data class Success<T>(val data: T) : RemoteResult<T>

    @Serializable
    data class Failure(val code: String, val message: String) : RemoteResult<Nothing>
}

@Serializable
sealed interface RemoteEvent {
    @Serializable
    data class Ready(val hostId: String, val generation: Long) : RemoteEvent

    @Serializable
    data class MessageDelta(val sessionId: String, val delta: String) : RemoteEvent

    @Serializable
    data class MessageDone(val sessionId: String, val message: ChatMessage) : RemoteEvent

    @Serializable
    data class SessionUpdated(val session: SessionInfo) : RemoteEvent
    data class SessionRunning(val sessionId: String, val running: Boolean) : RemoteEvent

    @Serializable
    data class Error(val code: String, val message: String) : RemoteEvent
}

interface DshClientProtocol {
    suspend fun authenticate(baseUrl: String, token: String?): RemoteResult<String>
    suspend fun listSessions(hostId: String): RemoteResult<List<SessionInfo>>
    suspend fun createSession(hostId: String, title: String, workspacePath: String?): RemoteResult<SessionInfo>
    suspend fun deleteSession(hostId: String, sessionId: String): RemoteResult<Boolean>
    suspend fun selectModel(hostId: String, sessionId: String, modelId: String, provider: String? = null): RemoteResult<Boolean>
    suspend fun selectPermissionPreset(hostId: String, sessionId: String, preset: String): RemoteResult<Boolean>
    suspend fun cancelSession(hostId: String, sessionId: String): Boolean

    suspend fun answerUserQuestion(hostId: String, sessionId: String, callId: String, questionId: String, selected: String): Boolean = false
    suspend fun getModelCatalog(hostId: String): RemoteResult<ModelCatalogInfo>
    suspend fun getSessionStats(hostId: String, sessionId: String): RemoteResult<ComposerStats>
    suspend fun getAccountState(hostId: String): RemoteResult<AccountViewInfo>
    suspend fun startAccountSignIn(hostId: String, callbackOrigin: String): RemoteResult<AccountViewInfo>
    suspend fun signOutAccount(hostId: String): RemoteResult<Boolean>
    suspend fun getAccountProfile(hostId: String): RemoteResult<AccountProfileInfo?>
    suspend fun getAccountBalance(hostId: String): RemoteResult<AccountBalanceInfo>
    suspend fun getPluginInventory(hostId: String): RemoteResult<List<BuiltInPluginInfo>>
    suspend fun getPluginInventorySnapshot(hostId: String): RemoteResult<PluginInventorySnapshotInfo>
    suspend fun getPluginManagerSnapshot(hostId: String): RemoteResult<PluginManagerSnapshot>
    suspend fun installPlugin(hostId: String, packageName: String): RemoteResult<Boolean>
    suspend fun uninstallPlugin(hostId: String, packageName: String): RemoteResult<Boolean>
    suspend fun setBundleEnabled(hostId: String, packageName: String, enabled: Boolean): RemoteResult<Boolean>
    suspend fun openSettingsDocument(hostId: String): RemoteResult<Boolean>
    suspend fun getRemoteSettings(hostId: String): RemoteResult<Map<String, String>>
    suspend fun updateRemoteSettings(hostId: String, ns: String, patchJson: String): RemoteResult<Boolean>
    suspend fun getAutomationTasks(hostId: String): RemoteResult<List<AutomationTaskItem>>
    suspend fun getTeamTasks(hostId: String): RemoteResult<List<TeamTaskItem>>
    suspend fun getPendingApprovals(hostId: String): RemoteResult<List<ApprovalItemModel>>
    suspend fun respondApproval(hostId: String, approvalId: String, approved: Boolean): RemoteResult<Boolean>
    suspend fun sendMessage(hostId: String, sessionId: String, text: String): RemoteResult<ChatMessage>
    fun subscribeEvents(hostId: String): Flow<RemoteEvent>
}




