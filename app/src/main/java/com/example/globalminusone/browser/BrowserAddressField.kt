package com.example.globalminusone.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Restore the overlay window first, then request the IME after window/editor focus agree. */
@Composable
internal fun BrowserAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    requestHostInput: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    onEditingChanged: (Boolean) -> Unit = {},
    placeholder: String = "搜索或输入网址",
    keyboardType: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Go,
    editorFocusRequester: FocusRequester? = null,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val fallbackRequester = remember { FocusRequester() }
    val requester = editorFocusRequester ?: fallbackRequester
    val hostInput by rememberUpdatedState(requestHostInput)
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, window.isWindowFocused) {
        if (focused && window.isWindowFocused) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    Row(
        modifier = modifier.height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) Box(Modifier.padding(start = 6.dp)) { leadingContent() }
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(8192)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(requester)
                .pointerInput(requester) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        hostInput(); requester.requestFocus()
                    }
                }
                .onFocusChanged { focused = it.isFocused; onEditingChanged(it.isFocused); if (it.isFocused) hostInput() },
            decorationBox = { editor ->
                AddressFieldInnerBox(value, placeholder, focused, hasLeading = leadingContent != null, editor)
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onGo = { onGo() }, onSearch = { onGo() })
        )
    }
}

@Composable
private fun AddressFieldInnerBox(
    value: String,
    placeholder: String,
    focused: Boolean,
    hasLeading: Boolean,
    editor: @Composable () -> Unit
) {
    val startPad = if (hasLeading) 8.dp else 14.dp
    Box(Modifier.fillMaxSize().padding(start = startPad, end = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!focused) {
            Text(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        editor()
    }
}
