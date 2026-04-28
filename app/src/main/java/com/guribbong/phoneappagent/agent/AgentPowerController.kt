package com.guribbong.phoneappagent.agent

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

private const val ACTIVE_RUN_WAKE_LOCK_MS = 30 * 60 * 1000L

class AgentPowerController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)
    private var activeRunWakeLock: PowerManager.WakeLock? = null

    fun blockingReason(): String? {
        val screenOff = powerManager?.isInteractive == false
        val locked = isDeviceLocked()
        return when {
            screenOff && locked -> "Unlock the device and keep the screen on before starting the agent."
            screenOff -> "Turn the screen on before starting the agent."
            locked -> "Unlock the device before starting the agent."
            else -> null
        }
    }

    fun runtimeBlocker(observedPackage: String?): String? {
        if (observedPackage != "com.android.systemui") return null
        val screenOff = powerManager?.isInteractive == false
        val locked = isDeviceLocked()
        return when {
            screenOff && locked -> "Device locked and screen turned off during execution."
            screenOff -> "Screen turned off during execution."
            locked -> "Device locked during execution."
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    fun acquireForActiveRun() {
        val manager = powerManager ?: return
        val wakeLock =
            activeRunWakeLock ?: manager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "${appContext.packageName}:agent-run",
            ).apply {
                setReferenceCounted(false)
            }.also { activeRunWakeLock = it }
        if (!wakeLock.isHeld) {
            wakeLock.acquire(ACTIVE_RUN_WAKE_LOCK_MS)
        }
    }

    fun releaseAfterActiveRun() {
        val wakeLock = activeRunWakeLock ?: return
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private fun isDeviceLocked(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            keyguardManager?.isDeviceLocked == true
        } else {
            @Suppress("DEPRECATION")
            keyguardManager?.isKeyguardLocked == true
        }
}
