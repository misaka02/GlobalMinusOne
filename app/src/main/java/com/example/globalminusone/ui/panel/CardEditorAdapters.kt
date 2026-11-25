package com.example.globalminusone.ui.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.system.SystemController

/** Feature-specific business editors. Every invocation supplies the original explicit target. */
internal interface CardPropertyEditors {
    @Composable fun Component(item: ComponentItem, pageIndex: Int, initialSection: String, onDismiss: () -> Unit)
    @Composable fun LegacyAction(pageIndex: Int, containerId: String, action: QuickActionItem, onDismiss: () -> Unit)
}
internal interface CardDestinationPicker {
    @Composable fun Show(excludedSubtreeId: String?, title: String, onDismiss: () -> Unit, onSelect: (String, String?) -> Unit)
}

@Composable
internal fun rememberCardPropertyEditors(repository: MinusOneRepository, system: SystemController?): CardPropertyEditors = remember(repository, system) {
    object : CardPropertyEditors {
        @Composable override fun Component(item: ComponentItem, pageIndex: Int, initialSection: String, onDismiss: () -> Unit) {
            ComponentEditModal(item, pageIndex, repository, system, initialSection = initialSection, onDismiss = onDismiss)
        }
        @Composable override fun LegacyAction(pageIndex: Int, containerId: String, action: QuickActionItem, onDismiss: () -> Unit) {
            EditContainerActionDialog(repository, pageIndex, containerId, action, onDismiss)
        }
    }
}

@Composable
internal fun rememberCardDestinationPicker(repository: MinusOneRepository): CardDestinationPicker = remember(repository) {
    object : CardDestinationPicker {
        @Composable override fun Show(excludedSubtreeId: String?, title: String, onDismiss: () -> Unit, onSelect: (String, String?) -> Unit) {
            ComponentDestinationDialog(repository, excludedSubtreeId, title, onDismiss, onSelect)
        }
    }
}
