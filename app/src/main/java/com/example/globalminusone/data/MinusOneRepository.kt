package com.example.globalminusone.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import com.example.globalminusone.data.operations.ComponentGridOperations
import com.example.globalminusone.data.operations.ComponentInsertionOperations
import com.example.globalminusone.data.operations.ComponentPlacementOperations
import com.example.globalminusone.data.operations.ComponentRemovalOperations
import com.example.globalminusone.data.operations.ComponentTransferOperations
import com.example.globalminusone.data.operations.ComponentUpdateOperations
import com.example.globalminusone.data.operations.ConfigProfileOperations
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.storage.ConfigurationStore
import com.example.globalminusone.data.operations.PageOperations
import com.example.globalminusone.data.policy.PagePresetOption
import com.example.globalminusone.data.operations.PickedCardOperations
import com.example.globalminusone.data.operations.RepositoryStateOperations
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelLibraryOperations
import com.example.globalminusone.data.operations.SubPanelOperations
import com.example.globalminusone.data.operations.SubPanelTemplate
import com.example.globalminusone.data.operations.UserContentOperations
import com.example.globalminusone.data.policy.buildPagePresetConfig
import com.example.globalminusone.data.policy.componentDisplayRows
import com.example.globalminusone.data.operations.componentPath
import com.example.globalminusone.data.policy.containerPopupWidthDp
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.policy.fitPageGridColumns
import com.example.globalminusone.data.policy.nextComponentPlacementY
import com.example.globalminusone.data.policy.normalizedPanelGeometry
import com.example.globalminusone.data.policy.prepareFileShortcutCard
import com.example.globalminusone.data.policy.prepareStatisticsCard
import com.example.globalminusone.data.operations.savedWidgetIds
import com.example.globalminusone.data.operations.transformComponent
import com.example.globalminusone.data.operations.validateComponentTrees
import com.example.globalminusone.data.operations.withRemovedComponentWidgets

class MinusOneRepository internal constructor(prefs: SharedPreferences) {

