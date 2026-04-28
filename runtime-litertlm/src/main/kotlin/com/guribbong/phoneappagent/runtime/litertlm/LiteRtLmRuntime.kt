package com.guribbong.phoneappagent.runtime.litertlm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.GlobalActionType
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.ScrollDirection
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import com.guribbong.phoneappagent.core.dsl.historyKey
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.policy.RiskLevel
import com.guribbong.phoneappagent.core.runner.ContextProfile
import com.guribbong.phoneappagent.core.runner.DeviceCapabilityProfile
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.core.runner.LocalAgentRuntime
import com.guribbong.phoneappagent.core.runner.PlanDraft
import com.guribbong.phoneappagent.core.runner.PlanningMode
import com.guribbong.phoneappagent.core.runner.PlannerInput
import com.guribbong.phoneappagent.core.runner.RuntimePhase
import com.guribbong.phoneappagent.core.runner.RuntimePreparation
import com.guribbong.phoneappagent.core.runner.RuntimeState
import com.guribbong.phoneappagent.core.runner.WarmUpReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

private const val TAG = "LiteRtLmRuntime"
private const val BYTES_PER_MB = 1024L * 1024L
private const val MIN_STORAGE_MB = 10_240L
private const val HEAVY_STORAGE_MB = 20_480L
private const val UNSUPPORTED_RAM_MB = 4_096L
private const val REDUCED_RAM_MB = 10_240L
private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
private const val DOWNLOAD_READ_TIMEOUT_MS = 120_000
private const val CHROME_PACKAGE = "com.android.chrome"
private const val CHROME_URL_BAR_RESOURCE_ID = "com.android.chrome:id/url_bar"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val SETTINGS_INTELLIGENCE_PACKAGE = "com.android.settings.intelligence"
private const val SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC = "설정 검색"
private const val SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID = "com.android.settings.intelligence:id/search_src_text"
private const val SAMSUNG_BLUETOOTH_RESULT_LABEL = "블루투스"
private const val SAMSUNG_CONTACTS_PACKAGE = "com.samsung.android.app.contacts"
private const val SAMSUNG_CONTACTS_SEARCH_BUTTON_ID = "com.samsung.android.app.contacts:id/menu_search"
private const val SAMSUNG_CONTACTS_SEARCH_TEXT_ID = "com.samsung.android.app.contacts:id/search_src_text"
private const val SAMSUNG_CLOCK_PACKAGE = "com.sec.android.app.clockpackage"
private const val SAMSUNG_CLOCK_ALARM_TAB_LABEL = "알람"
private const val SAMSUNG_CLOCK_TAB_TITLE_ID = "com.sec.android.app.clockpackage:id/title"
private const val SAMSUNG_CLOCK_ALARM_LAYOUT_ID = "com.sec.android.app.clockpackage:id/alarm_main_layout"
private const val SAMSUNG_MESSAGES_PACKAGE = "com.samsung.android.messaging"
private const val SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID = "com.samsung.android.messaging:id/fab"
private const val SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID = "com.samsung.android.messaging:id/chat_fab"
private const val SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID = "com.samsung.android.messaging:id/search_src_text"
private const val SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID = "com.samsung.android.messaging:id/chat_with_button"
private const val SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID = "com.samsung.android.messaging:id/message_edit_text"
private const val SAMSUNG_MESSAGES_SEND_BUTTON_ID = "com.samsung.android.messaging:id/send_button"
private const val MAX_DOWNLOAD_ATTEMPTS = 8
private const val RETRY_DELAY_BASE_MS = 3_000L
private const val FULL_CONTEXT_TOKENS = 1_024
private const val REDUCED_CONTEXT_TOKENS = 1_024
private const val AGENT_APP_PACKAGE = "com.guribbong.phoneappagent"
private const val MAX_PLANNER_PROMPT_CHARS = 2_200
private const val MAX_REDUCED_PLANNER_PROMPT_CHARS = 420
private const val DEFAULT_MODEL_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
private const val DEFAULT_MODEL_FILE = "gemma-4-E2B-it.litertlm"

data class LiteRtLmRuntimeConfig(
    val modelName: String = "Gemma 4 E2B",
    val modelUrl: String = DEFAULT_MODEL_URL,
    val modelFileName: String = DEFAULT_MODEL_FILE,
)

