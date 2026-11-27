package com.example.globalminusone.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.policy.PanelOpenPageChoice
import com.example.globalminusone.data.policy.PanelOpenPageMode
import com.example.globalminusone.data.policy.buildPanelOpenPageChoices
import com.example.globalminusone.data.policy.describePanelOpenPageSetting

@Composable
internal fun PanelOpenPagePreferenceRow(
    state: GlobalMinusOneState,
    repository: MinusOneRepository
) {
    var showDialog by remember { mutableStateOf(false) }
    PreferenceItem(
        title = "唤出时默认页面",
        summary = "${describePanelOpenPageSetting(state)}\n可选打开上次离开的面板，或固定打开某一页/子面板",
        onClick = { showDialog = true }
    )
    if (showDialog) {
        PanelOpenPageDialog(
            state = state,
            onChooseLastPage = {
                val saved = repository.updateAdvancedConfig {
                    it.copy(panelOpenPageMode = PanelOpenPageMode.LAST_VISITED)
                }
                if (saved) showDialog = false
            },
            onSelectLockPage = { pageId ->
                val saved = repository.updateAdvancedConfig {
                    it.copy(
                        panelOpenPageMode = PanelOpenPageMode.LOCKED_PAGE,
                        lockedOpenPageId = pageId
                    )
                }
                if (saved) showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun PanelOpenPageDialog(
    state: GlobalMinusOneState,
    onChooseLastPage: () -> Unit,
    onSelectLockPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = remember(state.pages, state.subPanels) { buildPanelOpenPageChoices(state) }
    val mode = state.advancedConfig.panelOpenPageMode
    val lockedId = state.advancedConfig.lockedOpenPageId
    val fallbackRootId = remember(choices) { choices.firstOrNull { !it.isSubPanel }?.pageId.orEmpty() }
    val activeLockedId = if (mode == PanelOpenPageMode.LOCKED_PAGE) {
        choices.firstOrNull { it.pageId == lockedId }?.pageId ?: fallbackRootId
    } else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("唤出时默认页面", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OpenPageRadioRow(
                    title = PanelOpenPageMode.LAST_VISITED.label,
                    subtitle = "保留退出前的页面或子面板位置",
                    selected = mode == PanelOpenPageMode.LAST_VISITED,
                    onClick = onChooseLastPage
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                choices.forEach { choice ->
                    OpenPageChoiceItem(
                        choice = choice,
                        selected = mode == PanelOpenPageMode.LOCKED_PAGE && activeLockedId == choice.pageId,
                        onClick = { onSelectLockPage(choice.pageId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OpenPageChoiceItem(
    choice: PanelOpenPageChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    OpenPageRadioRow(
        title = choice.label,
        subtitle = choice.subtitle,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun OpenPageRadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(text = subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
