package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.data.policy.isContainerGridIcon
import com.example.globalminusone.data.policy.isContainerGridIconItem
import com.example.globalminusone.ui.panel.cards.LocalContainerGridIconCell
import com.example.globalminusone.ui.panel.container.LocalFolderEditAction

/** Container content and its trailing layout/menu controls remain reachable while the parent can be dragged. */
@Composable
internal fun BoxScope.cardEditDragTarget(item: ComponentItem): Modifier = when {
    LocalContainerGridIconCell.current && isContainerGridIconItem(item) -> Modifier.matchParentSize()
    LocalFolderEditAction.current != null && !item.type.isContainerType() -> Modifier.fillMaxSize().padding(end = 36.dp)
    !item.type.isContainerType() -> Modifier.matchParentSize()
    item.type == ComponentType.CONTAINER_BOX && item.containerCompact -> Modifier.align(Alignment.TopStart)
        .fillMaxWidth().padding(end = 136.dp).height(40.dp)
    item.showTitle && item.title.isNotBlank() -> Modifier.align(Alignment.TopStart)
        .fillMaxWidth().padding(end = 120.dp).height(32.dp)
    else -> Modifier.align(Alignment.TopStart).size(48.dp)
}.semantics { contentDescription = "长按拖动${item.title.ifBlank { "卡片" }}" }

@Composable
internal fun ContainerDragHint(item: ComponentItem) {
    if (!LocalContainerGridIconCell.current && item.type.isContainerType() && (!item.showTitle || item.title.isBlank())) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("⠿", Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
