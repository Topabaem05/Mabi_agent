package com.guribbong.phoneappagent.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AgentServiceController(
    private val context: Context,
) {
    fun startGoal(goal: String) {
        sendCommand(AgentForegroundService.ACTION_START) {
            putExtra(AgentForegroundService.EXTRA_GOAL, goal)
        }
    }

    fun pause() {
        sendCommand(AgentForegroundService.ACTION_PAUSE)
    }

    fun resume() {
        sendCommand(AgentForegroundService.ACTION_RESUME)
    }

    fun stop() {
        sendCommand(AgentForegroundService.ACTION_STOP)
    }

    fun confirm() {
        sendCommand(AgentForegroundService.ACTION_CONFIRM)
    }

    private fun sendCommand(
        action: String,
        configure: Intent.() -> Unit = {},
    ) {
        val intent = Intent(context, AgentForegroundService::class.java)
            .setAction(action)
            .apply(configure)
        ContextCompat.startForegroundService(context, intent)
    }
}
