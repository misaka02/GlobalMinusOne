package com.example.globalminusone.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.UUID

class MinusOneRepository internal constructor(private val prefs: SharedPreferences) {

    private constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    ) {
        val metrics = context.resources.displayMetrics
        measuredPanelWidthDp = metrics.widthPixels / metrics.density * _state.value.themeConfig.panelWidthPercent / 100f
        measuredFontScale = context.resources.configuration.fontScale
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private var pendingCorruptRawBackup: String? = null
    private var verifiedCorruptBackup: CorruptStateBackup? = null
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
    private val configurationWrites = ConfigurationWriteAcceptance(prefs, KEY_STATE_JSON)
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
    fun ensureCorruptStateBackup(): CorruptStateBackup? {
        val raw = pendingCorruptRawBackup ?: return verifiedCorruptBackup
        val key = "${KEY_STATE_JSON}_corrupt_${System.currentTimeMillis()}_${UUID.randomUUID()}"
        check(prefs.edit().putString(key, raw).commit()) { "损坏配置原文备份写入失败，未覆盖原配置" }
        check(prefs.getString(key, null) == raw) { "损坏配置原文备份回读不一致，未覆盖原配置" }
        return CorruptStateBackup(key, raw).also {
            verifiedCorruptBackup = it
            pendingCorruptRawBackup = null
        }
    }

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


    private val _currentForegroundPackage = MutableStateFlow("")
    val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

    private val _isOverlayServiceRunning = MutableStateFlow(false)
    val isOverlayServiceRunning: StateFlow<Boolean> = _isOverlayServiceRunning.asStateFlow()

    private val _isPanelExpanded = MutableStateFlow(false)
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()
    private val _panelCloseGeneration = MutableStateFlow(0L)
    val panelCloseGeneration: StateFlow<Long> = _panelCloseGeneration.asStateFlow()

    private fun loadInitialState(): GlobalMinusOneState {
        val raw = prefs.getString(KEY_STATE_JSON, null)
        return if (raw == null) {
            GlobalMinusOneState()
        } else {
            try {
                decodeValidatedState(raw)
            } catch (_: Exception) {
                pendingCorruptRawBackup = raw
                _corruptStateRecoveredNotice.value = true
                GlobalMinusOneState()
            }
        }
    }

    private fun decodeValidatedState(raw: String): GlobalMinusOneState {
        checkConfigurationJsonEnvelope(raw)
        val hasSchemaVersion = runCatching {
            json.parseToJsonElement(raw).jsonObject.containsKey("schemaVersion")
        }.getOrDefault(true)
        val parsed = json.decodeFromString<GlobalMinusOneState>(raw).let {
            if (hasSchemaVersion) it else it.migrateLegacyRowGapSentinels()
        }
        require(parsed.advancedConfig.gridColumns in 8..64) { "网格列数必须在 8 到 64 之间" }
        require(parsed.pages.isNotEmpty()) { "配置必须至少包含一页" }
        require(parsed.currentPageIndex in parsed.pages.indices) { "当前页面下标无效" }
        require(parsed.triggerConfig.minimumSwipeDistanceDp in 16..160) { "最小滑动距离必须在 16 到 160 dp 之间" }
        val directoryUris = parsed.fileBrowserConfig.roots.map { it.uri }
        require(directoryUris.all { it.isNotBlank() }) { "文件目录地址不能为空" }
        require(directoryUris.distinct().size == directoryUris.size) { "文件目录地址不能重复" }
        validateComponentTrees(parsed.pages)
        val bounded = parsed.copy(
            themeConfig = parsed.themeConfig.normalizedPanelGeometry(),
            triggerConfig = parsed.triggerConfig.copy(
                sidePullHorizontalOffsetDp = parsed.triggerConfig.sidePullHorizontalOffsetDp.coerceIn(-1, 160)
            )
        )
        // A process restart never resumes an unconfirmed editing gesture/session.
        return bounded.copy(editMode = false, selectedComponentId = null)
    }

    private fun persist(newState: GlobalMinusOneState): Boolean {
        if (!persistenceNeedsRetry && lastPersistedState == newState) {
            _persistenceError.value = null
            lastPersistenceFailure = null
            return true
        }
        val result = configurationWrites.accept(
            serialize = { json.encodeToString(newState) },
            protectOriginal = { ensureCorruptStateBackup() }
        )
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
            val defaultCompId = if (enabled && current.selectedComponentId == null) {
                val p = current.pages.getOrNull(current.currentPageIndex)
                p?.components?.getOrNull(1)?.id ?: p?.components?.firstOrNull()?.id
            } else if (!enabled) {
                null
            } else {
                current.selectedComponentId
            }
            current.copy(editMode = enabled, selectedComponentId = defaultCompId)
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
    fun addPage(layoutType: PageLayoutType, name: String = ""): Int {
        var newIndex = -1
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val pageNumber = current.pages.size + 1
            val resolvedName = if (name.isBlank()) {
                "${layoutType.label} $pageNumber"
            } else {
                name
            }
            // 新建页默认附带一个底栏操作栏组件
            val defaultComponents = when (layoutType) {
                PageLayoutType.FREE -> listOf(
                    ComponentItem(
                        id = UUID.randomUUID().toString(),
                        type = ComponentType.OPERATION_BAR,
                        title = "操作栏",
                        showTitle = false,
                        x = 1,
                        y = 44,
                        w = 30,
                        h = 4
                    )
                )
                PageLayoutType.GRID -> listOf(
                    ComponentItem(
                        id = UUID.randomUUID().toString(),
                        type = ComponentType.OPERATION_BAR,
                        title = "操作栏",
                        showTitle = false,
                        w = 32,
                        h = 4,
                        gridSpanX = GRID_SPAN_FULL_ROW,
                        gridSpanY = 1
                    )
                )
                PageLayoutType.LINEAR -> listOf(
                    ComponentItem(
                        id = UUID.randomUUID().toString(),
                        type = ComponentType.OPERATION_BAR,
                        title = "操作栏",
                        showTitle = false,
                        w = 32,
                        h = 4
                    )
                )
            }
            val newPage = PageConfig(
                id = UUID.randomUUID().toString(),
                name = resolvedName,
                layoutType = layoutType,
                components = defaultComponents
            )
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
    fun addPagePreset(preset: PagePresetOption, name: String = ""): Int {
        if (!preset.isPreset) return addPage(preset.layoutType, name)
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
            )
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

    /**
     * 删除当前指定页，至少保留 1 页
     */
    fun deletePage(pageIndex: Int): Boolean {
        var success = false
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (current.pages.size <= 1 || pageIndex !in current.pages.indices) {
                return@updateState current
            }
            success = true
            val updatedPages = current.pages.filterIndexed { index, _ -> index != pageIndex }
            val newCurrent = when {
                current.currentPageIndex >= updatedPages.size -> updatedPages.size - 1
                current.currentPageIndex == pageIndex -> current.currentPageIndex.coerceAtMost(updatedPages.size - 1)
                current.currentPageIndex > pageIndex -> current.currentPageIndex - 1
                else -> current.currentPageIndex
            }
            current.copy(
                pages = updatedPages,
                currentPageIndex = newCurrent,
                selectedComponentId = null
            )
        }
        return accepted && success
    }

    /**
     * 与左侧相邻页交换顺序
     */
    fun swapPageLeft(pageIndex: Int): Boolean {
        var success = false
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex <= 0 || pageIndex >= current.pages.size) {
                return@updateState current
            }
            success = true
            val mutable = current.pages.toMutableList()
            val temp = mutable[pageIndex]
            mutable[pageIndex] = mutable[pageIndex - 1]
            mutable[pageIndex - 1] = temp
            current.copy(pages = mutable, currentPageIndex = pageIndex - 1)
        }
        return accepted && success
    }

    /**
     * 与右侧相邻页交换顺序
     */
    fun swapPageRight(pageIndex: Int): Boolean {
        var success = false
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex < 0 || pageIndex >= current.pages.size - 1) {
                return@updateState current
            }
            success = true
            val mutable = current.pages.toMutableList()
            val temp = mutable[pageIndex]
            mutable[pageIndex] = mutable[pageIndex + 1]
            mutable[pageIndex + 1] = temp
            current.copy(pages = mutable, currentPageIndex = pageIndex + 1)
        }
        return accepted && success
    }

    fun updatePageConfig(pageIndex: Int, transform: (PageConfig) -> PageConfig): Boolean {
        var valid = false
        val accepted = updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val updated = current.pages.toMutableList()
            updated[pageIndex] = transform(updated[pageIndex])
            if (runCatching { validateComponentTrees(updated) }.isFailure) return@updateState current
            valid = true
            current.copy(pages = updated)
        }
        return accepted && valid
    }

    fun updatePageGridStyle(pageIndex: Int, itemSizeDp: Int? = null, itemGapDp: Int? = null,
        columnCount: Int? = null, iconSizeDp: Int? = null, rowGapDp: Int? = null): Boolean {
        return updatePageConfig(pageIndex) { page ->
            val nextRowGap = rowGapDp?.let {
                if (it == ROW_GAP_INHERIT) ROW_GAP_INHERIT else it.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
            } ?: page.gridRowGapDp
            page.copy(
                gridItemSizeDp = (itemSizeDp ?: page.gridItemSizeDp).coerceIn(44, 160),
                gridItemGapDp = (itemGapDp ?: page.gridItemGapDp).coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP),
                gridRowGapDp = nextRowGap,
                gridColumnCount = (columnCount ?: page.gridColumnCount).coerceIn(0, 12),
                gridIconSizeDp = (iconSizeDp ?: page.gridIconSizeDp).coerceIn(0, 192)
            )
        }
    }

    fun fitPageGridColumnCount(pageIndex: Int, columns: Int, availableWidthDp: Float) {
        if (columns == 0) { updatePageGridStyle(pageIndex, columnCount = 0); return }
        val fitted = fitPageGridColumns(availableWidthDp, columns)
        updatePageGridStyle(pageIndex, fitted.itemSizeDp, fitted.itemGapDp, fitted.columnCount, fitted.iconSizeDp)
    }

    fun estimatedPageGridWidthDp(): Float = (measuredPanelWidthDp *
        _state.value.themeConfig.panelWidthPercent.coerceAtLeast(1) / measuredPanelWidthPercent - 20f).coerceAtLeast(64f)

    /**
     * 直接设置指定页的布局类型（线性布局 / 网格布局 / 自由布局）
     */
    fun setPageLayoutType(pageIndex: Int, newType: PageLayoutType) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            if (targetPage.layoutType == newType) return@updateState current
            val columns = current.advancedConfig.gridColumns.coerceIn(8, 64)
            val orderedSource = if (targetPage.layoutType == PageLayoutType.FREE) {
                targetPage.components.sortedWith(compareBy({ it.y }, { it.x }))
            } else {
                targetPage.components
            }
            val updatedComponents = when (newType) {
                PageLayoutType.FREE -> {
                    var currentY = 0
                    orderedSource.map { comp ->
                        val w = if (targetPage.layoutType != PageLayoutType.LINEAR && comp.w in 1..columns) {
                            comp.w
                        } else {
                            comp.type.defaultW.coerceIn(1, columns)
                        }
                        val h = if (comp.h > 0) comp.h else comp.type.defaultH.coerceAtLeast(2)
                        val y = currentY
                        currentY += h + 1
                        comp.copy(x = 0, y = y, w = w, h = h)
                    }
                }
                PageLayoutType.LINEAR -> {
                    orderedSource.map { comp -> comp.copy(w = columns) }
                }
                PageLayoutType.GRID -> {
                    orderedSource.map { comp ->
                        val naturalW = if (targetPage.layoutType == PageLayoutType.LINEAR) {
                            comp.type.defaultW.coerceIn(1, columns)
                        } else {
                            comp.w.coerceIn(1, columns)
                        }
                        comp.copy(w = naturalW)
                    }
                }
            }
            val updatedPage = targetPage.copy(layoutType = newType, components = updatedComponents)
            val updatedPages = current.pages.toMutableList()
            updatedPages[pageIndex] = updatedPage
            current.copy(pages = updatedPages)
        }
    }

    /**
     * 将指定页在 线性布局 -> 网格布局 -> 自由布局 之间循环切换
     */
    fun togglePageLayoutType(pageIndex: Int) {
        val currentPage = _state.value.pages.getOrNull(pageIndex) ?: return
        val nextType = when (currentPage.layoutType) {
            PageLayoutType.LINEAR -> PageLayoutType.GRID
            PageLayoutType.GRID -> PageLayoutType.FREE
            PageLayoutType.FREE -> PageLayoutType.LINEAR
        }
        setPageLayoutType(pageIndex, nextType)
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

            val resolvedItem = when (targetPage.layoutType) {
                PageLayoutType.FREE -> {
                    val clampedW = item.w.coerceIn(1, columns)
                    val clampedH = item.h.coerceAtLeast(1)
                    val resolvedY = if (item.y == 0 && targetPage.components.isNotEmpty()) {
                        nextPlacementY(current, targetPage)
                    } else {
                        item.y.coerceAtLeast(0)
                    }
                    val resolvedX = if (item.x == 0 && targetPage.components.isNotEmpty()) {
                        ((columns - clampedW) / 2).coerceIn(0, columns - clampedW)
                    } else {
                        item.x.coerceIn(0, (columns - clampedW).coerceAtLeast(0))
                    }
                    item.copy(x = resolvedX, y = resolvedY, w = clampedW, h = clampedH)
                }
                PageLayoutType.GRID -> item.copy(
                    w = item.w.coerceIn(1, columns),
                    h = item.h.coerceAtLeast(1)
                )
                PageLayoutType.LINEAR -> item.copy(w = columns)
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
            val width = item.w.coerceIn(1, columns)
            val child = item.copy(
                x = item.x.coerceIn(0, columns - width),
                y = if (item.y == 0) nextPlacementY(current, page, container.id) else item.y.coerceAtLeast(0),
                w = width, h = item.h.coerceAtLeast(1)
            )
            val components = page.components.transformComponent(containerId) { it.copy(containerComponents = it.containerComponents + child) }
            val pages = current.pages.toMutableList().apply { this[pageIndex] = page.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            added = true
            current.copy(pages = pages, selectedComponentId = child.id)
        }
        return accepted && added
    }

    fun placeMenuComponent(pageId: String, containerId: String?, item: ComponentItem, beforeId: String? = null): Boolean {
        var added = false
        val accepted = updateState { current ->
            added = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val index = current.pages.indexOfFirst { it.id == pageId }
            if (index < 0) return@updateState current
            val page = current.pages[index]
            val container = containerId?.let { page.components.findComponent(it) }
            if (containerId != null && container?.type?.isContainerType() != true) return@updateState current
            val siblings = container?.containerComponents ?: page.components
            val width = item.w.coerceIn(1, current.advancedConfig.gridColumns)
            val placed = item.copy(w = width, x = item.x.coerceIn(0, current.advancedConfig.gridColumns - width),
                y = nextPlacementY(current, page, containerId))
            val children = insertCardAbove(siblings, placed, beforeId,
                free = if (container == null) page.layoutType == PageLayoutType.FREE else
                    container.containerLayout == ContainerLayoutType.FREE,
                columns = current.advancedConfig.gridColumns, rows = insertionRows(current, placed)) ?: return@updateState current
            val components = if (container == null) children else
                page.components.transformComponent(container.id) { it.copy(containerComponents = children) }
            val pages = current.pages.toMutableList().apply { this[index] = page.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            added = true
            current.copy(pages = pages, selectedComponentId = placed.id)
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
            val original = current.pages[sourceIndex].components.findComponent(componentId) ?: return@updateState current
            // A widget host ID cannot be shared by two cards; copying widgets requires a new system binding.
            if (copy && listOf(original).flattenComponents().any { it.widgetId >= 0 }) return@updateState current
            if (targetContainerId != null && listOf(original).findComponent(targetContainerId) != null) return@updateState current
            val destination = targetContainerId?.let { current.pages[targetIndex].components.findComponent(it) }
            if (targetContainerId != null && destination?.type?.isContainerType() != true) return@updateState current
            fun duplicate(node: ComponentItem): ComponentItem = node.copy(id = java.util.UUID.randomUUID().toString(),
                containerActions = node.containerActions.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                containerComponents = node.containerComponents.map(::duplicate))
            val picked = if (copy) duplicate(original) else original
            val pages = current.pages.toMutableList()
            if (!copy) pages[sourceIndex] = pages[sourceIndex].copy(components = pages[sourceIndex].components.removeComponentTree(componentId))
            val target = pages[targetIndex]
            val siblings = if (targetContainerId == null) target.components else
                target.components.findComponent(targetContainerId)?.containerComponents ?: return@updateState current
            val columns = current.advancedConfig.gridColumns
            val width = picked.w.coerceIn(1, columns)
            val placed = picked.copy(x = picked.x.coerceIn(0, columns - width), w = width,
                y = nextPlacementY(current, target, targetContainerId))
            val components = if (targetContainerId == null) target.components + placed else
                target.components.transformComponent(targetContainerId) { it.copy(containerComponents = it.containerComponents + placed) }
            pages[targetIndex] = target.copy(components = components)
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            transferred = true
            current.copy(pages = pages, selectedComponentId = placed.id)
        }
        return accepted && transferred
    }

    fun saveFileShortcut(pageId: String, componentId: String?, title: String, uri: String, containerId: String? = null, beforeId: String? = null): Boolean =
        savePickedCard(pageId, componentId, title, uri, ComponentType.FILE_SHORTCUT, containerId, beforeId)

    fun saveImageCard(pageId: String, componentId: String?, title: String, uri: String, containerId: String? = null, beforeId: String? = null): Boolean =
        savePickedCard(pageId, componentId, title, uri, ComponentType.SINGLE_IMAGE, containerId, beforeId)
            .also { if (it) notifyImageAccessChanged(uri) }

    fun savePhotoCube(pageId: String, componentId: String?, uris: List<String>,
        containerId: String? = null, beforeId: String? = null): Boolean {
        val images = uris.distinct().take(100)
        if (images.isEmpty() || images.any { value -> !runCatching {
                java.net.URI(value).let { it.scheme == "content" && !it.authority.isNullOrBlank() }
            }.getOrDefault(false) }) return false
        return savePickedCard(pageId, componentId, "图片魔方", images.first(), ComponentType.PHOTO_CUBE,
            containerId, beforeId, images).also { saved ->
                if (saved) images.forEach(::notifyImageAccessChanged)
            }
    }

    /** Recheck the destination when the system picker returns, rather than trusting old indices. */
    private fun savePickedCard(pageId: String, componentId: String?, title: String, uri: String, type: ComponentType, containerId: String?, beforeId: String?, uris: List<String> = emptyList()): Boolean {
        val validUri = runCatching { java.net.URI(uri).let { it.scheme == "content" && !it.authority.isNullOrBlank() } }.getOrDefault(false)
        if (!validUri) return false
        var saved = false
        val accepted = updateState { current ->
            saved = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val pageIndex = current.pages.indexOfFirst { it.id == pageId }
            if (pageIndex < 0) return@updateState current
            val page = current.pages[pageIndex]
            val existing = componentId?.let { id -> page.components.findComponent(id)?.takeIf { it.type == type } }
            val container = containerId?.let { page.components.findComponent(it)?.takeIf { node -> node.type.isContainerType() } }
            if (containerId != null && container == null) return@updateState current
            if (container != null && existing != null && container.containerComponents.findComponent(existing.id) == null) return@updateState current
            if (componentId != null && existing == null) return@updateState current
            val columns = current.advancedConfig.gridColumns
            val base = existing ?: ComponentItem(
                type = type,
                w = type.defaultW.coerceAtMost(columns),
                y = if (container != null) nextPlacementY(current, page, container.id)
                    else if (page.layoutType == PageLayoutType.FREE) nextPlacementY(current, page) else 0
            )
            val readableBase = if (existing == null && type == ComponentType.FILE_SHORTCUT)
                prepareNewComponent(pageIndex, base, containerId) else base
            val named = if (type == ComponentType.PHOTO_CUBE && existing != null) readableBase
                else readableBase.copy(title = title.ifBlank { type.label })
            val picked = when (type) {
                ComponentType.FILE_SHORTCUT -> named.copy(intentUri = uri)
                ComponentType.PHOTO_CUBE -> named.copy(photoCubeUris = uris.toList())
                else -> named.copy(imageUri = uri)
            }
            val siblings = container?.containerComponents ?: page.components
            val inserted = if (existing != null) siblings else insertCardAbove(siblings, picked, beforeId,
                if (container == null) page.layoutType == PageLayoutType.FREE else
                    container.containerLayout == ContainerLayoutType.FREE,
                columns, insertionRows(current, picked)) ?: return@updateState current
            val components = when {
                existing != null -> page.components.transformComponent(existing.id) { picked }
                container != null -> page.components.transformComponent(container.id) { it.copy(containerComponents = inserted) }
                else -> inserted
            }
            val pages = current.pages.toMutableList().apply { this[pageIndex] = page.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            saved = true
            current.copy(pages = pages, currentPageIndex = pageIndex, selectedComponentId = picked.id)
        }
        return accepted && saved
    }

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

            val updatedComponents = runCatching { targetPage.components.transformComponent(componentId) { comp ->
                val transformed = transform(comp)
                val clampedW = transformed.w.coerceIn(1, columns)
                val clampedSpanX = if (transformed.gridSpanX >= GRID_SPAN_FULL_ROW) {
                    GRID_SPAN_FULL_ROW
                } else {
                    transformed.gridSpanX.coerceIn(0, 12)
                }
                transformed.copy(
                    x = transformed.x.coerceIn(0, (columns - clampedW).coerceAtLeast(0)),
                    y = transformed.y.coerceAtLeast(0),
                    w = clampedW,
                    h = transformed.h.coerceAtLeast(1),
                    gridSpanX = clampedSpanX,
                    gridSpanY = transformed.gridSpanY.coerceIn(0, 12),
                    gridCol = transformed.gridCol.coerceIn(-1, 63),
                    gridRow = transformed.gridRow.coerceIn(-1, 200)
                )
            } }.getOrElse { return@updateState current }

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
    fun removeComponent(pageIndex: Int, componentId: String): Boolean {
        return updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            if (pageIndex !in current.pages.indices) return@updateState current
            val targetPage = current.pages[pageIndex]
            val gridWidth = estimatedPageGridWidthDp()
            val updatedComponents = removeFromListPreservingPinned(
                items = targetPage.components,
                targetId = componentId,
                isGridScope = targetPage.layoutType == PageLayoutType.GRID,
                pageConfig = targetPage,
                state = current,
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

    private fun removeFromListPreservingPinned(
        items: List<ComponentItem>,
        targetId: String,
        isGridScope: Boolean,
        pageConfig: PageConfig,
        state: GlobalMinusOneState,
        pageGridWidthDp: Float,
        container: ComponentItem? = null
    ): List<ComponentItem> {
        val directMatch = items.any { it.id == targetId }
        val remaining = items.filterNot { it.id == targetId }
        if (directMatch) {
            if (!isGridScope || remaining.none { it.gridCol >= 0 && it.gridRow >= 0 }) {
                return remaining
            }
            val resolvedCoords: Map<String, Pair<Int, Int>> = if (container == null) {
                arrangePageGridComponents(
                    components = items,
                    availableWidthDp = pageGridWidthDp,
                    itemSizeDp = pageConfig.gridItemSizeDp,
                    itemGapDp = pageConfig.gridItemGapDp,
                    gridColumns = state.advancedConfig.gridColumns,
                    configuredHeightUnitDp = state.advancedConfig.heightUnitDp,
                    fontScale = measuredFontScale,
                    columnCount = pageConfig.gridColumnCount,
                    iconSizeDp = pageConfig.gridIconSizeDp,
                    fillGaps = pageConfig.gridFillGaps,
                    rowGapDp = pageConfig.gridRowGapDp
                ).placements.associate { it.id to (it.col to it.row) }
            } else {
                val stepY = if (state.advancedConfig.heightUnitDp > 0) state.advancedConfig.heightUnitDp.toFloat()
                    else pageGridWidthDp / state.advancedConfig.gridColumns.coerceIn(8, 64)
                containerGridChildBounds(
                    children = items,
                    columns = state.advancedConfig.gridColumns,
                    widthDp = pageGridWidthDp,
                    stepYDp = stepY,
                    configuredHeightUnitDp = state.advancedConfig.heightUnitDp,
                    fontScale = measuredFontScale,
                    iconColumns = container.containerColumns.coerceIn(1, 8),
                    itemGapDp = container.containerItemGapDp,
                    itemSizeDp = container.containerItemSizeDp,
                    iconSizeDp = container.containerIconSizeDp,
                    rowGapDp = containerRowGapDp(container),
                    itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
                    iconLabelGapDp = container.containerIconLabelGapDp
                ).associate { it.item.id to (it.col to it.row) }
            }
            return remaining.map { child ->
                if (child.gridCol >= 0 && child.gridRow >= 0) child
                else resolvedCoords[child.id]?.let { (c, r) -> child.copy(gridCol = c, gridRow = r) } ?: child
            }
        }
        return items.map { child ->
            if (!child.type.isContainerType() || child.containerComponents.isEmpty()) child
            else child.copy(
                containerComponents = removeFromListPreservingPinned(
                    items = child.containerComponents,
                    targetId = targetId,
                    isGridScope = child.containerLayout == ContainerLayoutType.GRID,
                    pageConfig = pageConfig,
                    state = state,
                    pageGridWidthDp = pageGridWidthDp,
                    container = child
                )
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
            val resolved = resolvePageGridDropPlacements(
                components = page.components,
                availableWidthDp = availableWidthDp,
                itemSizeDp = page.gridItemSizeDp,
                itemGapDp = page.gridItemGapDp,
                gridColumns = _state.value.advancedConfig.gridColumns,
                configuredHeightUnitDp = _state.value.advancedConfig.heightUnitDp,
                fontScale = fontScale,
                columnCount = page.gridColumnCount,
                iconSizeDp = page.gridIconSizeDp,
                fillGaps = page.gridFillGaps,
                rowGapDp = page.gridRowGapDp,
                movedId = movedId,
                targetCol = targetCol,
                targetRow = targetRow
            )
            page.copy(components = resolved)
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
            val updated = nudgePageGridComponent(
                components = page.components,
                availableWidthDp = availableWidthDp,
                itemSizeDp = page.gridItemSizeDp,
                itemGapDp = page.gridItemGapDp,
                gridColumns = _state.value.advancedConfig.gridColumns,
                configuredHeightUnitDp = _state.value.advancedConfig.heightUnitDp,
                fontScale = fontScale,
                columnCount = page.gridColumnCount,
                iconSizeDp = page.gridIconSizeDp,
                fillGaps = page.gridFillGaps,
                rowGapDp = page.gridRowGapDp,
                movedId = movedId,
                deltaCol = deltaCol,
                deltaRow = deltaRow
            )
            page.copy(components = updated)
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
            val current = arrangePageGridComponents(
                components = page.components,
                availableWidthDp = availableWidthDp,
                itemSizeDp = page.gridItemSizeDp,
                itemGapDp = page.gridItemGapDp,
                gridColumns = _state.value.advancedConfig.gridColumns,
                configuredHeightUnitDp = _state.value.advancedConfig.heightUnitDp,
                fontScale = fontScale,
                columnCount = page.gridColumnCount,
                iconSizeDp = page.gridIconSizeDp,
                fillGaps = page.gridFillGaps,
                rowGapDp = page.gridRowGapDp
            )
            val coords = current.placements.associate { it.id to (it.row to it.col) }
            val compacted = page.components
                .sortedWith(compareBy({ coords[it.id]?.first ?: Int.MAX_VALUE }, { coords[it.id]?.second ?: Int.MAX_VALUE }))
                .map { it.copy(gridCol = -1, gridRow = -1) }
            page.copy(components = compacted)
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
            val resolved = resolveContainerGridDropPlacements(
                children = container.containerComponents,
                columns = _state.value.advancedConfig.gridColumns,
                widthDp = widthDp,
                stepYDp = stepYDp,
                configuredHeightUnitDp = _state.value.advancedConfig.heightUnitDp,
                fontScale = fontScale,
                iconColumns = iconColumns,
                itemGapDp = container.containerItemGapDp,
                itemSizeDp = container.containerItemSizeDp,
                iconSizeDp = container.containerIconSizeDp,
                rowGapDp = containerRowGapDp(container),
                itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
                iconLabelGapDp = container.containerIconLabelGapDp,
                movedId = movedId,
                targetCol = targetCol,
                targetRow = targetRow
            )
            container.copy(containerComponents = resolved)
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
            container.copy(
                containerActions = commitContainerActionGridPlacement(
                    actions = container.containerActions,
                    movedId = movedId,
                    columns = iconColumns,
                    targetCol = targetCol,
                    targetRow = targetRow
                )
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
            val bounds = containerGridChildBounds(
                children = container.containerComponents,
                columns = _state.value.advancedConfig.gridColumns,
                widthDp = widthDp,
                stepYDp = stepYDp,
                configuredHeightUnitDp = _state.value.advancedConfig.heightUnitDp,
                fontScale = fontScale,
                iconColumns = iconColumns,
                itemGapDp = container.containerItemGapDp,
                itemSizeDp = container.containerItemSizeDp,
                iconSizeDp = container.containerIconSizeDp,
                rowGapDp = containerRowGapDp(container),
                itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
                iconLabelGapDp = container.containerIconLabelGapDp
            ).associate { it.item.id to (it.row to it.col) }
            val compacted = container.containerComponents
                .sortedWith(compareBy({ bounds[it.id]?.first ?: Int.MAX_VALUE }, { bounds[it.id]?.second ?: Int.MAX_VALUE }))
                .map { it.copy(gridCol = -1, gridRow = -1) }
            container.copy(containerComponents = compacted)
        }
    }

    /**
     * 重置组件样式为全局默认
     */
    fun resetComponentStyle(pageIndex: Int, componentId: String) {
        updateComponent(pageIndex, componentId) { comp ->
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

    fun removeActionFromContainer(pageIndex: Int, containerId: String, actionId: String) {
        updateComponent(pageIndex, containerId) { comp ->
            if (!comp.type.isContainerType()) comp else comp.copy(containerActions = comp.containerActions.filterNot { it.id == actionId })
        }
    }

    fun clearContainerActions(pageIndex: Int, containerId: String) {
        updateComponent(pageIndex, containerId) { comp ->
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
            if (!darkMode) {
                current.copy(
                    isDarkMode = false,
                    followSystemDark = false,
                    maskColorHex = 0xFF000000L,
                    maskAlphaPercent = 20,
                    panelBgColorHex = 0xFFF7F8FAL,
                    panelBgAlphaPercent = panelAlpha,
                    defaultCardBgColorHex = 0xFFFFFFFFL,
                    cardBgAlphaPercent = cardAlpha,
                    defaultTextColorHex = 0xFF1F2329L,
                    textAlphaPercent = 100,
                    titleColorHex = null,
                    titleAlphaPercent = 100
                )
            } else {
                current.copy(
                    isDarkMode = true,
                    followSystemDark = false,
                    maskColorHex = 0xFF000000L,
                    maskAlphaPercent = 45,
                    panelBgColorHex = 0xFF14171FL,
                    panelBgAlphaPercent = panelAlpha,
                    defaultCardBgColorHex = 0xFF222630L,
                    cardBgAlphaPercent = cardAlpha,
                    defaultTextColorHex = 0xFFF5F7FAL,
                    textAlphaPercent = 100,
                    titleColorHex = null,
                    titleAlphaPercent = 100
                )
            }
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
            val profile = ConfigProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "配置方案" },
                author = author,
                description = description,
                updatedAt = System.currentTimeMillis(),
                stateJson = json.encodeToString(
                    _state.value.copy(
                        profiles = emptyList(),
                        activeProfileId = "default",
                        editMode = false,
                        selectedComponentId = null
                    )
                )
            )
            val accepted = updateState { current ->
                current.copy(
                    profiles = current.profiles + profile,
                    activeProfileId = profile.id
                )
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
            val parsed = profile.stateJson.takeIf { it.isNotBlank() }?.let(::decodeValidatedState)
            var applied = false
            val accepted = updateState { current ->
                applied = !current.advancedConfig.layoutLocked && !current.editMode
                if (!applied) return@updateState current
                parsed?.copy(profiles = current.profiles, activeProfileId = profileId)
                    ?: current.copy(activeProfileId = profileId)
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
            current.copy(profiles = current.profiles.filterNot { it.id == profileId })
        }
        return accepted && success
    }

    fun exportConfigCode(): String {
        return prettyJson.encodeToString(_state.value)
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
        if (current.advancedConfig.layoutLocked || current.editMode || folder.type != ComponentType.FOLDER) return false
        val index = current.pages.indexOfFirst { it.id == targetPageId }
        if (index < 0) return false
        val page = current.pages[index]
        val parent = targetContainerId?.let { page.components.findComponent(it) }
        if (targetContainerId != null && parent?.type?.isContainerType() != true) return false
        val columns = current.advancedConfig.gridColumns
        val width = folder.w.coerceIn(1, columns)
        val free = parent?.containerLayout?.let { it == ContainerLayoutType.FREE } ?: (page.layoutType == PageLayoutType.FREE)
        val placed = folder.copy(w = width, x = folder.x.coerceIn(0, columns - width),
            y = if (free) nextPlacementY(current, page, targetContainerId) else 0)
        val components = if (parent == null) page.components + placed else
            page.components.transformComponent(parent.id) { it.copy(containerComponents = it.containerComponents + placed) }
        val pages = current.pages.toMutableList().apply { this[index] = page.copy(components = components) }
        if (runCatching { validateComponentTrees(pages) }.isFailure) return false
        return commitImportedState(current.copy(pages = pages))
    }

    @Synchronized
    fun appendImportedContainers(
        containers: List<ComponentItem>,
        targetPageId: String,
        targetContainerId: String? = null
    ): Boolean {
        val current = _state.value
        if (containers.isEmpty() || current.advancedConfig.layoutLocked || current.editMode) return false
        if (containers.any { !it.type.isContainerType() }) return false
        val index = current.pages.indexOfFirst { it.id == targetPageId }
        if (index < 0) return false
        var workingPage = current.pages[index]
        val initialParent = targetContainerId?.let { workingPage.components.findComponent(it) }
        if (targetContainerId != null && initialParent?.type?.isContainerType() != true) return false
        val columns = current.advancedConfig.gridColumns.coerceIn(8, 64)
        val free = initialParent?.containerLayout?.let { it == ContainerLayoutType.FREE }
            ?: (workingPage.layoutType == PageLayoutType.FREE)
        var rowX = 0
        var rowY = if (free) nextPlacementY(current, workingPage, targetContainerId) else 0
        var rowMaxH = 0
        for (item in containers) {
            val width = if (!free && targetContainerId == null && item.type == ComponentType.CONTAINER_BOX) {
                columns
            } else {
                item.w.coerceIn(1, columns)
            }
            val placed = if (!free) {
                item.copy(w = width, x = 0, y = 0)
            } else if (item.type == ComponentType.FOLDER && width * 2 <= columns) {
                if (rowX + width > columns) {
                    rowX = 0
                    rowY += rowMaxH.coerceAtLeast(8) + 1
                    rowMaxH = 0
                }
                val folderPlaced = item.copy(w = width, x = rowX, y = rowY)
                rowX += width
                rowMaxH = maxOf(rowMaxH, folderPlaced.h.coerceAtLeast(8))
                folderPlaced
            } else {
                val y = nextPlacementY(current, workingPage, targetContainerId)
                val placedBox = item.copy(w = width, x = item.x.coerceIn(0, (columns - width).coerceAtLeast(0)), y = y)
                rowX = 0
                rowMaxH = 0
                placedBox
            }
            val updatedComponents = if (targetContainerId == null) {
                workingPage.components + placed
            } else {
                workingPage.components.transformComponent(targetContainerId) {
                    it.copy(containerComponents = it.containerComponents + placed)
                }
            }
            workingPage = workingPage.copy(components = updatedComponents)
            if (free && rowX == 0) rowY = nextPlacementY(current, workingPage, targetContainerId)
        }
        val pages = current.pages.toMutableList().apply { this[index] = workingPage }
        if (runCatching { validateComponentTrees(pages) }.isFailure) return false
        return commitImportedState(current.copy(pages = pages))
    }

    private fun commitImportedState(value: GlobalMinusOneState): Boolean {
        ensureCorruptStateBackup()
        val previous = prefs.getString(KEY_STATE_JSON, null)
        val committed = runCatching { prefs.edit().putString(KEY_STATE_JSON, json.encodeToString(value)).commit() }.getOrDefault(false)
        if (!committed) {
            reportPersistenceFailure()
            val restored = runCatching { if (previous == null) prefs.edit().remove(KEY_STATE_JSON).commit()
                else prefs.edit().putString(KEY_STATE_JSON, previous).commit() }.getOrDefault(false)
            check(restored) { "写入失败且回滚失败；请保留恢复备份并检查存储空间" }
            return false
        }
        panelEditBaseline = null
        lastPersistedState = value
        persistenceNeedsRetry = false
        _persistenceError.value = null
        lastPersistenceFailure = null
        verifiedCorruptBackup = null
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
        val oldRaw = prefs.getString(KEY_STATE_JSON, null)
        try {
            commitCompanion()
            check(prefs.edit().putString(KEY_STATE_JSON, json.encodeToString(parsed)).commit()) { "配置写入失败" }
        } catch (failure: Exception) {
            reportPersistenceFailure()
            val problems = mutableListOf<String>()
            runCatching {
                check(if (oldRaw == null) prefs.edit().remove(KEY_STATE_JSON).commit()
                    else prefs.edit().putString(KEY_STATE_JSON, oldRaw).commit())
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
        verifiedCorruptBackup = null
        _corruptStateRecoveredNotice.value = false
        _state.value = parsed
        _configurationRestoreGeneration.update { it + 1 }
    }

    fun exportConfigProfileCode(profileId: String): String? {
        val profile = _state.value.profiles.find { it.id == profileId } ?: return null
        // 内置默认方案没有快照，沿用应用时的语义：使用当前配置。
        if (profile.stateJson.isBlank()) return exportConfigCode()
        return try {
            prettyJson.encodeToString(decodeValidatedState(profile.stateJson))
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

    fun addBoundWidget(widgetInfo: BoundWidgetInfo, pageId: String? = null, containerId: String? = null, beforeId: String? = null): Boolean {
        var added = false
        val accepted = updateState { current ->
            added = false
            if (current.advancedConfig.layoutLocked) return@updateState current
            val pageIdx = if (pageId != null) current.pages.indexOfFirst { it.id == pageId } else current.currentPageIndex
            val targetPage = current.pages.getOrNull(pageIdx) ?: return@updateState current
            val container = containerId?.let { targetPage.components.findComponent(it)?.takeIf { node -> node.type.isContainerType() } }
            if (containerId != null && container == null) return@updateState current
            val siblings = container?.containerComponents ?: targetPage.components
            val widgetComponent = ComponentItem(
                id = "widget_${widgetInfo.appWidgetId}", type = ComponentType.APP_WIDGET,
                title = widgetInfo.label.ifBlank { "桌面小部件" }, widgetId = widgetInfo.appWidgetId,
                w = current.advancedConfig.gridColumns, h = 14, y = nextPlacementY(current, targetPage, containerId)
            )
            val inserted = insertCardAbove(siblings, widgetComponent, beforeId,
                if (container == null) targetPage.layoutType == PageLayoutType.FREE else
                    container.containerLayout == ContainerLayoutType.FREE,
                current.advancedConfig.gridColumns, insertionRows(current, widgetComponent)) ?: return@updateState current
            val components = if (container != null) targetPage.components.transformComponent(container.id) {
                it.copy(containerComponents = inserted)
            } else inserted
            val pages = current.pages.toMutableList().apply { this[pageIdx] = targetPage.copy(components = components) }
            if (runCatching { validateComponentTrees(pages) }.isFailure) return@updateState current
            val newCard = CardConfig(id = "widget_${widgetInfo.appWidgetId}", type = CardType.APP_WIDGET,
                title = widgetInfo.label.ifBlank { "桌面小部件 #${widgetInfo.appWidgetId}" },
                enabled = true, order = current.cards.size, widgetId = widgetInfo.appWidgetId)
            added = true
            current.copy(pages = pages,
                boundWidgets = current.boundWidgets.filterNot { it.appWidgetId == widgetInfo.appWidgetId } + widgetInfo,
                cards = if (container == null) current.cards + newCard else current.cards,
                selectedComponentId = widgetComponent.id)
        }
        return accepted && added
    }

    fun updateWidgetHeight(appWidgetId: Int, heightDp: Int) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val updatedWidgets = current.boundWidgets.map {
                if (it.appWidgetId == appWidgetId) it.copy(heightDp = heightDp.coerceIn(80, 520)) else it
            }
            val updatedPages = current.pages.map { page ->
                page.copy(components = page.components.mapComponentTree { comp ->
                    if (comp.widgetId == appWidgetId) comp.copy(h = (heightDp / 20).coerceIn(4, 30)) else comp
                })
            }
            current.copy(boundWidgets = updatedWidgets, pages = updatedPages)
        }
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
            current.copy(todos = listOf(item) + current.todos)
        }
    }

    fun toggleTodo(id: String) {
        updateState { current ->
            current.copy(todos = current.todos.map { if (it.id == id) it.copy(done = !it.done) else it })
        }
    }

    fun deleteTodo(id: String) {
        updateState { current ->
            current.copy(todos = current.todos.filterNot { it.id == id })
        }
    }

    fun updateNoteText(text: String) {
        updateState { it.copy(noteText = text) }
    }

    fun recordClipboardText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        updateState { current ->
            val existing = current.clipboardHistory.find { it.text == trimmed }
            val updatedList = if (existing != null) {
                val refreshed = existing.copy(timestamp = System.currentTimeMillis())
                listOf(refreshed) + current.clipboardHistory.filterNot { it.id == existing.id }
            } else {
                val newRecord = ClipboardRecord(text = trimmed)
                (listOf(newRecord) + current.clipboardHistory).take(25)
            }
            val sorted = updatedList.sortedWith(
                compareByDescending<ClipboardRecord> { it.pinned }.thenByDescending { it.timestamp }
            )
            current.copy(clipboardHistory = sorted)
        }
    }

    fun togglePinClipboard(id: String) {
        updateState { current ->
            val updated = current.clipboardHistory.map {
                if (it.id == id) it.copy(pinned = !it.pinned) else it
            }.sortedWith(
                compareByDescending<ClipboardRecord> { it.pinned }.thenByDescending { it.timestamp }
            )
            current.copy(clipboardHistory = updated)
        }
    }

    fun deleteClipboardRecord(id: String) {
        updateState { current ->
            current.copy(clipboardHistory = current.clipboardHistory.filterNot { it.id == id })
        }
    }

    fun clearUnpinnedClipboard() {
        updateState { current ->
            current.copy(clipboardHistory = current.clipboardHistory.filter { it.pinned })
        }
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
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val sorted = current.cards.sortedBy { it.order }.toMutableList()
            val idx = sorted.indexOfFirst { it.id == cardId }
            if (idx == -1) return@updateState current
            val targetIdx = if (moveUp) idx - 1 else idx + 1
            if (targetIdx !in sorted.indices) return@updateState current
            val temp = sorted[idx]
            sorted[idx] = sorted[targetIdx]
            sorted[targetIdx] = temp
            val reindexed = sorted.mapIndexed { index, card -> card.copy(order = index) }
            current.copy(cards = reindexed)
        }
    }

    fun removeCard(cardId: String) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val target = current.cards.find { it.id == cardId }
            val updatedWidgets = if (target?.widgetId != null) {
                current.boundWidgets.filterNot { it.appWidgetId == target.widgetId }
            } else {
                current.boundWidgets
            }
            val remaining = current.cards
                .filterNot { it.id == cardId }
                .sortedBy { it.order }
                .mapIndexed { idx, card -> card.copy(order = idx) }
            current.copy(cards = remaining, boundWidgets = updatedWidgets)
        }
    }

    fun updateCustomImageCard(uriString: String?, heightDp: Int = 200) {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) return@updateState current
            val hasImageCard = current.cards.any { it.type == CardType.CUSTOM_IMAGE }
            val updatedCards = if (hasImageCard) {
                current.cards.map {
                    if (it.type == CardType.CUSTOM_IMAGE) {
                        it.copy(
                            enabled = uriString != null,
                            customImageUri = uriString,
                            customImageHeightDp = heightDp
                        )
                    } else it
                }
            } else {
                current.cards + CardConfig(
                    id = "card_custom_image",
                    type = CardType.CUSTOM_IMAGE,
                    enabled = uriString != null,
                    order = current.cards.size,
                    customImageUri = uriString,
                    customImageHeightDp = heightDp
                )
            }
            current.copy(cards = updatedCards)
        }
    }

    fun resetToDefaults() {
        updateState { current ->
            if (current.advancedConfig.layoutLocked) current else GlobalMinusOneState()
        }
    }

    companion object {
        private const val PREFS_NAME = "global_minus_one_prefs"
        private const val KEY_STATE_JSON = "state_json_v5"

        @Volatile
        private var INSTANCE: MinusOneRepository? = null

        fun getInstance(context: Context): MinusOneRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MinusOneRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
