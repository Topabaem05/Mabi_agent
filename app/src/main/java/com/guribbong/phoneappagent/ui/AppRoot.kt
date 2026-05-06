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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
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
import com.guribbong.phoneappagent.data.history.ChatTranscript
import com.guribbong.phoneappagent.data.history.ChatTranscriptMessage
import com.guribbong.phoneappagent.data.history.ChatTranscriptRole
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
            MabiHistoryDrawer(
                sessions = state.sessions,
                onSessionClick = { sessionId ->
                    viewModel.openSession(sessionId)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(containerColor = FigmaBackground) { innerPadding ->
            FigmaChatHome(
                modifier = Modifier.padding(innerPadding),
                text = state.composer,
                showRiskProcessPopup = state.showRiskProcessPopup,
                policyReason = state.policyReason,
                riskOptionUiModel = state.riskOptionUiModel,
                canConfirm = state.canConfirm,
                canStop = state.canStop,
                selectedTranscript = state.selectedTranscript,
                onValueChange = viewModel::onComposerChanged,
                onQueueTask = viewModel::queueTask,
                onConfirm = viewModel::confirmExecution,
                onStop = viewModel::stopExecution,
                onRiskFollowUp = viewModel::runRiskFollowUp,
                onRiskRefinement = viewModel::runRiskRefinement,
                onCloseTranscript = viewModel::closeSession,
                onDrawerClick = { scope.launch { drawerState.open() } },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FigmaChatHome(
    text: String,
    showRiskProcessPopup: Boolean,
    policyReason: String,
    riskOptionUiModel: RiskOptionUiModel,
    canConfirm: Boolean,
    canStop: Boolean,
    selectedTranscript: ChatTranscript?,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    onConfirm: () -> Unit,
    onStop: () -> Unit,
    onRiskFollowUp: (String) -> Unit,
    onRiskRefinement: (String) -> Unit,
    onCloseTranscript: () -> Unit,
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
        if (showRiskProcessPopup) {
            RiskOptionPopup(
                model = riskOptionUiModel,
                fallbackReason = policyReason,
                canProceed = canConfirm,
                canStop = canStop,
                onProceed = onConfirm,
                onStop = onStop,
                onFollowUp = onRiskFollowUp,
                onRefinement = onRiskRefinement,
                modifier = Modifier.fillMaxSize(),
            )
        }
        selectedTranscript?.let { transcript ->
            MabiTranscriptScreen(
                transcript = transcript,
                composerText = text,
                onValueChange = onValueChange,
                onQueueTask = onQueueTask,
                onBack = onCloseTranscript,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RiskOptionPopup(
    model: RiskOptionUiModel,
    fallbackReason: String,
    canProceed: Boolean,
    canStop: Boolean,
    onProceed: () -> Unit,
    onStop: () -> Unit,
    onFollowUp: (String) -> Unit,
    onRefinement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var refinementOpen by remember(model.title) { mutableStateOf(false) }
    var refinementText by remember(model.title) { mutableStateOf("") }
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 430.dp,
        sheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        sheetContainerColor = Color.White,
        sheetContentColor = Color.Black,
        sheetShadowElevation = 18.dp,
        sheetDragHandle = { RiskSheetDragHandle() },
        sheetContent = {
            RiskConfirmationSheetContent(
                model = model,
                fallbackReason = fallbackReason,
                refinementOpen = refinementOpen,
                refinementText = refinementText,
                canProceed = canProceed,
                canStop = canStop,
                onRefinementTextChange = { refinementText = it },
                onOptionSelected = { option ->
                    when (option.action) {
                        RiskOptionAction.FOLLOW_UP -> option.followUpPrompt?.let(onFollowUp)
                        RiskOptionAction.PROCEED -> if (canProceed) onProceed()
                        RiskOptionAction.REFINE -> refinementOpen = true
                        RiskOptionAction.STOP -> if (canStop) onStop()
                    }
                },
                onSendRefinement = {
                    if (refinementText.isNotBlank()) {
                        onRefinement(refinementText)
                    }
                },
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.36f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f)),
        )
    }
}

@Composable
private fun RiskSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .width(54.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color.Black),
    )
}

@Composable
private fun RiskConfirmationSheetContent(
    model: RiskOptionUiModel,
    fallbackReason: String,
    refinementOpen: Boolean,
    refinementText: String,
    canProceed: Boolean,
    canStop: Boolean,
    onRefinementTextChange: (String) -> Unit,
    onOptionSelected: (RiskOptionItem) -> Unit,
    onSendRefinement: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 430.dp, max = 720.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (refinementOpen) {
            RiskSheetRefinementContent(
                model = model,
                refinementText = refinementText,
                onRefinementTextChange = onRefinementTextChange,
                onSendRefinement = onSendRefinement,
            )
        } else {
            val primaryOptions = model.options
                .filter { it.action == RiskOptionAction.FOLLOW_UP || it.action == RiskOptionAction.PROCEED }
                .take(2)
                .ifEmpty { model.options.take(2) }
            val secondaryOptions = model.options.filterNot { primaryOptions.contains(it) }
            RiskSheetHeader(model = model)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                primaryOptions.forEachIndexed { index, option ->
                    val enabled = option.action != RiskOptionAction.PROCEED || canProceed
                    RiskOptionCard(
                        option = option,
                        index = index,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onOptionSelected(option) },
                    )
                }
            }
            if (secondaryOptions.isNotEmpty()) {
                RiskConditionSection(
                    model = model,
                    options = secondaryOptions,
                    optionIndexStart = primaryOptions.size,
                    canStop = canStop,
                    onOptionSelected = onOptionSelected,
                )
            }
            RiskExpandHint()
            RiskSheetProcessDetails(
                model = model,
                fallbackReason = fallbackReason,
            )
        }
    }
}

