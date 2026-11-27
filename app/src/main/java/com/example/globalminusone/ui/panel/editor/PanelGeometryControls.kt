package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.policy.normalizedPanelGeometry
import com.example.globalminusone.ui.panel.PanelAlertDialog

/** Numeric fallback is a transient menu; it never changes the page's measured viewport. */
@Composable
internal fun PanelGeometryControls(theme: ThemeConfig, onCommit: (ThemeConfig) -> Unit, onDismiss: () -> Unit) {
    var number by remember { mutableStateOf<String?>(null) }
    val current by rememberUpdatedState(theme)
    val field = number
    if (field != null) {
        val value = when (field) { "宽度" -> current.panelWidthPercent; "高度" -> current.panelHeightPercent
            "横向位置" -> current.panelHorizontalOffsetPercent; else -> current.panelVerticalPositionPercent }
        ExactNumberInput("面板$field（%）", value, if (field == "宽度" || field == "高度") 30 else 0, 100,
            { edited -> onCommit(when (field) {
                "宽度" -> current.copy(panelWidthPercent = edited)
                "高度" -> current.copy(panelHeightPercent = edited)
                "横向位置" -> current.copy(panelHorizontalOffsetPercent = edited)
                else -> current.copy(panelVerticalPositionPercent = edited)
            }.normalizedPanelGeometry()) }, { number = null })
    } else PanelAlertDialog(onDismissRequest = onDismiss, title = { Text("面板位置与大小") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            listOf("宽度" to current.panelWidthPercent, "高度" to current.panelHeightPercent,
                "横向位置" to current.panelHorizontalOffsetPercent, "纵向位置" to current.panelVerticalPositionPercent).forEach { (label, value) ->
                TextButton(onClick = { number = label }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(label, Modifier.weight(1f)); Text("$value%  ›")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = { onCommit(current.copy(panelWidthPercent = 70, panelHeightPercent = 100,
            panelHorizontalOffsetPercent = 0, panelVerticalPositionPercent = 50)) }) { Text("复位") } })
}
