package com.example.globalminusone.ui.panel

import com.example.globalminusone.data.LayoutMutationResult

import android.appwidget.AppWidgetHostView
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.globalminusone.data.AppAppearanceOverride
import com.example.globalminusone.data.ClipboardClearResult
import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.findComponent
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal interface ClipboardHistoryActions {
    val currentRecords: List<ClipboardRecord>
    fun recordClipboardText(text: String)
    fun togglePinClipboard(id: String)
    fun deleteClipboardRecord(id: String)
    fun clearUnpinnedClipboard(expectedIds: Set<String>): ClipboardClearResult
}
internal interface ClipboardTextAccess {
    fun readCurrentClipboardText(): String?
    fun copyToClipboard(label: String, text: String)
}
@Composable
internal fun rememberClipboardHistoryActions(repository: MinusOneRepository): ClipboardHistoryActions = remember(repository) { clipboardHistoryActions(repository) }

internal fun clipboardHistoryActions(repository: MinusOneRepository): ClipboardHistoryActions =
    object : ClipboardHistoryActions {
        override val currentRecords: List<ClipboardRecord> get() = repository.state.value.clipboardHistory
        override fun recordClipboardText(text: String) = repository.recordClipboardText(text)
        override fun togglePinClipboard(id: String) = repository.togglePinClipboard(id)
        override fun deleteClipboardRecord(id: String) = repository.deleteClipboardRecord(id)
        override fun clearUnpinnedClipboard(expectedIds: Set<String>) = repository.tryClearUnpinnedClipboard(expectedIds)
    }
@Composable
internal fun rememberClipboardTextAccess(system: SystemController): ClipboardTextAccess = remember(system) {
    object : ClipboardTextAccess {
        override fun readCurrentClipboardText() = system.readCurrentClipboardText()
        override fun copyToClipboard(label: String, text: String) = system.copyToClipboard(label, text)
    }
}

internal fun interface WidgetCardViewFactory {
    fun createHostView(context: Context, widgetId: Int): AppWidgetHostView?
}
@Composable
internal fun rememberWidgetCardViewFactory(manager: WidgetHostManager): WidgetCardViewFactory = remember(manager) {
    WidgetCardViewFactory { context, id -> manager.createHostView(context, id) }
}

internal data class AppMenuReadState(val layoutLocked: Boolean, val autoCloseOnWidgetClick: Boolean)
internal interface AppCardMutationAccess {
    val currentLayout: CardLayoutSnapshot
    fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem): Boolean
}
internal interface AppCardMenuActions : AppCardMutationAccess {
    val currentAppearanceOverrides: Map<String, AppAppearanceOverride>
    val appearanceOverrides: Flow<Map<String, AppAppearanceOverride>>
    @Composable fun observeMenuState(): AppMenuReadState
    fun setEditMode(enabled: Boolean): Boolean
    fun selectComponent(componentId: String?)
    fun updateAppAppearance(packageName: String, displayName: String? = null, iconUri: String? = null): Boolean
    fun removeActionFromContainer(pageIndex: Int, containerId: String, actionId: String): Boolean
    fun removeComponent(pageIndex: Int, componentId: String): Boolean
    fun transferComponent(sourcePageId: String, componentId: String, targetPageId: String, targetContainerId: String?, copy: Boolean): Boolean
    fun placeMenuComponent(pageId: String, containerId: String?, item: ComponentItem): Boolean
}
@Composable
internal fun rememberAppCardMenuActions(repository: MinusOneRepository): AppCardMenuActions = remember(repository) {
    object : AppCardMenuActions {
        private val streams = CardStateStreams(repository.state)
        override val currentLayout: CardLayoutSnapshot get() = repository.state.value.cardLayoutSnapshot()
        override val currentAppearanceOverrides: Map<String, AppAppearanceOverride> get() = repository.state.value.appAppearanceOverrides
        override val appearanceOverrides: Flow<Map<String, AppAppearanceOverride>> get() = repository.state.map { it.appAppearanceOverrides }.distinctUntilChanged()
        @Composable override fun observeMenuState(): AppMenuReadState = streams.observeMenu()
        override fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem) = repository.updateComponent(pageIndex, componentId, transform)
        override fun setEditMode(enabled: Boolean) = repository.setEditMode(enabled)
        override fun selectComponent(componentId: String?) = repository.selectComponent(componentId)
        override fun updateAppAppearance(packageName: String, displayName: String?, iconUri: String?) = repository.updateAppAppearance(packageName, displayName, iconUri)
        override fun removeActionFromContainer(pageIndex: Int, containerId: String, actionId: String) = repository.tryRemoveActionFromContainer(pageIndex, containerId, actionId)
        override fun removeComponent(pageIndex: Int, componentId: String): Boolean {
            val pageId = repository.state.value.pages.getOrNull(pageIndex)?.id ?: return false
            return repository.tryRemoveComponent(pageId, componentId) == LayoutMutationResult.APPLIED
        }
        override fun transferComponent(sourcePageId: String, componentId: String, targetPageId: String, targetContainerId: String?, copy: Boolean) = repository.transferComponent(sourcePageId, componentId, targetPageId, targetContainerId, copy)
        override fun placeMenuComponent(pageId: String, containerId: String?, item: ComponentItem) = repository.placeMenuComponent(pageId, containerId, item)
    }
}

