package com.guribbong.phoneappagent.ui

import android.graphics.Rect as AndroidRect
import android.view.ViewTreeObserver
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import com.guribbong.phoneappagent.data.history.ChatSessionSummary
import com.guribbong.phoneappagent.overlay.ActiveAgentBadge
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
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
            ClaudeHistoryDrawer(sessions = state.sessions)
        },
    ) {
        Scaffold(containerColor = FigmaBackground) { innerPadding ->
            FigmaChatHome(
                modifier = Modifier.padding(innerPadding),
                text = state.composer,
                onValueChange = viewModel::onComposerChanged,
                onQueueTask = viewModel::queueTask,
                onDrawerClick = { scope.launch { drawerState.open() } },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FigmaChatHome(
    text: String,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    onDrawerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberLayerBackdrop {
        drawRect(FigmaBackground)
        drawContent()
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    var chatBarBounds by remember { mutableStateOf<Rect?>(null) }
    var isComposerFocused by remember { mutableStateOf(false) }
    var measuredKeyboardHeightPx by remember { mutableIntStateOf(0) }
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    DisposableEffect(view) {
        val visibleFrame = AndroidRect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            view.getWindowVisibleDisplayFrame(visibleFrame)
            val rootHeight = view.rootView.height
            val obscuredHeight = (rootHeight - visibleFrame.bottom).coerceAtLeast(0)
            measuredKeyboardHeightPx = if (obscuredHeight > rootHeight * 0.15f) {
                obscuredHeight
            } else {
                0
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    val measuredKeyboardHeight = with(density) { measuredKeyboardHeightPx.toDp() }
    val focusedKeyboardFallback = if (isComposerFocused) {
        configuration.screenHeightDp.dp * 0.44f
    } else {
        0.dp
    }
    val keyboardBottomPadding = maxOf(imeBottomPadding, measuredKeyboardHeight, focusedKeyboardFallback)
    val isKeyboardVisible = keyboardBottomPadding > 0.dp
    val isKeyboardAvoiding = isComposerFocused || isKeyboardVisible
    val animatedChatBottomPadding by animateDpAsState(
        targetValue = if (isKeyboardVisible) {
            keyboardBottomPadding + 8.dp
        } else {
            32.dp
        },
        animationSpec = tween(durationMillis = 360, easing = SineEaseInOut),
        label = "chatBarSlide",
    )
    val animatedGreetingTopPadding by animateDpAsState(
        targetValue = if (isKeyboardAvoiding) 118.dp else 311.dp,
        animationSpec = tween(durationMillis = 360, easing = SineEaseInOut),
        label = "greetingSlide",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FigmaBackground)
            .pointerInput(chatBarBounds) {
                detectTapGestures { tapOffset ->
                    if (chatBarBounds?.contains(tapOffset) != true) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            GreetingTitle(topPadding = animatedGreetingTopPadding)
        }
        LiquidGlassIconButton(
            backdrop = backdrop,
            onClick = onDrawerClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 57.dp, start = 24.dp)
                .size(48.dp),
            surfaceColor = Color.White.copy(alpha = 0.54f),
            borderColor = Color.Transparent,
            elevation = 5.dp,
        ) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open history",
                tint = Color.Black,
                modifier = Modifier.size(26.dp),
            )
        }
        LiquidGlassChatBar(
            text = text,
            backdrop = backdrop,
            onValueChange = onValueChange,
            onQueueTask = onQueueTask,
            onComposerFocusChanged = { isComposerFocused = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 25.dp,
                    end = 25.dp,
                    bottom = animatedChatBottomPadding,
                )
                .fillMaxWidth()
                .height(108.dp)
                .onGloballyPositioned { coordinates ->
                    chatBarBounds = coordinates.boundsInRoot()
                },
        )
    }
}

@Composable
private fun ClaudeHistoryDrawer(sessions: List<ChatSessionSummary>) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(338.dp),
        drawerContainerColor = FigmaBackground,
        drawerContentColor = Color.Black,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.04f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Mabi",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "+",
                    color = Color.Black,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Chats",
                modifier = Modifier.padding(horizontal = 10.dp),
                color = FigmaHistoryMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    ClaudeHistoryRow(session = session)
                }
            }
        }
    }
}

