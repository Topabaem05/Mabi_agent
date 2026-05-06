package com.guribbong.phoneappagent.data.history

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface SessionRepository {
    fun observeSessions(): Flow<List<ChatSessionSummary>>
    fun observeLatestAgentState(): Flow<AgentSessionState?>
    suspend fun transcriptForSession(sessionId: Long): ChatTranscript?

    suspend fun createSession(
        title: String,
        appName: String,
        status: String,
        userGoal: String,
        plannerInputSummary: String = "",
        validatedPlanJson: String = "",
        currentStepIndex: Int = -1,
        lastObservedPackage: String? = null,
        lastSelectorFailureReason: String? = null,
        confirmGateState: String = "not_required",
        failureReason: String? = null,
    ): Long

    suspend fun updateSessionStatus(
        sessionId: Long,
        status: String,
        appName: String? = null,
        plannerInputSummary: String? = null,
        validatedPlanJson: String? = null,
        currentStepIndex: Int? = null,
        lastObservedPackage: String? = null,
        lastSelectorFailureReason: String? = null,
        confirmGateState: String? = null,
        failureReason: String? = null,
        replaceFailureReason: Boolean = false,
    )

    suspend fun appendActionLog(
        sessionId: Long,
        stepIndex: Int,
        actionType: String,
        selectorSummary: String?,
        resultStatus: String,
        detail: String,
        observedPackage: String?,
    )

    suspend fun appMemoriesForPackages(
        packageNames: List<String>,
        limit: Int = 8,
    ): List<AgentAppMemory>

    suspend fun rememberAppOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String = "agent_session",
    )

    suspend fun skillMemoriesForPackages(
        packageNames: List<String>,
        limit: Int = 8,
    ): List<AgentSkillMemory>

    suspend fun rememberSkillOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String = "agent_skill_learning",
    )

    suspend fun latestRecoverableSession(): RecoverableSession?
    suspend fun ensureSeedSession()
}

data class RecoverableSession(
    val sessionId: Long,
    val goal: String,
    val status: String,
    val planJson: String,
    val currentStepIndex: Int,
    val confirmGateState: String,
)

