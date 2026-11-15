package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal fun parseExactInteger(text: String, minimum: Int, maximum: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in minimum..maximum }

@Composable
internal fun ExactNumberInput(label: String, value: Int, minimum: Int, maximum: Int,
    onCommit: (Int) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf(value.toString()) }
    val parsed = parseExactInteger(input, minimum, maximum)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val save = { parsed?.let { keyboard?.hide(); onCommit(it); onDismiss() }; Unit }
    PanelAlertDialog(onDismissRequest = { keyboard?.hide(); onDismiss() },
        title = { Text("输入$label") },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        val cleaned = buildString {
                            raw.forEachIndexed { idx, ch ->
                                if (ch.isDigit() || (minimum < 0 && idx == 0 && ch == '-')) append(ch)
                            }
                        }
                        input = cleaned.take(12)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    label = { Text(label) },
                    supportingText = { Text("整数范围：$minimum ～ $maximum") },
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() })
                )
                if (minimum < 0) {
                    FilledTonalButton(
                        onClick = {
                            input = if (input.startsWith("-")) input.drop(1)
                            else if (input == "0") "-" else "-$input"
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("±") }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = save) { Text("确定") } },
        dismissButton = { TextButton(onClick = { keyboard?.hide(); onDismiss() }) { Text("取消") } })
}
