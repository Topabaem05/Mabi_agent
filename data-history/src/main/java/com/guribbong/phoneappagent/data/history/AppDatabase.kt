package com.guribbong.phoneappagent.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatSessionEntity::class,
        AgentActionLogEntity::class,
        AgentAppMemoryEntity::class,
        AgentSkillMemoryEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
}
