package com.guribbong.phoneappagent.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.guribbong.phoneappagent.driver.accessibility.UiNodeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccessibilityServiceHealth {
    DISCONNECTED,
    CONNECTED,
    RECONNECT_REQUIRED,
}

data class AccessibilitySnapshot(
    val enabled: Boolean = false,
    val serviceHealth: AccessibilityServiceHealth = AccessibilityServiceHealth.DISCONNECTED,
    val foregroundPackage: String? = null,
    val lastExternalForegroundPackage: String? = null,
    val topNodeLabel: String? = null,
    val nodeCount: Int = 0,
    val lastEvent: String = "idle",
    val visibleNodes: List<UiNodeSnapshot> = emptyList(),
    val lastError: String? = null,
)

internal object AccessibilityBridge {
    private val _snapshot = MutableStateFlow(AccessibilitySnapshot())
    val snapshot = _snapshot.asStateFlow()

    fun markConnected() {
        _snapshot.update { current ->
            current.copy(
                enabled = true,
                serviceHealth = AccessibilityServiceHealth.CONNECTED,
                lastError = null,
            )
        }
    }

    fun markDisconnected() {
        _snapshot.update { current ->
            current.copy(
                serviceHealth = AccessibilityServiceHealth.DISCONNECTED,
                lastError = "Accessibility service disconnected.",
            )
        }
    }

    fun update(
        foregroundPackage: String?,
        lastExternalForegroundPackage: String?,
        topNodeLabel: String?,
        nodeCount: Int,
        lastEvent: String,
        visibleNodes: List<UiNodeSnapshot>,
        lastError: String? = null,
    ) {
        _snapshot.value = AccessibilitySnapshot(
            enabled = true,
            serviceHealth = AccessibilityServiceHealth.CONNECTED,
            foregroundPackage = foregroundPackage,
            lastExternalForegroundPackage = lastExternalForegroundPackage,
            topNodeLabel = topNodeLabel,
            nodeCount = nodeCount,
            lastEvent = lastEvent,
            visibleNodes = visibleNodes,
            lastError = lastError,
        )
    }
}

interface AccessibilityStatusRepository {
    val snapshot: StateFlow<AccessibilitySnapshot>

    fun refreshState()

    fun openAccessibilitySettings()

    fun setOverlayEnabled(enabled: Boolean)
}

class AndroidAccessibilityStatusRepository(
    private val context: Context,
) : AccessibilityStatusRepository {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val componentName = ComponentName(appContext, AgentAccessibilityService::class.java)
    private val _snapshot = MutableStateFlow(currentSnapshot())

    override val snapshot: StateFlow<AccessibilitySnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            AccessibilityBridge.snapshot.collectLatest {
                _snapshot.value = currentSnapshot()
            }
        }
    }

    override fun refreshState() {
        _snapshot.value = currentSnapshot()
    }

    override fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    override fun setOverlayEnabled(enabled: Boolean) {
        AccessibilityOverlayBridge.setEnabled(enabled)
    }

    private fun currentSnapshot(): AccessibilitySnapshot {
        val serviceEnabled = isServiceEnabled()
        val bridgeSnapshot = AccessibilityBridge.snapshot.value
        val serviceHealth =
            when {
                serviceEnabled && bridgeSnapshot.serviceHealth != AccessibilityServiceHealth.CONNECTED -> {
                    AccessibilityServiceHealth.RECONNECT_REQUIRED
                }

                serviceEnabled -> AccessibilityServiceHealth.CONNECTED
                else -> AccessibilityServiceHealth.DISCONNECTED
            }
        return bridgeSnapshot.copy(
            enabled = serviceEnabled,
            serviceHealth = serviceHealth,
            lastError =
                if (serviceHealth == AccessibilityServiceHealth.RECONNECT_REQUIRED) {
                    "Accessibility is enabled in settings, but the service needs reconnection."
                } else {
                    bridgeSnapshot.lastError
                },
        )
    }

    private fun isServiceEnabled(): Boolean {
        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
            ) == 1
        }.getOrDefault(false)

        if (!accessibilityEnabled) {
            return false
        }

        val enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        return enabledServices
            .split(':')
            .mapNotNull { entry -> ComponentName.unflattenFromString(entry) }
            .any { enabledComponent ->
                enabledComponent.packageName == componentName.packageName &&
                    enabledComponent.className == componentName.className
            }
    }
}