class LiteRtLmLocalAgentRuntime(
    context: Context,
    private val policyGate: PolicyGate,
    private val config: LiteRtLmRuntimeConfig = LiteRtLmRuntimeConfig(),
) : LocalAgentRuntime {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RuntimeState())
    private var engine: Engine? = null

    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override suspend fun probeDeviceCapability(): DeviceCapabilityProfile =
        withContext(Dispatchers.Default) {
            val activityManager = appContext.getSystemService<ActivityManager>()
            val memoryInfo = ActivityManager.MemoryInfo().also { info ->
                activityManager?.getMemoryInfo(info)
            }
            val totalMemoryMb = memoryInfo.totalMem / BYTES_PER_MB
            val statFs = StatFs(appContext.filesDir.absolutePath)
            val availableStorageMb = statFs.availableBytes / BYTES_PER_MB
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val recommendedThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val contextProfile =
                when {
                    totalMemoryMb < REDUCED_RAM_MB || availableStorageMb < HEAVY_STORAGE_MB -> ContextProfile.REDUCED
                    else -> ContextProfile.FULL
                }

            when {
                totalMemoryMb < UNSUPPORTED_RAM_MB -> DeviceCapabilityProfile(
                    supported = false,
                    reason = "Device RAM is below 4 GB.",
                    primaryAbi = abi,
                    totalMemoryMb = totalMemoryMb,
                    availableStorageMb = availableStorageMb,
                    recommendedThreads = recommendedThreads,
                    contextProfile = contextProfile,
                    backendLabel = "Unsupported",
                )

                availableStorageMb < MIN_STORAGE_MB -> DeviceCapabilityProfile(
                    supported = false,
                    reason = "Free storage is below 10 GB.",
                    primaryAbi = abi,
                    totalMemoryMb = totalMemoryMb,
                    availableStorageMb = availableStorageMb,
                    recommendedThreads = recommendedThreads,
                    contextProfile = contextProfile,
                    backendLabel = "Unsupported",
                )

                else -> DeviceCapabilityProfile(
                    supported = true,
                    reason = if (contextProfile == ContextProfile.REDUCED) {
                        "Reduced-context profile enabled."
                    } else {
                        "Full profile available."
                    },
                    primaryAbi = abi,
                    totalMemoryMb = totalMemoryMb,
                    availableStorageMb = availableStorageMb,
                    recommendedThreads = recommendedThreads,
                    contextProfile = contextProfile,
                    backendLabel = "LiteRT-LM CPU",
                )
            }
        }

    override suspend fun prepare(profile: DeviceCapabilityProfile): RuntimePreparation =
        mutex.withLock {
            if (!profile.supported) {
                val result = RuntimePreparation(
                    ready = false,
                    detail = profile.reason,
                    profile = profile,
                )
                _state.value = RuntimeState(
                    phase = RuntimePhase.UNSUPPORTED,
                    detail = profile.reason,
                    profile = profile,
                )
                return result
            }

            _state.value = RuntimeState(
                phase = RuntimePhase.DOWNLOADING_MODEL,
                detail = "Downloading ${config.modelName}",
                profile = profile,
            )
            val modelFile = ensureModelFile()

            _state.value = RuntimeState(
                phase = RuntimePhase.PREPARING,
                detail = "Initializing LiteRT-LM engine",
                modelPath = modelFile.absolutePath,
                profile = profile,
            )

            engine?.close()
            val backendCandidates = buildList {
                if (profile.contextProfile == ContextProfile.REDUCED) {
                    add(Backend.GPU())
                }
                add(Backend.CPU(profile.recommendedThreads))
                if (profile.contextProfile != ContextProfile.REDUCED) {
                    add(Backend.GPU())
                }
            }
            var preparedEngine: Engine? = null
            var lastEngineError: Throwable? = null
            var selectedBackend: Backend? = null
            for (backend in backendCandidates) {
                val engineConfig = EngineConfig(
                    modelFile.absolutePath,
                    backend,
                    null,
                    null,
                    if (profile.contextProfile == ContextProfile.REDUCED) REDUCED_CONTEXT_TOKENS else FULL_CONTEXT_TOKENS,
                    null,
                    appContext.cacheDir.absolutePath,
                )
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        Engine(engineConfig).also { it.initialize() }
                    }
                }
                if (result.isSuccess) {
                    preparedEngine = result.getOrNull()
                    selectedBackend = backend
                    break
                }
                lastEngineError = result.exceptionOrNull()
                Log.w(TAG, "engine init failed backend=${backend.name}", lastEngineError)
            }
            engine = preparedEngine ?: throw IllegalStateException(
                normalizeRuntimeFailure(lastEngineError?.message)
                    ?: lastEngineError?.message
                    ?: "Engine init failed",
                lastEngineError,
            )

            return RuntimePreparation(
                ready = true,
                detail = "Engine initialized (${selectedBackend?.name ?: "unknown"}).",
                modelPath = modelFile.absolutePath,
                profile = profile,
            ).also { ready ->
                _state.value = RuntimeState(
                    phase = RuntimePhase.READY,
                    detail = ready.detail,
                    modelPath = ready.modelPath,
                    profile = profile,
                )
            }
        }

    @OptIn(ExperimentalApi::class)
    override suspend fun warmUp(): WarmUpReport =
        mutex.withLock {
            val activeEngine = engine ?: return WarmUpReport(
                success = false,
                detail = "Engine is not prepared.",
            )

            return withContext(Dispatchers.IO) {
                activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = com.google.ai.edge.litertlm.Contents.of(
                            "You are a strict JSON mobile planning assistant.",
                        ),
                        samplerConfig = SamplerConfig(topK = 10, topP = 0.9, temperature = 0.1, seed = 7),
                    ),
                ).use { conversation ->
                    try {
                        val startedAt = SystemClock.elapsedRealtimeNanos()
                        val warmUpResponse = conversation.sendMessage("Respond with READY.")
                        val elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000_000.0
                        val responseText = warmUpResponse.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString(separator = "\n") { it.text }
                        val responseValid = responseText.contains("READY", ignoreCase = true)
                        if (elapsedSeconds > 25.0) {
                            _state.value = _state.value.copy(
                                phase = RuntimePhase.UNSUPPORTED,
                                detail = "Warm-up exceeded 25 seconds on this device.",
                            )
                        } else if (!responseValid) {
                            _state.value = _state.value.copy(
                                phase = RuntimePhase.PREPARING,
                                detail = "Warm-up response was invalid.",
                            )
                        } else {
                            _state.value = _state.value.copy(
                                phase = RuntimePhase.READY,
                                detail = "Warm-up complete in %.1fs".format(elapsedSeconds),
                            )
                        }
                        WarmUpReport(
                            success = responseValid && elapsedSeconds <= 25.0,
                            detail = _state.value.detail,
                            initTimeSeconds = elapsedSeconds,
                            timeToFirstTokenSeconds = elapsedSeconds,
                        )
                    } catch (throwable: Throwable) {
                        val detail = normalizeRuntimeFailure(throwable.message) ?: (throwable.message ?: "Warm-up failed.")
                        _state.value = _state.value.copy(
                            phase = RuntimePhase.UNSUPPORTED,
                            detail = detail,
                        )
                        WarmUpReport(
                            success = false,
                            detail = detail,
                        )
                    }
                }
            }
        }

    override suspend fun plan(input: PlannerInput): PlanDraft =
        mutex.withLock {
            val activeEngine = engine ?: error("Engine not prepared")
            val prompt = buildPrompt(input)
            Log.d(
                TAG,
                "planner prompt chars=${prompt.length} profile=${_state.value.profile?.contextProfile ?: ContextProfile.FULL}",
            )
            val responseText = try {
                withContext(Dispatchers.IO) {
                    activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = com.google.ai.edge.litertlm.Contents.of(systemInstruction),
                            samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1, seed = 7),
                        ),
                    ).use { conversation ->
                        conversation.sendMessage(prompt).contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString(separator = "\n") { it.text }
                    }
                }
            } catch (throwable: Throwable) {
                val detail = normalizeRuntimeFailure(throwable.message)
                if (detail != null) {
                    _state.value = _state.value.copy(
                        phase = RuntimePhase.UNSUPPORTED,
                        detail = detail,
                    )
                    throw IllegalStateException(detail, throwable)
                }
                throw throwable
            }
            validatePlan(
                input = input,
                rawOutput = responseText,
            )
        }

    private suspend fun ensureModelFile(): File =
        withContext(Dispatchers.IO) {
            val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
            val finalFile = File(modelDir, config.modelFileName)
            val tempFile = File(modelDir, "${config.modelFileName}.part")
            val checksumFile = File(modelDir, "${config.modelFileName}.sha256")

            val metadata = fetchRemoteMetadata()
            if (finalFile.exists()) {
                val cachedChecksum = checksumFile.takeIf(File::exists)?.readText()?.trim()
                val finalLengthMatches = metadata.contentLength <= 0L || finalFile.length() == metadata.contentLength
                if ((cachedChecksum == metadata.expectedSha256 || metadata.expectedSha256 == null) && finalLengthMatches) {
                    return@withContext finalFile
                }
                finalFile.delete()
            }

            if (metadata.contentLength > 0L && tempFile.exists() && tempFile.length() >= metadata.contentLength) {
                val tempSha = sha256(tempFile)
                if (metadata.expectedSha256 == null || tempSha.equals(metadata.expectedSha256, ignoreCase = true)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    checksumFile.writeText(tempSha)
                    tempFile.delete()
                    Log.d(TAG, "model promoted from existing partial file=${finalFile.absolutePath} size=${finalFile.length()}")
                    return@withContext finalFile
                }
                tempFile.delete()
            }

            var lastFailure: String? = null
            repeat(MAX_DOWNLOAD_ATTEMPTS) { attemptIndex ->
                val attemptNumber = attemptIndex + 1
                var resumeOffset = tempFile.takeIf(File::exists)?.length() ?: 0L
                if (metadata.contentLength > 0L && resumeOffset > metadata.contentLength) {
                    tempFile.delete()
                    resumeOffset = 0L
                }

                var attemptCompleted = false
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(config.modelUrl).openConnection() as HttpURLConnection
                    if (resumeOffset > 0L) {
                        connection.setRequestProperty("Range", "bytes=$resumeOffset-")
                    }
                    connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                    connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS

                    val responseCode = connection.responseCode
                    val appendToTemp = resumeOffset > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (resumeOffset > 0L && !appendToTemp) {
                        tempFile.delete()
                        lastFailure = "Model resume was rejected by server (HTTP $responseCode)."
                    } else if (resumeOffset == 0L && responseCode !in 200..299) {
                        throw IOException("Model download failed with HTTP $responseCode.")
                    } else {
                        connection.inputStream.use { input ->
                            FileOutputStream(tempFile, appendToTemp).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                            }
                        }
                        attemptCompleted = true
                    }
                } catch (ioException: IOException) {
                    lastFailure = ioException.message ?: ioException::class.java.simpleName
                    Log.w(
                        TAG,
                        "model download interrupted attempt=$attemptNumber resumeOffset=$resumeOffset size=${tempFile.length()} message=$lastFailure",
                        ioException,
                    )
                } finally {
                    connection?.disconnect()
                }

                if (!attemptCompleted) {
                    if (attemptIndex == MAX_DOWNLOAD_ATTEMPTS - 1) {
                        error(lastFailure ?: "Model download interrupted.")
                    }
                    delay(retryDelayMs(attemptIndex))
                    return@repeat
                }

                if (metadata.contentLength > 0L) {
                    val downloadedBytes = tempFile.length()
                    if (downloadedBytes < metadata.contentLength) {
                        lastFailure = "Model download incomplete: $downloadedBytes / ${metadata.contentLength} bytes."
                        Log.w(TAG, "model download incomplete attempt=$attemptNumber bytes=$downloadedBytes expected=${metadata.contentLength}")
                        if (attemptIndex == MAX_DOWNLOAD_ATTEMPTS - 1) {
                            error(lastFailure!!)
                        }
                        delay(retryDelayMs(attemptIndex))
                        return@repeat
                    }
                    if (downloadedBytes > metadata.contentLength) {
                        tempFile.delete()
                        lastFailure = "Model download overflowed expected length."
                        if (attemptIndex == MAX_DOWNLOAD_ATTEMPTS - 1) {
                            error(lastFailure!!)
                        }
                        delay(retryDelayMs(attemptIndex))
                        return@repeat
                    }
                }

                val computedSha = sha256(tempFile)
                if (metadata.expectedSha256 != null && !computedSha.equals(metadata.expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    lastFailure = "Model checksum mismatch."
                    Log.w(TAG, "model checksum mismatch attempt=$attemptNumber; retrying clean download")
                    if (attemptIndex == MAX_DOWNLOAD_ATTEMPTS - 1) {
                        error(lastFailure!!)
                    }
                    delay(retryDelayMs(attemptIndex))
                    return@repeat
                }

                tempFile.copyTo(finalFile, overwrite = true)
                checksumFile.writeText(computedSha)
                tempFile.delete()
                Log.d(TAG, "model prepared file=${finalFile.absolutePath} size=${finalFile.length()}")
                return@withContext finalFile
            }

            error("Model download failed after $MAX_DOWNLOAD_ATTEMPTS attempts.")
        }

    private fun retryDelayMs(attemptIndex: Int): Long =
        RETRY_DELAY_BASE_MS * (attemptIndex + 1).coerceAtMost(5)

    private fun fetchRemoteMetadata(): RemoteModelMetadata {
        val huggingFaceMetadata = fetchHuggingFaceMetadata()
        val connection = URL(config.modelUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return runCatching {
            connection.inputStream.close()
            RemoteModelMetadata(
                contentLength = huggingFaceMetadata?.contentLength
                    ?: connection.getHeaderFieldLong("Content-Length", -1L),
                expectedSha256 = huggingFaceMetadata?.expectedSha256,
            )
        }.getOrElse {
            huggingFaceMetadata ?: RemoteModelMetadata(contentLength = -1L, expectedSha256 = null)
        }.also {
            connection.disconnect()
        }
    }

    private fun fetchHuggingFaceMetadata(): RemoteModelMetadata? {
        val sourceUrl = runCatching { URL(config.modelUrl) }.getOrNull() ?: return null
        if (sourceUrl.host != "huggingface.co") return null

        val segments = sourceUrl.path.trim('/').split('/')
        if (segments.size < 5 || segments[2] != "resolve") return null

        val owner = segments[0]
        val repo = segments[1]
        val revision = segments[3]
        val filePath = segments.drop(4).joinToString("/")
        val encodedRevision = URLEncoder.encode(revision, Charsets.UTF_8.name())
        val apiUrl = URL("https://huggingface.co/api/models/$owner/$repo/tree/$encodedRevision?recursive=1")
        val connection = apiUrl.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val entries = JSONArray(payload)
                var metadata: RemoteModelMetadata? = null
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    if (entry.optString("path") != filePath) continue
                    val lfs = entry.optJSONObject("lfs")
                    metadata = RemoteModelMetadata(
                        contentLength = entry.optLong("size", -1L),
                        expectedSha256 = lfs?.optString("oid")?.ifBlank { null },
                    )
                    break
                }
                metadata
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPrompt(input: PlannerInput): String =
        buildCompactPrompt(
            goal = input.goal,
            planningMode = input.planningMode,
            stepBudget = input.stepBudget,
            priorPlanSummary = input.priorPlanSummary,
            foregroundPackage = input.foregroundPackage,
            lastExternalForegroundPackage = input.lastExternalForegroundPackage,
            serializedNodeTree = input.serializedNodeTree,
            recentActionHistory = input.recentActionHistory,
            appMemory = input.appMemory,
            appSkillGuidance = input.appSkillGuidance,
            riskHints = input.riskHints,
            candidateApps = input.candidateApps,
        )

    private fun buildCompactPrompt(
        goal: String,
        planningMode: PlanningMode,
        stepBudget: Int,
        priorPlanSummary: String?,
        foregroundPackage: String?,
        lastExternalForegroundPackage: String?,
        serializedNodeTree: String,
        recentActionHistory: List<String>,
        appMemory: List<String>,
        appSkillGuidance: List<String>,
        riskHints: List<String>,
        candidateApps: List<com.guribbong.phoneappagent.core.runner.AppCandidate>,
    ): String {
        val reducedContext = _state.value.profile?.contextProfile == ContextProfile.REDUCED
        val compactNodes = compactNodeSummary(serializedNodeTree)
        val compactHistory = recentActionHistory.takeLast(if (reducedContext) 2 else 4).map { it.take(64) }
        val compactMemory = appMemory.take(if (reducedContext) 2 else 5).map { it.take(if (reducedContext) 96 else 180) }
        val compactSkills = appSkillGuidance.take(if (reducedContext) 1 else 3).map { it.take(if (reducedContext) 180 else 500) }
        val compactRiskHints = riskHints.take(if (reducedContext) 2 else 4).map { it.take(64) }
        val compactApps = candidateApps.take(if (reducedContext) 4 else 6)

        val nodeCharBudgets = if (reducedContext) listOf(120, 80, 40, 0) else listOf(1_200, 900, 700, 500, 320)
        val promptLimit = if (reducedContext) MAX_REDUCED_PLANNER_PROMPT_CHARS else MAX_PLANNER_PROMPT_CHARS
        for (budget in nodeCharBudgets) {
            val prompt = JSONObject()
                .put("g", goal.take(if (reducedContext) 48 else 120))
                .put("m", planningMode.name.lowercase())
                .put("b", stepBudget.coerceIn(1, 6))
                .put("s", priorPlanSummary?.take(if (reducedContext) 64 else 180).orEmpty())
                .put("f", foregroundPackage?.take(48).orEmpty())
                .put("l", lastExternalForegroundPackage?.take(48).orEmpty())
                .put("n", if (budget == 0) "" else compactNodes.take(budget))
                .put("h", JSONArray(compactHistory))
                .put("mem", JSONArray(compactMemory))
                .put("skill", JSONArray(compactSkills))
                .put("r", JSONArray(compactRiskHints))
                .put(
                    "a",
                    JSONArray().apply {
                        compactApps.forEach { candidate ->
                            put(
                                JSONObject()
                                    .put("l", candidate.label.take(24))
                                    .put("p", candidate.packageName.take(48)),
                            )
                        }
                    },
                ).toString()
            if (prompt.length <= promptLimit || budget == nodeCharBudgets.last()) {
                return prompt
            }
        }
        error("Planner prompt compaction failed.")
    }

    private fun compactNodeSummary(serializedNodeTree: String): String =
        serializedNodeTree.lineSequence()
            .map(::compactNodeLine)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending(::nodePriority)
            .take(18)
            .joinToString(separator = "\n")

    private fun compactNodeLine(line: String): String =
        line.split(" | ")
            .mapNotNull { segment ->
                val (key, rawValue) = segment.split("=", limit = 2).let { parts ->
                    if (parts.size != 2) return@mapNotNull null
                    parts[0] to parts[1].trim()
                }
                if (rawValue.isBlank()) return@mapNotNull null
                when (key) {
                    "text" -> "t=${rawValue.take(32)}"
                    "desc" -> "d=${rawValue.take(32)}"
                    "id" -> "id=${rawValue.substringAfterLast('/').take(28)}"
                    "class" -> "c=${rawValue.substringAfterLast('.').take(20)}"
                    "package" -> "p=${rawValue.take(48)}"
                    "editable" -> rawValue.takeIf { it == "true" }?.let { "e=true" }
                    "clickable" -> rawValue.takeIf { it == "true" }?.let { "k=true" }
                    else -> null
                }
            }.joinToString(separator = "|")

    private fun nodePriority(line: String): Int =
        buildList {
            if ("t=" in line) add(4)
            if ("d=" in line) add(4)
            if ("id=" in line) add(2)
            if ("e=true" in line) add(2)
            if ("k=true" in line) add(1)
        }.sum()

    private fun normalizeRuntimeFailure(message: String?): String? {
        val raw = message?.trim().orEmpty()
        if (raw.isBlank()) return null
        return when {
            "OpenCL library" in raw ->
                "This device cannot run Gemma 4 E2B on GPU because OpenCL is unavailable."

            "Failed to allocate tensors" in raw ||
                "Failed to invoke the compiled model" in raw ||
                "Failed to create engine" in raw ->
                "This device cannot run Gemma 4 E2B on-device with current LiteRT-LM memory limits."

            "Input token ids are too long" in raw ->
                "Planner prompt exceeded the LiteRT-LM token limit on this device."

            else -> null
        }
    }

    private fun validatePlan(
        input: PlannerInput,
        rawOutput: String,
    ): PlanDraft {
        val jsonText = extractJson(rawOutput)
        val root = JSONObject(jsonText)
        val stepsJson = root.optJSONArray("steps") ?: JSONArray()
        val parsedSteps = buildList {
            for (index in 0 until stepsJson.length()) {
                when (val rawStep = stepsJson.get(index)) {
                    is JSONObject ->
                        add(
                            ExecutionStep(
                                action = parseAction(rawStep),
                                expectedObservation = rawStep.optString(
                                    "expectedObservation",
                                    "Observe the requested UI transition.",
                                ),
                            ),
                        )

                    is String ->
                        add(
                            ExecutionStep(
                                action = parseStringStep(rawStep),
                                expectedObservation = "Observe the requested UI transition.",
                            ),
                        )

                    else -> error("Unsupported step payload at index $index.")
                }
            }
        }
        val steps = normalizeGoalSpecificSteps(input, parsedSteps)
        validateGoalSpecificConstraints(input.goal, input.candidateApps, steps)
        val runtimeRisk = root.optString("riskLevel", RiskLevel.LOW.name)
            .let { value -> runCatching { RiskLevel.valueOf(value.uppercase()) }.getOrDefault(RiskLevel.LOW) }
        val policyDecision = policyGate.evaluate(input.goal, steps.map { it.action }, runtimeRisk)
        val gatedSteps =
            if (policyDecision.needsConfirmation && steps.none { it.action is AgentAction.ConfirmUser }) {
                steps + ExecutionStep(
                    action = AgentAction.ConfirmUser(policyDecision.reason),
                    expectedObservation = "Execution pauses until the user confirms.",
                )
            } else {
                steps
            }
        val finalSteps = adaptStepsToCurrentProgress(input, gatedSteps)
        return PlanDraft(
            summary = root.opt("summary")?.toString()?.takeIf { it.isNotBlank() } ?: "On-device plan ready",
            steps = finalSteps,
            riskLevel = runtimeRisk,
            needsConfirmation = policyDecision.needsConfirmation || root.optBoolean("needsConfirmation", false),
            targetPackageCandidates = root.optJSONArray("targetPackageCandidates")
                ?.let(::jsonArrayToStrings)
                .orEmpty(),
            rawModelOutput = rawOutput,
            rawPlanJson = buildNormalizedPlanJson(
                root = root,
                steps = finalSteps,
                needsConfirmation = policyDecision.needsConfirmation || root.optBoolean("needsConfirmation", false),
            ),
        )
    }

    private fun normalizeGoalSpecificSteps(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val lowerGoal = input.goal.lowercase()
        return when {
            isChromeSearchGoal(lowerGoal) -> normalizeChromeSearchPlan(input.goal, steps)
            isSamsungBluetoothSettingsGoal(lowerGoal, steps) -> normalizeSamsungBluetoothSettingsPlan(steps)
            isSamsungSettingsSearchGoal(lowerGoal, steps) -> normalizeSamsungSettingsSearchPlan(input.goal, steps)
            isSamsungClockAlarmGoal(lowerGoal, steps) -> normalizeSamsungClockAlarmPlan(steps)
            isSamsungContactsSearchGoal(lowerGoal, steps) -> normalizeSamsungContactsSearchPlan(input.goal, steps)
            isSamsungMessagesSendGoal(lowerGoal, steps) -> normalizeSamsungMessagesSendPlan(input.goal, steps)
            else -> steps
        }
    }

    private fun normalizeChromeSearchPlan(
        goal: String,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val query = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()?.text
            ?: extractSearchQuery(goal)
            ?: return steps
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == CHROME_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(CHROME_PACKAGE),
                    expectedObservation = "Chrome launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == CHROME_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(CHROME_PACKAGE),
                    expectedObservation = "Chrome becomes foreground.",
                )
        val urlBarSelector = NodeSelector(resourceId = CHROME_URL_BAR_RESOURCE_ID)
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.WaitForNode(urlBarSelector),
                expectedObservation = "Chrome URL bar is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(urlBarSelector, label = "Focus Chrome URL bar"),
                expectedObservation = "Chrome URL bar is focused.",
            ),
            ExecutionStep(
                action = AgentAction.InputText(
                    selector = urlBarSelector,
                    text = query,
                ),
                expectedObservation = "The search query appears in the URL bar.",
            ),
            ExecutionStep(
                action = AgentAction.SubmitInput(urlBarSelector),
                expectedObservation = "Chrome submits the search query.",
            ),
            ExecutionStep(
                action = AgentAction.Stop,
                expectedObservation = "Chrome search has been submitted.",
            ),
        )
    }

    private fun adaptStepsToCurrentProgress(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        if (steps.isEmpty()) {
            return listOf(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "No more safe actions are needed from the current screen.",
                ),
            )
        }
        val trimmedSteps = steps.dropSatisfiedPrefix(input)
        if (trimmedSteps.isEmpty()) {
            return listOf(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "The current screen already satisfies the next checkpoint.",
                ),
            )
        }
        val budget = input.stepBudget.coerceIn(1, 6)
        if (trimmedSteps.size <= budget) return trimmedSteps
        val window = trimmedSteps.take(budget).toMutableList()
        val nextStep = trimmedSteps.getOrNull(window.size)
        if (window.lastOrNull()?.action is AgentAction.ConfirmUser && nextStep != null) {
            window += nextStep
        }
        if (nextStep?.action is AgentAction.Stop) {
            window += nextStep
        }
        return window
    }

    private fun List<ExecutionStep>.dropSatisfiedPrefix(input: PlannerInput): List<ExecutionStep> {
        var index = 0
        while (index < size && stepAlreadySatisfied(this[index], getOrNull(index + 1), input)) {
            index += 1
        }
        return drop(index)
    }

    private fun stepAlreadySatisfied(
        step: ExecutionStep,
        nextStep: ExecutionStep?,
        input: PlannerInput,
    ): Boolean {
        val history = input.recentActionHistory.toSet()
        if (step.action.historyKey() in history) {
            return true
        }
        return when (val action = step.action) {
            is AgentAction.LaunchApp,
            is AgentAction.WaitForApp,
            -> packageMatchesCurrentContext((action as? AgentAction.LaunchApp)?.packageName ?: (action as AgentAction.WaitForApp).packageName, input)

            is AgentAction.WaitForNode -> selectorVisible(action.selector, input.serializedNodeTree)
            is AgentAction.AssertVisible -> selectorVisible(action.selector, input.serializedNodeTree)
            is AgentAction.Tap,
            is AgentAction.InputText,
            is AgentAction.SubmitInput,
            is AgentAction.ClearText,
            is AgentAction.Scroll,
            is AgentAction.PressGlobal,
            -> nextStep?.let { next -> observableGoalSatisfied(next.action, input) } ?: false

            is AgentAction.ConfirmUser,
            AgentAction.Stop,
            is AgentAction.WaitForCondition,
            -> false
        }
    }

    private fun observableGoalSatisfied(
        action: AgentAction,
        input: PlannerInput,
    ): Boolean =
        when (action) {
            is AgentAction.WaitForApp -> packageMatchesCurrentContext(action.packageName, input)
            is AgentAction.WaitForNode -> selectorVisible(action.selector, input.serializedNodeTree)
            is AgentAction.AssertVisible -> selectorVisible(action.selector, input.serializedNodeTree)
            else -> false
        }

    private fun packageMatchesCurrentContext(
        packageName: String,
        input: PlannerInput,
    ): Boolean {
        if (packageName == input.foregroundPackage) return true
        if (input.foregroundPackage == AGENT_APP_PACKAGE) return false
        return packageName == input.lastExternalForegroundPackage
    }

    private fun selectorVisible(
        selector: NodeSelector,
        serializedNodeTree: String,
    ): Boolean =
        serializedNodeTree.lineSequence().any { line ->
            val segments = line.split(" | ")
                .mapNotNull { segment ->
                    val parts = segment.split("=", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    parts[0] to parts[1]
                }.toMap()
            selector.text?.let { if (!segments["text"].orEmpty().contains(it, ignoreCase = true)) return@any false }
            selector.contentDescription?.let { if (!segments["desc"].orEmpty().contains(it, ignoreCase = true)) return@any false }
            selector.resourceId?.let { if (segments["id"] != it) return@any false }
            selector.className?.let { if (segments["class"] != it) return@any false }
            selector.packageName?.let { if (segments["package"] != it) return@any false }
            selector.editable?.let { if (segments["editable"]?.toBooleanStrictOrNull() != it) return@any false }
            selector.clickable?.let { if (segments["clickable"]?.toBooleanStrictOrNull() != it) return@any false }
            selector.nearText?.let {
                val text = segments["text"].orEmpty()
                val desc = segments["desc"].orEmpty()
                if (!text.contains(it, ignoreCase = true) && !desc.contains(it, ignoreCase = true)) {
                    return@any false
                }
            }
            true
        }

    private fun normalizeSamsungBluetoothSettingsPlan(steps: List<ExecutionStep>): List<ExecutionStep> {
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SETTINGS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SETTINGS_PACKAGE),
                    expectedObservation = "Settings launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SETTINGS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SETTINGS_PACKAGE),
                    expectedObservation = "Settings becomes foreground.",
                )
        val searchButtonSelector = NodeSelector(
            contentDescription = SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC,
            packageName = SETTINGS_PACKAGE,
        )
        val searchFieldSelector = NodeSelector(
            resourceId = SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID,
            packageName = SETTINGS_INTELLIGENCE_PACKAGE,
        )
        val bluetoothResultSelector = NodeSelector(
            text = SAMSUNG_BLUETOOTH_RESULT_LABEL,
            packageName = SETTINGS_INTELLIGENCE_PACKAGE,
        )
        val bluetoothPageTitleSelector = NodeSelector(
            text = SAMSUNG_BLUETOOTH_RESULT_LABEL,
            packageName = SETTINGS_PACKAGE,
        )
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.WaitForNode(searchButtonSelector),
                expectedObservation = "Settings search button is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = searchButtonSelector,
                    label = "Open Settings search",
                ),
                expectedObservation = "Settings search opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(searchFieldSelector),
                expectedObservation = "Settings search field is visible and focused.",
            ),
            ExecutionStep(
                action = AgentAction.InputText(
                    selector = searchFieldSelector,
                    text = "Bluetooth",
                ),
                expectedObservation = "Bluetooth query appears in Settings search.",
            ),
            ExecutionStep(
                action = AgentAction.SubmitInput(searchFieldSelector),
                expectedObservation = "Settings search results update for Bluetooth.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(bluetoothResultSelector),
                expectedObservation = "Bluetooth result is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = bluetoothResultSelector,
                    label = "Open Bluetooth settings",
                ),
                expectedObservation = "Bluetooth settings page opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(bluetoothPageTitleSelector),
                expectedObservation = "Bluetooth settings page title is visible.",
            ),
        )
    }

    private fun normalizeSamsungSettingsSearchPlan(
        goal: String,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val query = extractSearchQuery(goal) ?: return steps
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SETTINGS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SETTINGS_PACKAGE),
                    expectedObservation = "Settings launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SETTINGS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SETTINGS_PACKAGE),
                    expectedObservation = "Settings becomes foreground.",
                )
        val searchButtonSelector = NodeSelector(
            contentDescription = SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC,
            packageName = SETTINGS_PACKAGE,
        )
        val searchFieldSelector = NodeSelector(
            resourceId = SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID,
            packageName = SETTINGS_INTELLIGENCE_PACKAGE,
        )
        val inputQuery = normalizeSettingsSearchInput(query)
        val resultText = normalizeSettingsSearchResultText(query)
        return buildList {
            add(launchStep)
            add(waitForAppStep)
            add(
                ExecutionStep(
                    action = AgentAction.WaitForNode(searchButtonSelector),
                    expectedObservation = "Settings search button is visible.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = searchButtonSelector,
                        label = "Open Settings search",
                    ),
                    expectedObservation = "Settings search opens.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.WaitForNode(searchFieldSelector),
                    expectedObservation = "Settings search field is visible and focused.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = searchFieldSelector,
                        text = inputQuery,
                    ),
                    expectedObservation = "The search query appears in Settings.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.SubmitInput(searchFieldSelector),
                    expectedObservation = "Settings search results update for the query.",
                ),
            )
            if (resultText != null) {
                add(
                    ExecutionStep(
                        action = AgentAction.WaitForNode(
                            selector = NodeSelector(
                                text = resultText,
                                packageName = SETTINGS_INTELLIGENCE_PACKAGE,
                            ),
                        ),
                        expectedObservation = "Relevant Settings search result is visible.",
                    ),
                )
            }
            add(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "The requested Settings search result is visible.",
                ),
            )
        }
    }

    private fun normalizeSamsungClockAlarmPlan(steps: List<ExecutionStep>): List<ExecutionStep> {
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SAMSUNG_CLOCK_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SAMSUNG_CLOCK_PACKAGE),
                    expectedObservation = "Clock launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SAMSUNG_CLOCK_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SAMSUNG_CLOCK_PACKAGE),
                    expectedObservation = "Clock becomes foreground.",
                )
        val alarmTabSelector = NodeSelector(
            text = SAMSUNG_CLOCK_ALARM_TAB_LABEL,
            resourceId = SAMSUNG_CLOCK_TAB_TITLE_ID,
            packageName = SAMSUNG_CLOCK_PACKAGE,
        )
        val alarmLayoutSelector = NodeSelector(resourceId = SAMSUNG_CLOCK_ALARM_LAYOUT_ID)
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.WaitForNode(alarmTabSelector),
                expectedObservation = "Alarm tab is visible in Clock.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = alarmTabSelector,
                    label = "Open Alarm tab",
                ),
                expectedObservation = "Clock switches to the Alarm tab.",
            ),
            ExecutionStep(
                action = AgentAction.AssertVisible(alarmLayoutSelector),
                expectedObservation = "Alarm tab content is visible.",
            ),
        )
    }

    private fun normalizeSamsungContactsSearchPlan(
        goal: String,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val query = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()?.text
            ?: extractSearchQuery(goal)
            ?: return steps
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SAMSUNG_CONTACTS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SAMSUNG_CONTACTS_PACKAGE),
                    expectedObservation = "Contacts launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SAMSUNG_CONTACTS_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SAMSUNG_CONTACTS_PACKAGE),
                    expectedObservation = "Contacts becomes foreground.",
                )
        val searchButtonSelector = NodeSelector(resourceId = SAMSUNG_CONTACTS_SEARCH_BUTTON_ID)
        val searchFieldSelector = NodeSelector(resourceId = SAMSUNG_CONTACTS_SEARCH_TEXT_ID)
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = searchButtonSelector,
                    label = "Open Contacts search",
                ),
                expectedObservation = "Contacts search field opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(searchFieldSelector),
                expectedObservation = "Contacts search field is visible and focused.",
            ),
            ExecutionStep(
                action = AgentAction.InputText(
                    selector = searchFieldSelector,
                    text = query,
                ),
                expectedObservation = "The search query appears in Contacts.",
            ),
            ExecutionStep(
                action = AgentAction.SubmitInput(searchFieldSelector),
                expectedObservation = "Contacts search results update for the query.",
            ),
        )
    }

    private fun normalizeSamsungMessagesSendPlan(
        goal: String,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val recipient = extractSendRecipient(goal) ?: return steps
        val message = extractSendMessage(goal) ?: return steps
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SAMSUNG_MESSAGES_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SAMSUNG_MESSAGES_PACKAGE),
                    expectedObservation = "Messages launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SAMSUNG_MESSAGES_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SAMSUNG_MESSAGES_PACKAGE),
                    expectedObservation = "Messages becomes foreground.",
                )
        val newMessageButtonSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        val oneToOneSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        val recipientSearchSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        val chatWithSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        val messageSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        val sendSelector = NodeSelector(
            resourceId = SAMSUNG_MESSAGES_SEND_BUTTON_ID,
            packageName = SAMSUNG_MESSAGES_PACKAGE,
        )
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.WaitForNode(newMessageButtonSelector),
                expectedObservation = "New message button is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = newMessageButtonSelector,
                    label = "Create a new message",
                ),
                expectedObservation = "New conversation shortcuts open.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(oneToOneSelector),
                expectedObservation = "1:1 conversation action is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = oneToOneSelector,
                    label = "Start 1:1 conversation",
                ),
                expectedObservation = "Recipient picker opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(recipientSearchSelector),
                expectedObservation = "Recipient search field is visible.",
            ),
            ExecutionStep(
                action = AgentAction.InputText(
                    selector = recipientSearchSelector,
                    text = recipient,
                ),
                expectedObservation = "Recipient search text appears.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(chatWithSelector),
                expectedObservation = "Chat-with-recipient action is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = chatWithSelector,
                    label = "Chat with recipient",
                ),
                expectedObservation = "Conversation composer opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(messageSelector),
                expectedObservation = "Message body field is visible.",
            ),
            ExecutionStep(
                action = AgentAction.InputText(
                    selector = messageSelector,
                    text = message,
                ),
                expectedObservation = "Message body appears in the composer.",
            ),
            ExecutionStep(
                action = AgentAction.ConfirmUser("User confirmation required before commit actions."),
                expectedObservation = "Execution pauses before send.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = sendSelector,
                    label = "Send message",
                ),
                expectedObservation = "Message send is triggered.",
            ),
        )
    }

    private fun buildNormalizedPlanJson(
        root: JSONObject,
        steps: List<ExecutionStep>,
        needsConfirmation: Boolean,
    ): String {
        val normalized = JSONObject().apply {
            put("summary", root.optString("summary", "LiteRT plan ready"))
            put("riskLevel", root.optString("riskLevel", RiskLevel.LOW.name))
            put("needsConfirmation", needsConfirmation)
            root.optJSONArray("targetPackageCandidates")?.let { candidates ->
                put("targetPackageCandidates", candidates)
            }
            put(
                "steps",
                JSONArray().apply {
                    steps.forEach { step -> put(serializeStep(step)) }
                },
            )
        }
        return normalized.toString().orEmpty().ifBlank { "{}" }
    }

    private fun serializeStep(step: ExecutionStep): JSONObject =
        serializeAction(step.action).apply {
            put("expectedObservation", step.expectedObservation)
        }

    private fun serializeAction(action: AgentAction): JSONObject =
        when (action) {
            is AgentAction.LaunchApp ->
                JSONObject()
                    .put("type", "launch_app")
                    .put("packageName", action.packageName)

            is AgentAction.WaitForApp ->
                JSONObject()
                    .put("type", "wait_for_app")
                    .put("packageName", action.packageName)
                    .put("timeoutMs", action.timeoutMs)

            is AgentAction.WaitForNode ->
                JSONObject()
                    .put("type", "wait_for_node")
                    .put("selector", serializeSelector(action.selector))
                    .put("timeoutMs", action.timeoutMs)

            is AgentAction.Tap ->
                JSONObject()
                    .put("type", "tap")
                    .put("selector", serializeSelector(action.selector))
                    .put("label", action.label)

            is AgentAction.InputText ->
                JSONObject()
                    .put("type", "input_text")
                    .put("selector", serializeSelector(action.selector))
                    .put("text", action.text)

            is AgentAction.SubmitInput ->
                JSONObject()
                    .put("type", "submit_input")
                    .put("selector", serializeSelector(action.selector))

            is AgentAction.ClearText ->
                JSONObject()
                    .put("type", "clear_text")
                    .put("selector", serializeSelector(action.selector))

            is AgentAction.Scroll ->
                JSONObject()
                    .put("type", "scroll")
                    .put("direction", action.direction.name)
                    .apply {
                        action.selector?.let { selector -> put("selector", serializeSelector(selector)) }
                    }

            is AgentAction.PressGlobal ->
                JSONObject()
                    .put("type", "press_global")
                    .put("action", action.action.name)

            is AgentAction.AssertVisible ->
                JSONObject()
                    .put("type", "assert_visible")
                    .put("selector", serializeSelector(action.selector))

            is AgentAction.ConfirmUser ->
                JSONObject()
                    .put("type", "confirm_user")
                    .put("reason", action.reason)

            AgentAction.Stop ->
                JSONObject().apply {
                    put("type", "stop")
                }

            is AgentAction.WaitForCondition ->
                error("WaitForCondition is not supported in planner serialization.")
        }

    private fun serializeSelector(selector: NodeSelector): JSONObject =
        JSONObject().apply {
            selector.text?.let { put("text", it) }
            selector.contentDescription?.let { put("contentDescription", it) }
            selector.resourceId?.let { put("resourceId", it) }
            selector.className?.let { put("className", it) }
            selector.editable?.let { put("editable", it) }
            selector.clickable?.let { put("clickable", it) }
            selector.packageName?.let { put("packageName", it) }
            if (selector.indexPath.isNotEmpty()) {
                put(
                    "indexPath",
                    JSONArray().apply {
                        selector.indexPath.forEach { put(it) }
                    },
                )
            }
            selector.boundsHint?.let { bounds ->
                put(
                    "boundsHint",
                    JSONObject()
                        .put("left", bounds.left)
                        .put("top", bounds.top)
                        .put("right", bounds.right)
                        .put("bottom", bounds.bottom),
                )
            }
            selector.nearText?.let { put("nearText", it) }
        }

    private fun parseAction(stepJson: JSONObject): AgentAction {
        val payload = actionPayload(stepJson)
        val type = actionType(stepJson, payload)
        return when (type) {
            "launch_app" -> AgentAction.LaunchApp(payload.requiredString("packageName", "package", "package_name"))
            "wait_for_app" -> AgentAction.WaitForApp(
                packageName = payload.requiredString("packageName", "package", "package_name"),
                timeoutMs = payload.optLong("timeoutMs", 15_000L).coerceAtLeast(15_000L),
            )

            "wait_for_node" -> AgentAction.WaitForNode(
                selector = parseSelector(payload.getJSONObject("selector")),
                timeoutMs = payload.optLong("timeoutMs", 5_000L),
            )

            "tap" -> AgentAction.Tap(
                selector = parseSelector(payload.getJSONObject("selector")),
                label = payload.optString("label", ""),
            )

            "input_text" -> AgentAction.InputText(
                selector = parseSelector(payload.getJSONObject("selector")),
                text = payload.getString("text"),
            )

            "submit_input" -> AgentAction.SubmitInput(parseSelector(payload.getJSONObject("selector")))
            "clear_text" -> AgentAction.ClearText(parseSelector(payload.getJSONObject("selector")))
            "scroll" -> AgentAction.Scroll(
                selector = payload.optJSONObject("selector")?.let(::parseSelector),
                direction = ScrollDirection.valueOf(
                    payload.optString("direction", ScrollDirection.DOWN.name).uppercase(),
                ),
            )

            "press_global" -> AgentAction.PressGlobal(
                action = GlobalActionType.valueOf(payload.getString("action").uppercase()),
            )

            "assert_visible" -> AgentAction.AssertVisible(parseSelector(payload.getJSONObject("selector")))
            "confirm_user" -> AgentAction.ConfirmUser(
                payload.optString("reason").ifBlank { "User confirmation required before commit actions." },
            )
            "stop" -> AgentAction.Stop
            else -> error("Unsupported action type: $type")
        }
    }

    private fun actionPayload(stepJson: JSONObject): JSONObject {
        val nested = stepJson.optJSONObject("action") ?: stepJson.optJSONObject("step")
        if (nested == null) return stepJson
        return JSONObject(stepJson.toString()).apply {
            nested.keys().forEach { key -> put(key, nested.get(key)) }
        }
    }

    private fun actionType(stepJson: JSONObject, payload: JSONObject): String {
        val direct = payload.firstNonBlankString("type", "actionType", "action_type", "kind", "name")
        if (direct != null) return direct.toActionType()
        val action = stepJson.opt("action")
        if (action is String && action.isNotBlank()) return action.toActionType()
        error("Step missing action type. keys=${stepJson.keys().asSequence().toList().joinToString(",")}")
    }

    private fun JSONObject.firstNonBlankString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }

    private fun JSONObject.requiredString(vararg keys: String): String =
        firstNonBlankString(*keys) ?: error("Missing required string: ${keys.joinToString("/")}")

    private fun String.toActionType(): String =
        trim()
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace('-', '_')
            .lowercase()

    private fun parseStringStep(stepType: String): AgentAction =
        when (stepType.trim().lowercase()) {
            "stop" -> AgentAction.Stop
            else -> error("Unsupported string step type: $stepType")
        }

    private fun parseSelector(json: JSONObject): NodeSelector =
        NodeSelector(
            text = json.optString("text").ifBlank { null },
            contentDescription = json.optString("contentDescription").ifBlank { null },
            resourceId = json.optString("resourceId").ifBlank { null },
            className = json.optString("className").ifBlank { null },
            editable = json.takeIf { it.has("editable") }?.optBoolean("editable"),
            clickable = json.takeIf { it.has("clickable") }?.optBoolean("clickable"),
            packageName = json.optString("packageName").ifBlank { null },
            indexPath = json.optJSONArray("indexPath")?.let(::jsonArrayToInts).orEmpty(),
            boundsHint = json.optJSONObject("boundsHint")?.let { bounds ->
                ScreenBounds(
                    left = bounds.optInt("left"),
                    top = bounds.optInt("top"),
                    right = bounds.optInt("right"),
                    bottom = bounds.optInt("bottom"),
                )
            },
            nearText = json.optString("nearText").ifBlank { null },
        ).also(::requireConcreteSelector)

    private fun extractJson(rawOutput: String): String {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        require(start >= 0 && end > start) { "Model did not return JSON." }
        return rawOutput.substring(start, end + 1)
    }

    private fun validateGoalSpecificConstraints(
        goal: String,
        candidateApps: List<com.guribbong.phoneappagent.core.runner.AppCandidate>,
        steps: List<ExecutionStep>,
    ) {
        val allowedPackages = candidateApps.map { it.packageName }.toSet()
        val launchTargets =
            steps.mapNotNull { step ->
                when (val action = step.action) {
                    is AgentAction.LaunchApp -> action.packageName
                    is AgentAction.WaitForApp -> action.packageName
                    else -> null
                }
            }
        if (allowedPackages.isEmpty()) {
            require(launchTargets.isEmpty()) {
                "Plan cannot launch an app because candidateApps is empty."
            }
        } else {
            launchTargets.forEach { packageName ->
                require(packageName in allowedPackages) {
                    "Launch target $packageName must come from candidateApps."
                }
            }
        }
        val lowerGoal = goal.lowercase()
        val forbiddenGlobalAction = steps.firstNotNullOfOrNull { step ->
            val action = step.action as? AgentAction.PressGlobal ?: return@firstNotNullOfOrNull null
            when {
                action.action == GlobalActionType.BACK -> null
                goalExplicitlyRequestsGlobalAction(lowerGoal, action.action) -> null
                else -> action.action
            }
        }
        require(forbiddenGlobalAction == null) {
            "Global action $forbiddenGlobalAction is only allowed when the goal explicitly asks for it."
        }
        if (isChromeSearchGoal(lowerGoal)) {
            val inputAction = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()
            val submitAction = steps.map { it.action }.filterIsInstance<AgentAction.SubmitInput>().lastOrNull()
            require(inputAction != null) { "Chrome search plans must type a query before submitting it." }
            require(submitAction != null) { "Chrome search plans must include submit_input after typing the query." }
            require(inputAction.selector.resourceId == CHROME_URL_BAR_RESOURCE_ID) {
                "Chrome search input_text must target $CHROME_URL_BAR_RESOURCE_ID."
            }
            require(submitAction.selector.resourceId == CHROME_URL_BAR_RESOURCE_ID) {
                "Chrome search submit_input must target $CHROME_URL_BAR_RESOURCE_ID."
            }
            extractSearchQuery(goal)?.let { query ->
                require(inputAction.text.equals(query, ignoreCase = true)) {
                    "Chrome search input_text must use the exact query '$query'."
                }
            }
        }
        if (isSamsungBluetoothSettingsGoal(lowerGoal, steps)) {
            val waitNodes = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>()
            val taps = steps.map { it.action }.filterIsInstance<AgentAction.Tap>()
            val inputAction = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()
            val submitAction = steps.map { it.action }.filterIsInstance<AgentAction.SubmitInput>().lastOrNull()
            require(waitNodes.getOrNull(0)?.selector?.contentDescription == SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC) {
                "Bluetooth settings plans must wait for contentDescription '$SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC'."
            }
            require(taps.getOrNull(0)?.selector?.contentDescription == SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC) {
                "Bluetooth settings plans must tap contentDescription '$SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC'."
            }
            require(waitNodes.getOrNull(1)?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Bluetooth settings plans must wait for $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            require(inputAction?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Bluetooth settings input_text must target $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            require(inputAction.text == "Bluetooth") {
                "Bluetooth settings input_text must use the exact text 'Bluetooth'."
            }
            require(submitAction?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Bluetooth settings submit_input must target $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            require(waitNodes.getOrNull(2)?.selector?.text == SAMSUNG_BLUETOOTH_RESULT_LABEL) {
                "Bluetooth settings plans must wait for Bluetooth search result text '$SAMSUNG_BLUETOOTH_RESULT_LABEL'."
            }
            require(taps.getOrNull(1)?.selector?.text == SAMSUNG_BLUETOOTH_RESULT_LABEL) {
                "Bluetooth settings plans must tap the Bluetooth search result '$SAMSUNG_BLUETOOTH_RESULT_LABEL'."
            }
            require(waitNodes.getOrNull(3)?.selector?.text == SAMSUNG_BLUETOOTH_RESULT_LABEL) {
                "Bluetooth settings plans must wait for Bluetooth page title '$SAMSUNG_BLUETOOTH_RESULT_LABEL'."
            }
        } else if (isSamsungSettingsSearchGoal(lowerGoal, steps)) {
            val tapAction = steps.map { it.action }.filterIsInstance<AgentAction.Tap>().firstOrNull()
            val waitForSearchButton = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>().firstOrNull()
            val waitForSearchField = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>().drop(1).firstOrNull()
            val inputAction = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()
            val submitAction = steps.map { it.action }.filterIsInstance<AgentAction.SubmitInput>().lastOrNull()
            require(waitForSearchButton?.selector?.contentDescription == SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC) {
                "Samsung Settings search plans must wait for contentDescription '$SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC'."
            }
            require(waitForSearchButton.selector.packageName == SETTINGS_PACKAGE) {
                "Samsung Settings search button wait must target $SETTINGS_PACKAGE."
            }
            require(tapAction?.selector?.contentDescription == SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC) {
                "Samsung Settings search plans must tap contentDescription '$SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC'."
            }
            require(tapAction.selector.packageName == SETTINGS_PACKAGE) {
                "Samsung Settings search button tap must target $SETTINGS_PACKAGE."
            }
            require(waitForSearchField?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Samsung Settings search plans must wait for $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            require(inputAction?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Samsung Settings search input_text must target $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            require(submitAction?.selector?.resourceId == SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID) {
                "Samsung Settings search submit_input must target $SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID."
            }
            extractSearchQuery(goal)?.let { query ->
                val expectedQuery = normalizeSettingsSearchInput(query)
                require(inputAction.text.equals(expectedQuery, ignoreCase = true)) {
                    "Samsung Settings search input_text must use the exact query '$expectedQuery'."
                }
            }
        }
        if (isSamsungClockAlarmGoal(lowerGoal, steps)) {
            val waitForNodeAction = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>().lastOrNull()
            val tapAction = steps.map { it.action }.filterIsInstance<AgentAction.Tap>().lastOrNull()
            val assertVisibleAction = steps.map { it.action }.filterIsInstance<AgentAction.AssertVisible>().lastOrNull()
            require(waitForNodeAction?.selector?.text == SAMSUNG_CLOCK_ALARM_TAB_LABEL) {
                "Samsung Clock alarm plans must wait for tab text '$SAMSUNG_CLOCK_ALARM_TAB_LABEL'."
            }
            require(waitForNodeAction.selector.resourceId == SAMSUNG_CLOCK_TAB_TITLE_ID) {
                "Samsung Clock alarm plans must wait for tab resourceId $SAMSUNG_CLOCK_TAB_TITLE_ID."
            }
            require(tapAction?.selector?.text == SAMSUNG_CLOCK_ALARM_TAB_LABEL) {
                "Samsung Clock alarm plans must tap tab text '$SAMSUNG_CLOCK_ALARM_TAB_LABEL'."
            }
            require(tapAction.selector.resourceId == SAMSUNG_CLOCK_TAB_TITLE_ID) {
                "Samsung Clock alarm plans must tap tab resourceId $SAMSUNG_CLOCK_TAB_TITLE_ID."
            }
            require(assertVisibleAction?.selector?.resourceId == SAMSUNG_CLOCK_ALARM_LAYOUT_ID) {
                "Samsung Clock alarm plans must assert $SAMSUNG_CLOCK_ALARM_LAYOUT_ID."
            }
        }
        if (isSamsungContactsSearchGoal(lowerGoal, steps)) {
            val tapAction = steps.map { it.action }.filterIsInstance<AgentAction.Tap>().firstOrNull()
            val waitForNodeAction = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>().lastOrNull()
            val inputAction = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()
            require(tapAction?.selector?.resourceId == SAMSUNG_CONTACTS_SEARCH_BUTTON_ID) {
                "Samsung Contacts search plans must tap $SAMSUNG_CONTACTS_SEARCH_BUTTON_ID first."
            }
            require(waitForNodeAction?.selector?.resourceId == SAMSUNG_CONTACTS_SEARCH_TEXT_ID) {
                "Samsung Contacts search plans must wait for $SAMSUNG_CONTACTS_SEARCH_TEXT_ID."
            }
            require(inputAction?.selector?.resourceId == SAMSUNG_CONTACTS_SEARCH_TEXT_ID) {
                "Samsung Contacts search input_text must target $SAMSUNG_CONTACTS_SEARCH_TEXT_ID."
            }
            extractSearchQuery(goal)?.let { query ->
                require(inputAction.text.equals(query, ignoreCase = true)) {
                    "Samsung Contacts search input_text must use the exact query '$query'."
                }
            }
        }
        if (isSamsungMessagesSendGoal(lowerGoal, steps)) {
            val waitNodes = steps.map { it.action }.filterIsInstance<AgentAction.WaitForNode>()
            val tapActions = steps.map { it.action }.filterIsInstance<AgentAction.Tap>()
            val inputActions = steps.map { it.action }.filterIsInstance<AgentAction.InputText>()
            val confirmAction = steps.map { it.action }.filterIsInstance<AgentAction.ConfirmUser>().firstOrNull()
            require(waitNodes.getOrNull(0)?.selector?.resourceId == SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID) {
                "Samsung Messages plans must wait for $SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID."
            }
            require(tapActions.getOrNull(0)?.selector?.resourceId == SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID) {
                "Samsung Messages plans must tap $SAMSUNG_MESSAGES_NEW_MESSAGE_BUTTON_ID first."
            }
            require(waitNodes.getOrNull(1)?.selector?.resourceId == SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID) {
                "Samsung Messages plans must wait for $SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID."
            }
            require(tapActions.getOrNull(1)?.selector?.resourceId == SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID) {
                "Samsung Messages plans must tap $SAMSUNG_MESSAGES_ONE_TO_ONE_FAB_ID."
            }
            require(waitNodes.getOrNull(2)?.selector?.resourceId == SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID) {
                "Samsung Messages plans must wait for $SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID."
            }
            require(inputActions.getOrNull(0)?.selector?.resourceId == SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID) {
                "Samsung Messages recipient input must target $SAMSUNG_MESSAGES_RECIPIENT_SEARCH_ID."
            }
            extractSendRecipient(goal)?.let { recipient ->
                require(inputActions.getOrNull(0)?.text == recipient) {
                    "Samsung Messages recipient input must use the exact recipient '$recipient'."
                }
            }
            require(waitNodes.getOrNull(3)?.selector?.resourceId == SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID) {
                "Samsung Messages plans must wait for $SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID."
            }
            require(tapActions.getOrNull(2)?.selector?.resourceId == SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID) {
                "Samsung Messages plans must tap $SAMSUNG_MESSAGES_CHAT_WITH_BUTTON_ID."
            }
            require(waitNodes.getOrNull(4)?.selector?.resourceId == SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID) {
                "Samsung Messages plans must wait for $SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID."
            }
            require(inputActions.getOrNull(1)?.selector?.resourceId == SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID) {
                "Samsung Messages message input must target $SAMSUNG_MESSAGES_MESSAGE_EDITOR_ID."
            }
            extractSendMessage(goal)?.let { message ->
                require(inputActions.getOrNull(1)?.text == message) {
                    "Samsung Messages message input must use the exact message '$message'."
                }
            }
            require(confirmAction != null) {
                "Samsung Messages send plans must include confirm_user before send."
            }
            require(tapActions.lastOrNull()?.selector?.resourceId == SAMSUNG_MESSAGES_SEND_BUTTON_ID) {
                "Samsung Messages plans must tap $SAMSUNG_MESSAGES_SEND_BUTTON_ID after confirm_user."
            }
        }
    }

    private fun goalExplicitlyRequestsGlobalAction(
        lowerGoal: String,
        action: GlobalActionType,
    ): Boolean =
        when (action) {
            GlobalActionType.BACK -> true
            GlobalActionType.HOME -> listOf("home", "홈").any { it in lowerGoal }
            GlobalActionType.RECENTS -> listOf("recents", "recent apps", "최근 앱").any { it in lowerGoal }
            GlobalActionType.NOTIFICATIONS -> listOf("notification", "notifications", "알림").any { it in lowerGoal }
            GlobalActionType.QUICK_SETTINGS -> listOf("quick settings", "빠른 설정").any { it in lowerGoal }
        }

    private fun isChromeSearchGoal(lowerGoal: String): Boolean =
        ("chrome" in lowerGoal || "크롬" in lowerGoal) &&
            ("search" in lowerGoal || "검색" in lowerGoal)

    private fun isSamsungSettingsSearchGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        ("settings" in lowerGoal || "설정" in lowerGoal) &&
            ("search" in lowerGoal || "검색" in lowerGoal)

    private fun isSamsungBluetoothSettingsGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        ("bluetooth" in lowerGoal || "블루투스" in lowerGoal) &&
            ("settings" in lowerGoal || "설정" in lowerGoal)

    private fun isSamsungClockAlarmGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        ("clock" in lowerGoal || "시계" in lowerGoal) &&
            ("alarm" in lowerGoal || "알람" in lowerGoal)

    private fun isSamsungContactsSearchGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        ("contacts" in lowerGoal || "연락처" in lowerGoal) &&
            ("search" in lowerGoal || "검색" in lowerGoal)

    private fun isSamsungMessagesSendGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        ("messages" in lowerGoal || "메시지" in lowerGoal) &&
            ("send" in lowerGoal || "보내" in lowerGoal)

    private fun extractSearchQuery(goal: String): String? {
        val searchMatch = Regex("(?i)search(?:\\s+for)?\\s+(.+)$").find(goal)?.groupValues?.getOrNull(1)
        if (!searchMatch.isNullOrBlank()) return cleanExtractedSearchQuery(searchMatch)
        val koreanMatch = Regex("검색\\s+(.+)$").find(goal)?.groupValues?.getOrNull(1)
        return koreanMatch?.let(::cleanExtractedSearchQuery)
    }

    private fun cleanExtractedSearchQuery(value: String): String? =
        value
            .trim()
            .trim('"', '\'')
            .trimEnd('.', '?', '!', '。')
            .trim()
            .takeIf { it.isNotBlank() }

    private fun extractSendRecipient(goal: String): String? =
        Regex("(?i)\\bto\\s+([^\"']+?)(?:\\s+and\\s+send|\\s*$)")
            .find(goal)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }

    private fun extractSendMessage(goal: String): String? =
        Regex("(?i)send\\s+(.+?)\\s+to\\s+").find(goal)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }

    private fun normalizeSettingsSearchResultText(query: String): String? {
        val normalized = query.trim().lowercase()
        return when (normalized) {
            "wifi", "wi-fi", "wi fi", "와이파이" -> "Wi-Fi"
            else -> null
        }
    }

    private fun normalizeSettingsSearchInput(query: String): String =
        normalizeSettingsSearchResultText(query) ?: query

    private fun jsonArrayToStrings(array: JSONArray): List<String> =
        buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }

    private fun jsonArrayToInts(array: JSONArray): List<Int> =
        buildList {
            for (index in 0 until array.length()) {
                add(array.getInt(index))
            }
        }

    private fun requireConcreteSelector(selector: NodeSelector) {
        val hasValue =
            !selector.text.isNullOrBlank() ||
                !selector.contentDescription.isNullOrBlank() ||
                !selector.resourceId.isNullOrBlank() ||
                !selector.className.isNullOrBlank() ||
                selector.editable != null ||
                selector.clickable != null ||
                !selector.packageName.isNullOrBlank() ||
                selector.indexPath.isNotEmpty() ||
                selector.boundsHint != null ||
                !selector.nearText.isNullOrBlank()
        require(hasValue) { "Selector must include at least one identifying field." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    private data class RemoteModelMetadata(
        val contentLength: Long,
        val expectedSha256: String?,
    )

    private companion object {
        val systemInstruction =
            "Android planner. Analyze the current visible UI first, then plan. Reply JSON only with keys summary,riskLevel,needsConfirmation,targetPackageCandidates,steps. " +
                "summary must describe the current screen situation and the next checkpoint. " +
                "Step types: launch_app,wait_for_app,wait_for_node,tap,input_text,submit_input,clear_text,scroll,press_global,assert_visible,confirm_user,stop. " +
                "Use recentActionHistory,appMemory,appSkillGuidance,planningMode,priorPlanSummary,foregroundPackage,lastExternalForegroundPackage,and visible nodes to understand current progress. " +
                "appMemory stores prior success/failure notes for the target app; avoid repeating failed selectors/packages, but current visible nodes are source of truth. " +
                "appSkillGuidance is advisory only. Current visible nodes override stale skills. Output strict JSON DSL only. " +
                "Return only the next small horizon of steps from the current screen, at most stepBudget steps unless confirm_user must be followed by its guarded action. " +
                "Continue from the current screen instead of restarting completed work. Emit stop when the current screen already satisfies the goal or the next safe checkpoint. " +
                "launch_app and wait_for_app must use only package names listed in candidateApps. " +
                "Do not use HOME,RECENTS,NOTIFICATIONS,QUICK_SETTINGS unless the user explicitly requested that system surface. " +
                "If the goal is to search in Chrome, use selector resourceId com.android.chrome:id/url_bar for input_text and submit_input, and use the exact query from the goal text. " +
                "If the goal is to enter the Alarm tab in Samsung Clock package com.sec.android.app.clockpackage, use selector text 알람 with resourceId com.sec.android.app.clockpackage:id/title for the tab, then assert com.sec.android.app.clockpackage:id/alarm_main_layout. " +
                "If the goal is to search in Samsung Contacts package com.samsung.android.app.contacts, tap com.samsung.android.app.contacts:id/menu_search before waiting for or typing into com.samsung.android.app.contacts:id/search_src_text. " +
                "If the goal is to send a message in Samsung Messages package com.samsung.android.messaging, tap com.samsung.android.messaging:id/fab, then tap com.samsung.android.messaging:id/chat_fab, type the recipient into com.samsung.android.messaging:id/search_src_text, tap com.samsung.android.messaging:id/chat_with_button, use com.samsung.android.messaging:id/message_edit_text for message body, then require confirm_user before com.samsung.android.messaging:id/send_button. " +
                "Dangerous actions require confirm_user."
    }
}