@Composable
private fun ClaudeHistoryRow(session: ChatSessionSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = session.title,
            color = Color.Black.copy(alpha = 0.82f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
        Text(
            text = "${session.appName} · ${session.status}",
            color = FigmaHistoryMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
        Text(
            text = session.updatedLabel,
            color = FigmaHistoryMuted.copy(alpha = 0.8f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun GreetingTitle(topPadding: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "어떤 도움이 필요하신가요?",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = topPadding),
            color = Color.Black,
            style = TextStyle(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun LiquidGlassChatBar(
    text: String,
    backdrop: Backdrop,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                shape = shape,
                surfaceColor = FigmaGlassSurface.copy(alpha = 0.18f),
                borderColor = Color.Transparent,
                borderWidth = 0.dp,
                elevation = 4.dp,
            )
            .padding(start = 22.dp, top = 27.dp, end = 13.dp, bottom = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onFocusChanged { onComposerFocusChanged(it.isFocused) },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            cursorBrush = SolidColor(Color.Black),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) {
                        onQueueTask()
                    }
                },
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "메세지 입력・명령",
                            color = FigmaCompactPlaceholder,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "+",
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Voice input",
                tint = Color.Black,
                modifier = Modifier.size(29.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            WaveformButton(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (text.isNotBlank()) {
                            onQueueTask()
                        }
                    },
            )
        }
    }
}

@Composable
private fun WaveformButton(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(Color.Black, CircleShape)
            .padding(6.dp),
    ) {
        val bars = listOf(0.32f, 0.62f, 0.42f, 0.76f, 0.48f)
        val gap = size.width / 8f
        val stroke = size.width / 10f
        bars.forEachIndexed { index, heightFraction ->
            val x = gap * (index + 2)
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(x - stroke / 2f, (size.height - barHeight) / 2f),
                size = androidx.compose.ui.geometry.Size(stroke, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
            )
        }
    }
}

@Composable
private fun LiquidGlassIconButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = FigmaGlassControl.copy(alpha = 0.2f),
    borderColor: Color = Color.Transparent,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val capsule = RoundedCornerShape(1000.dp)
    Box(
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                shape = capsule,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                elevation = elevation,
            )
            .clip(capsule)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    surfaceColor: Color,
    borderColor: Color,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    elevation: androidx.compose.ui.unit.Dp = 0.dp,
): Modifier =
    this
        .then(
            if (elevation > 0.dp) {
                Modifier.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.24f))
            } else {
                Modifier
            },
        )
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(8.dp.toPx(), 18.dp.toPx(), depthEffect = true, chromaticAberration = true)
            },
            onDrawSurface = {
                drawRect(surfaceColor)
            },
        )
        .then(
            if (borderWidth > 0.dp && borderColor.alpha > 0f) {
                Modifier.border(borderWidth, borderColor, shape)
            } else {
                Modifier
            },
        )
        .clip(shape)

private val FigmaBackground = Color(0xFFF2EFE9)
private val FigmaRule = Color(0xFFB8B8B8)
private val FigmaGlassSurface = Color(0xFFEAEAEA)
private val FigmaGlassControl = Color(0xFFD9D9D9)
private val FigmaGlassBorder = Color(0xFFE9E9E9)
private val FigmaPlaceholder = Color(0xFF8B8B8B)
private val FigmaCompactPlaceholder = Color(0xFF636363)
private val FigmaHistoryMuted = Color(0xFF6F6B64)
private val SineEaseInOut = Easing { fraction ->
    ((1f - cos(PI * fraction).toFloat()) / 2f)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                    label = {
                        Text(
                            text = if (enabled) health else "Off",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Last external app: ${lastExternalForegroundApp ?: "waiting"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Top node: ${topNodeLabel ?: "waiting"} • nodes: $nodeCount • event: $lastEvent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (enabled) "Open access settings" else "Enable access")
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("Refresh state")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        modifier = Modifier.fillMaxWidth(),
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "phone_app_agent",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Foreground Android agent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactStatusChip(label = status)
                CompactStatusChip(label = modelName, modifier = Modifier.widthIn(max = 280.dp))
                CompactStatusChip(label = phaseLabel, modifier = Modifier.widthIn(max = 180.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActiveAgentBadge(
                    label = backend,
                    accent = if (overlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.widthIn(max = 280.dp),
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
private fun CompactStatusChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        Text(
                            text = if (needsConfirmation) "Confirm gate" else "Remote runtime",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = step.expectedObservation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Outlined.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = policyReason,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
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
