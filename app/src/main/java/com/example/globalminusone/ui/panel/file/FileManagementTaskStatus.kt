package com.example.globalminusone.ui.panel.file

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Display only: operation ownership and cancellation policy stay with the management session. */
@Composable
internal fun FileManagementTaskStatus(busy: Boolean, progress: String?, notice: String?, showNotice: Boolean,
    noticeExpanded: Boolean, onCancel: () -> Unit, onToggleNotice: () -> Unit) {
    if (busy) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
        Text(progress ?: "处理中…", Modifier.weight(1f), fontSize = 11.sp, maxLines = 2)
        FileToolButton(onClick = onCancel) { Text("取消") }
    }
    if (showNotice) notice?.let {
        Text(it, Modifier.fillMaxWidth().clickable(onClick = onToggleNotice).padding(horizontal = 8.dp),
            maxLines = if (noticeExpanded) 4 else 1, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}
