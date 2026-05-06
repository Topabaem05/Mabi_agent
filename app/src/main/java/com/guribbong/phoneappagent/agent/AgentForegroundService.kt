package com.guribbong.phoneappagent.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.guribbong.phoneappagent.MainActivity
import com.guribbong.phoneappagent.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AgentForegroundService : Service() {
    private val orchestrator: AgentOrchestrator by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        val notification = buildNotification(orchestrator.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scope.launch {
            orchestrator.state.collectLatest { state ->
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(state))
                if (state.phase == AgentRunPhase.WAITING_FOR_CONFIRM) {
                    notificationManager?.notify(CONFIRMATION_NOTIFICATION_ID, buildConfirmationNotification(state))
                } else {
                    notificationManager?.cancel(CONFIRMATION_NOTIFICATION_ID)
                }
                if (state.phase == AgentRunPhase.COMPLETED || state.phase == AgentRunPhase.FAILED) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notificationManager?.cancel(CONFIRMATION_NOTIFICATION_ID)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> orchestrator.startGoal(intent.getStringExtra(EXTRA_GOAL).orEmpty())
            ACTION_PAUSE -> orchestrator.pause()
            ACTION_RESUME -> orchestrator.resume()
            ACTION_CONFIRM -> {
                getSystemService(NotificationManager::class.java)?.cancel(CONFIRMATION_NOTIFICATION_ID)
                orchestrator.confirmAndContinue()
            }
            ACTION_STOP -> {
                orchestrator.stop()
                getSystemService(NotificationManager::class.java)?.cancel(CONFIRMATION_NOTIFICATION_ID)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: AgentOrchestratorState) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (state.phase == AgentRunPhase.WAITING_FOR_CONFIRM) {
                    getString(R.string.agent_confirmation_title)
                } else {
                    getString(R.string.agent_notification_title)
                },
            )
            .setContentText(notificationDetail(state))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(
                state.phase != AgentRunPhase.COMPLETED &&
                    state.phase != AgentRunPhase.FAILED,
            )
            .setContentIntent(openAppPendingIntent())
            .setPriority(
                if (state.phase == AgentRunPhase.WAITING_FOR_CONFIRM) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_LOW
                },
            )
            .apply {
                if (state.phase == AgentRunPhase.WAITING_FOR_CONFIRM) {
                    addAction(
                        0,
                        getString(R.string.agent_action_confirm),
                        servicePendingIntent(ACTION_CONFIRM),
                    )
                } else {
                    addAction(
                        0,
                        getString(
                            if (state.phase == AgentRunPhase.PAUSED) {
                                R.string.agent_action_resume
                            } else {
                                R.string.agent_action_pause
                            },
                        ),
                        servicePendingIntent(
                            if (state.phase == AgentRunPhase.PAUSED) ACTION_RESUME else ACTION_PAUSE,
                        ),
                    )
                    addAction(
                        0,
                        getString(R.string.agent_action_return),
                        openAppPendingIntent(),
                    )
                }
                addAction(
                    0,
                    getString(R.string.agent_action_stop),
                    servicePendingIntent(ACTION_STOP),
                )
            }
            .build()

    private fun buildConfirmationNotification(state: AgentOrchestratorState) =
        NotificationCompat.Builder(this, CONFIRMATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.agent_confirmation_title))
            .setContentText(notificationDetail(state))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(
                0,
                getString(R.string.agent_action_confirm),
                servicePendingIntent(ACTION_CONFIRM),
            )
            .addAction(
                0,
                getString(R.string.agent_action_stop),
                servicePendingIntent(ACTION_STOP),
            )
            .build()

    private fun notificationDetail(state: AgentOrchestratorState): String =
        if (state.phase == AgentRunPhase.WAITING_FOR_CONFIRM) {
            state.detail.ifBlank { "The agent is paused before the next risky step." }
        } else {
            "${state.phase.name.lowercase()} • ${state.detail}"
        }

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun servicePendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AgentForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.agent_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        if (manager.getNotificationChannel(CONFIRMATION_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CONFIRMATION_CHANNEL_ID,
                    getString(R.string.agent_confirmation_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    companion object {
        const val ACTION_START = "com.guribbong.phoneappagent.agent.START"
        const val ACTION_PAUSE = "com.guribbong.phoneappagent.agent.PAUSE"
        const val ACTION_RESUME = "com.guribbong.phoneappagent.agent.RESUME"
        const val ACTION_CONFIRM = "com.guribbong.phoneappagent.agent.CONFIRM"
        const val ACTION_STOP = "com.guribbong.phoneappagent.agent.STOP"
        const val EXTRA_GOAL = "goal"

        private const val CHANNEL_ID = "agent_run"
        private const val CONFIRMATION_CHANNEL_ID = "agent_confirmation"
        private const val NOTIFICATION_ID = 7101
        private const val CONFIRMATION_NOTIFICATION_ID = 7102

        fun start(
            context: Context,
            goal: String,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_GOAL, goal),
            )
        }
    }
}
