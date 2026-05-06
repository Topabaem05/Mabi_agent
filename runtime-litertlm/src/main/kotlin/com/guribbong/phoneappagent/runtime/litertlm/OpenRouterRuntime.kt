package com.guribbong.phoneappagent.runtime.litertlm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.content.getSystemService
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.GlobalActionType
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.ScrollDirection
import com.guribbong.phoneappagent.core.dsl.ScreenBounds
import com.guribbong.phoneappagent.core.dsl.historyKey
import com.guribbong.phoneappagent.core.policy.PolicyGate
import com.guribbong.phoneappagent.core.policy.RiskLevel
import com.guribbong.phoneappagent.core.runner.AppCandidate
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val OPENROUTER_CONNECT_TIMEOUT_MS = 30_000
private const val OPENROUTER_READ_TIMEOUT_MS = 180_000
private const val OPENROUTER_MAX_PROMPT_CHARS = 6_000
private const val OPENROUTER_MIN_STORAGE_MB = 1_024L
private const val OPENROUTER_TAG = "OpenRouterRuntime"
private const val AGENT_APP_PACKAGE = "com.guribbong.phoneappagent"
private const val CHROME_PACKAGE = "com.android.chrome"
private const val CHROME_URL_BAR_RESOURCE_ID = "com.android.chrome:id/url_bar"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val SETTINGS_INTELLIGENCE_PACKAGE = "com.android.settings.intelligence"
private const val SAMSUNG_SETTINGS_SEARCH_BUTTON_DESC = "설정 검색"
private const val SETTINGS_INTELLIGENCE_SEARCH_TEXT_ID = "com.android.settings.intelligence:id/search_src_text"
private const val SAMSUNG_SETTINGS_CONNECTIONS_LABEL = "연결"
private const val SAMSUNG_SETTINGS_WIFI_LABEL = "Wi-Fi"
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
private const val SAMSUNG_CAMERA_PACKAGE = "com.sec.android.app.camera"
private const val SAMSUNG_CAMERA_SHUTTER_ID = "com.sec.android.app.camera:id/normal_center_button"
private const val SAMSUNG_CAMERA_SHUTTER_DESC = "사진 촬영"
private const val SAMSUNG_MYFILES_PACKAGE = "com.sec.android.app.myfiles"
private const val PLAY_STORE_PACKAGE = "com.android.vending"
private const val PLAY_STORE_SEARCH_TAB_KO = "검색"
private const val PLAY_STORE_SEARCH_TAB_EN = "Search"
private const val PLAY_STORE_SEARCH_DESC_KO = "Google Play 검색"
private const val PLAY_STORE_SEARCH_PLACEHOLDER_KO = "앱 및 게임 검색"
private const val PLAY_STORE_INSTALL_TEXT_KO = "설치"
private const val PLAY_STORE_INSTALL_TEXT_EN = "Install"
private const val PLAY_STORE_INSTALL_CONFIRM_REASON = "User confirmation required before installing an app from Google Play."
private val PLAY_STORE_SEARCH_QUERY_ALIASES = mapOf(
    "claude" to "Claude by Anthropic",
)
private val PLAY_STORE_DIRECT_PACKAGE_ALIASES = mapOf(
    "claude" to "com.anthropic.claude",
    "claude by anthropic" to "com.anthropic.claude",
)
private const val KORAIL_TALK_PACKAGE = "com.korail.talk"
private const val KORAIL_DEPARTURE_STATION_ID = "com.korail.talk:id/v_departure_station"
private const val KORAIL_ARRIVAL_STATION_ID = "com.korail.talk:id/v_arrival_station"
private const val KORAIL_DEPARTURE_TEXT_ID = "com.korail.talk:id/tv_departure_station"
private const val KORAIL_ARRIVAL_TEXT_ID = "com.korail.talk:id/tv_arrival_station"
private const val KORAIL_STATION_NAME_ID = "com.korail.talk:id/stationNameTxt"
private const val KORAIL_STATION_SEARCH_ID = "com.korail.talk:id/stationNameEdit"
private const val KORAIL_TRAIN_SEARCH_BUTTON_ID = "com.korail.talk:id/btn_right"
private const val KORAIL_RESULTS_TITLE_ID = "com.korail.talk:id/titleTxt"
private const val KORAIL_STANDARD_RESERVE_BUTTON_ID = "com.korail.talk:id/standardReserveButton"
private const val KORAIL_STANDARD_AVAILABLE_FARE_TEXT = "43,500원"
private const val KORAIL_BOOKING_BUTTON_ID = "com.korail.talk:id/bookingBtn"
private const val KORAIL_BOOKING_CONFIRM_REASON = "User confirmation required before entering KorailTalk booking."
private val SUPPORTED_ACTION_TYPES = setOf(
    "launch_app",
    "wait_for_app",
    "wait_for_node",
    "tap",
    "input_text",
    "submit_input",
    "clear_text",
    "scroll",
    "press_global",
    "assert_visible",
    "confirm_user",
    "stop",
)

data class OpenRouterRuntimeConfig(
    val apiKey: String,
    val modelName: String,
    val endpoint: String,
    val appReferer: String,
    val appTitle: String,
)

