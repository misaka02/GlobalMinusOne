package com.example.globalminusone.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** A small visual mark; the caller keeps a separate, generous transparent touch target. */
@Composable
internal fun CardResizeGripMark(color: Color, modifier: Modifier = Modifier, verticalOnly: Boolean = false) {
    Canvas(modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        if (verticalOnly) {
            listOf(.35f, .65f).forEach { y ->
                drawLine(color, Offset(size.width * .2f, size.height * y),
                    Offset(size.width * .8f, size.height * y), stroke, StrokeCap.Round)
            }
        } else {
            drawLine(color, Offset(size.width * .2f, size.height * .8f),
                Offset(size.width * .8f, size.height * .2f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * .55f, size.height * .8f),
                Offset(size.width * .8f, size.height * .55f), stroke, StrokeCap.Round)
        }
    }
}
