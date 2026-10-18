package com.deepseek.dsh.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DshDao {

    @Query("SELECT * FROM hosts ORDER BY sortOrder ASC")
    fun getAllHosts(): Flow<List<HostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: HostEntity)

    @Query("SELECT * FROM sessions WHERE hostId = :hostId ORDER BY pinned DESC, updatedAt DESC")
    fun getSessionsForHost(hostId: String): Flow<List<SessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE hostId = :hostId AND sessionId = :sessionId")
    suspend fun deleteSession(hostId: String, sessionId: String)

    @Query("UPDATE sessions SET model = :model WHERE hostId = :hostId AND sessionId = :sessionId")
    suspend fun updateSessionModel(hostId: String, sessionId: String, model: String)

    @Query("UPDATE sessions SET permissionPreset = :preset WHERE hostId = :hostId AND sessionId = :sessionId")
    suspend fun updateSessionPermission(hostId: String, sessionId: String, preset: String)

    @Query("SELECT * FROM app_settings WHERE id = 'default'")
    fun getAppSettings(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(settings: AppSettingsEntity)

    @Query("SELECT * FROM messages WHERE hostId = :hostId AND sessionId = :sessionId ORDER BY seq ASC, createdAt ASC")
    fun getMessages(hostId: String, sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("SELECT * FROM surface_catalog WHERE clientId = :clientId")
    fun getSurfaceCatalog(clientId: String): Flow<List<SurfaceCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurfaces(surfaces: List<SurfaceCatalogEntity>)
}