class OpenRouterLocalAgentRuntime(
    context: Context,
    private val policyGate: PolicyGate,
    private val config: OpenRouterRuntimeConfig,
) : LocalAgentRuntime {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RuntimeState(detail = "OpenRouter idle"))

    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override suspend fun probeDeviceCapability(): DeviceCapabilityProfile =
        withContext(Dispatchers.Default) {
            val activityManager = appContext.getSystemService<ActivityManager>()
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
            val statFs = StatFs(appContext.filesDir.absolutePath)
            val totalMemoryMb = memoryInfo.totalMem / (1024L * 1024L)
            val availableStorageMb = statFs.availableBytes / (1024L * 1024L)
            val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
            val supported = config.apiKey.isNotBlank() && availableStorageMb >= OPENROUTER_MIN_STORAGE_MB
            DeviceCapabilityProfile(
                supported = supported,
                reason = when {
                    config.apiKey.isBlank() -> "OPENROUTER_API_KEY is missing."
                    availableStorageMb < OPENROUTER_MIN_STORAGE_MB -> "Free storage is below 1 GB."
                    else -> "OpenRouter runtime available."
                },
                primaryAbi = abi,
                totalMemoryMb = totalMemoryMb,
                availableStorageMb = availableStorageMb,
                recommendedThreads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2),
                contextProfile = ContextProfile.FULL,
                backendLabel = "OpenRouter / ${config.modelName}",
            )
        }

    override suspend fun prepare(profile: DeviceCapabilityProfile): RuntimePreparation =
        mutex.withLock {
            if (!profile.supported) {
                _state.value = RuntimeState(
                    phase = RuntimePhase.UNSUPPORTED,
                    detail = profile.reason,
                    profile = profile,
                )
                return RuntimePreparation(
                    ready = false,
                    detail = profile.reason,
                    profile = profile,
                )
            }

            _state.value = RuntimeState(
                phase = RuntimePhase.PREPARING,
                detail = "Preparing OpenRouter runtime",
                profile = profile,
            )
            return RuntimePreparation(
                ready = true,
                detail = "OpenRouter runtime prepared.",
                profile = profile,
            ).also {
                _state.value = RuntimeState(
                    phase = RuntimePhase.READY,
                    detail = it.detail,
                    profile = profile,
                )
            }
        }

    override suspend fun warmUp(): WarmUpReport =
        mutex.withLock {
            val detail = "OpenRouter runtime ready."
            _state.value = _state.value.copy(
                phase = RuntimePhase.READY,
                detail = detail,
            )
            return WarmUpReport(
                success = true,
                detail = detail,
            )
        }

    override suspend fun plan(input: PlannerInput): PlanDraft =
        mutex.withLock {
            Log.d(
                OPENROUTER_TAG,
                "plan start model=${config.modelName} goal=${input.goal.take(120)}",
            )
            _state.value = _state.value.copy(
                phase = RuntimePhase.PREPARING,
                detail = "Requesting OpenRouter plan",
            )
            val prompt = buildPrompt(input)
            val response = requestCompletion(
                systemInstruction = SYSTEM_INSTRUCTION,
                userPrompt = prompt,
                maxTokens = 900,
            )
            _state.value = _state.value.copy(
                phase = RuntimePhase.READY,
                detail = "OpenRouter plan ready",
            )
            try {
                val plan = validatePlan(
                    input = input,
                    rawOutput = response,
                )
                Log.d(
                    OPENROUTER_TAG,
                    "plan ready model=${config.modelName} chars=${response.length}",
                )
                plan
            } catch (validationError: Throwable) {
                Log.w(
                    OPENROUTER_TAG,
                    "plan validation failed model=${config.modelName} error=${validationError.message}",
                )
                Log.w(
                    OPENROUTER_TAG,
                    "invalid plan output=${response.take(1_200)}",
                )
                val repairedResponse = requestCompletion(
                    systemInstruction = SYSTEM_INSTRUCTION,
                    userPrompt = buildRepairPrompt(prompt, response, validationError.message.orEmpty()),
                    maxTokens = 900,
                )
                val repairedPlan =
                    try {
                        validatePlan(
                            input = input,
                            rawOutput = repairedResponse,
                        )
                    } catch (repairError: Throwable) {
                        Log.w(
                            OPENROUTER_TAG,
                            "repaired plan validation failed model=${config.modelName} error=${repairError.message}",
                        )
                        Log.w(
                            OPENROUTER_TAG,
                            "invalid repaired plan output=${repairedResponse.take(1_200)}",
                        )
                        throw repairError
                    }
                Log.d(
                    OPENROUTER_TAG,
                    "plan repaired model=${config.modelName} chars=${repairedResponse.length}",
                )
                repairedPlan
            }
        }

    private suspend fun requestCompletion(
        systemInstruction: String,
        userPrompt: String,
        maxTokens: Int,
    ): String = withContext(Dispatchers.IO) {
        Log.d(
            OPENROUTER_TAG,
            "requestCompletion model=${config.modelName} promptChars=${userPrompt.length} maxTokens=$maxTokens",
        )
        val requestBody = JSONObject()
            .put("model", config.modelName)
            .put("temperature", 0.1)
            .put("top_p", 0.9)
            .put("max_tokens", maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemInstruction),
                    ).put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userPrompt),
                    ),
            )

        val connection = URL(config.endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = OPENROUTER_CONNECT_TIMEOUT_MS
        connection.readTimeout = OPENROUTER_READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("HTTP-Referer", config.appReferer)
        connection.setRequestProperty("X-Title", config.appTitle)

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }

            val statusCode = connection.responseCode
            val responseText =
                (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { input ->
                        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
                    }.orEmpty()

            if (statusCode !in 200..299) {
                val errorMessage = parseRemoteError(responseText)
                Log.w(
                    OPENROUTER_TAG,
                    "request failed model=${config.modelName} status=$statusCode message=$errorMessage",
                )
                if (statusCode == 429) {
                    error("OpenRouter rate limited ${config.modelName}. Retry later.")
                }
                error("OpenRouter HTTP $statusCode: $errorMessage")
            }

            Log.d(
                OPENROUTER_TAG,
                "request ok model=${config.modelName} status=$statusCode responseChars=${responseText.length}",
            )
            extractResponseText(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRemoteError(responseText: String): String {
        return runCatching {
            val root = JSONObject(responseText)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
                ?: responseText
        }.getOrDefault(responseText.ifBlank { "Unknown OpenRouter error." })
    }

    private fun extractResponseText(responseText: String): String =
        runCatching {
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices") ?: error("OpenRouter returned no choices.")
            val message = choices.getJSONObject(0).optJSONObject("message") ?: error("OpenRouter returned no message.")
            when (val content = message.opt("content")) {
                is String -> content
                is JSONArray -> buildString {
                    for (index in 0 until content.length()) {
                        val item = content.opt(index)
                        if (item is JSONObject) {
                            append(item.optString("text"))
                        } else if (item is String) {
                            append(item)
                        }
                    }
                }

                else -> error("Unsupported OpenRouter content payload.")
            }
        }.getOrElse {
            error("OpenRouter response parsing failed: ${it.message ?: "unknown"}")
        }

    private fun buildPrompt(input: PlannerInput): String =
        JSONObject()
            .put("goal", input.goal.take(160))
            .put("planningMode", input.planningMode.name.lowercase())
            .put("stepBudget", input.stepBudget.coerceIn(1, 6))
            .put("priorPlanSummary", input.priorPlanSummary?.take(240).orEmpty())
            .put("foregroundPackage", input.foregroundPackage?.take(80).orEmpty())
            .put("lastExternalForegroundPackage", input.lastExternalForegroundPackage?.take(80).orEmpty())
            .put("candidateApps", compactApps(input.candidateApps))
            .put("recentActionHistory", JSONArray(input.recentActionHistory.takeLast(10).map { it.take(160) }))
            .put("appMemory", JSONArray(input.appMemory.take(8).map { it.take(260) }))
            .put("appSkillGuidance", JSONArray(input.appSkillGuidance.take(4).map { it.take(1_200) }))
            .put("riskHints", JSONArray(input.riskHints.take(6).map { it.take(120) }))
            .put("visibleNodes", compactNodes(input.serializedNodeTree))
            .toString()
            .take(OPENROUTER_MAX_PROMPT_CHARS)

    private fun compactApps(apps: List<AppCandidate>): JSONArray =
        JSONArray().apply {
            apps.take(8).forEach { candidate ->
                put(
                    JSONObject()
                        .put("label", candidate.label.take(40))
                        .put("packageName", candidate.packageName.take(96)),
                )
            }
        }

    private fun compactNodes(serializedNodeTree: String): JSONArray =
        JSONArray().apply {
            serializedNodeTree.lineSequence()
                .map(::compactNodeLine)
                .filter { it.isNotBlank() }
                .distinct()
                .take(24)
                .forEach { put(it) }
        }

    private fun compactNodeLine(line: String): String =
        line.split(" | ")
            .mapNotNull { segment ->
                val parts = segment.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val key = parts[0]
                val value = parts[1].trim()
                if (value.isBlank()) return@mapNotNull null
                when (key) {
                    "ref" -> "ref=${value.take(8)}"
                    "role" -> "r=${value.take(16)}"
                    "text" -> "t=${value.take(32)}"
                    "desc" -> "d=${value.take(32)}"
                    "id" -> "id=${value.substringAfterLast('/').take(28)}"
                    "class" -> "c=${value.substringAfterLast('.').take(20)}"
                    "package" -> "p=${value.take(48)}"
                    "editable" -> value.takeIf { it == "true" }?.let { "e=true" }
                    "clickable" -> value.takeIf { it == "true" }?.let { "k=true" }
                    "idx" -> "idx=${value.take(32)}"
                    "bounds" -> "b=${value.take(32)}"
                    else -> null
                }
            }.joinToString(separator = "|")

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
                                action = parseAction(rawStep, input),
                                expectedObservation = rawStep.optString(
                                    "expectedObservation",
                                    "Observe the requested UI transition.",
                                ),
                            ),
                        )

                    is String -> {
                        val action = parseStringStep(rawStep)
                        add(
                            ExecutionStep(
                                action = action,
                                expectedObservation = "Observe the requested UI transition.",
                            ),
                        )
                    }

                    else -> error("Unsupported step payload at index $index.")
                }
            }
        }
        val steps = normalizeGoalSpecificSteps(input, parsedSteps)
        validateGoalSpecificConstraints(input.goal, input.candidateApps, steps)
        val runtimeRisk = root.optString("riskLevel", RiskLevel.LOW.name)
            .let { value -> runCatching { RiskLevel.valueOf(value.uppercase()) }.getOrDefault(RiskLevel.LOW) }
        val policyDecision = policyGate.evaluate(input.goal, steps.map { it.action }, runtimeRisk)
        val deferPolicyConfirmation = shouldDeferPolicyConfirmation(input, steps)
        val effectiveNeedsConfirmation = policyDecision.needsConfirmation && !deferPolicyConfirmation
        val gatedSteps =
            if (effectiveNeedsConfirmation && steps.none { it.action is AgentAction.ConfirmUser }) {
                steps + ExecutionStep(
                    action = AgentAction.ConfirmUser(policyDecision.reason),
                    expectedObservation = "Execution pauses until the user confirms.",
                )
            } else {
                steps
            }
        val finalSteps = enforceCurrentScreenRiskGate(
            input = input,
            steps = adaptStepsToCurrentProgress(input, gatedSteps),
        )
        return PlanDraft(
            summary = root.opt("summary")?.toString()?.takeIf { it.isNotBlank() } ?: "Remote plan ready",
            steps = finalSteps,
            riskLevel = runtimeRisk,
            needsConfirmation = effectiveNeedsConfirmation || root.optBoolean("needsConfirmation", false),
            targetPackageCandidates = root.optJSONArray("targetPackageCandidates")
                ?.let(::jsonArrayToStrings)
                .orEmpty(),
            rawModelOutput = rawOutput,
            rawPlanJson = buildNormalizedPlanJson(
                root = root,
                steps = finalSteps,
                needsConfirmation = effectiveNeedsConfirmation || root.optBoolean("needsConfirmation", false),
            ),
        )
    }

    private fun enforceCurrentScreenRiskGate(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val lowerGoal = input.goal.lowercase()
        if (isPlayStoreInstallGoal(lowerGoal, input) &&
            !playStoreInstallConfirmationGranted(input)
        ) {
            val query = playStoreSearchQueryFor(extractPlayStoreInstallQuery(input.goal) ?: "Claude")
            val directPackageId = playStoreDirectPackageFor(query)
            val installSelector = playStoreInstallSelector(input.serializedNodeTree)
            if (installSelector != null && playStoreTargetAppVisible(query, input.serializedNodeTree)) {
                return playStoreInstallConfirmationSteps(installSelector, directPackageId)
            }
            if (steps.size == 1 && steps.single().action is AgentAction.Stop) {
                return normalizePlayStoreInstallPlan(input, emptyList())
            }
        }
        return steps
    }

    private fun shouldDeferPolicyConfirmation(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): Boolean {
        val lowerGoal = input.goal.lowercase()
        if (isPlayStoreInstallGoal(lowerGoal, input)) {
            if (steps.any { it.action is AgentAction.ConfirmUser }) {
                return false
            }
            val query = playStoreSearchQueryFor(extractPlayStoreInstallQuery(input.goal) ?: "Claude")
            val installVisibleOnTarget =
                playStoreInstallSelector(input.serializedNodeTree) != null &&
                    playStoreTargetAppVisible(query, input.serializedNodeTree)
            return !installVisibleOnTarget
        }
        if (!isKorailTalkTrainSearchGoal(lowerGoal, input) || !goalRequestsBooking(lowerGoal)) {
            return false
        }
        if (korailBookingConfirmationGranted(input)) {
            return true
        }
        if (steps.any { it.action is AgentAction.ConfirmUser }) {
            return false
        }
        return !korailResultsVisible(input.serializedNodeTree)
    }

    private fun normalizeGoalSpecificSteps(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val lowerGoal = input.goal.lowercase()
        return when {
            isChromeSearchGoal(lowerGoal) -> normalizeChromeSearchPlan(input.goal, steps)
            isSamsungBluetoothSettingsGoal(lowerGoal, steps) -> normalizeSamsungBluetoothSettingsPlan(steps)
            isSamsungWifiSettingsGoal(lowerGoal) -> normalizeSamsungWifiSettingsPlan(steps)
            isSamsungSettingsSearchGoal(lowerGoal, steps) -> normalizeSamsungSettingsSearchPlan(input.goal, steps)
            isSamsungClockAlarmGoal(lowerGoal, steps) -> normalizeSamsungClockAlarmPlan(steps)
            isSamsungContactsSearchGoal(lowerGoal, steps) -> normalizeSamsungContactsSearchPlan(input, steps)
            isSamsungMessagesSendGoal(lowerGoal, steps) -> normalizeSamsungMessagesSendPlan(input.goal, steps)
            isSamsungCameraGoal(lowerGoal, steps) -> normalizeSamsungCameraPlan(input.goal, steps)
            isPlayStoreInstallGoal(lowerGoal, input) -> normalizePlayStoreInstallPlan(input, steps)
            isKorailTalkTrainSearchGoal(lowerGoal, input) -> normalizeKorailTalkTrainSearchPlan(input, steps)
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
        val confirmIndex = window.indexOfFirst { it.action is AgentAction.ConfirmUser }
        if (confirmIndex >= 0 && window.size < trimmedSteps.size) {
            var includeIndex = window.size
            while (includeIndex < trimmedSteps.size) {
                val action = trimmedSteps[includeIndex].action
                window += trimmedSteps[includeIndex]
                includeIndex += 1
                if (action !is AgentAction.WaitForNode &&
                    action !is AgentAction.WaitForApp &&
                    action !is AgentAction.WaitForCondition
                ) {
                    break
                }
            }
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
            is AgentAction.OpenUri -> false
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
            selector.resourceId?.let { if (!resourceIdMatches(it, segments["id"])) return@any false }
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

    private fun resourceIdMatches(
        expected: String,
        actual: String?,
    ): Boolean {
        if (actual.isNullOrBlank()) return false
        val expectedLower = expected.trim().lowercase()
        val actualLower = actual.trim().lowercase()
        return actualLower == expectedLower ||
            actualLower.endsWith("/$expectedLower") ||
            actualLower.endsWith(":id/$expectedLower")
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

    private fun normalizeSamsungWifiSettingsPlan(steps: List<ExecutionStep>): List<ExecutionStep> {
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
                    text = SAMSUNG_SETTINGS_WIFI_LABEL,
                ),
                expectedObservation = "Wi-Fi query appears in Settings search.",
            ),
            ExecutionStep(
                action = AgentAction.SubmitInput(searchFieldSelector),
                expectedObservation = "Settings search results update for Wi-Fi.",
            ),
            ExecutionStep(
                action = AgentAction.Stop,
                expectedObservation = "Wi-Fi/network search results are visible.",
            ),
        )
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
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val query = steps.map { it.action }.filterIsInstance<AgentAction.InputText>().lastOrNull()?.text
            ?: extractSearchQuery(input.goal)
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
        val inContacts = input.foregroundPackage == SAMSUNG_CONTACTS_PACKAGE ||
            input.lastExternalForegroundPackage == SAMSUNG_CONTACTS_PACKAGE
        val searchFieldVisible = selectorVisible(searchFieldSelector, input.serializedNodeTree)
        val searchButtonVisible = selectorVisible(searchButtonSelector, input.serializedNodeTree)
        return buildList {
            if (!inContacts) {
                add(launchStep)
                add(waitForAppStep)
                return@buildList
            }
            if (!searchFieldVisible) {
                if (!searchButtonVisible) {
                    add(
                        ExecutionStep(
                            action = AgentAction.WaitForNode(searchButtonSelector),
                            expectedObservation = "Contacts search button is visible.",
                        ),
                    )
                }
                add(
                    ExecutionStep(
                        action = AgentAction.Tap(
                            selector = searchButtonSelector,
                            label = "Open Contacts search",
                        ),
                        expectedObservation = "Contacts search field opens.",
                    ),
                )
                add(
                    ExecutionStep(
                        action = AgentAction.WaitForNode(searchFieldSelector),
                        expectedObservation = "Contacts search field is visible and focused.",
                    ),
                )
            }
            add(
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = searchFieldSelector,
                        text = query,
                    ),
                    expectedObservation = "The search query appears in Contacts.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.SubmitInput(searchFieldSelector),
                    expectedObservation = "Contacts search results update for the query.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "Contacts search results are visible.",
                ),
            )
        }
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

    private fun normalizeSamsungCameraPlan(
        goal: String,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == SAMSUNG_CAMERA_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(SAMSUNG_CAMERA_PACKAGE),
                    expectedObservation = "Camera launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == SAMSUNG_CAMERA_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(SAMSUNG_CAMERA_PACKAGE),
                    expectedObservation = "Camera becomes foreground.",
                )
        if (!goalRequestsPhotoCapture(goal.lowercase())) {
            return listOf(
                launchStep,
                waitForAppStep,
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "Samsung Camera is open in the foreground.",
                ),
            )
        }

        val shutterSelector = NodeSelector(
            contentDescription = SAMSUNG_CAMERA_SHUTTER_DESC,
            resourceId = SAMSUNG_CAMERA_SHUTTER_ID,
            packageName = SAMSUNG_CAMERA_PACKAGE,
        )
        return listOf(
            launchStep,
            waitForAppStep,
            ExecutionStep(
                action = AgentAction.WaitForNode(shutterSelector),
                expectedObservation = "Camera shutter button is visible.",
            ),
            ExecutionStep(
                action = AgentAction.ConfirmUser("User confirmation required before taking a photo."),
                expectedObservation = "Execution pauses before camera capture.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = shutterSelector,
                    label = "Take photo",
                ),
                expectedObservation = "The camera shutter is pressed.",
            ),
        )
    }

    private fun normalizePlayStoreInstallPlan(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val query = playStoreSearchQueryFor(extractPlayStoreInstallQuery(input.goal) ?: "Claude")
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == PLAY_STORE_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(PLAY_STORE_PACKAGE),
                    expectedObservation = "Google Play Store launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == PLAY_STORE_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(PLAY_STORE_PACKAGE),
                    expectedObservation = "Google Play Store becomes foreground.",
                )
        val tree = input.serializedNodeTree
        val inPlayStore = input.foregroundPackage == PLAY_STORE_PACKAGE ||
            tree.lineSequence().any { line -> "package=$PLAY_STORE_PACKAGE" in line }
        val directPackageId = playStoreDirectPackageFor(query)
        if (!inPlayStore) {
            if (directPackageId != null) {
                return playStoreDirectListingSteps(directPackageId)
            }
            return listOf(launchStep, waitForAppStep)
        }

        val installSelector = playStoreInstallSelector(tree)
        if (installSelector != null && playStoreTargetAppVisible(query, tree)) {
            return playStoreInstallConfirmationSteps(installSelector, directPackageId)
        }

        if (directPackageId != null && !playStoreDirectListingAlreadyOpened(directPackageId, input)) {
            return playStoreDirectListingSteps(directPackageId)
        }

        val searchFieldSelector = NodeSelector(
            className = "android.widget.EditText",
            editable = true,
            packageName = PLAY_STORE_PACKAGE,
        )
        if (playStoreSearchFieldVisible(tree)) {
            val searchSuggestionSelector = playStoreExactSearchSuggestionSelector(query)
            return listOf(
                ExecutionStep(
                    action = AgentAction.ClearText(searchFieldSelector),
                    expectedObservation = "The Google Play search field is ready for the requested app name.",
                ),
                ExecutionStep(
                    action = AgentAction.InputText(
                        selector = searchFieldSelector,
                        text = query,
                    ),
                    expectedObservation = "The requested app name appears in Google Play search.",
                ),
                ExecutionStep(
                    action = AgentAction.WaitForNode(searchSuggestionSelector),
                    expectedObservation = "The exact Google Play search suggestion is visible.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = searchSuggestionSelector,
                        label = "Search Google Play for $query",
                    ),
                    expectedObservation = "Google Play shows the requested app result or detail page.",
                ),
            )
        }

        val resultSelector = playStoreResultSelector(query, tree)
        if (resultSelector != null) {
            return listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(resultSelector),
                    expectedObservation = "The requested app result is visible in Google Play.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = resultSelector,
                        label = "Open $query result",
                    ),
                    expectedObservation = "The app detail page opens.",
                ),
            )
        }

        val searchBarSelector = playStoreSearchBarSelector(tree)
        if (searchBarSelector != null) {
            return listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(searchBarSelector),
                    expectedObservation = "The Google Play search bar is visible.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = searchBarSelector,
                        label = "Focus Google Play search",
                    ),
                    expectedObservation = "The Google Play search field opens.",
                ),
                ExecutionStep(
                    action = AgentAction.WaitForNode(searchFieldSelector),
                    expectedObservation = "The Google Play search field is visible.",
                ),
            )
        }

        val searchTabSelector = playStoreSearchTabSelector(tree)
        val defaultSearchBarSelector = NodeSelector(
            contentDescription = PLAY_STORE_SEARCH_DESC_KO,
            packageName = PLAY_STORE_PACKAGE,
        )
        return listOf(
            ExecutionStep(
                action = AgentAction.WaitForNode(searchTabSelector),
                expectedObservation = "The Google Play search tab is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = searchTabSelector,
                    label = "Open Google Play search",
                ),
                expectedObservation = "The Google Play search field opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(defaultSearchBarSelector),
                expectedObservation = "The Google Play search bar is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = defaultSearchBarSelector,
                    label = "Focus Google Play search",
                ),
                expectedObservation = "The Google Play search field opens.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(searchFieldSelector),
                expectedObservation = "The Google Play search field is visible.",
            ),
        )
    }

    private fun normalizeKorailTalkTrainSearchPlan(
        input: PlannerInput,
        steps: List<ExecutionStep>,
    ): List<ExecutionStep> {
        val launchStep =
            steps.firstOrNull { (it.action as? AgentAction.LaunchApp)?.packageName == KORAIL_TALK_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.LaunchApp(KORAIL_TALK_PACKAGE),
                    expectedObservation = "KorailTalk launches.",
                )
        val waitForAppStep =
            steps.firstOrNull { (it.action as? AgentAction.WaitForApp)?.packageName == KORAIL_TALK_PACKAGE }
                ?: ExecutionStep(
                    action = AgentAction.WaitForApp(KORAIL_TALK_PACKAGE),
                    expectedObservation = "KorailTalk becomes foreground.",
                )
        val inKorailTalk = input.foregroundPackage == KORAIL_TALK_PACKAGE ||
            input.lastExternalForegroundPackage == KORAIL_TALK_PACKAGE
        if (!inKorailTalk) {
            return listOf(launchStep, waitForAppStep)
        }

        val tree = input.serializedNodeTree
        if (goalRequestsBooking(input.goal.lowercase()) && korailReservationOptionSelected(input)) {
            return korailBookingButtonSteps(includeConfirm = false)
        }

        if (goalRequestsBooking(input.goal.lowercase()) && korailBookingButtonVisible(tree)) {
            return korailBookingButtonSteps(includeConfirm = !korailBookingConfirmationGranted(input))
        }

        if (korailResultsVisible(tree)) {
            if (goalRequestsBooking(input.goal.lowercase())) {
                return korailBookingConfirmationSteps()
            }
            return listOf(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "KorailTalk train results are visible.",
                ),
            )
        }

        val stationSelector = NodeSelector(
            resourceId = KORAIL_STATION_NAME_ID,
            packageName = KORAIL_TALK_PACKAGE,
        )
        if (korailStationSheetVisible(tree)) {
            val targetStation = if (korailDepartureIsDaegu(tree)) "서울" else "동대구"
            return listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(stationSelector.copy(text = targetStation)),
                    expectedObservation = "$targetStation station option is visible.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = stationSelector.copy(text = targetStation),
                        label = "Select $targetStation station",
                    ),
                    expectedObservation = "$targetStation is selected.",
                ),
            )
        }

        if (!korailDepartureIsDaegu(tree)) {
            val departureSelector = NodeSelector(
                resourceId = KORAIL_DEPARTURE_STATION_ID,
                packageName = KORAIL_TALK_PACKAGE,
            )
            return listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(departureSelector),
                    expectedObservation = "KorailTalk departure station field is visible.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = departureSelector,
                        label = "Open departure station picker",
                    ),
                    expectedObservation = "Departure station picker opens.",
                ),
            )
        }

        if (!korailArrivalIsSeoul(tree)) {
            val arrivalSelector = NodeSelector(
                resourceId = KORAIL_ARRIVAL_STATION_ID,
                packageName = KORAIL_TALK_PACKAGE,
            )
            return listOf(
                ExecutionStep(
                    action = AgentAction.WaitForNode(arrivalSelector),
                    expectedObservation = "KorailTalk arrival station field is visible.",
                ),
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = arrivalSelector,
                        label = "Open arrival station picker",
                    ),
                    expectedObservation = "Arrival station picker opens.",
                ),
            )
        }

        val searchSelector = NodeSelector(
            text = "열차조회",
            resourceId = KORAIL_TRAIN_SEARCH_BUTTON_ID,
            packageName = KORAIL_TALK_PACKAGE,
        )
        val resultsSelector = NodeSelector(
            text = "열차 조회",
            resourceId = KORAIL_RESULTS_TITLE_ID,
            packageName = KORAIL_TALK_PACKAGE,
        )
        val lookupSteps = listOf(
            ExecutionStep(
                action = AgentAction.WaitForNode(searchSelector),
                expectedObservation = "KorailTalk train search button is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = searchSelector,
                    label = "Search trains",
                ),
                expectedObservation = "KorailTalk submits the read-only train lookup.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(resultsSelector),
                expectedObservation = "KorailTalk train results screen appears.",
            ),
        )
        if (goalRequestsBooking(input.goal.lowercase())) {
            return lookupSteps + korailBookingConfirmationSteps()
        }
        return lookupSteps + ExecutionStep(
            action = AgentAction.Stop,
            expectedObservation = "Evening train options are visible.",
        )
    }

    private fun korailBookingConfirmationSteps(): List<ExecutionStep> {
        val reservationSelector = NodeSelector(
            contentDescription = KORAIL_STANDARD_AVAILABLE_FARE_TEXT,
            resourceId = KORAIL_STANDARD_RESERVE_BUTTON_ID,
            packageName = KORAIL_TALK_PACKAGE,
            clickable = true,
        )
        return listOf(
            ExecutionStep(
                action = AgentAction.ConfirmUser(KORAIL_BOOKING_CONFIRM_REASON),
                expectedObservation = "Execution pauses before selecting a train reservation option.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(reservationSelector),
                expectedObservation = "A KorailTalk reservation option is visible.",
            ),
            ExecutionStep(
                action = AgentAction.Tap(
                    selector = reservationSelector,
                    label = "Open KorailTalk reservation",
                ),
                expectedObservation = "KorailTalk enters the guarded reservation step.",
            ),
        ) + korailBookingButtonSteps(includeConfirm = false)
    }

    private fun korailBookingButtonSteps(includeConfirm: Boolean): List<ExecutionStep> {
        val bookingButtonSelector = NodeSelector(
            text = "예매",
            resourceId = KORAIL_BOOKING_BUTTON_ID,
            packageName = KORAIL_TALK_PACKAGE,
            clickable = true,
        )
        return buildList {
            if (includeConfirm) {
                add(
                    ExecutionStep(
                        action = AgentAction.ConfirmUser(KORAIL_BOOKING_CONFIRM_REASON),
                        expectedObservation = "Execution pauses before continuing from KorailTalk booking.",
                    ),
                )
            }
            add(
                ExecutionStep(
                    action = AgentAction.WaitForNode(bookingButtonSelector),
                    expectedObservation = "KorailTalk shows the final reservation button for the selected train.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = bookingButtonSelector,
                        label = "Continue KorailTalk booking",
                    ),
                    expectedObservation = "KorailTalk proceeds from the reservation confirmation sheet.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "Reservation entry was attempted; stop before login or payment handling.",
                ),
            )
        }
    }

    private fun buildNormalizedPlanJson(
        root: JSONObject,
        steps: List<ExecutionStep>,
        needsConfirmation: Boolean,
    ): String {
        val normalized = JSONObject().apply {
            put("summary", root.optString("summary", "Remote plan ready"))
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

            is AgentAction.OpenUri ->
                JSONObject()
                    .put("type", "open_uri")
                    .put("uri", action.uri)
                    .apply {
                        action.packageName?.let { packageName -> put("packageName", packageName) }
                    }

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

    private fun parseAction(stepJson: JSONObject, input: PlannerInput): AgentAction {
        val payload = actionPayload(stepJson)
        val type = actionType(stepJson, payload)
        return when (type) {
            "launch_app" -> AgentAction.LaunchApp(
                payload.optionalPackageName(input)
                    ?: error("Missing required string: packageName/package/package_name"),
            )
            "wait_for_app" -> AgentAction.WaitForApp(
                packageName = payload.optionalPackageName(input)
                    ?: error("Missing required string: packageName/package/package_name"),
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
        val payload = JSONObject(stepJson.toString())
        val nested = stepJson.optJSONObject("action") ?: stepJson.optJSONObject("step")
        nested?.keys()?.forEach { key -> payload.put(key, nested.get(key)) }
        val embeddedActionKey = embeddedActionKey(stepJson)
        val embeddedAction = embeddedActionKey?.let { stepJson.optJSONObject(it) }
        embeddedAction?.keys()?.forEach { key -> payload.put(key, embeddedAction.get(key)) }
        embeddedActionKey?.let { payload.put("type", it.toActionType()) }
        val parameters = stepJson.optJSONObject("parameters")
            ?: stepJson.optJSONObject("params")
            ?: stepJson.optJSONObject("arguments")
        parameters?.keys()?.forEach { key -> payload.put(key, parameters.get(key)) }
        return payload
    }

    private fun actionType(stepJson: JSONObject, payload: JSONObject): String {
        if (payload.has("stop")) return "stop"
        val direct = payload.firstNonBlankString("type", "actionType", "action_type", "kind", "name")
        if (direct != null) return direct.toActionType()
        val action = stepJson.opt("action")
        if (action is String && action.isNotBlank()) return action.toActionType()
        embeddedActionKey(stepJson)?.let { return it.toActionType() }
        error("Step missing action type. keys=${stepJson.keys().asSequence().toList().joinToString(",")}")
    }

    private fun embeddedActionKey(stepJson: JSONObject): String? =
        stepJson.keys().asSequence().firstOrNull { key ->
            key.toActionType() in SUPPORTED_ACTION_TYPES && stepJson.optJSONObject(key) != null
        }

    private fun JSONObject.firstNonBlankString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }

    private fun JSONObject.requiredString(vararg keys: String): String =
        firstNonBlankString(*keys) ?: error("Missing required string: ${keys.joinToString("/")}")

    private fun JSONObject.optionalPackageName(input: PlannerInput): String? =
        firstNonBlankString("packageName", "package", "package_name")
            ?: inferPackageNameFromCandidate(input, this)

    private fun inferPackageNameFromCandidate(
        input: PlannerInput,
        payload: JSONObject,
    ): String? {
        if (input.candidateApps.size == 1) return input.candidateApps.first().packageName
        val appHint = payload.firstNonBlankString("appName", "app", "label", "target", "targetApp")
            ?.lowercase()
            .orEmpty()
        if (appHint.isNotBlank()) {
            input.candidateApps.firstOrNull { candidate ->
                candidate.label.lowercase() in appHint ||
                    appHint in candidate.label.lowercase() ||
                    candidate.packageName.lowercase() in appHint
            }?.let { return it.packageName }
        }
        val lowerGoal = input.goal.lowercase()
        if (listOf("my files", "files", "내 파일", "파일").any { it in lowerGoal }) {
            input.candidateApps.firstOrNull { it.packageName == SAMSUNG_MYFILES_PACKAGE }?.let { return it.packageName }
        }
        return null
    }

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
            contentDescription = json.firstNonBlankString(
                "contentDescription",
                "content_description",
                "desc",
                "description",
            ),
            resourceId = json.firstNonBlankString("resourceId", "resource_id", "viewId", "view_id", "id"),
            className = json.firstNonBlankString("className", "class_name", "class"),
            editable = json.takeIf { it.has("editable") }?.optBoolean("editable"),
            clickable = json.takeIf { it.has("clickable") }?.optBoolean("clickable"),
            packageName = json.firstNonBlankString("packageName", "package_name", "package"),
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

    private fun buildRepairPrompt(
        originalPrompt: String,
        invalidOutput: String,
        validationError: String,
    ): String =
        JSONObject()
            .put("task", "repair_invalid_android_accessibility_plan")
            .put("requirements", "Return STRICT JSON ONLY. Keep the same user goal. Analyze the current visibleNodes and recentActionHistory before planning. summary must describe the current screen situation and the next checkpoint. Never output a long full-flow script. Return only the next small horizon of steps, at most originalPrompt.stepBudget steps unless confirm_user is immediately followed by its guarded action. Continue from the current screen instead of restarting from the top when progress is already visible. Emit stop when the goal or the next safe checkpoint is already satisfied. launch_app and wait_for_app MUST include packageName and MUST use only package names listed in originalPrompt.candidateApps. Every tap, input_text, submit_input, clear_text, wait_for_node, and assert_visible step MUST include a selector object with at least one identifying field. Prefer stable text/contentDescription/resourceId selectors; if visibleNodes only expose idx=0.1 style paths, emit selector.indexPath as [0,1], and if visibleNodes expose b=left,top,right,bottom, emit selector.boundsHint with those four fields. press_global action must be one of BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS. Do not use HOME, RECENTS, NOTIFICATIONS, or QUICK_SETTINGS unless the user explicitly requested that system surface. If the goal is to search in Chrome, use selector resourceId com.android.chrome:id/url_bar for input_text and submit_input, and set input_text.text to the exact query from the goal. If the goal is to open Bluetooth settings in Samsung Settings package com.android.settings, tap contentDescription 설정 검색 first, type Bluetooth into com.android.settings.intelligence:id/search_src_text, then tap the Bluetooth result. If the goal is to search in Samsung Settings package com.android.settings, open the homepage search button with contentDescription 설정 검색, then wait for and type into com.android.settings.intelligence:id/search_src_text. If the goal is to search in Samsung Contacts package com.samsung.android.app.contacts, tap com.samsung.android.app.contacts:id/menu_search before waiting for or typing into com.samsung.android.app.contacts:id/search_src_text. If the goal is to enter the Alarm tab in Samsung Clock package com.sec.android.app.clockpackage, use selector text 알람 with resourceId com.sec.android.app.clockpackage:id/title for the tab, then assert com.sec.android.app.clockpackage:id/alarm_main_layout is visible. If the goal is to open Samsung Camera package com.sec.android.app.camera, launch it, wait_for_app, then stop; do not wait for com.sec.android.app.camera:id/camera_viewfinder. For camera preview evidence use com.sec.android.app.camera:id/camera_preview only if it is visible. If the goal is to take a photo, require confirm_user before tapping shutter selector resourceId com.sec.android.app.camera:id/normal_center_button with contentDescription 사진 촬영. If the goal is to install or download an app through Google Play Store package com.android.vending, use the appSkillGuidance Play Store procedure: open Search/검색, type the requested app name into the editable search field, submit, then require confirm_user before tapping 설치 or Install. Stop before payment, login, account, or permission prompts. If the goal is to send a message in Samsung Messages package com.samsung.android.messaging, tap com.samsung.android.messaging:id/fab, then tap com.samsung.android.messaging:id/chat_fab, type the recipient into com.samsung.android.messaging:id/search_src_text, tap com.samsung.android.messaging:id/chat_with_button, use com.samsung.android.messaging:id/message_edit_text for message body, and stop at confirm_user before com.samsung.android.messaging:id/send_button. Booking, reservation, order, checkout, 예매, 예약, 주문, and payment actions must require confirm_user before the app enters the commit step.")
            .put("validationError", validationError.take(240))
            .put("originalPrompt", originalPrompt.take(OPENROUTER_MAX_PROMPT_CHARS / 2))
            .put("invalidOutput", invalidOutput.take(OPENROUTER_MAX_PROMPT_CHARS / 2))
            .toString()

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
            if (inputAction != null) {
                if (tapAction != null) {
                    require(tapAction.selector.resourceId == SAMSUNG_CONTACTS_SEARCH_BUTTON_ID) {
                        "Samsung Contacts search plans must tap $SAMSUNG_CONTACTS_SEARCH_BUTTON_ID first."
                    }
                    require(waitForNodeAction?.selector?.resourceId == SAMSUNG_CONTACTS_SEARCH_TEXT_ID) {
                        "Samsung Contacts search plans must wait for $SAMSUNG_CONTACTS_SEARCH_TEXT_ID."
                    }
                }
                require(inputAction.selector.resourceId == SAMSUNG_CONTACTS_SEARCH_TEXT_ID) {
                    "Samsung Contacts search input_text must target $SAMSUNG_CONTACTS_SEARCH_TEXT_ID."
                }
                extractSearchQuery(goal)?.let { query ->
                    require(inputAction.text.equals(query, ignoreCase = true)) {
                        "Samsung Contacts search input_text must use the exact query '$query'."
                    }
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
        if (isSamsungCameraGoal(lowerGoal, steps)) {
            val badCameraSelector = steps.map { it.action }.any { action ->
                when (action) {
                    is AgentAction.WaitForNode -> action.selector.resourceId == "com.sec.android.app.camera:id/camera_viewfinder"
                    is AgentAction.AssertVisible -> action.selector.resourceId == "com.sec.android.app.camera:id/camera_viewfinder"
                    is AgentAction.Tap -> action.selector.resourceId == "com.sec.android.app.camera:id/camera_viewfinder"
                    else -> false
                }
            }
            require(!badCameraSelector) {
                "Samsung Camera plans must not use nonexistent selector com.sec.android.app.camera:id/camera_viewfinder."
            }
            if (goalRequestsPhotoCapture(lowerGoal)) {
                val tapActions = steps.map { it.action }.filterIsInstance<AgentAction.Tap>()
                val confirmIndex = steps.indexOfFirst { it.action is AgentAction.ConfirmUser }
                val shutterTapIndex = steps.indexOfFirst { step ->
                    val tap = step.action as? AgentAction.Tap ?: return@indexOfFirst false
                    tap.selector.resourceId == SAMSUNG_CAMERA_SHUTTER_ID
                }
                require(confirmIndex >= 0) {
                    "Samsung Camera capture plans must include confirm_user before pressing the shutter."
                }
                require(shutterTapIndex > confirmIndex) {
                    "Samsung Camera capture plans must tap $SAMSUNG_CAMERA_SHUTTER_ID after confirm_user."
                }
                require(tapActions.lastOrNull()?.selector?.contentDescription == SAMSUNG_CAMERA_SHUTTER_DESC) {
                    "Samsung Camera capture plans must use contentDescription '$SAMSUNG_CAMERA_SHUTTER_DESC'."
                }
            } else {
                require(steps.none { it.action is AgentAction.Tap }) {
                    "Samsung Camera open-only plans must stop after launch_app/wait_for_app without tapping controls."
                }
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

    private fun isSamsungWifiSettingsGoal(lowerGoal: String): Boolean =
        ("settings" in lowerGoal || "설정" in lowerGoal) &&
            (
                "wi-fi" in lowerGoal ||
                    "wifi" in lowerGoal ||
                    "network" in lowerGoal ||
                    "네트워크" in lowerGoal ||
                    "와이파이" in lowerGoal
            )

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

    private fun isSamsungCameraGoal(
        lowerGoal: String,
        steps: List<ExecutionStep>,
    ): Boolean =
        listOf("camera", "카메라").any { it in lowerGoal } ||
            goalRequestsPhotoCapture(lowerGoal) ||
            steps.any { step ->
                when (val action = step.action) {
                    is AgentAction.LaunchApp -> action.packageName == SAMSUNG_CAMERA_PACKAGE
                    is AgentAction.WaitForApp -> action.packageName == SAMSUNG_CAMERA_PACKAGE
                    is AgentAction.WaitForNode -> action.selector.packageName == SAMSUNG_CAMERA_PACKAGE ||
                        action.selector.resourceId?.startsWith("$SAMSUNG_CAMERA_PACKAGE:id/") == true
                    is AgentAction.AssertVisible -> action.selector.packageName == SAMSUNG_CAMERA_PACKAGE ||
                        action.selector.resourceId?.startsWith("$SAMSUNG_CAMERA_PACKAGE:id/") == true
                    is AgentAction.Tap -> action.selector.packageName == SAMSUNG_CAMERA_PACKAGE ||
                        action.selector.resourceId?.startsWith("$SAMSUNG_CAMERA_PACKAGE:id/") == true
                    else -> false
                }
            }

    private fun isPlayStoreInstallGoal(
        lowerGoal: String,
        input: PlannerInput,
    ): Boolean {
        val asksForInstall = listOf("install", "download", "설치", "다운로드").any { it in lowerGoal }
        val referencesPlayStore = listOf("play store", "google play", "플레이 스토어", "플레이스토어").any { it in lowerGoal } ||
            input.candidateApps.any { it.packageName == PLAY_STORE_PACKAGE }
        return asksForInstall && referencesPlayStore
    }

    private fun isKorailTalkTrainSearchGoal(
        lowerGoal: String,
        input: PlannerInput,
    ): Boolean {
        val referencesKorail = listOf("코레일톡", "korail", "ktx").any { it in lowerGoal } ||
            input.candidateApps.any { it.packageName == KORAIL_TALK_PACKAGE }
        val asksForTrainLookup = listOf("check", "show", "search", "lookup", "find", "조회", "확인").any { it in lowerGoal } ||
            "ktx" in lowerGoal
        return referencesKorail && asksForTrainLookup
    }

    private fun korailStationSheetVisible(tree: String): Boolean =
        tree.lineSequence().any { line -> KORAIL_STATION_SEARCH_ID in line }

    private fun korailResultsVisible(tree: String): Boolean =
        korailLineContains(tree, KORAIL_RESULTS_TITLE_ID, "열차 조회") &&
            korailLineContains(tree, "com.korail.talk:id/departureTxt", "동대구") &&
            korailLineContains(tree, "com.korail.talk:id/arrivalTxt", "서울") &&
            tree.lineSequence().any { line ->
                "id=com.korail.talk:id/trainNameTxt" in line &&
                    ("KTX" in line || "ktx" in line)
            }

    private fun korailBookingButtonVisible(tree: String): Boolean =
        korailLineContains(tree, KORAIL_BOOKING_BUTTON_ID, "예매")

    private fun korailBookingConfirmationGranted(input: PlannerInput): Boolean =
        input.recentActionHistory.any { it == "confirm_user:$KORAIL_BOOKING_CONFIRM_REASON" } ||
            korailReservationOptionSelected(input)

    private fun korailReservationOptionSelected(input: PlannerInput): Boolean =
        input.recentActionHistory.any { history ->
            history.startsWith("tap:") && "id=$KORAIL_STANDARD_RESERVE_BUTTON_ID" in history
        }

    private fun korailDepartureIsDaegu(tree: String): Boolean =
        korailLineContains(tree, KORAIL_DEPARTURE_TEXT_ID, "동대구") ||
            korailLineContains(tree, KORAIL_DEPARTURE_TEXT_ID, "대구")

    private fun korailArrivalIsSeoul(tree: String): Boolean =
        korailLineContains(tree, KORAIL_ARRIVAL_TEXT_ID, "서울")

    private fun korailLineContains(
        tree: String,
        resourceId: String,
        text: String,
    ): Boolean =
        tree.lineSequence().any { line ->
            "id=$resourceId" in line && "text=$text" in line
        }

    private fun playStoreSearchFieldVisible(tree: String): Boolean =
        tree.lineSequence().any { line ->
            "package=$PLAY_STORE_PACKAGE" in line &&
                "class=android.widget.EditText" in line &&
                "editable=true" in line
        }

    private fun playStoreInstallSelector(tree: String): NodeSelector? {
        val installText = when {
            playStoreLineContainsText(tree, PLAY_STORE_INSTALL_TEXT_KO) -> PLAY_STORE_INSTALL_TEXT_KO
            playStoreLineContainsText(tree, PLAY_STORE_INSTALL_TEXT_EN) -> PLAY_STORE_INSTALL_TEXT_EN
            else -> return null
        }
        return NodeSelector(
            text = installText,
            packageName = PLAY_STORE_PACKAGE,
        )
    }

    private fun playStoreDirectListingSteps(packageId: String): List<ExecutionStep> {
        val uri = "market://details?id=$packageId"
        val installSelector = NodeSelector(
            text = PLAY_STORE_INSTALL_TEXT_KO,
            packageName = PLAY_STORE_PACKAGE,
        )
        return listOf(
            ExecutionStep(
                action = AgentAction.OpenUri(
                    uri = uri,
                    packageName = PLAY_STORE_PACKAGE,
                ),
                expectedObservation = "Google Play opens the exact app listing.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForApp(PLAY_STORE_PACKAGE),
                expectedObservation = "Google Play Store becomes foreground.",
            ),
            ExecutionStep(
                action = AgentAction.WaitForNode(
                    selector = installSelector,
                    timeoutMs = 10_000L,
                ),
                expectedObservation = "The Google Play install button is visible on the exact listing.",
            ),
        )
    }

    private fun playStoreDirectListingAlreadyOpened(
        packageId: String,
        input: PlannerInput,
    ): Boolean {
        val key = AgentAction.OpenUri(
            uri = "market://details?id=$packageId",
            packageName = PLAY_STORE_PACKAGE,
        ).historyKey()
        return key in input.recentActionHistory
    }

    private fun playStoreInstallConfirmationSteps(
        installSelector: NodeSelector,
        directPackageId: String? = null,
    ): List<ExecutionStep> =
        buildList {
            add(
                ExecutionStep(
                    action = AgentAction.ConfirmUser(PLAY_STORE_INSTALL_CONFIRM_REASON),
                    expectedObservation = "Execution pauses before installing an app.",
                ),
            )
            if (directPackageId != null) {
                add(
                    ExecutionStep(
                        action = AgentAction.OpenUri(
                            uri = "market://details?id=$directPackageId",
                            packageName = PLAY_STORE_PACKAGE,
                        ),
                        expectedObservation = "Google Play returns to the exact app listing after confirmation.",
                    ),
                )
                add(
                    ExecutionStep(
                        action = AgentAction.WaitForApp(PLAY_STORE_PACKAGE),
                        expectedObservation = "Google Play Store becomes foreground.",
                    ),
                )
            }
            add(
                ExecutionStep(
                    action = AgentAction.WaitForNode(installSelector),
                    expectedObservation = "The Google Play install button is visible.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Tap(
                        selector = installSelector,
                        label = "Install app from Google Play",
                    ),
                    expectedObservation = "Google Play starts installing the selected app.",
                ),
            )
            add(
                ExecutionStep(
                    action = AgentAction.Stop,
                    expectedObservation = "Install was started or requested; stop before login, payment, permission, or account prompts.",
                ),
            )
        }

    private fun playStoreInstallConfirmationGranted(input: PlannerInput): Boolean =
        input.recentActionHistory.any { it == "confirm_user:$PLAY_STORE_INSTALL_CONFIRM_REASON" }

    private fun playStoreSearchTabSelector(tree: String): NodeSelector =
        when {
            playStoreLineContainsText(tree, PLAY_STORE_SEARCH_TAB_KO) ->
                NodeSelector(text = PLAY_STORE_SEARCH_TAB_KO, packageName = PLAY_STORE_PACKAGE)
            playStoreLineContainsText(tree, PLAY_STORE_SEARCH_TAB_EN) ->
                NodeSelector(text = PLAY_STORE_SEARCH_TAB_EN, packageName = PLAY_STORE_PACKAGE)
            playStoreLineContainsText(tree, PLAY_STORE_SEARCH_DESC_KO) ->
                NodeSelector(contentDescription = PLAY_STORE_SEARCH_DESC_KO, packageName = PLAY_STORE_PACKAGE)
            else ->
                NodeSelector(text = PLAY_STORE_SEARCH_TAB_KO, packageName = PLAY_STORE_PACKAGE)
        }

    private fun playStoreSearchBarSelector(tree: String): NodeSelector? =
        when {
            playStoreLineContainsText(tree, PLAY_STORE_SEARCH_DESC_KO) ->
                NodeSelector(contentDescription = PLAY_STORE_SEARCH_DESC_KO, packageName = PLAY_STORE_PACKAGE)
            playStoreLineContainsText(tree, PLAY_STORE_SEARCH_PLACEHOLDER_KO) ->
                NodeSelector(text = PLAY_STORE_SEARCH_PLACEHOLDER_KO, packageName = PLAY_STORE_PACKAGE)
            else -> null
        }

    private fun playStoreExactSearchSuggestionSelector(query: String): NodeSelector =
        NodeSelector(
            contentDescription = "\"${query.trim()}\" 검색 ",
            packageName = PLAY_STORE_PACKAGE,
        )

    private fun playStoreResultSelector(
        query: String,
        tree: String,
    ): NodeSelector? {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return null
        val queryTokens = normalizedQuery.split(' ').filter { it.length > 2 }.map { it.lowercase() }
        val resultLine = tree.lineSequence().firstOrNull { line ->
            val lowerLine = line.lowercase()
            "desc=" in lowerLine &&
                !playStoreLooksLikeSearchSuggestion(lowerLine) &&
                playStoreLineMatchesQueryTokens(lowerLine, queryTokens)
        } ?: return null
        val description = resultLine
            .split(" | ")
            .firstOrNull { it.startsWith("desc=") }
            ?.removePrefix("desc=")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return NodeSelector(
            contentDescription = description.take(80),
            packageName = PLAY_STORE_PACKAGE,
        )
    }

    private fun playStoreTargetAppVisible(
        query: String,
        tree: String,
    ): Boolean {
        val queryTokens = query
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
        if (queryTokens.isEmpty()) return false
        return tree.lineSequence().any { line ->
            val lowerLine = line.lowercase()
            ("desc=" in lowerLine || "text=" in lowerLine) &&
                !playStoreLooksLikeSearchSuggestion(lowerLine) &&
                playStoreLineMatchesQueryTokens(lowerLine, queryTokens)
        }
    }

    private fun playStoreLineMatchesQueryTokens(
        lowerLine: String,
        queryTokens: List<String>,
    ): Boolean =
        when {
            queryTokens.isEmpty() -> false
            queryTokens.size == 1 -> queryTokens.single() in lowerLine
            else -> queryTokens.all { token -> token in lowerLine }
        }

    private fun playStoreLooksLikeSearchSuggestion(lowerLine: String): Boolean =
        "\" " in lowerLine && "검색" in lowerLine

    private fun playStoreLineContainsText(
        tree: String,
        text: String,
    ): Boolean =
        tree.lineSequence().any { line ->
            "text=$text" in line ||
                "desc=$text" in line
        }

    private fun goalRequestsPhotoCapture(lowerGoal: String): Boolean =
        listOf(
            "take a picture",
            "take picture",
            "take a photo",
            "take photo",
            "capture a photo",
            "capture photo",
            "snap a photo",
            "press the shutter",
            "press shutter",
            "shutter",
            "사진 찍",
            "사진을 찍",
            "사진 촬영",
            "촬영",
        ).any { it in lowerGoal }

    private fun goalRequestsBooking(lowerGoal: String): Boolean =
        listOf(
            "book",
            "booking",
            "reserve",
            "reservation",
            "order",
            "checkout",
            "예매",
            "예약",
            "주문",
        ).any { it in lowerGoal }

    private fun extractSearchQuery(goal: String): String? {
        val searchMatch = Regex("(?i)search(?:\\s+for)?\\s+(.+)$").find(goal)?.groupValues?.getOrNull(1)
        if (!searchMatch.isNullOrBlank()) return cleanExtractedSearchQuery(searchMatch)
        val koreanMatch = Regex("검색\\s+(.+)$").find(goal)?.groupValues?.getOrNull(1)
        return koreanMatch?.let(::cleanExtractedSearchQuery)
    }

    private fun extractPlayStoreInstallQuery(goal: String): String? {
        Regex("(?i)\\b(?:install|download)\\b").findAll(goal).lastOrNull()?.let { match ->
            cleanExtractedPlayStoreQuery(goal.substring(match.range.last + 1))?.let { return it }
        }
        Regex("(?:설치|다운로드)").findAll(goal).lastOrNull()?.let { match ->
            cleanExtractedPlayStoreQuery(goal.substring(match.range.last + 1))?.let { return it }
        }
        val patterns = listOf(
            Regex("(?i)install\\s+(.+?)(?:\\s+(?:from|in|on)\\s+(?:google\\s+play|play\\s+store).*)?$"),
            Regex("(?i)download\\s+(.+?)(?:\\s+(?:from|in|on)\\s+(?:google\\s+play|play\\s+store).*)?$"),
            Regex("(?i)(?:google\\s+play|play\\s+store).*?(?:install|download)\\s+(.+)$"),
            Regex("(.+?)(?:을|를)?\\s*(?:플레이\\s*스토어|구글\\s*플레이).*?(?:설치|다운로드)"),
            Regex("(?:설치|다운로드)\\s+(.+?)(?:\\s*(?:플레이\\s*스토어|구글\\s*플레이).*)?$"),
        )
        return patterns
            .asSequence()
            .mapNotNull { regex ->
                regex.find(goal)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let(::cleanExtractedPlayStoreQuery)
            }
            .firstOrNull()
    }

    private fun cleanExtractedPlayStoreQuery(value: String): String? =
        value
            .trim()
            .trim('"', '\'')
            .replace(Regex("(?i)\\b(app|application)\\b"), "")
            .replace(Regex("(?i)\\s+(then|and|but|without|stop)\\b.*$"), "")
            .replace(Regex("(?i)\\s+(from|in|on)\\s+(google\\s+play|play\\s+store).*$"), "")
            .replace(Regex("\\s*(앱|어플|애플리케이션)$"), "")
            .trimEnd('.', '?', '!', '。')
            .trim()
            .takeIf { it.isNotBlank() }

    private fun playStoreSearchQueryFor(query: String): String =
        PLAY_STORE_SEARCH_QUERY_ALIASES[query.trim().lowercase()] ?: query.trim()

    private fun playStoreDirectPackageFor(query: String): String? =
        PLAY_STORE_DIRECT_PACKAGE_ALIASES[query.trim().lowercase()]

    private fun cleanExtractedSearchQuery(value: String): String? =
        value
            .trim()
            .trim('"', '\'')
            .replace(Regex("(?i)\\s+(read only|do not|don't|but|without)\\b.*$"), "")
            .trimEnd('.', '?', '!', '。')
            .trim()
            .takeIf { it.isNotBlank() }

    private fun extractSendRecipient(goal: String): String? =
        sequenceOf(
            Regex("(?i)\\bto\\s+(.+?)\\s+saying\\b"),
            Regex("(?i)\\bto\\s+([^\"']+?)(?:\\s+and\\s+send|\\s*$)"),
        ).mapNotNull { regex ->
            regex.find(goal)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }
        }.firstOrNull()

    private fun extractSendMessage(goal: String): String? =
        sequenceOf(
            Regex("(?i)\\bsaying\\s+(.+?)(?:,?\\s+but\\b|\\.\\s*$|$)"),
            Regex("(?i)send\\s+(.+?)\\s+to\\s+"),
        ).mapNotNull { regex ->
            regex.find(goal)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }
        }.firstOrNull()

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
                when (val value = array.opt(index)) {
                    is String -> value
                    is JSONObject -> value.optString("packageName")
                    else -> null
                }?.takeIf { it.isNotBlank() }?.let(::add)
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

    internal companion object {
        const val SYSTEM_INSTRUCTION: String =
            "You are an Android accessibility planning engine. " +
                "Analyze the current visible UI first, then plan. " +
                "Reply STRICT JSON ONLY with keys summary,riskLevel,needsConfirmation,targetPackageCandidates,steps. " +
                "summary must describe the current screen situation and the next checkpoint. " +
                "Supported step types: launch_app,wait_for_app,wait_for_node,tap,input_text,submit_input,clear_text,scroll,press_global,assert_visible,confirm_user,stop. " +
                "Use visibleNodes,foregroundPackage,lastExternalForegroundPackage,recentActionHistory,appMemory,appSkillGuidance,planningMode,priorPlanSummary,and stepBudget to understand current progress. " +
                "appMemory contains prior success/failure notes for the target app; use it to avoid repeating failed selectors/packages, but current visibleNodes are still the source of truth. " +
                "appSkillGuidance contains advisory app-use procedures and learned skill notes; use it to choose selectors and recovery strategy, but current visibleNodes are source of truth. " +
                "Never execute a skill directly; always emit strict JSON DSL actions only. " +
                "PDF skill aliases map to DSL only: snapshotScreen/read screen means use visibleNodes; findElement means create a selector; clickElement means tap; fillField means clear_text then input_text; scrollView means scroll; getText means observe visibleNodes/assert_visible; takeScreenshot is QA-only and not a runtime action; replayScript is not supported in runtime plans. " +
                "Do not emit a long end-to-end script. Return only the next small horizon of steps from the current screen, at most stepBudget steps unless confirm_user must be followed by its guarded action. " +
                "When the current screen already shows progress in the flow, continue from that point instead of restarting from the top. " +
                "Emit stop when the current screen already satisfies the goal or the next safe checkpoint. " +
                "launch_app and wait_for_app must include packageName and use only package names listed in candidateApps. " +
                "If you use press_global, action must be one of BACK,HOME,RECENTS,NOTIFICATIONS,QUICK_SETTINGS. " +
                "Do not use HOME,RECENTS,NOTIFICATIONS,QUICK_SETTINGS unless the user explicitly requested that system surface. " +
                "Every tap,input_text,submit_input,clear_text,wait_for_node,assert_visible step must include a non-empty selector object. " +
                "Visible nodes may include ref=@eN, r=role, idx=0.1, and b=left,top,right,bottom; ref is for reasoning only, idx maps to selector.indexPath, and b maps to selector.boundsHint when stable text/contentDescription/resourceId is unavailable. " +
                "If the goal is to search in Chrome, use selector resourceId com.android.chrome:id/url_bar for input_text and submit_input, and use the exact query from the goal text. " +
                "If the goal is to open Bluetooth settings in Samsung Settings package com.android.settings, tap contentDescription 설정 검색 first, type Bluetooth into com.android.settings.intelligence:id/search_src_text, then tap the Bluetooth result. " +
                "If the goal is to search in Samsung Settings package com.android.settings, tap contentDescription 설정 검색 first, then wait for and type into com.android.settings.intelligence:id/search_src_text. " +
                "If the goal is to enter the Alarm tab in Samsung Clock package com.sec.android.app.clockpackage, use selector text 알람 with resourceId com.sec.android.app.clockpackage:id/title for the tab, then assert com.sec.android.app.clockpackage:id/alarm_main_layout. " +
                "If the goal is to search in Samsung Contacts package com.samsung.android.app.contacts, tap com.samsung.android.app.contacts:id/menu_search before waiting for or typing into com.samsung.android.app.contacts:id/search_src_text. " +
                "If the goal is to open Samsung Camera package com.sec.android.app.camera, launch_app com.sec.android.app.camera, wait_for_app com.sec.android.app.camera, then stop; do not wait for com.sec.android.app.camera:id/camera_viewfinder. Use com.sec.android.app.camera:id/camera_preview only as visible preview evidence when it appears. If the goal is to take a photo, require confirm_user before tapping shutter selector resourceId com.sec.android.app.camera:id/normal_center_button with contentDescription 사진 촬영. " +
                "If the goal is to install or download an app through Google Play Store package com.android.vending, use the appSkillGuidance Play Store procedure: open Search/검색, type the requested app name into the editable search field, submit, then require confirm_user before tapping 설치 or Install. Stop before payment, login, account, or permission prompts. " +
                "If the goal is to send a message in Samsung Messages package com.samsung.android.messaging, tap com.samsung.android.messaging:id/fab, then tap com.samsung.android.messaging:id/chat_fab, type the recipient into com.samsung.android.messaging:id/search_src_text, tap com.samsung.android.messaging:id/chat_with_button, use com.samsung.android.messaging:id/message_edit_text for message body, then require confirm_user before com.samsung.android.messaging:id/send_button. " +
                "Dangerous actions such as send,pay,delete,post,share,install,permission,take photo,press shutter,book,reserve,order,checkout,예매,예약,주문 must require confirm_user."
    }
}