private class DefaultSessionRepository(
    private val dao: ChatSessionDao,
) : SessionRepository {
    private val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun observeSessions(): Flow<List<ChatSessionSummary>> =
        dao.observeAll().map { items ->
            items.map { item ->
                ChatSessionSummary(
                    id = item.id,
                    title = item.title,
                    appName = item.appName,
                    status = item.status,
                    updatedLabel = formatter.format(Date(item.updatedAtEpochMs)),
                )
            }
        }

    override fun observeLatestAgentState(): Flow<AgentSessionState?> =
        dao.observeLatest().map { entity ->
            entity?.toAgentSessionState()
        }

    override suspend fun transcriptForSession(sessionId: Long): ChatTranscript? {
        val session = dao.getById(sessionId) ?: return null
        val actionLogs = dao.getActionLogs(sessionId)
        return session.toChatTranscript(actionLogs)
    }

    override suspend fun createSession(
        title: String,
        appName: String,
        status: String,
        userGoal: String,
        plannerInputSummary: String,
        validatedPlanJson: String,
        currentStepIndex: Int,
        lastObservedPackage: String?,
        lastSelectorFailureReason: String?,
        confirmGateState: String,
        failureReason: String?,
    ): Long =
        dao.insert(
            ChatSessionEntity(
                title = title,
                appName = appName,
                status = status,
                updatedAtEpochMs = System.currentTimeMillis(),
                userGoal = userGoal,
                plannerInputSummary = plannerInputSummary,
                validatedPlanJson = validatedPlanJson,
                currentStepIndex = currentStepIndex,
                lastObservedPackage = lastObservedPackage,
                lastSelectorFailureReason = lastSelectorFailureReason,
                confirmGateState = confirmGateState,
                failureReason = failureReason,
            ),
        )

    override suspend fun updateSessionStatus(
        sessionId: Long,
        status: String,
        appName: String?,
        plannerInputSummary: String?,
        validatedPlanJson: String?,
        currentStepIndex: Int?,
        lastObservedPackage: String?,
        lastSelectorFailureReason: String?,
        confirmGateState: String?,
        failureReason: String?,
        replaceFailureReason: Boolean,
    ) {
        val current = dao.getById(sessionId) ?: return
        dao.updateSession(
            sessionId = sessionId,
            status = status,
            appName = appName ?: current.appName,
            plannerInputSummary = plannerInputSummary ?: current.plannerInputSummary,
            validatedPlanJson = validatedPlanJson ?: current.validatedPlanJson,
            currentStepIndex = currentStepIndex ?: current.currentStepIndex,
            lastObservedPackage = lastObservedPackage ?: current.lastObservedPackage,
            lastSelectorFailureReason = lastSelectorFailureReason ?: current.lastSelectorFailureReason,
            confirmGateState = confirmGateState ?: current.confirmGateState,
            failureReason = if (replaceFailureReason) failureReason else failureReason ?: current.failureReason,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    override suspend fun appendActionLog(
        sessionId: Long,
        stepIndex: Int,
        actionType: String,
        selectorSummary: String?,
        resultStatus: String,
        detail: String,
        observedPackage: String?,
    ) {
        dao.insertActionLog(
            AgentActionLogEntity(
                sessionId = sessionId,
                stepIndex = stepIndex,
                actionType = actionType,
                selectorSummary = selectorSummary,
                resultStatus = resultStatus,
                detail = detail,
                observedPackage = observedPackage,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun appMemoriesForPackages(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentAppMemory> {
        val distinctPackages = packageNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (distinctPackages.isEmpty()) return emptyList()
        return dao.getRecentAppMemories(distinctPackages, limit.coerceIn(1, 16)).map { entity ->
            AgentAppMemory(
                packageName = entity.packageName,
                appName = entity.appName,
                outcome = entity.outcome,
                note = entity.note,
            )
        }
    }

    override suspend fun rememberAppOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String,
    ) {
        val normalizedPackage = packageName.trim()
        val normalizedNote = note.trim()
        if (normalizedPackage.isEmpty() || normalizedNote.isEmpty()) return
        dao.insertAppMemory(
            AgentAppMemoryEntity(
                packageName = normalizedPackage.take(128),
                appName = appName.trim().ifBlank { normalizedPackage }.take(80),
                goal = goal.trim().take(180),
                outcome = outcome.trim().take(32),
                note = normalizedNote.take(360),
                source = source.trim().ifBlank { "agent_session" }.take(80),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun skillMemoriesForPackages(
        packageNames: List<String>,
        limit: Int,
    ): List<AgentSkillMemory> {
        val distinctPackages = packageNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (distinctPackages.isEmpty()) return emptyList()
        return dao.getRecentSkillMemories(distinctPackages, limit.coerceIn(1, 16)).map { entity ->
            AgentSkillMemory(
                packageName = entity.packageName,
                appName = entity.appName,
                outcome = entity.outcome,
                note = entity.note,
            )
        }
    }

    override suspend fun rememberSkillOutcome(
        packageName: String,
        appName: String,
        goal: String,
        outcome: String,
        note: String,
        source: String,
    ) {
        val normalizedPackage = packageName.trim()
        val normalizedNote = note.trim()
        if (normalizedPackage.isEmpty() || normalizedNote.isEmpty()) return
        dao.insertSkillMemory(
            AgentSkillMemoryEntity(
                packageName = normalizedPackage.take(128),
                appName = appName.trim().ifBlank { normalizedPackage }.take(80),
                goal = goal.trim().take(180),
                outcome = outcome.trim().take(32),
                note = normalizedNote.take(500),
                source = source.trim().ifBlank { "agent_skill_learning" }.take(80),
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun latestRecoverableSession(): RecoverableSession? =
        dao.getLatestRecoverableSession()?.let { entity ->
            RecoverableSession(
                sessionId = entity.id,
                goal = entity.userGoal,
                status = entity.status,
                planJson = entity.validatedPlanJson,
                currentStepIndex = entity.currentStepIndex,
                confirmGateState = entity.confirmGateState,
            )
        }

    override suspend fun ensureSeedSession() {
        if (dao.count() > 0) return
        createSession(
            title = "Scaffold milestone: Compose shell + drawer",
            appName = "phone_app_agent",
            status = "qa pending",
            userGoal = "Scaffold milestone: Compose shell + drawer",
            plannerInputSummary = "seed session",
        )
    }

    private fun ChatSessionEntity.toAgentSessionState(): AgentSessionState =
        AgentSessionState(
            sessionId = id,
            status = status,
            currentStepIndex = currentStepIndex,
            lastObservedPackage = lastObservedPackage,
            failureReason = failureReason,
            requiresUserAction = confirmGateState == "awaiting_confirmation",
            confirmGateState = confirmGateState,
            planJson = validatedPlanJson,
            goal = userGoal,
        )

    private fun ChatSessionEntity.toChatTranscript(
        actionLogs: List<AgentActionLogEntity>,
    ): ChatTranscript {
        val messages = buildList {
            add(
                ChatTranscriptMessage(
                    role = ChatTranscriptRole.USER,
                    title = "User",
                    body = userGoal.ifBlank { title },
                    meta = updatedLabel(),
                ),
            )
            plannerInputSummary
                .takeIf { it.isNotBlank() && it != "seed session" }
                ?.let { summary ->
                    add(
                        ChatTranscriptMessage(
                            role = ChatTranscriptRole.AGENT,
                            title = "Planner context",
                            body = summary.compactForTranscript(360),
                            meta = appName,
                        ),
                    )
                }
            if (validatedPlanJson.isNotBlank()) {
                add(
                    ChatTranscriptMessage(
                        role = ChatTranscriptRole.AGENT,
                        title = "Plan",
                        body = validatedPlanJson.compactPlanForTranscript(),
                        meta = "step ${currentStepIndex.coerceAtLeast(0)}",
                    ),
                )
            }
            actionLogs.forEach { log ->
                add(log.toTranscriptMessage())
            }
            if (confirmGateState != "not_required") {
                add(
                    ChatTranscriptMessage(
                        role = ChatTranscriptRole.STATUS,
                        title = "Confirmation",
                        body = when (confirmGateState) {
                            "awaiting_confirmation" -> "Waiting for the user to proceed or stop."
                            "confirmed" -> "The user confirmed continuation."
                            else -> confirmGateState.replace('_', ' ')
                        },
                        meta = status,
                    ),
                )
            }
            failureReason?.takeIf { it.isNotBlank() }?.let { reason ->
                add(
                    ChatTranscriptMessage(
                        role = ChatTranscriptRole.STATUS,
                        title = "Result",
                        body = reason.compactForTranscript(420),
                        meta = status,
                    ),
                )
            } ?: add(
                ChatTranscriptMessage(
                    role = ChatTranscriptRole.STATUS,
                    title = "Session",
                    body = status.replaceFirstChar(Char::titlecase),
                    meta = updatedLabel(),
                ),
            )
        }
        return ChatTranscript(
            sessionId = id,
            title = title,
            appName = appName,
            status = status,
            updatedLabel = updatedLabel(),
            messages = messages,
        )
    }

    private fun AgentActionLogEntity.toTranscriptMessage(): ChatTranscriptMessage {
        val actionLabel = actionType.replace('_', ' ').replaceFirstChar(Char::titlecase)
        val target = selectorSummary?.takeIf { it.isNotBlank() }?.compactForTranscript(120)
        val packageMeta = observedPackage?.takeIf { it.isNotBlank() }
        val body = buildString {
            append(detail.ifBlank { resultStatus }.compactForTranscript(420))
            if (!target.isNullOrBlank()) {
                append("\nTarget: ")
                append(target)
            }
        }
        return ChatTranscriptMessage(
            role = if (resultStatus.equals("success", ignoreCase = true)) {
                ChatTranscriptRole.THOUGHT
            } else {
                ChatTranscriptRole.STATUS
            },
            title = "Step ${stepIndex + 1} · $actionLabel",
            body = body,
            meta = listOf(resultStatus, packageMeta)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { null },
        )
    }

    private fun ChatSessionEntity.updatedLabel(): String =
        formatter.format(Date(updatedAtEpochMs))

    private fun String.compactPlanForTranscript(): String {
        val normalized = compactForTranscript(520)
        return if (normalized.startsWith("{") || normalized.startsWith("[")) {
            "Validated deterministic action plan is stored for this session.\n$normalized"
        } else {
            normalized
        }
    }

    private fun String.compactForTranscript(limit: Int): String {
        val normalized = trim()
            .replace(Regex("\\s+"), " ")
        if (normalized.length <= limit) return normalized
        return normalized.take((limit - 1).coerceAtLeast(0)).trimEnd() + "…"
    }
}

private fun provideDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "phone_app_agent.db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

val dataHistoryModule = module {
    single<AppDatabase> { provideDatabase(androidContext()) }
    single<ChatSessionDao> { get<AppDatabase>().chatSessionDao() }
    single<SessionRepository> { DefaultSessionRepository(get()) }
}