    private constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    ) {
        val metrics = context.resources.displayMetrics
        measuredPanelWidthDp = metrics.widthPixels / metrics.density * _state.value.themeConfig.panelWidthPercent / 100f
        measuredFontScale = context.resources.configuration.fontScale
    }

    private val configurationStore = ConfigurationStore(prefs)
    private val _corruptStateRecoveredNotice = MutableStateFlow(false)
    val corruptStateRecoveredNotice: StateFlow<Boolean> = _corruptStateRecoveredNotice.asStateFlow()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<GlobalMinusOneState> = _state.asStateFlow()

    fun acknowledgeCorruptStateNotice() {
        _corruptStateRecoveredNotice.value = false
    }
    // A successful restore must refresh long-lived overlay caches even when the
    // restored configuration equals the current state. Never serialize this signal.
    private val _configurationRestoreGeneration = MutableStateFlow(0L)
    val configurationRestoreGeneration: StateFlow<Long> = _configurationRestoreGeneration.asStateFlow()
    private val _imageAccessGeneration = MutableStateFlow(0L)
    val imageAccessGeneration: StateFlow<Long> = _imageAccessGeneration.asStateFlow()
    private val _imageAccessRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val imageAccessRevisions: StateFlow<Map<String, Long>> = _imageAccessRevisions.asStateFlow()
    fun notifyImageAccessChanged(uri: String? = null) {
        val key = uri?.trim().orEmpty()
        if (key.isNotEmpty()) _imageAccessRevisions.update { revisions ->
            revisions + (key to ((revisions[key] ?: 0L) + 1L))
        }
        // Retain the aggregate signal for legacy observers; image renderers use only their URI revision.
        _imageAccessGeneration.update { it + 1 }
    }
    private var panelEditBaseline: PanelEditSnapshot? = null
    private var lastPersistedState: GlobalMinusOneState? = null
    @Volatile private var persistenceNeedsRetry = false
    private val _persistenceError = MutableStateFlow<String?>(null)
    val persistenceError: StateFlow<String?> = _persistenceError.asStateFlow()
    internal var lastPersistenceFailure: Throwable? = null
        private set

    fun acknowledgePersistenceError() { _persistenceError.value = null }

    private fun reportPersistenceFailure() {
        persistenceNeedsRetry = true
        _persistenceError.value = "配置更改未应用，请重试"
    }

    /** The host must not permanently unbind widgets which an active edit can still restore. */
    @Synchronized
    fun retainedWidgetIdsForHost(): Set<Int> = _state.value.savedWidgetIds() + panelEditBaseline?.widgetIds().orEmpty()

    /** Preserve the exact unreadable source before any code path replaces the active preference. */
    @Synchronized
    fun ensureCorruptStateBackup(): CorruptStateBackup? = configurationStore.ensureCorruptStateBackup()

    @Volatile private var measuredPanelWidthDp = 320f
    @Volatile private var measuredPanelWidthPercent = _state.value.themeConfig.panelWidthPercent.coerceAtLeast(1)
    @Volatile private var measuredFontScale = 1f
    /** Transient actual panel width; retained across picker activities, never serialized as user geometry. */
    fun updatePanelLayoutMetrics(widthDp: Float, fontScale: Float) {
        if (widthDp.isFinite() && widthDp > 0f) {
            measuredPanelWidthDp = widthDp
            measuredPanelWidthPercent = _state.value.themeConfig.panelWidthPercent.coerceAtLeast(1)
        }
        if (fontScale.isFinite() && fontScale > 0f) measuredFontScale = fontScale
    }
    private fun nextPlacementY(state: GlobalMinusOneState, page: PageConfig, containerId: String? = null): Int =
        nextComponentPlacementY(page, containerId, state.advancedConfig.gridColumns, measuredPanelWidthDp,
            state.advancedConfig.heightUnitDp, measuredFontScale)

    private val _overlayLayerStatus = MutableStateFlow(OverlayLayerStatus())
    val overlayLayerStatus: StateFlow<OverlayLayerStatus> = _overlayLayerStatus.asStateFlow()
    private val _currentForegroundPackage = MutableStateFlow("")
    val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

    private val _isOverlayServiceRunning = MutableStateFlow(false)
    val isOverlayServiceRunning: StateFlow<Boolean> = _isOverlayServiceRunning.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()
    private val _panelCloseGeneration = MutableStateFlow(0L)
    val panelCloseGeneration: StateFlow<Long> = _panelCloseGeneration.asStateFlow()

    private fun loadInitialState(): GlobalMinusOneState {
        val loaded = configurationStore.loadInitialState()
        _corruptStateRecoveredNotice.value = loaded.corruptRecovered
        return loaded.state
    }

    private fun decodeValidatedState(raw: String): GlobalMinusOneState =
        ConfigurationCodec.decodeValidatedState(raw)

    private fun persist(newState: GlobalMinusOneState): Boolean {
        if (!persistenceNeedsRetry && lastPersistedState == newState) {
            _persistenceError.value = null
            lastPersistenceFailure = null
            return true
        }
        val result = configurationStore.accept(newState)
        if (result.isFailure) {
            lastPersistenceFailure = result.exceptionOrNull()
            reportPersistenceFailure()
            return false
        }
        // apply() accepted the update; it does not confirm an asynchronous disk write.
        lastPersistedState = newState
        persistenceNeedsRetry = false
        _persistenceError.value = null
        lastPersistenceFailure = null
        return true
    }

    @Synchronized
    private fun updateState(resolvePanelEdit: Boolean = false, transform: (GlobalMinusOneState) -> GlobalMinusOneState): Boolean {
        val current = _state.value
        val updated = transform(current).withRemovedComponentWidgets(current)
        if (updated != current || persistenceNeedsRetry) {
            // Only finishPanelEdit may resolve an active draft. Keep its baseline
            // until the resolved configuration has been synchronously accepted.
            if (current.editMode && !updated.editMode && panelEditBaseline != null && !resolvePanelEdit) return false
            val stored = (if (resolvePanelEdit) updated else panelEditBaseline?.restore(updated) ?: updated)
                .copy(editMode = false, selectedComponentId = null)
            if (!persist(stored)) return false
            if (current.editMode && !updated.editMode) panelEditBaseline = null
            _state.value = updated
        }
        return true
    }

    fun setOverlayServiceRunning(running: Boolean) {
        _isOverlayServiceRunning.value = running
    }

    fun setPanelExpanded(expanded: Boolean) {
        _isPanelExpanded.value = expanded
        if (!expanded) _panelCloseGeneration.update { it + 1 }
    }

    fun updateForegroundPackage(packageName: String) {
        _currentForegroundPackage.value = packageName
    }

    fun updateOverlayLayerStatus(status: OverlayLayerStatus) { _overlayLayerStatus.value = status }

    // --- 页面管理 ---

    fun setCurrentPageIndex(index: Int) {
        updateState { current ->
            val clamped = index.coerceIn(0, (current.pages.size - 1).coerceAtLeast(0))
            current.copy(currentPageIndex = clamped, selectedComponentId = null)
        }
    }

    @Synchronized
    fun setEditMode(enabled: Boolean): Boolean {
        if (!enabled) return finishPanelEdit(save = true)
        if (_state.value.editMode) return true
        if (_state.value.advancedConfig.layoutLocked) return false
        panelEditBaseline = PanelEditSnapshot(_state.value)
        val accepted = updateState { current ->
            if (enabled && current.advancedConfig.layoutLocked) return@updateState current
            current.copy(
                editMode = enabled,
                selectedComponentId = if (enabled) current.selectedComponentId else null
            )
        }
        if (!accepted) panelEditBaseline = null
        return accepted
    }

    @Synchronized
    fun hasPanelEditChanges(): Boolean = panelEditBaseline?.changed(_state.value) == true

    @Synchronized
    internal fun beginLinearReorder(pageId: String): LinearReorderSession? {
        val current = _state.value
        val baseline = panelEditBaseline ?: return null
        if (!current.editMode || current.advancedConfig.layoutLocked) return null
        val page = current.pages.firstOrNull { it.id == pageId && it.layoutType == PageLayoutType.LINEAR } ?: return null
        return LinearReorderSession(page.id, baseline, page.components.map { it.id })
    }

    @Synchronized
    internal fun commitLinearReorder(session: LinearReorderSession, order: List<String>?): Boolean {
        val current = _state.value
        if (!current.editMode || current.advancedConfig.layoutLocked || panelEditBaseline !== session.baseline) return false
        val index = current.pages.indexOfFirst { it.id == session.pageId && it.layoutType == PageLayoutType.LINEAR }
        if (index < 0 || order == null) return false
        val page = current.pages[index]
        if (page.components.map { it.id } != session.componentIds || order.size != session.componentIds.size ||
            order.toSet().size != order.size || order.toSet() != session.componentIds.toSet()) return false
        val items = page.components.associateBy { it.id }
        return updateState { state -> state.copy(pages = state.pages.toMutableList().apply {
            this[index] = page.copy(components = order.map { items.getValue(it) })
        }) }
    }

    @Synchronized
    fun finishPanelEdit(save: Boolean): Boolean {
        val baseline = panelEditBaseline
        return updateState(resolvePanelEdit = true) { current ->
            (if (!save && baseline != null) baseline.restore(current) else current)
                .copy(editMode = false, selectedComponentId = null)
        }
    }

    fun selectComponent(componentId: String?) {
        updateState { current ->
            current.copy(selectedComponentId = componentId.takeUnless { current.advancedConfig.layoutLocked })
        }
    }

    /**
     * 新建一页，返回新页在列表中的下标
     */
    fun addPage(layoutType: PageLayoutType, name: String = "", subPanelId: String = ""): Int {
        var newIndex = -1
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (subPanelId.isNotEmpty() && current.subPanels.none { it.id == subPanelId }) return@updateState current
            val ownedCount = current.pages.count { it.subPanelId == subPanelId }
            val newPage = PageOperations.createPage(layoutType, name, ownedCount + 1, subPanelId) { UUID.randomUUID().toString() }
            val updatedPages = current.pages + newPage
            newIndex = updatedPages.size - 1
            current.copy(
                pages = updatedPages,
                currentPageIndex = newIndex,
                selectedComponentId = null
            )
        }
        return if (accepted) newIndex else -1
    }

    /**
     * 使用预置模板新建一页并自动切换过去，受布局锁保护
     */
    fun addPagePreset(preset: PagePresetOption, name: String = "", subPanelId: String = ""): Int {
        if (!preset.isPreset) return addPage(preset.layoutType, name, subPanelId = subPanelId)
        var newIndex = -1
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val cleanName = name.trim()
            val pageNumber = current.pages.size + 1
            val defaultName = "${preset.title} $pageNumber"
            val built = buildPagePresetConfig(
                preset = preset,
                customName = cleanName.ifEmpty { defaultName },
                estimatedGridWidthDp = estimatedPageGridWidthDp()
            ).copy(subPanelId = subPanelId)
            val updatedPages = current.pages + built
            newIndex = updatedPages.size - 1
            current.copy(
                pages = updatedPages,
                currentPageIndex = newIndex,
                selectedComponentId = null
            )
        }
        return if (accepted) newIndex else -1
    }

    private inline fun mutateBool(crossinline transform: (GlobalMinusOneState) -> GlobalMinusOneState?): Boolean {
        var applied = false
        val accepted = updateState { current -> transform(current)?.also { applied = true } ?: current }
        return accepted && applied
    }

    private inline fun <T> mutateWith(crossinline transform: (GlobalMinusOneState) -> Pair<GlobalMinusOneState, T>?): T? {
        var out: T? = null
        val accepted = updateState { current ->
            val res = transform(current) ?: return@updateState current
            out = res.second
            res.first
        }
        return if (accepted) out else null
    }

    fun deletePage(pageIndex: Int): Boolean = mutateBool { current ->
        if (current.advancedConfig.layoutLocked) null
        else PageOperations.delete(current.pages, current.currentPageIndex, pageIndex)?.let {
            current.copy(pages = it.pages, currentPageIndex = it.activeIndex, selectedComponentId = null)
        }
    }

    fun swapPageLeft(pageIndex: Int): Boolean = mutateBool { current ->
        if (current.advancedConfig.layoutLocked) null
        else PageOperations.swapLeft(current.pages, pageIndex)?.let {
            current.copy(pages = it.pages, currentPageIndex = it.activeIndex)
        }
    }

    fun swapPageRight(pageIndex: Int): Boolean = mutateBool { current ->
        if (current.advancedConfig.layoutLocked) null
        else PageOperations.swapRight(current.pages, pageIndex)?.let {
            current.copy(pages = it.pages, currentPageIndex = it.activeIndex)
        }
    }

    fun updatePageConfig(pageIndex: Int, transform: (PageConfig) -> PageConfig): Boolean = mutateBool { current ->
        if (current.advancedConfig.layoutLocked) null
        else PageOperations.update(current.pages, pageIndex, transform)?.let { current.copy(pages = it) }
    }

    fun updatePageGridStyle(pageIndex: Int, itemSizeDp: Int? = null, itemGapDp: Int? = null,
        columnCount: Int? = null, iconSizeDp: Int? = null, rowGapDp: Int? = null,
        rowCount: Int? = null, autoRows: Boolean? = null): Boolean {
        return updatePageConfig(pageIndex) { page ->
            PageOperations.gridStyle(page, itemSizeDp, itemGapDp, columnCount, iconSizeDp, rowGapDp, rowCount, autoRows)
        }
    }

    fun createSubPanel(name: String, inheritGeometry: Boolean = true, defaultLayout: PageLayoutType = PageLayoutType.GRID): SubPanelConfig? =
        mutateWith { SubPanelOperations.create(it, name, inheritGeometry, defaultLayout) { UUID.randomUUID().toString() } }

    fun updateSubPanel(subPanelId: String, transform: (SubPanelConfig) -> SubPanelConfig): Boolean =
        mutateBool { SubPanelOperations.update(it, subPanelId, transform) }

    fun deleteSubPanel(subPanelId: String): Boolean =
        mutateBool { SubPanelOperations.delete(it, subPanelId) }

    fun saveSubPanelToLibrary(subPanelId: String, templateName: String? = null): SubPanelTemplate? =
        mutateWith {
            SubPanelLibraryOperations.saveSubPanelToLibrary(it, subPanelId, templateName, System.currentTimeMillis()) {
                UUID.randomUUID().toString()
            }
        }

    fun createSubPanelFromLibrary(templateId: String, customName: String? = null): SubPanelConfig? =
        mutateWith { SubPanelLibraryOperations.instantiateFromLibrary(it, templateId, customName) { UUID.randomUUID().toString() } }

    fun renameSubPanelTemplate(templateId: String, newName: String): Boolean =
        mutateBool { SubPanelLibraryOperations.renameTemplate(it, templateId, newName) }

    fun deleteSubPanelTemplate(templateId: String): Boolean =
        mutateBool { SubPanelLibraryOperations.deleteTemplate(it, templateId) }

    @Synchronized
    fun fitPageGridColumnCount(pageIndex: Int, columns: Int, availableWidthDp: Float) {
        val pageId = _state.value.pages.getOrNull(pageIndex)?.id ?: return
        tryFitPageGridColumnCount(pageId, columns, availableWidthDp)
    }

    private fun fitPageGridColumnCountAtIndex(pageIndex: Int, columns: Int, availableWidthDp: Float): Boolean {
        if (columns == 0) return updatePageGridStyle(pageIndex, columnCount = 0)
        val fitted = fitPageGridColumns(availableWidthDp, columns)
        return updatePageGridStyle(pageIndex, fitted.itemSizeDp, fitted.itemGapDp, fitted.columnCount, fitted.iconSizeDp)
    }

    fun estimatedPageGridWidthDp(): Float = (measuredPanelWidthDp *
        _state.value.themeConfig.panelWidthPercent.coerceAtLeast(1) / measuredPanelWidthPercent - 20f).coerceAtLeast(64f)

    /**
     * 直接设置指定页的布局类型（线性布局 / 网格布局 / 自由布局）
     */
    @Synchronized
    fun setPageLayoutType(pageIndex: Int, newType: PageLayoutType) {
        val pageId = _state.value.pages.getOrNull(pageIndex)?.id ?: return
        trySetPageLayoutType(pageId, newType)
    }

    private fun setPageLayoutTypeAtIndex(pageIndex: Int, newType: PageLayoutType): Boolean {
        return updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            if (targetPage.layoutType == newType) return@updateState current
            val updatedPage = PageOperations.convertLayout(targetPage, newType, current.advancedConfig.gridColumns)
            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = updatedPage
            current.copy(pages = updatedPages)
        }
    }

    /**
     * 将指定页在 线性布局 -> 网格布局 -> 自由布局 之间循环切换
     */
    @Synchronized
    fun togglePageLayoutType(pageIndex: Int) {
        _state.value.pages.getOrNull(pageIndex)?.id?.let { tryTogglePageLayoutType(it) }
    }

    // --- 组件管理（自由布局、网格布局与线性布局） ---

    /** Called only by the add picker (and explicit reset), not by loading/import/move. */
    fun prepareNewComponent(pageIndex: Int, item: ComponentItem, containerId: String? = null): ComponentItem {
        if (item.type.category != ComponentCategory.STATISTICS && item.type != ComponentType.FILE_SHORTCUT) return item
        val current = _state.value
        val page = current.pages.getOrNull(pageIndex) ?: return item
        val columns = current.advancedConfig.gridColumns
        var width = estimatedPageGridWidthDp()
        if (page.layoutType == PageLayoutType.LINEAR) width = (width - 24f).coerceAtLeast(1f)
        if (containerId != null) {
            val path = page.components.componentPath(containerId) ?: return item
            var siblings = page.components
            for (id in path) {
                val parent = siblings.firstOrNull { it.id == id } ?: return item
                width = if (parent.type == ComponentType.FOLDER) {
                    val preview = if (id == containerId) parent.copy(containerComponents = parent.containerComponents + item) else parent
                    containerPopupWidthDp(preview, measuredPanelWidthDp * 100f /
                        current.themeConfig.panelWidthPercent.coerceAtLeast(1)) - 24f
                } else parent.w.coerceIn(1, columns) * width / columns -
                    parent.marginLeftDp - parent.marginRightDp -
                    parent.paddingLeftDp.coerceAtLeast(8) - parent.paddingRightDp.coerceAtLeast(8)
                width = width.coerceAtLeast(1f)
                siblings = parent.containerComponents
            }
        }
        return if (item.type == ComponentType.FILE_SHORTCUT) prepareFileShortcutCard(item, columns, width,
            current.advancedConfig.heightUnitDp, measuredFontScale) else
            prepareStatisticsCard(item, columns, width, current.advancedConfig.heightUnitDp,
                measuredFontScale, containerId == null && page.layoutType == PageLayoutType.LINEAR)
    }

    /**
     * 向指定页添加组件，自动计算自由布局坐标
     */
    fun addComponentToPage(pageIndex: Int, item: ComponentItem) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            val columns = current.advancedConfig.gridColumns

            val resolvedItem = ComponentPlacementOperations.inPage(targetPage = targetPage, item = item, columns = columns) {
                nextPlacementY(current, targetPage)
            }

            val updatedComponents = targetPage.components + resolvedItem
            if (runCatching { validateComponentTrees(current.pages.mapIndexed { index, page -> if (index == pageIndex) page.copy(components = updatedComponents) else page }) }.isFailure) return@updateState current
            val updatedPage = targetPage.copy(components = updatedComponents)
            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = updatedPage

            current.copy(pages = updatedPages, selectedComponentId = resolvedItem.id)
        }
    }

    fun addComponentToContainer(pageIndex: Int, containerId: String, item: ComponentItem): Boolean {
        var added = false
        val accepted = updateState { current ->
            added = false
            if (current.advancedConfig.layoutLocked || pageIndex !in current.pages.indices) return@updateState current
            val page = current.pages[pageIndex]
            val container = page.components.findComponent(containerId)?.takeIf { it.type.isContainerType() } ?: return@updateState current
            val columns = current.advancedConfig.gridColumns
            val child = ComponentPlacementOperations.inContainer(item = item, columns = columns) {
                nextPlacementY(current, page, container.id)
            }
            val components = page.components.transformComponent(containerId) { it.copy(containerComponents = it.containerComponents + child) }
            val pages = current.pages.toMutableList().apply { this[pageIndex] = page.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            added = true
            current.copy(pages = pages, selectedComponentId = child.id)
        }
        return accepted && added
    }

    fun placeMenuComponent(
        pageId: String,
        containerId: String?,
        item: ComponentItem,
        beforeId: String? = null,
        targetGridCell: Pair<Int, Int>? = null
    ): Boolean {
        var added = false
        val accepted = updateState { current ->
            added = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val index = current.pages.indexOfFirst { it.id == pageId }
            if (index < 0) return@updateState current
            val page = current.pages[index]
            val candidate = ComponentInsertionOperations.placeMenu(
                page = page, containerId = containerId, item = item, beforeId = beforeId,
                columns = current.advancedConfig.gridColumns,
                nextY = { nextPlacementY(current, page, containerId) },
                rows = { placed -> insertionRows(current, placed) },
                targetGridCell = targetGridCell,
                availableWidthDp = estimatedPageGridWidthDp(),
                fontScale = measuredFontScale,
                heightUnitDp = current.advancedConfig.heightUnitDp
            ) ?: return@updateState current
            val pages = current.pages.toMutableList().apply { this[index] = page.copy(components = candidate.components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            added = true
            current.copy(pages = pages, selectedComponentId = candidate.placedId)
        }
        return accepted && added
    }

    private fun insertionRows(state: GlobalMinusOneState, item: ComponentItem): Int {
        val sx = measuredPanelWidthDp / state.advancedConfig.gridColumns
        val sy = state.advancedConfig.heightUnitDp.takeIf { it > 0 }?.toFloat() ?: sx
        return componentDisplayRows(item, item.w * sx, sy, state.advancedConfig.gridColumns,
            state.advancedConfig.heightUnitDp, measuredFontScale).coerceAtLeast(1) + 1
    }

    /** Icon pickers recheck lock and stable page/component identity when returning asynchronously. */
    fun saveComponentIcon(pageId: String, componentId: String, uri: String): Boolean {
        if (!validIconUri(uri)) return false
        var saved = false
        val accepted = updateState { current ->
            saved = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val index = current.pages.indexOfFirst { it.id == pageId }
            if (index < 0 || current.pages[index].components.findComponent(componentId) == null) return@updateState current
            val page = current.pages[index]
            val pages = current.pages.toMutableList().apply {
                this[index] = page.copy(components = page.components.transformComponent(componentId) { it.copy(iconUri = uri) })
            }
            saved = true
            current.copy(pages = pages)
        }
        if (accepted && saved) notifyImageAccessChanged(uri)
        return accepted && saved
    }

    fun updateAppAppearance(packageName: String, displayName: String? = null, iconUri: String? = null): Boolean {
        if (packageName.isBlank() || iconUri != null && iconUri.isNotEmpty() && !validIconUri(iconUri)) return false
        var saved = false
        val accepted = updateState { current ->
            saved = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val old = current.appAppearanceOverrides[packageName] ?: AppAppearanceOverride()
            val edited = old.copy(displayName = displayName?.trim()?.take(100) ?: old.displayName, iconUri = iconUri ?: old.iconUri)
            val values = current.appAppearanceOverrides.toMutableMap()
            if (edited == AppAppearanceOverride()) values.remove(packageName) else values[packageName] = edited
            saved = true
            current.copy(appAppearanceOverrides = values)
        }
        return accepted && saved
    }

    fun saveAppIcon(packageName: String, uri: String): Boolean =
        (validIconUri(uri) && updateAppAppearance(packageName, iconUri = uri)).also { if (it) notifyImageAccessChanged(uri) }

    private fun validIconUri(uri: String): Boolean = runCatching {
        java.net.URI(uri).let { it.scheme == "content" && !it.authority.isNullOrBlank() }
    }.getOrDefault(false)

    /** Atomic move/copy: never falls back to a top-level destination after a container disappears. */
    fun transferComponent(sourcePageId: String, componentId: String, targetPageId: String,
        targetContainerId: String? = null, copy: Boolean = false): Boolean {
        var transferred = false
        val accepted = updateState { current ->
            transferred = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val sourceIndex = current.pages.indexOfFirst { it.id == sourcePageId }
            val targetIndex = current.pages.indexOfFirst { it.id == targetPageId }
            if (sourceIndex < 0 || targetIndex < 0) return@updateState current
            val result = ComponentTransferOperations.transfer(
                originalPages = current.pages, sourceIndex = sourceIndex, targetIndex = targetIndex,
                componentId = componentId, targetContainerId = targetContainerId, copy = copy,
                columns = current.advancedConfig.gridColumns,
                nextY = { target, containerId -> nextPlacementY(current, target, containerId) }
            ) ?: return@updateState current
            val pages = result.pages
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            transferred = true
            current.copy(pages = pages, selectedComponentId = result.placedId)
        }
        return accepted && transferred
    }

    fun saveFileShortcut(pageId: String, componentId: String?, title: String, uri: String, containerId: String? = null, beforeId: String? = null, targetGridCell: Pair<Int, Int>? = null): Boolean =
        savePickedCard(pageId, componentId, title, uri, ComponentType.FILE_SHORTCUT, containerId, beforeId, targetGridCell = targetGridCell)

    fun saveImageCard(pageId: String, componentId: String?, title: String, uri: String, containerId: String? = null, beforeId: String? = null, targetGridCell: Pair<Int, Int>? = null): Boolean =
        savePickedCard(pageId, componentId, title, uri, ComponentType.SINGLE_IMAGE, containerId, beforeId, targetGridCell = targetGridCell)
            .also { if (it) notifyImageAccessChanged(uri) }

    fun savePhotoCube(pageId: String, componentId: String?, uris: List<String>, containerId: String? = null, beforeId: String? = null, targetGridCell: Pair<Int, Int>? = null): Boolean {
        val images = uris.distinct().take(100)
        if (images.isEmpty() || images.any { !PickedCardOperations.validContentUri(it) }) return false
        return savePickedCard(pageId, componentId, "图片魔方", images.first(), ComponentType.PHOTO_CUBE, containerId, beforeId, images, targetGridCell)
            .also { saved -> if (saved) images.forEach(::notifyImageAccessChanged) }
    }

    /** Recheck the destination when the system picker returns, rather than trusting old indices. */
    private fun savePickedCard(pageId: String, componentId: String?, title: String, uri: String, type: ComponentType, containerId: String?, beforeId: String?, uris: List<String> = emptyList(), targetGridCell: Pair<Int, Int>? = null): Boolean {
        if (!PickedCardOperations.validContentUri(uri)) return false
        var saved = false
        val accepted = updateState { current ->
            saved = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val pageIndex = current.pages.indexOfFirst { it.id == pageId }
            if (pageIndex < 0) return@updateState current
            val page = current.pages[pageIndex]
            val target = PickedCardOperations.resolveTarget(page, componentId, containerId, type) ?: return@updateState current
            val columns = current.advancedConfig.gridColumns
            val picked = PickedCardOperations.build(
                page = page, target = target, type = type, title = title, uri = uri, uris = uris, columns = columns,
                nextY = { targetContainerId -> nextPlacementY(current, page, targetContainerId) },
                prepareNewFile = { base -> prepareNewComponent(pageIndex, base, containerId) }
            )
            val components = if (target.existing != null) page.components.transformComponent(target.existing.id) { picked }
            else ComponentInsertionOperations.insert(
                page, target.container, picked, beforeId, columns, insertionRows(current, picked),
                targetGridCell, estimatedPageGridWidthDp(), measuredFontScale, current.advancedConfig.heightUnitDp
            ) ?: return@updateState current
            val pages = current.pages.toMutableList().apply { this[pageIndex] = page.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            saved = true
            current.copy(pages = pages, currentPageIndex = pageIndex, selectedComponentId = picked.id)
        }
        return accepted && saved
    }

    /** Resolve stable identities and execute while holding the same monitor as updateState. */
    @Synchronized
    private fun acceptLayoutMutation(pageId: String, componentId: String? = null,
        write: (Int) -> Boolean): LayoutMutationResult {
        val state = _state.value
        if (state.advancedConfig.layoutLocked) return LayoutMutationResult.TARGET_CHANGED
        val index = state.pages.indexOfFirst { it.id == pageId }
        if (index < 0 || componentId != null && state.pages[index].components.findComponent(componentId) == null)
            return LayoutMutationResult.TARGET_CHANGED
        return if (write(index)) LayoutMutationResult.APPLIED else LayoutMutationResult.WRITE_REJECTED
    }

    internal fun tryRemoveComponent(pageId: String, id: String): LayoutMutationResult =
        acceptLayoutMutation(pageId, id) { removeComponent(it, id) }

    internal fun tryResetComponentStyle(pageId: String, id: String): LayoutMutationResult =
        acceptLayoutMutation(pageId, id) { resetComponentStyleAtIndex(it, id) }

    internal fun trySetPageLayoutType(pageId: String, type: PageLayoutType): LayoutMutationResult =
        acceptLayoutMutation(pageId) { setPageLayoutTypeAtIndex(it, type) }

    internal fun tryTogglePageLayoutType(pageId: String): LayoutMutationResult =
        acceptLayoutMutation(pageId) { index ->
            val next = when (_state.value.pages[index].layoutType) {
                PageLayoutType.LINEAR -> PageLayoutType.GRID
                PageLayoutType.GRID -> PageLayoutType.FREE
                PageLayoutType.FREE -> PageLayoutType.LINEAR
            }
            setPageLayoutTypeAtIndex(index, next)
        }

    internal fun tryFitPageGridColumnCount(pageId: String, columns: Int, width: Float): LayoutMutationResult =
        acceptLayoutMutation(pageId) { fitPageGridColumnCountAtIndex(it, columns, width) }

    internal fun tryUpdateComponent(pageId: String, id: String, transform: (ComponentItem) -> ComponentItem): LayoutMutationResult =
        acceptLayoutMutation(pageId, id) { updateComponent(it, id, transform) }

    /**
     * 更新组件（实时拖动网格坐标、尺寸与属性修改）
     */
    fun updateComponent(pageIndex: Int, componentId: String, transform: (ComponentItem) -> ComponentItem): Boolean {
        var valid = false
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            if (targetPage.components.findComponent(componentId) == null) return@updateState current
            val columns = current.advancedConfig.gridColumns

            val updatedComponents = runCatching {
                ComponentUpdateOperations.transform(targetPage.components, componentId, columns, transform)
            }.getOrElse { return@updateState current }

            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = targetPage.copy(components = updatedComponents)
            if (runCatching { validateComponentTrees(updatedPages) }.isFailure) return@updateState current
            valid = true
            current.copy(pages = updatedPages, selectedComponentId = current.selectedComponentId?.takeIf { id -> updatedPages.any { it.components.findComponent(id) != null } })
        }
        return accepted && valid
    }

    /**
     * 移除组件；在网格页或网格容器中若存在 2D 固定坐标兄弟项，先固化剩余兄弟项坐标以防空位塌缩。
     */
    @Synchronized
    fun removeComponent(pageIndex: Int, componentId: String): Boolean {
        // Legacy callers observe storage acceptance, including no-ops. Keep the dangling
        // selection cleanup below; UI commands use tryRemoveComponent for target outcomes.
        return updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            val gridWidth = estimatedPageGridWidthDp()
            val updatedComponents = ComponentRemovalOperations.remove(
                items = targetPage.components,
                targetId = componentId,
                isGridScope = targetPage.layoutType == PageLayoutType.GRID,
                pageConfig = targetPage,
                gridColumns = current.advancedConfig.gridColumns,
                heightUnitDp = current.advancedConfig.heightUnitDp,
                fontScale = { measuredFontScale },
                pageGridWidthDp = gridWidth
            )
            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = targetPage.copy(components = updatedComponents)
            current.copy(
                pages = updatedPages,
                selectedComponentId = current.selectedComponentId?.takeIf { id -> updatedPages.any { it.components.findComponent(id) != null } }
            )
        }
    }


    /**
     * 线性布局中上下移动卡片顺序
     */
    fun moveComponentOrderInLinearPage(pageIndex: Int, componentId: String, moveUp: Boolean) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            val list = targetPage.components.toMutableList()
            val idx = list.indexOfFirst { it.id == componentId }
            if (idx == -1) return@updateState current
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in list.indices) return@updateState current
            val temp = list[idx]
            list[idx] = list[targetIdx]
            list[targetIdx] = temp

            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = targetPage.copy(components = list)
            current.copy(pages = updatedPages)
        }
    }

    /** Places a dragged card at an arbitrary 2D cell in a GRID page while preserving gaps. */
    fun placeComponentInPageGrid(
        pageIndex: Int,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        availableWidthDp: Float = estimatedPageGridWidthDp(),
        fontScale: Float = measuredFontScale
    ): Boolean {
        return updatePageConfig(pageIndex) { page ->
            if (page.layoutType != PageLayoutType.GRID) return@updatePageConfig page
            ComponentGridOperations.placeComponentInPageGrid(
                page = page, movedId = movedId, targetCol = targetCol, targetRow = targetRow,
                availableWidthDp = availableWidthDp, fontScale = fontScale,
                gridColumns = _state.value.advancedConfig.gridColumns,
                heightUnitDp = _state.value.advancedConfig.heightUnitDp
            )
        }
    }

    /** Moves [movedId] by ([deltaCol], [deltaRow]) cells in a GRID page using 2D placement. */
    fun nudgeComponentInPageGrid(
        pageIndex: Int,
        movedId: String,
        deltaCol: Int,
        deltaRow: Int,
        availableWidthDp: Float = estimatedPageGridWidthDp(),
        fontScale: Float = measuredFontScale
    ): Boolean {
        return updatePageConfig(pageIndex) { page ->
            if (page.layoutType != PageLayoutType.GRID) return@updatePageConfig page
            ComponentGridOperations.nudgeComponentInPageGrid(
                page = page, movedId = movedId, deltaCol = deltaCol, deltaRow = deltaRow,
                availableWidthDp = availableWidthDp, fontScale = fontScale,
                gridColumns = _state.value.advancedConfig.gridColumns,
                heightUnitDp = _state.value.advancedConfig.heightUnitDp
            )
        }
    }

    /** Compacts a GRID page back to top-left flowing order by clearing manual 2D cell pins. */
    fun compactPageGridPlacements(
        pageIndex: Int,
        availableWidthDp: Float = estimatedPageGridWidthDp(),
        fontScale: Float = measuredFontScale
    ): Boolean {
        return updatePageConfig(pageIndex) { page ->
            if (page.layoutType != PageLayoutType.GRID) return@updatePageConfig page
            ComponentGridOperations.compactPageGridPlacements(
                page = page, availableWidthDp = availableWidthDp, fontScale = fontScale,
                gridColumns = _state.value.advancedConfig.gridColumns,
                heightUnitDp = _state.value.advancedConfig.heightUnitDp
            )
        }
    }

    /** Places a dragged child at an arbitrary 2D cell inside a GRID folder or container. */
    fun placeChildInContainerGrid(
        pageIndex: Int,
        containerId: String,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        widthDp: Float,
        stepYDp: Float,
        iconColumns: Int,
        fontScale: Float = measuredFontScale
    ): Boolean {
        return updateComponent(pageIndex, containerId) { container ->
            if (container.containerLayout != ContainerLayoutType.GRID) return@updateComponent container
            ComponentGridOperations.placeChildInContainerGrid(
                container = container, movedId = movedId, targetCol = targetCol, targetRow = targetRow,
                widthDp = widthDp, stepYDp = stepYDp, iconColumns = iconColumns, fontScale = fontScale,
                gridColumns = _state.value.advancedConfig.gridColumns,
                heightUnitDp = _state.value.advancedConfig.heightUnitDp
            )
        }
    }

    /** Places or swaps a legacy QuickActionItem at an arbitrary 2D cell inside a GRID container. */
    fun placeActionInContainerGrid(
        pageIndex: Int,
        containerId: String,
        movedId: String,
        targetCol: Int,
        targetRow: Int,
        iconColumns: Int
    ): Boolean {
        return updateComponent(pageIndex, containerId) { container ->
            if (container.containerLayout != ContainerLayoutType.GRID) return@updateComponent container
            ComponentGridOperations.placeActionInContainerGrid(
                container = container, movedId = movedId,
                targetCol = targetCol, targetRow = targetRow, iconColumns = iconColumns
            )
        }
    }

    /** Compacts a GRID folder/container back to sequential top-left flowing order. */
    fun compactContainerGridPlacements(
        pageIndex: Int,
        containerId: String,
        widthDp: Float,
        stepYDp: Float,
        iconColumns: Int,
        fontScale: Float = measuredFontScale
    ): Boolean {
        return updateComponent(pageIndex, containerId) { container ->
            if (container.containerLayout != ContainerLayoutType.GRID) return@updateComponent container
            ComponentGridOperations.compactContainerGridPlacements(
                container = container, widthDp = widthDp, stepYDp = stepYDp,
                iconColumns = iconColumns, fontScale = fontScale,
                gridColumns = _state.value.advancedConfig.gridColumns,
                heightUnitDp = _state.value.advancedConfig.heightUnitDp
            )
        }
    }

    /**
     * 重置组件样式为全局默认
     */
    @Synchronized
    fun resetComponentStyle(pageIndex: Int, componentId: String) {
        val pageId = _state.value.pages.getOrNull(pageIndex)?.id ?: return
        tryResetComponentStyle(pageId, componentId)
    }

    private fun resetComponentStyleAtIndex(pageIndex: Int, componentId: String): Boolean {
        return updateComponent(pageIndex, componentId) { comp ->
            comp.copy(
                bgColorHex = null,
                bgAlphaPercent = -1,
                textColorHex = null,
                textAlphaPercent = -1,
                cornerRadiusDp = -1,
                rotationDeg = 0,
                paddingTopDp = 6,
                paddingBottomDp = 6,
                paddingLeftDp = 8,
                paddingRightDp = 8,
                marginTopDp = 2,
                marginBottomDp = 2,
                marginLeftDp = 2,
                marginRightDp = 2
            )
        }
    }

    /**
     * 容器卡片管理（添加、移除、清空子项）
     */
    fun addActionToContainer(pageIndex: Int, containerId: String, action: QuickActionItem) {
        updateComponent(pageIndex, containerId) { comp ->
            if (!comp.type.isContainerType()) comp else comp.copy(containerActions = comp.containerActions + action)
        }
    }

    /** Preserve legacy action identity and validate the original container under the transaction lock. */
    @Synchronized
    fun tryUpdateContainerAction(pageIndex: Int, containerId: String, actionId: String,
        transform: (QuickActionItem) -> QuickActionItem): Boolean {
        val current = _state.value
        val target = current.pages.getOrNull(pageIndex)?.components?.findComponent(containerId) ?: return false
        if (current.advancedConfig.layoutLocked || !target.type.isContainerType() || target.containerActions.none { it.id == actionId }) return false
        return updateComponent(pageIndex, containerId) { container ->
            container.copy(containerActions = container.containerActions.map {
                if (it.id == actionId) transform(it).copy(id = it.id) else it
            })
        }
    }

    fun removeActionFromContainer(pageIndex: Int, containerId: String, actionId: String) { tryRemoveActionFromContainer(pageIndex, containerId, actionId) }

    @Synchronized
    fun tryRemoveActionFromContainer(pageIndex: Int, containerId: String, actionId: String): Boolean {
        val current = _state.value
        val target = current.pages.getOrNull(pageIndex)?.components?.findComponent(containerId) ?: return false
        if (current.advancedConfig.layoutLocked || !target.type.isContainerType() || target.containerActions.none { it.id == actionId }) return false
        return updateComponent(pageIndex, containerId) { comp ->
            if (!comp.type.isContainerType()) comp else comp.copy(containerActions = comp.containerActions.filterNot { it.id == actionId })
        }
    }

    fun clearContainerActions(pageIndex: Int, containerId: String) { tryClearContainerActions(pageIndex, containerId) }

    @Synchronized
    fun tryClearContainerActions(pageIndex: Int, containerId: String): Boolean {
        val current = _state.value
        val target = current.pages.getOrNull(pageIndex)?.components?.findComponent(containerId) ?: return false
        if (current.advancedConfig.layoutLocked || !target.type.isContainerType()) return false
        return updateComponent(pageIndex, containerId) { comp ->
            if (!comp.type.isContainerType()) comp else comp.copy(containerActions = emptyList(), containerComponents = emptyList())
        }
    }

    // --- 触发设置、悬浮球、主题与高级设置 ---

    fun updateTriggerConfig(transform: (TriggerConfig) -> TriggerConfig): Boolean {
        return updateState { current ->
            val updated = transform(current.triggerConfig)
            current.copy(triggerConfig = updated.copy(
                minimumSwipeDistanceDp = updated.minimumSwipeDistanceDp.coerceIn(16, 160),
                sidePullHorizontalOffsetDp = updated.sidePullHorizontalOffsetDp.coerceIn(-1, 160)
            ))
        }
    }

    fun updateSuspensionBallConfig(transform: (SuspensionBallConfig) -> SuspensionBallConfig): Boolean {
        return updateState { it.copy(suspensionBallConfig = transform(it.suspensionBallConfig)) }
    }

    fun updateThemeConfig(transform: (ThemeConfig) -> ThemeConfig): Boolean {
        return updateState { current ->
            val theme = transform(current.themeConfig).normalizedPanelGeometry()
            current.copy(themeConfig = theme, appearanceConfig = current.appearanceConfig.copy(
                panelWidthFraction = theme.panelWidthPercent / 100f,
                cardCornerRadiusDp = theme.cardCornerRadiusDp,
                cardSpacingDp = theme.cardSpacingDp,
                backgroundBlurRadius = theme.backgroundBlurRadius
            ))
        }
    }

    fun applyLightDarkPreset(
        darkMode: Boolean,
        cardAlpha: Int = if (darkMode) 88 else 95,
        panelAlpha: Int = 0
    ) {
        updateThemeConfig { current ->
            RepositoryStateOperations.lightDarkPresetTheme(current, darkMode, cardAlpha, panelAlpha)
        }
    }

    /** Layout locking is changed only through setLayoutLocked, independently of other preferences. */
    fun updateAdvancedConfig(transform: (AdvancedConfig) -> AdvancedConfig): Boolean {
        return updateState { current ->
            val updated = transform(current.advancedConfig)
            current.copy(advancedConfig = updated.copy(
                layoutLocked = current.advancedConfig.layoutLocked,
                gridColumns = if (current.advancedConfig.layoutLocked) current.advancedConfig.gridColumns else updated.gridColumns.coerceIn(8, 64)
            ))
        }
    }

    @Synchronized
    fun setLayoutLocked(locked: Boolean): Boolean {
        if (locked && _state.value.editMode) {
            if (hasPanelEditChanges()) return false
            if (!finishPanelEdit(save = true)) return false
        }
        return updateState { current ->
            current.copy(
                advancedConfig = current.advancedConfig.copy(layoutLocked = locked),
                editMode = if (locked) false else current.editMode,
                selectedComponentId = if (locked) null else current.selectedComponentId
            )
        }
    }

    /** Content remains usable while card placement and appearance are locked. */
    fun updateComponentText(pageIndex: Int, componentId: String, text: String): Boolean {
        return updateState { current ->
            if (pageIndex !in current.pages.indices) return@updateState current
            val pages = current.pages.toMutableList()
            pages[pageIndex] = pages[pageIndex].copy(components = pages[pageIndex].components.transformComponent(componentId) { it.copy(textContent = text) })
            current.copy(pages = pages)
        }
    }

    fun updateFileBrowserConfig(transform: (FileBrowserConfig) -> FileBrowserConfig): Boolean {
        return updateState { it.copy(fileBrowserConfig = transform(it.fileBrowserConfig)) }
    }

    fun updateAppearanceConfig(transform: (AppearanceConfig) -> AppearanceConfig): Boolean {
        return updateState { current ->
            val updated = transform(current.appearanceConfig)
            val theme = current.themeConfig.copy(
                panelWidthPercent = (updated.panelWidthFraction * 100).toInt(),
                cardCornerRadiusDp = updated.cardCornerRadiusDp,
                cardSpacingDp = updated.cardSpacingDp,
                backgroundBlurRadius = updated.backgroundBlurRadius
            ).normalizedPanelGeometry()
            current.copy(
                appearanceConfig = updated.copy(panelWidthFraction = theme.panelWidthPercent / 100f),
                themeConfig = theme
            )
        }
    }

    /**
     * 临时关闭触发条
     */
    fun tempDisableTrigger(durationMs: Long = 60_000L) {
        val until = System.currentTimeMillis() + durationMs
        updateTriggerConfig { it.copy(tempDisabledUntilMs = until) }
    }

    /**
     * 立即恢复触发条
     */
    fun resumeTriggerNow() {
        updateTriggerConfig { it.copy(tempDisabledUntilMs = 0L) }
    }

    // --- 多配置方案管理 ---

    @Synchronized
    fun createConfigProfile(name: String, author: String = "默认", description: String = ""): ConfigProfile {
        check(!_state.value.editMode) { "请先保存或取消当前编辑，再创建方案" }
        try {
            val profileId = UUID.randomUUID().toString()
            val updatedAt = System.currentTimeMillis()
            val profile = ConfigProfileOperations.createSnapshot(
                _state.value, profileId, name, author, description, updatedAt
            )
            val accepted = updateState { current ->
                ConfigProfileOperations.add(current, profile)
            }
            if (!accepted) throw IllegalStateException("配置方案未创建，请重试", lastPersistenceFailure)
            return profile
        } catch (failure: Exception) {
            if (lastPersistenceFailure == null || failure.cause !== lastPersistenceFailure) {
                lastPersistenceFailure = failure
            }
            reportPersistenceFailure()
            throw failure
        }
    }

    fun applyConfigProfile(profileId: String): Boolean {
        if (_state.value.advancedConfig.layoutLocked || _state.value.editMode) return false
        val profile = _state.value.profiles.find { it.id == profileId } ?: return false
        return try {
            val parsed = ConfigProfileOperations.decodeSnapshot(profile)
            var applied = false
            val accepted = updateState { current ->
                applied = !current.advancedConfig.layoutLocked && !current.editMode
                if (!applied) return@updateState current
                ConfigProfileOperations.apply(current, profileId, parsed)
            }
            accepted && applied
        } catch (_: Exception) {
            false
        }
    }

    fun deleteConfigProfile(profileId: String): Boolean {
        var success = false
        val accepted = updateState { current ->
            if (current.activeProfileId == profileId) {
                return@updateState current // 无法删除正在使用的配置
            }
            success = true
            current.copy(profiles = ConfigProfileOperations.delete(current.profiles, profileId))
        }
        return accepted && success
    }

    fun exportConfigCode(): String {
        return ConfigurationCodec.encode(_state.value, pretty = true)
    }

    /** Imports append only; picker targets are stable IDs and never fall back to another parent. */
    @Synchronized
    fun appendImportedPages(pages: List<PageConfig>): Boolean {
        if (pages.isEmpty() || _state.value.advancedConfig.layoutLocked || _state.value.editMode) return false
        val current = _state.value
        val combined = current.pages + pages
        if (combined.map { it.id }.any { it.isBlank() } || combined.map { it.id }.distinct().size != combined.size) return false
        if (runCatching { validateComponentTrees(combined) }.isFailure) return false
        return commitImportedState(current.copy(pages = combined))
    }

    @Synchronized
    fun appendImportedFolder(folder: ComponentItem, targetPageId: String, targetContainerId: String? = null): Boolean {
        val current = _state.value
        val updated = RepositoryStateOperations.appendImportedFolderState(
            current = current,
            folder = folder,
            targetPageId = targetPageId,
            targetContainerId = targetContainerId,
            nextY = { page, cid -> nextPlacementY(current, page, cid) }
        ) ?: return false
        return commitImportedState(updated)
    }

    @Synchronized
    fun appendImportedContainers(
        containers: List<ComponentItem>,
        targetPageId: String,
        targetContainerId: String? = null
    ): Boolean {
        val current = _state.value
        val updated = RepositoryStateOperations.appendImportedContainersState(
            current = current,
            containers = containers,
            targetPageId = targetPageId,
            targetContainerId = targetContainerId,
            nextY = { page, cid -> nextPlacementY(current, page, cid) }
        ) ?: return false
        return commitImportedState(updated)
    }

    private fun commitImportedState(value: GlobalMinusOneState): Boolean {
        ensureCorruptStateBackup()
        val previous = configurationStore.readRaw()
        val committed = runCatching { configurationStore.commitState(value) }.getOrDefault(false)
        if (!committed) {
            reportPersistenceFailure()
            val restored = runCatching { configurationStore.restoreRaw(previous) }.getOrDefault(false)
            check(restored) { "写入失败且回滚失败；请保留恢复备份并检查存储空间" }
            return false
        }
        panelEditBaseline = null
        lastPersistedState = value
        persistenceNeedsRetry = false
        _persistenceError.value = null
        lastPersistenceFailure = null
        configurationStore.clearVerifiedCorruptBackup()
        _corruptStateRecoveredNotice.value = false
        _state.value = value
        return true
    }

    /** Called on IO. Keep state publication after all disk writes; a failed commit is never success. */
    @Synchronized
    fun restoreNativeBackup(stateJson: String, commitCompanion: () -> Unit, rollbackCompanion: () -> Unit) {
        check(!_state.value.advancedConfig.layoutLocked) { "请先解锁布局，再恢复备份" }
        check(!_state.value.editMode) { "请先保存或取消当前编辑，再恢复备份" }
        val parsed = decodeValidatedState(stateJson)
        com.example.globalminusone.data.backup.NativeBackupCodec.validateState(parsed)
        ensureCorruptStateBackup()
        val oldRaw = configurationStore.readRaw()
        try {
            commitCompanion()
            check(configurationStore.commitState(parsed)) { "配置写入失败" }
        } catch (failure: Exception) {
            reportPersistenceFailure()
            val problems = mutableListOf<String>()
            runCatching {
                check(configurationStore.restoreRaw(oldRaw))
            }.onFailure { problems += "配置回滚失败" }
            runCatching(rollbackCompanion).onFailure { problems += "浏览器设置回滚失败" }
            if (problems.isNotEmpty()) throw IllegalStateException("${problems.joinToString()}；请使用恢复前备份", failure)
            throw failure
        }
        panelEditBaseline = null
        lastPersistedState = parsed
        persistenceNeedsRetry = false
        _persistenceError.value = null
        lastPersistenceFailure = null
        configurationStore.clearVerifiedCorruptBackup()
        _corruptStateRecoveredNotice.value = false
        _state.value = parsed
        _configurationRestoreGeneration.update { it + 1 }
    }

    fun exportConfigProfileCode(profileId: String): String? {
        val profile = _state.value.profiles.find { it.id == profileId } ?: return null
        // 内置默认方案没有快照，沿用应用时的语义：使用当前配置。
        if (profile.stateJson.isBlank()) return exportConfigCode()
        return try {
            ConfigProfileOperations.exportSnapshot(profile)
        } catch (_: Exception) {
            null
        }
    }

    fun importConfigCode(code: String): Boolean {
        return importConfigCodeResult(code).isSuccess
    }

    /** Import UI calls on IO; success acknowledges disk persistence instead of apply(). */
    @Synchronized
    fun importConfigCodeResult(code: String): Result<Unit> {
        return runCatching {
            check(!_state.value.advancedConfig.layoutLocked) { "请先解锁布局，再导入配置" }
            check(!_state.value.editMode) { "请先保存或取消当前编辑，再导入配置" }
            val parsed = decodeValidatedState(code.trim())
            com.example.globalminusone.data.backup.NativeBackupCodec.validateState(parsed)
            check(commitImportedState(parsed)) { "配置写入失败，原配置已保留" }
        }
    }

    // --- AppWidget 管理 ---

    fun addBoundWidget(widgetInfo: BoundWidgetInfo, pageId: String? = null, containerId: String? = null, beforeId: String? = null, targetGridCell: Pair<Int, Int>? = null): Boolean {
        var added = false
        val accepted = updateState { current ->
            added = false
            RepositoryStateOperations.addBoundWidgetState(
                current, widgetInfo, pageId, containerId, beforeId,
                { page, cid -> nextPlacementY(current, page, cid) },
                { widget -> insertionRows(current, widget) },
                targetGridCell, estimatedPageGridWidthDp(), measuredFontScale
            )?.also { added = true } ?: current
        }
        return accepted && added
    }

    fun updateWidgetHeight(appWidgetId: Int, heightDp: Int) {
        updateState { current -> RepositoryStateOperations.updateWidgetHeightState(current, appWidgetId, heightDp) }
    }

    // --- 快捷方式与应用项 ---

    fun addQuickAction(item: QuickActionItem) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(quickActions = current.quickActions + item)
        }
    }

    fun updateQuickAction(item: QuickActionItem) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(quickActions = current.quickActions.map { if (it.id == item.id) item else it })
        }
    }

    fun removeQuickAction(id: String) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(quickActions = current.quickActions.filterNot { it.id == id })
        }
    }

    fun toggleFavoriteApp(packageName: String) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val updated = if (packageName in current.favoriteApps) {
                current.favoriteApps - packageName
            } else {
                current.favoriteApps + packageName
            }
            current.copy(favoriteApps = updated)
        }
    }

    fun setFavoriteApps(packages: List<String>) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(favoriteApps = packages.distinct())
        }
    }

    fun toggleBlacklistApp(packageName: String) {
        updateAdvancedConfig { cfg ->
            val updated = if (packageName in cfg.blacklistPackages) {
                cfg.blacklistPackages - packageName
            } else {
                cfg.blacklistPackages + packageName
            }
            cfg.copy(blacklistPackages = updated)
        }
    }

    // --- 待办事项、便签与剪贴板 ---

    fun addTodo(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        updateState { current ->
            val item = TodoItem(id = UUID.randomUUID().toString(), content = trimmed, done = false)
            current.copy(todos = UserContentOperations.addTodo(current.todos, item))
        }
    }

    fun toggleTodo(id: String) {
        updateState { current ->
            current.copy(todos = UserContentOperations.toggleTodo(current.todos, id))
        }
    }

    fun deleteTodo(id: String) {
        updateState { current ->
            current.copy(todos = UserContentOperations.deleteTodo(current.todos, id))
        }
    }

    fun updateNoteText(text: String) {
        updateState { it.copy(noteText = text) }
    }

    fun recordClipboardText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        updateState { current ->
            current.copy(clipboardHistory = UserContentOperations.recordClipboard(
                current.clipboardHistory, trimmed,
                refreshTimestamp = System::currentTimeMillis,
                createRecord = { ClipboardRecord(text = it) }
            ))
        }
    }

    fun togglePinClipboard(id: String) {
        updateState { current ->
            current.copy(clipboardHistory = UserContentOperations.togglePinClipboard(current.clipboardHistory, id))
        }
    }

    fun deleteClipboardRecord(id: String) {
        updateState { current ->
            current.copy(clipboardHistory = UserContentOperations.deleteClipboard(current.clipboardHistory, id))
        }
    }

    fun clearUnpinnedClipboard() { tryClearUnpinnedClipboard() }

    @Synchronized
    internal fun tryClearUnpinnedClipboard(expectedIds: Set<String>? = null): ClipboardClearResult {
        if (expectedIds != null && (expectedIds.isEmpty() ||
                _state.value.clipboardHistory.filterNot { it.pinned }.map { it.id }.toSet() != expectedIds))
            return ClipboardClearResult.SELECTION_CHANGED
        val accepted = updateState { current ->
            current.copy(clipboardHistory = UserContentOperations.clearUnpinnedClipboard(current.clipboardHistory))
        }
        return if (accepted) ClipboardClearResult.ACCEPTED else ClipboardClearResult.SAVE_REJECTED
    }

    // --- 兼容旧版卡片操作 ---

    fun toggleCardEnabled(cardId: String, enabled: Boolean) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(cards = current.cards.map { if (it.id == cardId) it.copy(enabled = enabled) else it })
        }
    }

    fun toggleCardCollapsed(cardId: String) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            current.copy(cards = current.cards.map { if (it.id == cardId) it.copy(collapsed = !it.collapsed) else it })
        }
    }

    fun moveCard(cardId: String, moveUp: Boolean) {
        updateState { current -> RepositoryStateOperations.moveLegacyCard(current, cardId, moveUp) }
    }

    fun removeCard(cardId: String) {
        updateState { current -> RepositoryStateOperations.removeLegacyCard(current, cardId) }
    }

    fun updateCustomImageCard(uriString: String?, heightDp: Int = 200) {
        updateState { current -> RepositoryStateOperations.updateLegacyCustomImageCard(current, uriString, heightDp) }
    }

    fun resetToDefaults() {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) current else GlobalMinusOneState()
        }
    }

    companion object {
        private const val PREFS_NAME = "global_minus_one_prefs"

        @Volatile
        private var INSTANCE: MinusOneRepository? = null

        fun getInstance(context: Context): MinusOneRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MinusOneRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
