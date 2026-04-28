package com.guribbong.phoneappagent.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtEpochMs DESC LIMIT 1")
    fun observeLatest(): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: Long): ChatSessionEntity?

    @Query(
        """
        SELECT * FROM chat_sessions
        WHERE status IN ('preparing', 'planning', 'executing', 'recovering', 'paused', 'confirm required')
        ORDER BY updatedAtEpochMs DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestRecoverableSession(): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(actionLog: AgentActionLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppMemory(memory: AgentAppMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkillMemory(memory: AgentSkillMemoryEntity): Long

    @Query(
        """
        UPDATE chat_sessions
        SET status = :status,
            appName = :appName,
            plannerInputSummary = :plannerInputSummary,
            validatedPlanJson = :validatedPlanJson,
            currentStepIndex = :currentStepIndex,
            lastObservedPackage = :lastObservedPackage,
            lastSelectorFailureReason = :lastSelectorFailureReason,
            confirmGateState = :confirmGateState,
            failureReason = :failureReason,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :sessionId
        """,
    )
    suspend fun updateSession(
        sessionId: Long,
        status: String,
        appName: String,
        plannerInputSummary: String,
        validatedPlanJson: String,
        currentStepIndex: Int,
        lastObservedPackage: String?,
        lastSelectorFailureReason: String?,
        confirmGateState: String,
        failureReason: String?,
        updatedAtEpochMs: Long,
    )

    @Query("SELECT * FROM agent_action_logs WHERE sessionId = :sessionId ORDER BY stepIndex ASC, id ASC")
    suspend fun getActionLogs(sessionId: Long): List<AgentActionLogEntity>

    @Query(
        """
        SELECT * FROM agent_app_memories
        WHERE packageName IN (:packageNames)
        ORDER BY createdAtEpochMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentAppMemories(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentAppMemoryEntity>

    @Query(
        """
        SELECT * FROM agent_skill_memories
        WHERE packageName IN (:packageNames)
        ORDER BY createdAtEpochMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentSkillMemories(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentSkillMemoryEntity>

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun count(): Int
}
