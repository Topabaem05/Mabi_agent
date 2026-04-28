package com.guribbong.phoneappagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.data.history.ChatSessionSummary
import com.guribbong.phoneappagent.overlay.ActiveAgentBadge
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppRoot(
    initialPrompt: String? = null,
    initialPromptNonce: Int = 0,
    autoQueueInitialPrompt: Boolean = false,
    viewModel: MainViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.bootstrapIfNeeded()
        viewModel.refreshAccessibility()
    }

    LaunchedEffect(initialPromptNonce) {
        viewModel.bootstrapIfNeeded()
        if (!initialPrompt.isNullOrBlank()) {
            if (autoQueueInitialPrompt) {
                viewModel.runIncomingPrompt(initialPrompt)
            } else {
                viewModel.applyIncomingPrompt(initialPrompt)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibility()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight(),
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "History",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent planning sessions",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionCard(session = session)
                    }
                }
            }
        },
    ) {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ),
                        ),
                    )
                    .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusHeader(
                        status = state.status,
                        modelName = state.modelName,
                        backend = state.backend,
                        phaseLabel = state.phaseLabel,
                        overlayEnabled = state.overlayEnabled,
                        onDrawerClick = { scope.launch { drawerState.open() } },
                        onOverlayToggle = viewModel::toggleOverlay,
                    )
                    AccessibilityDriverCard(
                        enabled = state.accessibilityEnabled,
                        health = state.accessibilityHealth,
                        foregroundApp = state.foregroundApp,
                        lastExternalForegroundApp = state.lastExternalForegroundApp,
                        topNodeLabel = state.topNodeLabel,
                        nodeCount = state.nodeCount,
                        lastEvent = state.lastAccessibilityEvent,
                        onOpenSettings = viewModel::openAccessibilitySettings,
                        onRefresh = viewModel::refreshAccessibility,
                    )
                    PlannerSurface(
                        summary = state.planSummary,
                        planSteps = state.planSteps,
                        currentStepIndex = state.currentStepIndex,
                        needsConfirmation = state.needsConfirmation,
                        policyReason = state.policyReason,
                    )
                    ComposerPanel(
                        text = state.composer,
                        onValueChange = viewModel::onComposerChanged,
                        onQueueTask = viewModel::queueTask,
                        onPauseOrResume = viewModel::pauseOrResume,
                        onStop = viewModel::stopExecution,
                        onConfirm = viewModel::confirmExecution,
                        canPause = state.canPause,
                        canResume = state.canResume,
                        canStop = state.canStop,
                        canConfirm = state.canConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilityDriverCard(
    enabled: Boolean,
    health: String,
    foregroundApp: String?,
    lastExternalForegroundApp: String?,
    topNodeLabel: String?,
    nodeCount: Int,
    lastEvent: String,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Accessibility Driver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (enabled) {
                            "Live foreground capture is active."
                        } else {
                            "Enable service before cross-app planning."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (enabled) health else "Off") },
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
                ),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Foreground app",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = foregroundApp ?: "No foreground package captured yet",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Last external app: ${lastExternalForegroundApp ?: "waiting"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Top node: ${topNodeLabel ?: "waiting"} • nodes: $nodeCount • event: $lastEvent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (enabled) "Open access settings" else "Enable access")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("Refresh state")
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(
    status: String,
    modelName: String,
    backend: String,
    phaseLabel: String,
    overlayEnabled: Boolean,
    onDrawerClick: () -> Unit,
    onOverlayToggle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "phone_app_agent",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Foreground Android agent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onDrawerClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open history",
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text(status) })
                AssistChip(onClick = {}, label = { Text(modelName) })
                AssistChip(onClick = {}, label = { Text(phaseLabel) })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActiveAgentBadge(
                    label = backend,
                    accent = if (overlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = onOverlayToggle) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (overlayEnabled) "Hide overlay" else "Show overlay")
                }
            }
        }
    }
}

@Composable
private fun PlannerSurface(
    summary: String,
    planSteps: List<ExecutionStep>,
    currentStepIndex: Int,
    needsConfirmation: Boolean,
    policyReason: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(30.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Planner Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(if (needsConfirmation) "Confirm gate" else "Remote runtime")
                    },
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    planSteps.forEachIndexed { index, step ->
                        val isCurrent = index == currentStepIndex
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${index + 1}. ${step.action.describe()}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = step.expectedObservation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(policyReason)
                }
            }
        }
    }
}

@Composable
private fun ComposerPanel(
    text: String,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    canPause: Boolean,
    canResume: Boolean,
    canStop: Boolean,
    canConfirm: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Task Composer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = text,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(24.dp),
                placeholder = {
                    Text("Describe cross-app goal. Planner must turn it into safe DSL.")
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onQueueTask,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Queue task")
                }
                OutlinedButton(
                    onClick = onPauseOrResume,
                    enabled = canPause || canResume,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(
                        imageVector = if (canResume) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (canResume) "Resume" else "Pause")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("Confirm and run")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = canStop,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ChatSessionSummary) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${session.appName} • ${session.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = session.updatedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun AgentAction.describe(): String =
    when (this) {
        is AgentAction.LaunchApp -> "Launch ${packageName.ifBlank { "target app" }}"
        is AgentAction.WaitForApp -> "Wait for ${packageName.ifBlank { "target app" }}"
        is AgentAction.WaitForNode -> "Wait for ${selector.label()}"
        is AgentAction.Tap -> "Tap ${label.ifBlank { selector.label() }}"
        is AgentAction.InputText -> "Input text into ${selector.label()}"
        is AgentAction.SubmitInput -> "Submit ${selector.label()}"
        is AgentAction.ClearText -> "Clear ${selector.label()}"
        is AgentAction.Scroll -> "Scroll ${selector?.label() ?: "current container"}"
        is AgentAction.PressGlobal -> "Press ${action.name.lowercase()}"
        is AgentAction.AssertVisible -> "Assert ${selector.label()} visible"
        is AgentAction.WaitForCondition -> "Wait for ${condition}"
        is AgentAction.ConfirmUser -> "Pause at confirm_user: ${reason}"
        AgentAction.Stop -> "Stop runner"
    }
