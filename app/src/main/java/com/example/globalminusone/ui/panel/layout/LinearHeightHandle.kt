package com.example.globalminusone.ui.panel.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.panel.editor.CardResizeGripMark
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.resizedLinearHeight

@Composable
internal fun LinearHeightHandle(id: String, startRows: Int, unitPx: Float, maxRows: Int,
    modifier: Modifier, onSelect: () -> Unit, onPreview: (Int?) -> Unit, onCommit: (Int) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val start by rememberUpdatedState(startRows)
    val unit by rememberUpdatedState(unitPx)
    val maximum by rememberUpdatedState(maxRows)
    val colors = LocalPanelPalette.current
    DisposableEffect(id) { onDispose { preview(null) } }
    Box(modifier.size(48.dp)
        .semantics { contentDescription = "调整高度 ($id)" }
        .ownPanelScrollGestures(true, lockFromDown = true)
        .pointerInput(id) {
            var initial = 1; var delta = 0f; var final = 1
            detectDragGestures(onDragStart = {
                select(); initial = start; delta = 0f; final = initial; preview(initial)
            }, onDragEnd = { commit(final); preview(null) }, onDragCancel = { preview(null) }) { change, amount ->
                change.consume(); delta += amount.y
                final = resizedLinearHeight(initial, delta, unit, maximum)
                preview(final)
            }
        }, contentAlignment = Alignment.Center) { CardResizeGripMark(colors.accent, verticalOnly = true) }
}