@Composable
private fun RiskSheetHeader(model: RiskOptionUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "사용자 확인 필요",
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(TossBlue.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            color = TossBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = "계속 진행할까요?",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = model.sheetTitle,
            color = Color.Black,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Text(
            text = model.sheetSubtitle,
            color = Color.Black.copy(alpha = 0.58f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun RiskExpandHint() {
    Text(
        text = "위로 올리면 진행 과정을 자세히 볼 수 있어요.",
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        color = Color.Black.copy(alpha = 0.52f),
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    )
}

@Composable
private fun RiskConditionSection(
    model: RiskOptionUiModel,
    options: List<RiskOptionItem>,
    optionIndexStart: Int,
    canStop: Boolean,
    onOptionSelected: (RiskOptionItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF7F8FA))
            .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5E8ED)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${optionIndexStart + 1}",
                    color = Color.Black.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = model.refinementTitle,
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = "조건을 바꾸거나 다른 답변을 요청할 수 있어요.",
                    color = Color.Black.copy(alpha = 0.56f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val enabled = option.action != RiskOptionAction.STOP || canStop
                Text(
                    text = option.title,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
                        .clickable(enabled = enabled) { onOptionSelected(option) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.Black.copy(alpha = if (enabled) 0.68f else 0.34f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
private fun RiskSheetProcessDetails(
    model: RiskOptionUiModel,
    fallbackReason: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF8F9FB))
            .border(1.dp, Color.Black.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = model.progressEyebrow,
                color = Color.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            Text(
                text = model.description.ifBlank { fallbackReason },
                color = Color.Black.copy(alpha = 0.58f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            )
        }
        RiskSheetProgressRail(
            steps = model.progressSteps,
            activeIndex = model.activeProgressIndex,
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            model.statusRows.forEachIndexed { index, row ->
                RiskSheetStatusRow(row = row)
                if (index != model.statusRows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(start = 40.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.Black.copy(alpha = 0.06f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskSheetProgressRail(
    steps: List<String>,
    activeIndex: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        steps.take(3).forEachIndexed { index, label ->
            val done = index < activeIndex
            val active = index == activeIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (active) 32.dp else 28.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done || active -> RiskBlue
                                else -> Color(0xFFE5E8ED)
                            },
                        )
                        .then(
                            if (active) Modifier.border(2.dp, Color.White, CircleShape) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (done) "✓" else "${index + 1}",
                        color = if (done || active) Color.White else Color.Black.copy(alpha = 0.58f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    )
                }
                Text(
                    text = label,
                    color = if (active) RiskBlue else Color.Black.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
private fun RiskSheetStatusRow(row: RiskProgressRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (row.state == RiskProgressState.DONE) RiskBlue else Color.Transparent)
                .border(
                    width = if (row.state == RiskProgressState.DONE) 0.dp else 2.dp,
                    color = if (row.state == RiskProgressState.ACTIVE) RiskBlue else Color.Black.copy(alpha = 0.2f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (row.state == RiskProgressState.DONE) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.title,
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
            Text(
                text = row.detail,
                color = Color.Black.copy(alpha = 0.56f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
        }
        Text(
            text = when (row.state) {
                RiskProgressState.DONE -> "완료"
                RiskProgressState.ACTIVE -> "진행 중"
                RiskProgressState.WAITING -> "대기"
            },
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(
                    when (row.state) {
                        RiskProgressState.DONE -> Color(0xFFE7F7EC)
                        RiskProgressState.ACTIVE -> RiskBlue.copy(alpha = 0.12f)
                        RiskProgressState.WAITING -> Color.Black.copy(alpha = 0.05f)
                    },
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            color = when (row.state) {
                RiskProgressState.DONE -> Color(0xFF248B45)
                RiskProgressState.ACTIVE -> RiskBlue
                RiskProgressState.WAITING -> Color.Black.copy(alpha = 0.58f)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun RiskOptionCard(
    option: RiskOptionItem,
    index: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emphasized = option.action == RiskOptionAction.PROCEED
    val recommended = option.badge != null
    Column(
        modifier = modifier
            .height(126.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (emphasized) TossBlue.copy(alpha = 0.08f) else Color.White)
            .border(
                width = if (emphasized) 1.5.dp else 1.dp,
                color = if (emphasized) TossBlue else Color.Black.copy(alpha = 0.1f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (emphasized) TossBlue else Color(0xFFE5E8ED)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                color = if (emphasized) Color.White else Color.Black.copy(alpha = 0.62f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = option.title,
            color = if (enabled) Color.Black else Color.Black.copy(alpha = 0.38f),
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
        Text(
            text = option.subtitle,
            color = Color.Black.copy(alpha = if (enabled) 0.58f else 0.32f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
        option.badge?.let { badge ->
            Text(
                text = badge,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(TossBlue.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = TossBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun RiskSheetRefinementContent(
    model: RiskOptionUiModel,
    refinementText: String,
    onRefinementTextChange: (String) -> Unit,
    onSendRefinement: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = model.refinementTitle,
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = "원하는 조건을 입력하면 이 조건으로 다시 진행할게요.",
            color = Color.Black.copy(alpha = 0.58f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = refinementText.ifBlank { model.refinementPlaceholder },
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RiskBlue.copy(alpha = if (refinementText.isBlank()) 0.08f else 1f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = if (refinementText.isBlank()) Color.Black.copy(alpha = 0.38f) else Color.White,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            )
        }
        BasicTextField(
            value = refinementText,
            onValueChange = onRefinementTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.04f))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            cursorBrush = SolidColor(RiskBlue),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (refinementText.isEmpty()) {
                        Text(
                            text = model.refinementPlaceholder,
                            color = Color.Black.copy(alpha = 0.34f),
                            fontSize = 16.sp,
                            letterSpacing = 0.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Button(
            onClick = onSendRefinement,
            enabled = refinementText.isNotBlank(),
            modifier = Modifier
                .align(Alignment.End)
                .height(48.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("다시 찾기")
        }
    }
}

@Composable
private fun WaveBadge() {
    Canvas(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .padding(10.dp),
    ) {
        val bars = listOf(0.36f, 0.68f, 0.48f, 0.78f, 0.42f)
        val gap = size.width / 8f
        val stroke = size.width / 9f
        bars.forEachIndexed { index, heightFraction ->
            val x = gap * (index + 2)
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = RiskBlue,
                topLeft = androidx.compose.ui.geometry.Offset(x - stroke / 2f, (size.height - barHeight) / 2f),
                size = androidx.compose.ui.geometry.Size(stroke, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke, stroke),
            )
        }
    }
}

@Composable
private fun MabiHistoryDrawer(
    sessions: List<ChatSessionSummary>,
    onSessionClick: (Long) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(338.dp),
        drawerContainerColor = TossSurface,
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
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, TossDivider, RoundedCornerShape(16.dp))
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
                    text = "History",
                    color = TossMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Conversations",
                modifier = Modifier.padding(horizontal = 10.dp),
                color = TossMuted,
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
                    MabiHistoryRow(
                        session = session,
                        onClick = { onSessionClick(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MabiHistoryRow(
    session: ChatSessionSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(TossBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = TossBlue,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = session.title,
                color = Color.Black.copy(alpha = 0.86f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
            Text(
                text = "${session.appName} · ${session.status}",
                color = TossMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
            Text(
                text = session.updatedLabel,
                color = TossMuted.copy(alpha = 0.82f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
        }
        Text(
            text = "›",
            color = TossMuted.copy(alpha = 0.8f),
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun MabiTranscriptScreen(
    transcript: ChatTranscript,
    composerText: String,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(TossSurface)
            .padding(top = 42.dp),
    ) {
        MabiTranscriptTopBar(
            transcript = transcript,
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(transcript.messages) { message ->
                MabiTranscriptBubble(message = message)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        MabiTranscriptComposer(
            text = composerText,
            onValueChange = onValueChange,
            onQueueTask = onQueueTask,
            modifier = Modifier
                .fillMaxWidth()
                .background(TossDark)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun MabiTranscriptTopBar(
    transcript: ChatTranscript,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = transcript.title,
                color = Color.Black,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
            Text(
                text = "${transcript.appName} · ${transcript.status} · ${transcript.updatedLabel}",
                color = TossMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun MabiTranscriptBubble(message: ChatTranscriptMessage) {
    val isUser = message.role == ChatTranscriptRole.USER
    val isStatus = message.role == ChatTranscriptRole.STATUS
    val bubbleColor = when (message.role) {
        ChatTranscriptRole.USER -> TossBlue
        ChatTranscriptRole.AGENT -> Color.White
        ChatTranscriptRole.THOUGHT -> TossThought
        ChatTranscriptRole.STATUS -> Color.Transparent
    }
    val textColor = if (isUser) Color.White else Color.Black.copy(alpha = 0.86f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isStatus -> Arrangement.Center
            else -> Arrangement.Start
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isStatus) 0.92f else 0.82f)
                .clip(RoundedCornerShape(if (isStatus) 12.dp else 18.dp))
                .background(bubbleColor)
                .then(
                    if (message.role == ChatTranscriptRole.AGENT) {
                        Modifier.border(1.dp, TossDivider, RoundedCornerShape(18.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = if (isStatus) 10.dp else 14.dp, vertical = if (isStatus) 8.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = if (isStatus) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = message.title,
                color = if (isStatus) TossMuted else textColor.copy(alpha = if (isUser) 0.9f else 0.68f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isStatus) TextAlign.Center else TextAlign.Start,
                letterSpacing = 0.sp,
            )
            Text(
                text = message.body,
                color = if (isStatus) TossMuted else textColor,
                fontSize = if (message.role == ChatTranscriptRole.THOUGHT) 13.sp else 15.sp,
                lineHeight = if (message.role == ChatTranscriptRole.THOUGHT) 18.sp else 20.sp,
                textAlign = if (isStatus) TextAlign.Center else TextAlign.Start,
                letterSpacing = 0.sp,
            )
            message.meta?.takeIf { it.isNotBlank() }?.let { meta ->
                Text(
                    text = meta,
                    color = if (isUser) Color.White.copy(alpha = 0.72f) else TossMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.sp,
                )
            }
        }
    }
}

@Composable
private fun MabiTranscriptComposer(
    text: String,
    onValueChange: (String) -> Unit,
    onQueueTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            cursorBrush = SolidColor(TossBlue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) onQueueTask()
                },
            ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = "새 명령 입력",
                            color = TossMuted,
                            fontSize = 16.sp,
                            letterSpacing = 0.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Button(
            onClick = onQueueTask,
            enabled = text.isNotBlank(),
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("+", fontSize = 22.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
        }
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
private val RiskBlue = Color(0xFF477EF3)
private val TossBlue = Color(0xFF3081FB)
private val TossSurface = Color(0xFFFAFAFC)
private val TossThought = Color(0xFFF1F3F5)
private val TossDivider = Color(0xFFE2E2E2)
private val TossMuted = Color(0xFF7B7D83)
private val TossDark = Color(0xFF303030)
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
        is AgentAction.OpenUri -> "Open ${packageName ?: uri}"
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
