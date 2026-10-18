package com.deepseek.dsh.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HostEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        DraftEntity::class,
        ApprovalEntity::class,
        SurfaceCatalogEntity::class,
        AppSettingsEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DshDatabase : RoomDatabase() {
    abstract fun dshDao(): DshDao
}

