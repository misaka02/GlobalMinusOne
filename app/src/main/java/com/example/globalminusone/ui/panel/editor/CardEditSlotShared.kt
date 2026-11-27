package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.isSliderBarType
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.ownsPanelScrolling
import com.example.globalminusone.ui.panel.gesture.panelScrollAxisForComponent

private val nonLongPressCardTypes = setOf(
    ComponentType.APP_ICON,
    ComponentType.APP_DRAWER_CARD,
    ComponentType.APP_KEYBOARD_LAUNCHER,
    ComponentType.RECENT_APPS_CARD,
    ComponentType.CONTAINER_BOX,
    ComponentType.FOLDER
)

@Composable
internal fun CardSlotBodyLayer(
    item: ComponentItem,
    widthDp: Float,
    heightDp: Float,
    isEditMode: Boolean,
    dragEnabled: Boolean,
    layoutLocked: Boolean,
    allowCardLongPress: Boolean,
    onEnterEditMode: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .ownPanelScrollGestures(
                enabled = !isEditMode && item.type.ownsPanelScrolling() && !isSingleIconLauncherItem(item),
                lockFromDown = isSliderBarType(item.type) && !isSingleIconLauncherItem(item),
                axis = panelScrollAxisForComponent(item, widthDp, heightDp)
            )
            .cardLongPressToEdit(
                key = item.id,
                enabled = !dragEnabled && !layoutLocked && allowCardLongPress &&
                    !isSingleIconLauncherItem(item) &&
                    item.type !in nonLongPressCardTypes,
                requireIdleOwnership = !isSliderBarType(item.type) && item.type != ComponentType.APP_WIDGET
            ) {
                onEnterEditMode()
            }
    ) {
        content()
    }
}

@Composable
internal fun CardDropPlaceholder(
    offsetProvider: () -> IntOffset,
    widthPx: () -> Int,
    heightPx: () -> Int,
    accentColor: Color,
    fillAlpha: Float = 0.12f
) {
    Box(
        modifier = Modifier
            .offset { offsetProvider() }
            .layout { measurable, _ ->
                val w = widthPx().coerceAtLeast(1)
                val h = heightPx().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.placeRelative(0, 0) }
            }
            .background(accentColor.copy(alpha = fillAlpha), RoundedCornerShape(10.dp))
            .border(2.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
    )
}

@Composable
internal fun BoxScope.FreeCardResizeHandle(
    alignment: Alignment,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    description: String,
    selectionColor: Color,
    pillHorizontal: Boolean?,
    itemId: String,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .size(48.dp)
            .semantics { contentDescription = description }
            .ownPanelScrollGestures(true, lockFromDown = true)
            .then(if (pillHorizontal != null) Modifier.drawSlimResizePill(selectionColor, pillHorizontal) else Modifier)
            .pointerInput(itemId) {
                detectDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

private fun Modifier.drawSlimResizePill(color: Color, horizontal: Boolean): Modifier =
    drawBehind {
        val pillW = (if (horizontal) 20.dp else 4.dp).toPx()
        val pillH = (if (horizontal) 4.dp else 20.dp).toPx()
        val left = (size.width - pillW) * 0.5f
        val top = (size.height - pillH) * 0.5f
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(pillW, pillH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(left, top),
            size = Size(pillW, pillH),
            cornerRadius = radius,
            style = Stroke(width = 0.8.dp.toPx())
        )
    }
