package com.guribbong.phoneappagent.data.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val appName: String,
    val status: String,
    val updatedAtEpochMs: Long,
    val userGoal: String,
    val plannerInputSummary: String,
    val validatedPlanJson: String,
    val currentStepIndex: Int,
    val lastObservedPackage: String?,
    val lastSelectorFailureReason: String?,
    val confirmGateState: String,
    val failureReason: String?,
)

@Entity(
    tableName = "agent_action_logs",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"])],
)
data class AgentActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val stepIndex: Int,
    val actionType: String,
    val selectorSummary: String?,
    val resultStatus: String,
    val detail: String,
    val observedPackage: String?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "agent_app_memories",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["packageName", "createdAtEpochMs"]),
    ],
)
data class AgentAppMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val goal: String,
    val outcome: String,
    val note: String,
    val source: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "agent_skill_memories",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["packageName", "createdAtEpochMs"]),
    ],
)
data class AgentSkillMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val goal: String,
    val outcome: String,
    val note: String,
    val source: String,
    val createdAtEpochMs: Long,
)

data class ChatSessionSummary(
    val id: Long,
    val title: String,
    val appName: String,
    val status: String,
    val updatedLabel: String,
)

data class AgentActionLog(
    val stepIndex: Int,
    val actionType: String,
    val selectorSummary: String?,
    val resultStatus: String,
    val detail: String,
    val observedPackage: String?,
)

data class ChatTranscript(
    val sessionId: Long,
    val title: String,
    val appName: String,
    val status: String,
    val updatedLabel: String,
    val messages: List<ChatTranscriptMessage>,
)

data class ChatTranscriptMessage(
    val role: ChatTranscriptRole,
    val title: String,
    val body: String,
    val meta: String? = null,
)

enum class ChatTranscriptRole {
    USER,
    AGENT,
    THOUGHT,
    STATUS,
}

data class AgentSessionState(
    val sessionId: Long,
    val status: String,
    val currentStepIndex: Int,
    val lastObservedPackage: String?,
    val failureReason: String?,
    val requiresUserAction: Boolean,
    val confirmGateState: String,
    val planJson: String,
    val goal: String,
)

data class AgentAppMemory(
    val packageName: String,
    val appName: String,
    val outcome: String,
    val note: String,
)

data class AgentSkillMemory(
    val packageName: String,
    val appName: String,
    val outcome: String,
    val note: String,
)
