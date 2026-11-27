package com.example.globalminusone.ui.panel.cards

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures

@Composable
fun BrightnessBarComponent(
    systemController: SystemController,
    onBrightnessAdjusting: (Boolean) -> Unit,
    orientation: ComponentOrientation = ComponentOrientation.AUTO
) {
    BrightnessBarContent(rememberBrightnessCardControl(systemController),
        { source -> MinusOneRepository.getInstance(source).isPanelExpanded }, onBrightnessAdjusting, orientation)
}

@Composable
internal fun BrightnessBarContent(
    control: BrightnessCardControl,
    panelExpansion: (Context) -> StateFlow<Boolean>,
    onBrightnessAdjusting: (Boolean) -> Unit,
    orientation: ComponentOrientation = ComponentOrientation.AUTO
) {
    val cardColors = LocalCardColors.current
    val actual by control.brightness.collectAsState()
    var brightness by remember { mutableFloatStateOf(actual.percent?.toFloat() ?: 0f) }
    var lastAppliedPercent by remember { mutableIntStateOf(actual.percent ?: -1) }
    var adjusting by remember { mutableStateOf(false) }
    val brightnessInteraction = remember { MutableInteractionSource() }
    val context = LocalContext.current
    val panelExpanded by remember(context) {
        panelExpansion(context)
    }.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnAdjusting by rememberUpdatedState(onBrightnessAdjusting)
    LaunchedEffect(actual, adjusting) {
        if (!adjusting) {
            val currentPercent = actual.percent
            brightness = currentPercent?.toFloat() ?: 0f
            lastAppliedPercent = currentPercent ?: -1
        }
    }
    LaunchedEffect(brightnessInteraction) {
        brightnessInteraction.interactions.collect { interaction ->
            if (interaction is DragInteraction.Cancel) {
                adjusting = false
                currentOnAdjusting(false)
            }
        }
    }
    LaunchedEffect(panelExpanded) {
        adjusting = false
        currentOnAdjusting(false)
        if (panelExpanded) control.refreshBrightness()
    }
    LaunchedEffect(actual.canWrite) {
        if (!actual.canWrite) {
            adjusting = false
            currentOnAdjusting(false)
        }
    }
    DisposableEffect(control, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                adjusting = false
                currentOnAdjusting(false)
                control.refreshBrightness()
            }
        }
        lifecycle.addObserver(observer)
        control.refreshBrightness()
        onDispose {
            lifecycle.removeObserver(observer)
            currentOnAdjusting(false)
        }
    }

    // A disabled track used most of the row while hiding the reason in a tiny trailing button.
    // Keep the explanation itself as the explicit, full-row action within a normal 48dp card.
    if (!actual.canWrite) {
        val status = when {
            !actual.permitted -> "需修改系统设置权限"
            !actual.modeKnown -> "亮度模式未知"
            actual.percent == null -> "亮度范围未知"
            else -> "自动亮度 · 改为手动"
        }
        val compactVerticalStatus = when {
            !actual.permitted -> "授权设置"
            !actual.modeKnown -> "模式未知"
            actual.percent == null -> "范围未知"
            else -> "切手动"
        }
        val actionable = !actual.permitted || (actual.automatic && actual.percent != null)
        fun onAction() {
            if (!actual.permitted) control.requestWriteSettingsPermission()
            else if (!control.useManualBrightness())
                Toast.makeText(context, "未切换亮度模式", Toast.LENGTH_SHORT).show()
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isVerticalPrompt = when (orientation) {
                ComponentOrientation.VERTICAL -> true
                ComponentOrientation.HORIZONTAL -> false
                ComponentOrientation.AUTO -> maxHeight > maxWidth * 1.15f
            }
            if (isVerticalPrompt) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .then(if (actionable) Modifier.clickable(onClick = ::onAction) else Modifier)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.BrightnessMedium, contentDescription = "亮度", tint = cardColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = compactVerticalStatus,
                        color = if (actionable) cardColors.accent else cardColors.textPrimary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.BrightnessMedium, contentDescription = "亮度", tint = cardColors.accent, modifier = Modifier.size(18.dp))
                    if (actionable) {
                        TextButton(
                            onClick = ::onAction,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).fillMaxSize()
                        ) {
                            Text(status, color = cardColors.textPrimary, fontSize = 13.sp, lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text(status, color = cardColors.textPrimary, fontSize = 13.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        return
    }

    SystemSliderRow(
            icon = Icons.Rounded.BrightnessMedium,
            title = "亮度",
            value = brightness,
            interactionSource = brightnessInteraction,
            onValueChange = {
                brightness = it
                adjusting = true
                currentOnAdjusting(true)
                val targetPercent = it.roundToInt().coerceIn(0, 100)
                if (targetPercent != lastAppliedPercent) {
                    lastAppliedPercent = targetPercent
                    control.setBrightnessPercentAsync(targetPercent)
                }
            },
            onValueChangeFinished = {
                val finalPercent = brightness.roundToInt().coerceIn(0, 100)
                val wasAdjusting = adjusting
                lastAppliedPercent = finalPercent
                adjusting = false
                currentOnAdjusting(false)
                if (wasAdjusting) control.commitBrightnessPercentAsync(finalPercent) { ok ->
                    if (!ok) Toast.makeText(context, "未修改亮度，请检查权限、模式与设备范围", Toast.LENGTH_SHORT).show()
                }
            },
            orientation = orientation
    )
}

@Composable
fun VolumeBarComponent(
    systemController: SystemController,
    orientation: ComponentOrientation = ComponentOrientation.AUTO
) {
    VolumeBarContent(rememberVolumeCardControl(systemController),
        { source -> MinusOneRepository.getInstance(source).isPanelExpanded }, orientation)
}

@Composable
internal fun VolumeBarContent(
    control: VolumeCardControl,
    panelExpansion: (Context) -> StateFlow<Boolean>,
    orientation: ComponentOrientation = ComponentOrientation.AUTO
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val panelExpanded by remember(context) {
        panelExpansion(context)
    }.collectAsState()
    var volume by remember { mutableFloatStateOf(0f) }
    var draggingVolume by remember { mutableStateOf(false) }
    var committingVolume by remember { mutableStateOf(false) }
    var commitGeneration by remember { mutableIntStateOf(0) }
    var lastAppliedPercent by remember { mutableIntStateOf(-1) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    val volumeInteraction = remember { MutableInteractionSource() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun readCurrentVolume() {
        readJob?.cancel()
        readJob = scope.launch {
            val current = withContext(Dispatchers.IO) { control.getMediaVolumePercent() }
            if (!draggingVolume && !committingVolume) {
                volume = current.toFloat()
                lastAppliedPercent = current
            }
        }
    }

    LaunchedEffect(control) {
        readCurrentVolume()
    }

    // Slider does not call onValueChangeFinished when a pointer gesture is cancelled.
    // Release the draft so later hardware-volume broadcasts can update the row again.
    LaunchedEffect(volumeInteraction) {
        volumeInteraction.interactions.collect { interaction ->
            if (interaction is DragInteraction.Cancel) {
                draggingVolume = false
                committingVolume = false
                readCurrentVolume()
            }
        }
    }

    LaunchedEffect(panelExpanded) {
        draggingVolume = false
        committingVolume = false
        if (panelExpanded) readCurrentVolume()
    }

    DisposableEffect(context, control, lifecycle, panelExpanded) {
        if (!panelExpanded) {
            onDispose { readJob?.cancel() }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                    draggingVolume = false; committingVolume = false; readCurrentVolume()
                }
            }
            lifecycle.addObserver(observer)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                    if (!draggingVolume && !committingVolume) readCurrentVolume()
                }
            }
            runCatching {
                androidx.core.content.ContextCompat.registerReceiver(
                    context, receiver,
                    android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                )
            }
            onDispose {
                readJob?.cancel(); lifecycle.removeObserver(observer)
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    SystemSliderRow(
        icon = Icons.Rounded.VolumeUp,
        title = "音量",
        value = volume,
        interactionSource = volumeInteraction,
        onIconClick = { control.openSystemVolumePanel() },
        onValueChange = {
            draggingVolume = true
            committingVolume = false
            commitGeneration++
            volume = it
            val targetPercent = it.roundToInt().coerceIn(0, 100)
            if (targetPercent != lastAppliedPercent) {
                lastAppliedPercent = targetPercent
                control.setMediaVolumePercentAsync(targetPercent)
            }
        },
        onValueChangeFinished = {
            val finalPercent = volume.roundToInt().coerceIn(0, 100)
            lastAppliedPercent = finalPercent
            draggingVolume = false
            committingVolume = true
            val expectedGen = ++commitGeneration
            control.commitMediaVolumePercentAsync(finalPercent) { result ->
                if (expectedGen == commitGeneration && !draggingVolume) {
                    committingVolume = false
                    volume = result.actualPercent.toFloat()
                    lastAppliedPercent = result.actualPercent
                }
                if (!result.succeeded && result.reason.isNotBlank()) {
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
                }
            }
        },
        orientation = orientation
    )
}

@Composable
private fun SystemSliderRow(
    icon: ImageVector,
    title: String,
    value: Float,
    interactionSource: MutableInteractionSource,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    orientation: ComponentOrientation = ComponentOrientation.AUTO,
    onIconClick: (() -> Unit)? = null
) {
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isVertical = when (orientation) {
            ComponentOrientation.VERTICAL -> true
            ComponentOrientation.HORIZONTAL -> false
            ComponentOrientation.AUTO -> maxHeight > maxWidth * 1.15f
        }
        if (isVertical) {
            VerticalSystemSliderPill(
                icon, title, value, isEditMode, interactionSource,
                latestOnChange, latestOnFinished, onIconClick
            )
            return@BoxWithConstraints
        }
        Box(
            Modifier
                .fillMaxSize()
                .ownPanelScrollGestures(
                    enabled = !isEditMode,
                    lockFromDown = true,
                    axis = PanelGestureAxis.Horizontal
                )
                .padding(horizontal = 8.dp)
        ) {
        val availableRowWidth = this@BoxWithConstraints.maxWidth
        // At narrow widths or large font scales the icon identifies the control; preserve
        // the readable percentage and useful track length instead of squeezing an 11sp label.
        val compact = availableRowWidth < (240f * fontScale).dp
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = cardColors.accent,
                modifier = Modifier
                    .size(20.dp)
                    .then(
                        if (!isEditMode && onIconClick != null) {
                            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onIconClick)
                        } else Modifier
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (compact) "${value.roundToInt()}%" else "$title ${value.roundToInt()}%",
                color = cardColors.textPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = availableRowWidth * 0.42f)
            )
            Spacer(Modifier.width(8.dp))
            var trackWidthPx by remember { mutableIntStateOf(1) }
            val thumbRadiusPx = with(density) { 8.dp.toPx() }
            val trackHeightPx = with(density) { 6.dp.toPx() }
            val scrubSlopPx = with(density) { 2.dp.toPx() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                    .semantics {
                        contentDescription = title
                        stateDescription = "${value.roundToInt()}%"
                        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 100f), 0f..100f)
                        if (!isEditMode) setProgress { requested ->
                            latestOnChange(requested.coerceIn(0f, 100f))
                            latestOnFinished()
                            true
                        }
                    }
                    .then(
                        if (!isEditMode) {
                            Modifier.pointerInput(title) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val span = (trackWidthPx - thumbRadiusPx * 2f).coerceAtLeast(1f)
                                    fun percentAt(x: Float): Float =
                                        (((x - thumbRadiusPx) / span) * 100f).coerceIn(0f, 100f)

                                    var scrubbing = false
                                    var movedVertically = false
                                    var activeDrag: DragInteraction.Start? = null
                                    val startX = down.position.x
                                    val startY = down.position.y
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                                            if (!pointer.pressed) {
                                                if (scrubbing) {
                                                    pointer.consume()
                                                    activeDrag?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
                                                    activeDrag = null
                                                    latestOnFinished()
                                                } else if (!movedVertically && !pointer.isConsumed) {
                                                    pointer.consume()
                                                    latestOnChange(percentAt(pointer.position.x))
                                                    latestOnFinished()
                                                }
                                                break
                                            }
                                            val dx = pointer.position.x - startX
                                            val dy = pointer.position.y - startY
                                            if (!scrubbing && !movedVertically) {
                                                if (abs(dx) >= scrubSlopPx && abs(dx) >= abs(dy) * 0.6f) {
                                                    scrubbing = true
                                                    val startEvt = DragInteraction.Start()
                                                    activeDrag = startEvt
                                                    interactionSource.tryEmit(startEvt)
                                                    latestOnChange(percentAt(pointer.position.x))
                                                    pointer.consume()
                                                } else if (abs(dy) > scrubSlopPx * 4f && abs(dy) > abs(dx) * 1.6f) {
                                                    movedVertically = true
                                                }
                                            } else if (scrubbing) {
                                                if (pointer.positionChange() != Offset.Zero) {
                                                    latestOnChange(percentAt(pointer.position.x))
                                                }
                                                pointer.consume()
                                            }
                                        }
                                    } finally {
                                        activeDrag?.let {
                                            interactionSource.tryEmit(DragInteraction.Cancel(it))
                                        }
                                    }
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val fraction = (value / 100f).coerceIn(0f, 1f)
                val capsuleHeightPx = with(density) { 22.dp.toPx() }
                val capsuleRadiusPx = capsuleHeightPx / 2f
                val indicatorWidthPx = with(density) { 3.dp.toPx() }
                val indicatorHeightPx = with(density) { 10.dp.toPx() }
                val indicatorMarginPx = with(density) { 6.dp.toPx() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val topY = (size.height - capsuleHeightPx) / 2f
                    drawRoundRect(
                        color = cardColors.trackColor,
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, capsuleHeightPx),
                        cornerRadius = CornerRadius(capsuleRadiusPx, capsuleRadiusPx)
                    )
                    if (fraction > 0.005f) {
                        val fillWidth = (capsuleHeightPx + (size.width - capsuleHeightPx) * fraction).coerceAtMost(size.width)
                        drawRoundRect(
                            color = cardColors.accent,
                            topLeft = Offset(0f, topY),
                            size = Size(fillWidth, capsuleHeightPx),
                            cornerRadius = CornerRadius(capsuleRadiusPx, capsuleRadiusPx)
                        )
                        val indX = (fillWidth - indicatorMarginPx - indicatorWidthPx).coerceAtLeast(indicatorMarginPx)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.92f),
                            topLeft = Offset(indX, (size.height - indicatorHeightPx) / 2f),
                            size = Size(indicatorWidthPx, indicatorHeightPx),
                            cornerRadius = CornerRadius(indicatorWidthPx / 2f, indicatorWidthPx / 2f)
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun VerticalSystemSliderPill(
    icon: ImageVector,
    title: String,
    value: Float,
    isEditMode: Boolean,
    interactionSource: MutableInteractionSource,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onIconClick: (() -> Unit)? = null
) {
    val cardColors = LocalCardColors.current
    val density = LocalDensity.current
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val fraction = (value / 100f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .ownPanelScrollGestures(
                enabled = !isEditMode,
                lockFromDown = true,
                axis = PanelGestureAxis.Vertical
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val pillWidth = minOf(maxWidth, 64.dp).coerceAtLeast(30.dp)
        val scrubSlopPx = with(density) { 2.dp.toPx() }
        var trackHeightPx by remember { mutableIntStateOf(1) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
                .semantics {
                    contentDescription = "$title 竖排"
                    stateDescription = "${value.roundToInt()}%"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 100f), 0f..100f)
                    if (!isEditMode) setProgress { requested ->
                        latestOnChange(requested.coerceIn(0f, 100f))
                        latestOnFinished()
                        true
                    }
                }
                .then(
                    if (!isEditMode) {
                        Modifier.pointerInput(title) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val span = trackHeightPx.toFloat().coerceAtLeast(1f)
                                fun percentAt(y: Float): Float =
                                    ((1f - (y / span)) * 100f).coerceIn(0f, 100f)

                                var scrubbing = false
                                var movedHorizontally = false
                                var activeDrag: DragInteraction.Start? = null
                                val startX = down.position.x
                                val startY = down.position.y
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pointer = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                            ?: break
                                        if (!pointer.pressed) {
                                            if (scrubbing) {
                                                pointer.consume()
                                                activeDrag?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
                                                activeDrag = null
                                                latestOnFinished()
                                            } else if (!movedHorizontally && !pointer.isConsumed) {
                                                pointer.consume()
                                                latestOnChange(percentAt(pointer.position.y))
                                                latestOnFinished()
                                            }
                                            break
                                        }
                                        val dx = pointer.position.x - startX
                                        val dy = pointer.position.y - startY
                                        if (!scrubbing && !movedHorizontally) {
                                            if (abs(dy) >= scrubSlopPx && abs(dy) >= abs(dx) * 0.6f) {
                                                scrubbing = true
                                                val startEvt = DragInteraction.Start()
                                                activeDrag = startEvt
                                                interactionSource.tryEmit(startEvt)
                                                latestOnChange(percentAt(pointer.position.y))
                                                pointer.consume()
                                            } else if (abs(dx) > scrubSlopPx * 4f && abs(dx) > abs(dy) * 1.6f) {
                                                movedHorizontally = true
                                            }
                                        } else if (scrubbing) {
                                            if (pointer.positionChange() != Offset.Zero) {
                                                latestOnChange(percentAt(pointer.position.y))
                                            }
                                            pointer.consume()
                                        }
                                    }
                                } finally {
                                    activeDrag?.let {
                                        interactionSource.tryEmit(DragInteraction.Cancel(it))
                                    }
                                }
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = cardColors.trackColor,
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                    val fillH = (size.height * fraction).coerceIn(0f, size.height)
                    if (fillH > 0.5f) {
                        drawRoundRect(
                            color = cardColors.accent,
                            topLeft = Offset(0f, size.height - fillH),
                            size = Size(size.width, fillH),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${value.roundToInt()}%",
                        color = if (fraction > 0.86f) Color.White else cardColors.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (fraction > 0.22f) Color.White else cardColors.textSecondary,
                        modifier = if (!isEditMode && onIconClick != null) {
                            Modifier.size(18.dp).clickable(onClick = onIconClick)
                        } else Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
