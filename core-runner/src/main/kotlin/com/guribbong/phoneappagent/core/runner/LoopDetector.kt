package com.guribbong.phoneappagent.core.runner

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.historyKey

data class LoopDetectionResult(
    val triggered: Boolean,
    val repeatCount: Int,
    val packageName: String,
    val screenSummary: String,
    val actionHistoryKey: String,
)

class LoopDetector(
    private val repeatThreshold: Int = DEFAULT_REPEAT_THRESHOLD,
) {
    private var lastKey: LoopKey? = null
    private var repeatCount: Int = 0

    init {
        require(repeatThreshold > 0) { "repeatThreshold must be positive." }
    }

    fun record(
        packageName: String?,
        screenSummary: String,
        action: AgentAction,
    ): LoopDetectionResult {
        val key = LoopKey(
            packageName = packageName.orEmpty().trim(),
            screenSummary = screenSummary.trim(),
            actionHistoryKey = action.historyKey(),
        )

        repeatCount = if (key == lastKey) {
            repeatCount + 1
        } else {
            1
        }
        lastKey = key

        return LoopDetectionResult(
            triggered = repeatCount >= repeatThreshold,
            repeatCount = repeatCount,
            packageName = key.packageName,
            screenSummary = key.screenSummary,
            actionHistoryKey = key.actionHistoryKey,
        )
    }

    fun reset() {
        lastKey = null
        repeatCount = 0
    }

    private data class LoopKey(
        val packageName: String,
        val screenSummary: String,
        val actionHistoryKey: String,
    )

    private companion object {
        const val DEFAULT_REPEAT_THRESHOLD = 3
    }
}