/** Legacy action dispatch is a single existing feature; it exposes no repository or system owner. */
internal fun interface LegacyContainerActions {
    fun execute(action: QuickActionItem, context: Context, scope: CoroutineScope, onClose: () -> Unit, onMain: () -> Unit)
}
@Composable
internal fun rememberLegacyContainerActions(repository: MinusOneRepository?, system: SystemController?): LegacyContainerActions = remember(repository, system) {
    LegacyContainerActions { action, context, scope, close, main ->
        executeLegacyContainerAction(action, context,
            repository?.let { repo -> { repo.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }; Unit } },
            system?.let { legacyContainerSystemActions(it) }, scope, close, main)
    }
}
internal interface LegacyContainerSystemActions {
    fun takeRegionScreenshot()
    fun launchScreenAssistant()
    fun launchBreenoTouch()
    fun performTickHaptic()
    fun launchVoiceAssistant()
}

internal fun legacyContainerSystemActions(controller: SystemController): LegacyContainerSystemActions = object : LegacyContainerSystemActions {
    override fun takeRegionScreenshot() { controller.takeRegionScreenshot() }
    override fun launchScreenAssistant() { controller.launchScreenAssistant() }
    override fun launchBreenoTouch() { controller.launchBreenoTouch() }
    override fun performTickHaptic() = controller.performTickHaptic()
    override fun launchVoiceAssistant() = controller.launchVoiceAssistant()
}

/**
 * Resolve stable page/card identities at command time, including arbitrary nested children.
 * Return the repository's write-acceptance result; false keeps the caller's edit draft open.
 * A missing/locked target is rejected before update and never falls back to another location.
 */
internal fun mutateAppMenuCard(actions: AppCardMutationAccess?, pageId: String?, card: ComponentItem?, transform: (ComponentItem) -> ComponentItem): Boolean {
    val access = actions ?: return false
    val state = access.currentLayout
    val pageIndex = state.pages.indexOfFirst { it.id == pageId }
    if (state.layoutLocked || pageIndex < 0 || card == null || state.pages[pageIndex].components.findComponent(card.id) == null) return false
    return access.updateComponent(pageIndex, card.id, transform)
}
/** Resolve the original container/action identity and return actual write acceptance. */
internal fun mutateAppMenuLegacy(actions: AppCardMutationAccess?, pageId: String?, parentId: String, legacyAction: QuickActionItem?, transform: (QuickActionItem) -> QuickActionItem): Boolean {
    val access = actions ?: return false
    val state = access.currentLayout
    val pageIndex = state.pages.indexOfFirst { it.id == pageId }
    val parent = state.pages.getOrNull(pageIndex)?.components?.findComponent(parentId) ?: return false
    if (state.layoutLocked || legacyAction == null || parent.containerActions.none { it.id == legacyAction.id }) return false
    return access.updateComponent(pageIndex, parent.id) { it.copy(containerActions = it.containerActions.map { action ->
        if (action.id == legacyAction.id) transform(action) else action
    }) }
}
internal fun recordCurrentClipboard(history: ClipboardHistoryActions, clipboard: ClipboardTextAccess) {
    clipboard.readCurrentClipboardText()?.let { history.recordClipboardText(it) }
}
internal fun clearClipboardSelection(history: ClipboardHistoryActions, expected: Set<String>): ClipboardClearResult =
    history.clearUnpinnedClipboard(expected)
