package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.animateFloatAsState
import com.example.globalminusone.ui.motion.AnimatedOverlayPresence
import com.example.globalminusone.ui.motion.AnimatedOverlayTarget
import com.example.globalminusone.ui.motion.MotionTokens
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.policy.panelBounds
import com.example.globalminusone.data.resolvedContainerPopupMs
import com.example.globalminusone.data.resolvedModalMenuMs
import com.example.globalminusone.data.resolvedPageSwitchMs
import com.example.globalminusone.data.resolvedSubPanelMs
import com.example.globalminusone.data.operations.findComponent
import com.example.globalminusone.data.operations.parentContainerId
import com.example.globalminusone.data.operations.flattenComponents
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.file.PanelFileBrowser
import com.example.globalminusone.system.SystemMonitor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.globalminusone.ui.panel.editor.AddComponentModal
import com.example.globalminusone.ui.panel.editor.BottomNineKeyModal
import com.example.globalminusone.ui.panel.editor.ComponentEditModal
import com.example.globalminusone.ui.panel.container.ContainerPresentation
import com.example.globalminusone.ui.panel.editor.DeletePageConfirmModal
import com.example.globalminusone.ui.panel.editor.EditModeTopBanner
import com.example.globalminusone.ui.panel.file.FloatingFileBrowser
import com.example.globalminusone.ui.panel.container.FolderReturnState
import com.example.globalminusone.ui.panel.layout.FreeLayoutPage
import com.example.globalminusone.ui.panel.layout.GridLayoutPage
import com.example.globalminusone.ui.panel.keyboard.KeyboardCandidateOverlayHost
import com.example.globalminusone.ui.panel.keyboard.KeyboardCandidateOverlayLayer
import com.example.globalminusone.ui.panel.layout.LinearLayoutPage
import com.example.globalminusone.ui.panel.gesture.LocalAddAboveComponent
import com.example.globalminusone.ui.panel.cards.LocalAppAppearanceOverrides
import com.example.globalminusone.ui.panel.container.LocalContainerPresentation
import com.example.globalminusone.ui.panel.container.LocalFolderReturnState
import com.example.globalminusone.ui.panel.cards.LocalHardwareStatusFlow
import com.example.globalminusone.ui.panel.keyboard.LocalKeyboardCandidateOverlayHost
import com.example.globalminusone.ui.panel.cards.LocalLayoutLocked
import com.example.globalminusone.ui.panel.cards.LocalPanelOpen
import com.example.globalminusone.ui.panel.layout.LocalPageViewport
import com.example.globalminusone.ui.panel.layout.LocalPageViewportActive
import com.example.globalminusone.ui.panel.keyboard.LocalPanelActiveSide
import com.example.globalminusone.ui.panel.gesture.LocalPanelFlingCalibration
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.layout.BindResidentLoopPagerReconciler
import com.example.globalminusone.ui.panel.layout.LoopPagerNavigation
import com.example.globalminusone.ui.panel.layout.ResidentLoopPager
import com.example.globalminusone.ui.panel.layout.rememberResidentLoopPagerState
import com.example.globalminusone.ui.panel.editor.NewPageTypeModal
import com.example.globalminusone.ui.panel.layout.PageViewportStore
import com.example.globalminusone.ui.panel.gesture.PanelEditorBackHandler
import com.example.globalminusone.ui.panel.editor.PanelFooterPagePill
import com.example.globalminusone.ui.panel.editor.PanelGeometryControls
import com.example.globalminusone.ui.panel.editor.PanelGeometryOverlay
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.PanelGestureOwnership
import com.example.globalminusone.ui.panel.editor.ThemeAppearanceModal
import com.example.globalminusone.ui.panel.editor.TriggerZoneDragOverlay
import com.example.globalminusone.ui.panel.cards.UserIconCache
import com.example.globalminusone.ui.panel.editor.cardLongPressToEdit
import com.example.globalminusone.ui.panel.container.collectComponentTreeIcons
import com.example.globalminusone.ui.panel.gesture.hasLinearCardTools
import com.example.globalminusone.ui.panel.gesture.observePanelReleaseVelocity
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingCalibration
import com.example.globalminusone.ui.panel.layout.resolveTransitionCompositingStrategy
import com.example.globalminusone.ui.panel.gesture.shouldEnableChildHorizontalScroll
import com.example.globalminusone.ui.panel.gesture.strictPanelBackgroundTap

// -----------------------------------------------------------------------------------------
// 主题调色板定义
// -----------------------------------------------------------------------------------------

@Composable
internal fun MinusOneOverlayContainer(
    repository: MinusOneRepository,
    systemController: SystemController,
    widgetHostManager: WidgetHostManager,
    openProgress: Float,
    activeSide: TriggerSide,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    onRequestBindWidget: (pageId: String, containerId: String?, beforeId: String?, targetGridCell: Pair<Int, Int>?) -> Unit,
    onOpenEmbeddedApplication: (EmbeddedApplicationTarget) -> Unit,
    onBrowserHostInputAllowed: (Boolean) -> Unit = {},
    onFloatingWorkspaceInputMode: (Boolean) -> Unit = {}
) {
    MinusOneOverlayContainer(repository, systemController, widgetHostManager, { openProgress }, activeSide,
        onRequestClose, onOpenMainApp, onRequestBindWidget, onOpenEmbeddedApplication, onBrowserHostInputAllowed, onFloatingWorkspaceInputMode)
}

@Composable
internal fun MinusOneOverlayContainer(
    repository: MinusOneRepository,
    systemController: SystemController,
    widgetHostManager: WidgetHostManager,
    openProgressProvider: () -> Float,
    activeSide: TriggerSide,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    onRequestBindWidget: (pageId: String, containerId: String?, beforeId: String?, targetGridCell: Pair<Int, Int>?) -> Unit,
    onOpenEmbeddedApplication: (EmbeddedApplicationTarget) -> Unit,
    onBrowserHostInputAllowed: (Boolean) -> Unit = {},
    onFloatingWorkspaceInputMode: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    // Isolate page subtree from pure currentPageIndex updates triggered on pager settle.
    val normalizedPageState = state.copy(currentPageIndex = 0)
    val pageRenderState = remember(normalizedPageState) { normalizedPageState }
    val overlayPortal = remember { PanelOverlayPortalState() }
    val keyboardCandidateHost = remember { KeyboardCandidateOverlayHost() }
    val subPanelNav = rememberSubPanelNavigationController(repository, overlayPortal) {
        keyboardCandidateHost.dismiss()
    }
    val validSubPanelIds = remember(pageRenderState.subPanels) { pageRenderState.subPanels.mapTo(HashSet()) { it.id } }
    LaunchedEffect(validSubPanelIds) { subPanelNav.pruneMissing(validSubPanelIds) }
    val activeSubPanel = remember(subPanelNav.stack, pageRenderState.subPanels) {
        subPanelNav.activeSubPanel(pageRenderState)
    }
    val activeScopeId = activeSubPanel?.id ?: ""
    val visibleTrueIndices = remember(pageRenderState.pages, activeScopeId) {
        pageRenderState.pages.indices.filter { pageRenderState.pages[it].subPanelId == activeScopeId }
    }
    val visiblePages = remember(pageRenderState.pages, visibleTrueIndices) {
        visibleTrueIndices.map { pageRenderState.pages[it] }
    }
    val visiblePageIds = remember(visiblePages) { visiblePages.map { it.id } }
    val keepAwake by systemController.keepAwake.collectAsState()
    val panelView = LocalView.current
    val latestOpenProgress by rememberUpdatedState(openProgressProvider)
    val isPanelOpen by remember { derivedStateOf { latestOpenProgress() > 0f } }
    SideEffect { panelView.keepScreenOn = keepAwake && isPanelOpen }
    DisposableEffect(panelView) { onDispose { panelView.keepScreenOn = false } }
    val themeConfig = remember(state.themeConfig, activeSubPanel) {
        activeSubPanel?.let { com.example.globalminusone.data.operations.resolveSubPanelTheme(state.themeConfig, it) } ?: state.themeConfig
    }
    val layoutLocked = state.advancedConfig.layoutLocked
    val isEditMode = state.editMode && !layoutLocked
    val appearance = state.appearanceConfig
    val scope = rememberCoroutineScope()
    val panelDensity = LocalDensity.current
    var previewPanelTheme by remember { mutableStateOf<ThemeConfig?>(null) }
    var panelGeometryExpanded by remember(isEditMode) { mutableStateOf(false) }
    var triggerZoneExpanded by remember(isEditMode) { mutableStateOf(false) }
    var panelNumbersOpen by remember(isEditMode) { mutableStateOf(false) }
    val modalHost = remember { PanelModalHostState() }
    val folderReturnState = remember { FolderReturnState() }
    val subPanelPortalProgress = rememberSubPanelPortalProgress(subPanelNav, themeConfig.resolvedSubPanelMs())
    val corruptStateNotice by repository.corruptStateRecoveredNotice.collectAsState()
    com.example.globalminusone.ui.RepositoryPersistenceFeedback(repository, enabled = isPanelOpen)
    val gestureOwnership = remember { PanelGestureOwnership() }
    val flingCalibration = rememberPanelFlingCalibration()
    val pageViewports = remember { PageViewportStore() }
    // Non-observable, idempotent bookkeeping must precede child composition: an
    // editing child can immediately replace the deleted first-card anchor.
    if (isEditMode) pageViewports.beginEditing()
    SideEffect { if (!isEditMode) pageViewports.finishEditing(save = true) }
    val allPageIds = remember(pageRenderState.pages) { pageRenderState.pages.map { it.id } }
    LaunchedEffect(allPageIds) { pageViewports.retain(allPageIds.toSet()) }
    LaunchedEffect(isPanelOpen, corruptStateNotice) {
        if (isPanelOpen && corruptStateNotice) {
            val preserved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { repository.ensureCorruptStateBackup() }.isSuccess
            }
            if (preserved) repository.acknowledgeCorruptStateNotice()
            Toast.makeText(context, if (preserved) "原配置损坏，原文已校验备份；当前显示临时默认面板"
                else "原配置损坏且原文备份未完成；原文仍保留，导出和恢复将停止以防覆盖", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(isPanelOpen) {
        if (isPanelOpen) {
            subPanelNav.applyOpenPagePolicyIfLocked()
            systemController.refreshBrightness()
        }
    }
    LaunchedEffect(!isPanelOpen) {
        if (!isPanelOpen) {
            overlayPortal.clear()
            modalHost.dismiss()
            keyboardCandidateHost.dismiss()
            folderReturnState.clear()
            subPanelNav.onPanelClosed()
        }
    }

    var showFileBrowser by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    val browserSession = remember(context) { IndependentBrowserSession(context.applicationContext) }
    val configurationRestoreGeneration by repository.configurationRestoreGeneration.collectAsState()
    LaunchedEffect(configurationRestoreGeneration) {
        browserSession.reloadSavedPreferences()
        subPanelNav.reconcileActiveScope(applyLocked = isPanelOpen)
    }
    val floatingWindowOpen = showFileBrowser || showBrowser
    var fileWindowContentVisible by remember { mutableStateOf(true) }
    val floatingWindowInputActive = showBrowser || (showFileBrowser && fileWindowContentVisible)
    // The full-screen overlay stays fixed while FloatingPanelWindow applies IME
    // avoidance. Restore the ordinary card editor's resize policy on exit/hide.
    val updateWorkspaceInputMode by rememberUpdatedState(onFloatingWorkspaceInputMode)
    DisposableEffect(floatingWindowInputActive && isPanelOpen) {
        updateWorkspaceInputMode(floatingWindowInputActive && isPanelOpen)
        onDispose { updateWorkspaceInputMode(false) }
    }
    var previewFileShortcut by remember { mutableStateOf<ComponentItem?>(null) }
    val hardwareStatuses = remember(context) { SystemMonitor.observe(context) }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveThemeConfig = remember(themeConfig, systemDark) {
        if (themeConfig.followSystemDark) {
            themeConfig.copy(
                isDarkMode = systemDark,
                defaultCardBgColorHex = if (systemDark) 0xFF1E222BL else 0xFFFFFFFFL,
                defaultTextColorHex = if (systemDark) 0xFFF5F7FAL else 0xFF1F2329L,
                titleColorHex = if (systemDark) 0xFFFFFFFFL else 0xFF1F2329L
            )
        } else themeConfig
    }
    val palette = remember(effectiveThemeConfig, appearance) {
        calculatePanelPalette(effectiveThemeConfig, appearance)
    }

    // 拖动亮度滑块时临时半透明预览屏幕亮度
    var isAdjustingBrightness by remember { mutableStateOf(false) }

    // 内部弹窗状态管理
    var showNineKeyMenu by remember { mutableStateOf(false) }
    var showAddComponent by remember { mutableStateOf(false) }
    var addTargetContainerId by remember { mutableStateOf<String?>(null) }
    var addBeforeComponentId by remember { mutableStateOf<String?>(null) }
    var addTargetGridCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showThemeAppearance by remember { mutableStateOf(false) }
    var themeInitialSection by remember { mutableStateOf("") }
    var showDeletePageConfirm by remember { mutableStateOf(false) }
    var showNewPageTypeDialog by remember { mutableStateOf(false) }
    var editingComponent by remember { mutableStateOf<ComponentItem?>(null) }
    var showEditExitConfirmation by remember { mutableStateOf(false) }
    var afterEditExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun finishEditing(save: Boolean) {
        if (!repository.finishPanelEdit(save)) return
        pageViewports.finishEditing(save)
        val followUp = afterEditExit
        afterEditExit = null
        followUp?.invoke()
    }
    fun requestFinishEditing() {
        if (repository.hasPanelEditChanges()) showEditExitConfirmation = true
        else finishEditing(save = true)
    }
    val editorBack by rememberUpdatedState<() -> Boolean>({
        when {
            showDeletePageConfirm -> { showDeletePageConfirm = false; true }
            showNewPageTypeDialog -> { showNewPageTypeDialog = false; true }
            showEditExitConfirmation -> { showEditExitConfirmation = false; afterEditExit = null; true }
            editingComponent != null -> { editingComponent = null; true }
            subPanelNav.editingSubPanelId != null -> { subPanelNav.editingSubPanelId = null; true }
            showAddComponent -> {
                showAddComponent = false; addTargetContainerId = null; addBeforeComponentId = null; addTargetGridCell = null
                folderReturnState.triggerPendingReopen(); true
            }
            showThemeAppearance -> { showThemeAppearance = false; themeInitialSection = ""; true }
            showNineKeyMenu -> { showNineKeyMenu = false; true }
            panelNumbersOpen -> { panelNumbersOpen = false; true }
            triggerZoneExpanded -> { triggerZoneExpanded = false; true }
            panelGeometryExpanded -> { panelGeometryExpanded = false; previewPanelTheme = null; true }
            repository.state.value.hasLinearCardTools() -> {
                repository.selectComponent(null); true
            }
            repository.state.value.editMode -> { requestFinishEditing(); true }
            subPanelNav.pop(activeScopeId) -> true
            else -> false
        }
    })
    DisposableEffect(isPanelOpen) {
        val handler: () -> Boolean = { editorBack() }
        if (isPanelOpen) PanelEditorBackHandler.handler = handler
        onDispose { if (PanelEditorBackHandler.handler === handler) PanelEditorBackHandler.handler = null }
    }
    val returnToBrowser by PanelFileBrowser.returnToBrowser.collectAsState()

    fun allowLayoutEdit(): Boolean {
        if (!repository.state.value.advancedConfig.layoutLocked) return true
        Toast.makeText(context, "布局已锁定，请先在底部工具栏解锁", Toast.LENGTH_SHORT).show()
        return false
    }

    fun commitPanelGeometry(edited: ThemeConfig) {
        val latest = repository.state.value
        if (latest.editMode && !latest.advancedConfig.layoutLocked) {
            val currentSub = activeSubPanel
            if (currentSub != null) {
                repository.updateSubPanel(currentSub.id) { current ->
                    com.example.globalminusone.data.operations.applyGeometryToSubPanel(
                        current.copy(inheritGeometry = false),
                        edited
                    )
                }
            } else {
                repository.updateThemeConfig { current -> current.copy(
                    panelWidthPercent = edited.panelWidthPercent, panelHeightPercent = edited.panelHeightPercent,
                    panelHorizontalOffsetPercent = edited.panelHorizontalOffsetPercent,
                    panelVerticalPositionPercent = edited.panelVerticalPositionPercent) }
            }
        }
        previewPanelTheme = null
    }

    LaunchedEffect(returnToBrowser) {
        if (returnToBrowser) {
            showBrowser = false; showFileBrowser = true
            PanelFileBrowser.consumeReturnToBrowser()
        }
    }
    LaunchedEffect(isEditMode, !isPanelOpen) {
        if (!isEditMode || !isPanelOpen) {
            previewPanelTheme = null; panelGeometryExpanded = false; triggerZoneExpanded = false; panelNumbersOpen = false
        }
    }
    LaunchedEffect(layoutLocked) {
        if (layoutLocked) {
            showAddComponent = false
            showDeletePageConfirm = false
            showNewPageTypeDialog = false
            editingComponent = null
            subPanelNav.editingSubPanelId = null
        }
    }

    // Resident real-page circular pager avoids creating/disposing pages mid-swipe.
    val pageCount = visiblePages.size.coerceAtLeast(1)
    val initialLocalPage = remember(visiblePageIds, visibleTrueIndices) {
        visibleTrueIndices.indexOf(repository.state.value.currentPageIndex).let { if (it >= 0) it else 0 }
    }
    val pagerState = key(visiblePageIds) {
        rememberResidentLoopPagerState(initialRealPage = initialLocalPage, pageCount = pageCount)
    }
    // Deferred read avoids subscribing MinusOneOverlayContainer to currentRealPage flips mid-swipe.
    val latestCurrentLocalPage by rememberUpdatedState { pagerState.currentRealPage }
    val latestCurrentTruePage by rememberUpdatedState {
        visibleTrueIndices.getOrElse(pagerState.currentRealPage) { visibleTrueIndices.firstOrNull() ?: 0 }
    }
    var pagerNavigation by remember(pagerState) { mutableStateOf<LoopPagerNavigation?>(null) }
    val repositoryPageSelection by rememberUpdatedState(visiblePageIds to state.currentPageIndex)
    fun navigatePage(targetLocal: Int, direction: Int = 0) {
        val latest = repository.state.value
        val currentVisibleIndices = latest.pages.indices.filter { latest.pages[it].subPanelId == activeScopeId }
        val clampedLocal = targetLocal.coerceIn(0, (currentVisibleIndices.size - 1).coerceAtLeast(0))
        val targetTrue = currentVisibleIndices.getOrElse(clampedLocal) { return }
        if (currentVisibleIndices.map { latest.pages[it].id } == visiblePageIds && pageCount > 1) {
            pagerNavigation = LoopPagerNavigation((pagerNavigation?.sequence ?: 0L) + 1, clampedLocal, direction)
        }
        if (latest.currentPageIndex != targetTrue) repository.setCurrentPageIndex(targetTrue)
    }
    BindResidentLoopPagerReconciler(
        pagerState = pagerState,
        initialLocalPage = initialLocalPage,
        visibleTrueIndices = visibleTrueIndices,
        readRepositoryIndex = {
            val observedSelection = repositoryPageSelection
            val latest = repository.state.value
            val latestVisibleIds = latest.pages.filter { it.subPanelId == activeScopeId }.map { it.id }
            if (observedSelection.first != visiblePageIds || latestVisibleIds != visiblePageIds) null else {
                val activeId = latest.pages.getOrNull(latest.currentPageIndex)?.id
                latestVisibleIds.indexOf(activeId).let { if (it >= 0) it else 0 }
            }
        },
        readNavigation = { pagerNavigation },
        readAnimMs = {
            if (!isPanelOpen || subPanelNav.shouldSnapPageTransition()) 0
            else repository.state.value.themeConfig.resolvedPageSwitchMs()
        },
        onPersistTruePage = { targetTrue -> repository.setCurrentPageIndex(targetTrue) }
    )

    val latestImageRevisions by repository.imageAccessRevisions.collectAsState()
    LaunchedEffect(context, pageRenderState.pages, pageRenderState.appAppearanceOverrides) {
        val packages = linkedSetOf<String>()
        val uris = linkedSetOf<String>()
        pageRenderState.pages.forEach { page ->
            collectComponentTreeIcons(page.components, packages, uris)
        }
        pageRenderState.appAppearanceOverrides.values.forEach { override ->
            override.iconUri.trim().takeIf { it.isNotEmpty() }?.let { uris.add(it) }
        }
        AppAndIntentLauncher.syncPinnedPackages(packages)
        coroutineScope {
            if (uris.isNotEmpty()) launch { UserIconCache.prewarm(context, uris, latestImageRevisions) }
            if (packages.isNotEmpty()) launch { AppAndIntentLauncher.prewarmPackages(context, packages) }
        }
    }

    val latestAllowLayoutEdit by rememberUpdatedState(::allowLayoutEdit)
    val latestRequestFinishEditing by rememberUpdatedState(::requestFinishEditing)
    val latestOpenEmbeddedApp by rememberUpdatedState(onOpenEmbeddedApplication)
    val latestRequestClose by rememberUpdatedState(onRequestClose)
    val latestOpenMainApp by rememberUpdatedState(onOpenMainApp)

    val latestOpenSubPanel by rememberUpdatedState<(String, Rect?) -> Unit> { targetSubId, anchorBounds ->
        subPanelNav.open(activeScopeId, targetSubId, anchorBounds)
    }

    val closePanelForExternalApp = remember { { latestRequestClose() } }
    val openMainAppStable = remember { { latestOpenMainApp() } }
    val addAboveComponent = remember(repository) {
        { componentId: String ->
            if (latestAllowLayoutEdit()) {
                val page = repository.state.value.pages.getOrNull(latestCurrentTruePage())
                if (page?.components?.findComponent(componentId) != null) {
                    addTargetContainerId = page.components.parentContainerId(componentId)
                    addBeforeComponentId = componentId
                    showAddComponent = true
                }
            }
        }
    }
    val panelActions = remember(context, repository, browserSession) {
        PanelActions(
            openBrowserUrl = { url ->
                val target = browserDestination(url)
                if (target != null && browserSession.openTab(target)) {
                    showFileBrowser = false
                    showBrowser = true
                } else Toast.makeText(
                    context,
                    if (target == null) "网址无效" else "标签已满，请先关闭一个标签",
                    Toast.LENGTH_SHORT
                ).show()
            },
            closePanelForExternalApp = closePanelForExternalApp,
            openEmbeddedApplication = { target ->
                showFileBrowser = false; showBrowser = false; previewFileShortcut = null
                latestOpenEmbeddedApp(target)
            },
            exitPanelEdit = { latestRequestFinishEditing() },
            openNewPageModal = { showNewPageTypeDialog = true },
            addToContainer = { containerId ->
                if (latestAllowLayoutEdit()) {
                    addTargetContainerId = containerId
                    addBeforeComponentId = null
                    showAddComponent = true
                }
            },
            openFileBrowser = { previewFileShortcut = null; showBrowser = false; showFileBrowser = true },
            openFileShortcut = { item -> previewFileShortcut = item; showBrowser = false; showFileBrowser = true },
            openSubPanel = { targetSubId, anchorBounds -> latestOpenSubPanel(targetSubId, anchorBounds) },
            selectImage = { item ->
                val latest = repository.state.value
                val page = latest.pages.firstOrNull { page -> page.components.findComponent(item.id) != null }
                if (latest.advancedConfig.layoutLocked) Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
                else if (page != null) PanelFileBrowser.requestImage(context, page.id, item.id)
            }
        )
    }
    val onBrightnessAdjusting = remember { { adjusting: Boolean -> isAdjustingBrightness = adjusting } }
    val onOpenEditDialog = remember(repository) {
        { comp: ComponentItem ->
            if (latestAllowLayoutEdit()) {
                repository.selectComponent(comp.id)
                editingComponent = comp
            }
        }
    }
    val onOpenBottomNineKeyBar = remember { { showNineKeyMenu = true } }
    val onOpenAddComponent = remember(repository) {
        {
            if (latestAllowLayoutEdit()) {
                addTargetContainerId = null
                addTargetGridCell = null
                addBeforeComponentId = repository.state.value.pages
                    .getOrNull(latestCurrentTruePage())
                    ?.components
                    ?.lastOrNull { it.type == ComponentType.OPERATION_BAR }
                    ?.id
                showAddComponent = true
            }
        }
    }
    val onOpenAddAtGridCell = remember(repository) {
        { col: Int, row: Int ->
            if (latestAllowLayoutEdit()) {
                addTargetContainerId = null
                addBeforeComponentId = null
                addTargetGridCell = col to row
                showAddComponent = true
            }
        }
    }
    val onAddAboveComponentTop = remember {
        { targetId: String ->
            if (latestAllowLayoutEdit()) {
                addTargetContainerId = null; addTargetGridCell = null; addBeforeComponentId = targetId; showAddComponent = true
            }
        }
    }

    CompositionLocalProvider(
        LocalContainerPresentation provides remember { ContainerPresentation() },
        LocalFolderReturnState provides folderReturnState,
        LocalPanelPalette provides palette,
        LocalPanelActiveSide provides activeSide,
        LocalKeyboardCandidateOverlayHost provides keyboardCandidateHost,
        LocalAppAppearanceOverrides provides pageRenderState.appAppearanceOverrides,
        LocalPanelOpen provides isPanelOpen,
        LocalHardwareStatusFlow provides hardwareStatuses,
        LocalLayoutLocked provides layoutLocked,
        LocalPanelModalHost provides modalHost,
        LocalPanelOverlayPortal provides overlayPortal,
        LocalPanelGestureOwnership provides gestureOwnership,
        LocalPanelFlingCalibration provides flingCalibration,
        LocalAddAboveComponent provides addAboveComponent,
        LocalPanelActions provides panelActions
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().observePanelReleaseVelocity(flingCalibration)) {
            // 1. 背景遮罩：支持暗色遮罩与亮色雾化遮罩 + 透明度调节（抽离到 Draw 阶段，避免动画逐帧重组）
            val maskBaseColor = Color((themeConfig.maskColorHex and 0x00FFFFFFL) or 0xFF000000L)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .cardLongPressToEdit("panel-background", !layoutLocked, ignoreOwnedGesture = true) {
                        if (isEditMode) showThemeAppearance = true else repository.setEditMode(true)
                    }
                    .drawBehind {
                        val p = latestOpenProgress().coerceIn(0f, 1f)
                        val maskAlpha = (p * (themeConfig.maskAlphaPercent / 100f)).coerceIn(0f, 0.95f)
                        if (maskAlpha > 0f) drawRect(maskBaseColor.copy(alpha = maskAlpha))
                    }
                    .strictPanelBackgroundTap {
                        if (floatingWindowOpen) {
                            // A floating workspace closes via its own controls/Back, including dirty-editor checks.
                        } else if (editingComponent != null) {
                            editingComponent = null
                        } else if (subPanelNav.editingSubPanelId != null) {
                            subPanelNav.editingSubPanelId = null
                        } else if (showNineKeyMenu) {
                            showNineKeyMenu = false
                        } else if (showAddComponent && !layoutLocked) {
                            showAddComponent = false; addTargetContainerId = null; addBeforeComponentId = null; addTargetGridCell = null
                            folderReturnState.triggerPendingReopen()
                        } else if (showThemeAppearance) {
                            showThemeAppearance = false
                        } else if (showDeletePageConfirm && !layoutLocked) {
                            showDeletePageConfirm = false
                        } else if (showNewPageTypeDialog && !layoutLocked) {
                            showNewPageTypeDialog = false
                        } else if (panelGeometryExpanded) {
                            panelGeometryExpanded = false; previewPanelTheme = null
                        } else if (triggerZoneExpanded) {
                            triggerZoneExpanded = false
                        } else if (isEditMode) {
                            if (state.selectedComponentId != null) {
                                repository.selectComponent(null)
                            } else {
                                requestFinishEditing()
                            }
                        } else {
                            onRequestClose()
                        }
                    }
            )

            // 2. 负一屏面板主体（按 activeSide 靠左或靠右，支持亮度拖动临时半透明）
            val isRightSide = activeSide == TriggerSide.RIGHT
            val geometryTheme = previewPanelTheme ?: themeConfig
            val geometry = panelBounds(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), geometryTheme, isRightSide)
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()
            val containerPresentation = LocalContainerPresentation.current
            val cornerRadius = themeConfig.cardCornerRadiusDp.dp
            val panelShape = if (isRightSide) {
                RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius)
            } else {
                RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius)
            }

            val panelWindowRect = Rect(geometry.x, geometry.y, geometry.x + geometry.width, geometry.y + geometry.height)
            Box(
                    modifier = Modifier
                        .offset { IntOffset(geometry.x.roundToInt(), geometry.y.roundToInt()) }
                        .size(with(panelDensity) { geometry.width.toDp() }, with(panelDensity) { geometry.height.toDp() })
                        .graphicsLayer {
                            val p = latestOpenProgress().coerceIn(0f, 1f)
                            val distance = if (isRightSide) screenWidthPx - geometry.x else geometry.x + geometry.width
                            val slideX = if (isRightSide) (1f - p) * distance else -(1f - p) * distance
                            val resolvedAlpha = when {
                                isAdjustingBrightness -> 0.25f
                                triggerZoneExpanded -> 0.42f
                                else -> p
                            }
                            applyPanelWithSubPanelPortal(
                                baseTranslationX = slideX,
                                baseAlpha = resolvedAlpha,
                                portalProgress = subPanelPortalProgress(),
                                direction = subPanelNav.transitionDirection,
                                anchorBounds = subPanelNav.transitionAnchorBounds,
                                panelBounds = panelWindowRect,
                                style = themeConfig.moduleAnimations.subPanelTransitionStyle
                            )
                            shape = panelShape
                            clip = true
                        }
                        .onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInWindow()
                            containerPresentation?.panelBounds = com.example.globalminusone.data.policy.ContainerPopupBounds(
                                rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
                        }
                        .background(palette.panelBackgroundBrush)
                        .subPanelPortalRim(subPanelPortalProgress, subPanelNav.transitionDirection, palette.accent, cornerRadius, themeConfig.moduleAnimations.subPanelTransitionStyle)
                ) {
                    val animMs = themeConfig.resolvedModalMenuMs()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (themeConfig.coverStatusBar) Modifier else Modifier.statusBarsPadding())
                            .navigationBarsPadding()
                    ) {
                            // 编辑模式下仅保留顶部轻量让位区，释放底部空间避免遮挡页面
                            val chromeAlpha by animateFloatAsState(
                                targetValue = if (isEditMode) 0f else 1f,
                                animationSpec = MotionTokens.chromeFadeSpec(animMs),
                                label = "panel-chrome-alpha"
                            )
                            val headerTokens = panelOverlayTokens(palette)
                            val pillSurface = headerTokens.elevatedSurface.copy(alpha = 0.90f)
                            val pillBorder = BorderStroke(0.75.dp, headerTokens.border)
                            val pillDivider = headerTokens.divider
                            BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                                val iconOnly = maxWidth < 268.dp * LocalDensity.current.fontScale
                                val headerScroll = rememberScrollState()
                                val headerScrollEnabled = shouldEnableChildHorizontalScroll(!isEditMode, headerScroll.maxValue)
                                Row(
                                    Modifier.fillMaxWidth().height(48.dp).graphicsLayer { alpha = chromeAlpha }
                                        .then(if (iconOnly && maxWidth < 236.dp) Modifier
                                            .ownPanelScrollGestures(headerScrollEnabled, lockFromDown = true, axis = PanelGestureAxis.Horizontal)
                                            .horizontalScroll(headerScroll, enabled = headerScrollEnabled) else Modifier),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 左侧工具胶囊：在子面板中显示返回与设置；主面板显示 浏览器 | 文件
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = pillSurface,
                                        border = pillBorder
                                    ) {
                                        val headerSubPanel = activeSubPanel
                                        if (headerSubPanel != null) {
                                            SubPanelHeaderPill(
                                                subPanel = headerSubPanel,
                                                isEditMode = isEditMode,
                                                pillDivider = pillDivider,
                                                palette = palette,
                                                onExitSubPanel = { subPanelNav.pop(activeScopeId) },
                                                onOpenSettings = {
                                                    if (allowLayoutEdit()) subPanelNav.editingSubPanelId = headerSubPanel.id
                                                }
                                            )
                                        } else {
                                            Row(
                                                Modifier.height(36.dp).padding(horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable(enabled = !isEditMode) {
                                                            showFileBrowser = false
                                                            previewFileShortcut = null
                                                            showBrowser = true
                                                        }
                                                        .height(32.dp)
                                                        .padding(horizontal = if (iconOnly) 10.dp else 10.dp)
                                                        .semantics { contentDescription = "浏览器" },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(Icons.Language, null, Modifier.size(16.dp), tint = palette.accent)
                                                    if (!iconOnly) {
                                                        Text(
                                                            "浏览器",
                                                            color = palette.textPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Box(Modifier.width(0.8.dp).height(16.dp).background(pillDivider))
                                                Row(
                                                    Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable(enabled = !isEditMode) {
                                                            previewFileShortcut = null
                                                            showBrowser = false
                                                            showFileBrowser = true
                                                        }
                                                        .height(32.dp)
                                                        .padding(horizontal = if (iconOnly) 10.dp else 10.dp)
                                                        .semantics { contentDescription = "文件浏览" },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Folder, null, Modifier.size(16.dp), tint = palette.accent)
                                                    if (!iconOnly) {
                                                        Text(
                                                            "文件",
                                                            color = palette.textPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 右侧快捷胶囊：仅保留添加与编辑（页面与菜单统一由底栏胶囊承担）
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = pillSurface,
                                        border = pillBorder
                                    ) {
                                        Row(
                                            Modifier.height(36.dp).padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = onOpenAddComponent,
                                                enabled = !isEditMode,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Add,
                                                    contentDescription = "添加组件",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = if (layoutLocked) palette.textSecondary.copy(alpha = 0.45f) else palette.textPrimary
                                                )
                                            }
                                            Box(Modifier.width(0.8.dp).height(16.dp).background(pillDivider))
                                            IconButton(
                                                onClick = {
                                                    if (allowLayoutEdit()) {
                                                        repository.setEditMode(true)
                                                        Toast.makeText(context, "已进入编辑模式，长按卡片拖动", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                enabled = !isEditMode,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Edit,
                                                    contentDescription = "编辑布局",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (layoutLocked) palette.textSecondary.copy(alpha = 0.45f) else palette.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 多页常驻循环滑动容器与 12 种转场动画（编辑模式下禁用左右滑动，防止抢占卡片拖拽手势）
                            ResidentLoopPager(
                                state = pagerState,
                                pageKey = { visiblePageIds.getOrElse(it) { "empty" } },
                                transition = themeConfig.pageTransition,
                                pageSwitchMs = themeConfig.resolvedPageSwitchMs(),
                                userScrollEnabled = pageCount > 1 && !isEditMode && modalHost.entry == null && !overlayPortal.hasEntries && !gestureOwnership.exclusiveHorizontalActive,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { localPageIndex ->
                                val pageIndex = visibleTrueIndices.getOrNull(localPageIndex) ?: return@ResidentLoopPager
                                val targetPage = pageRenderState.pages.getOrNull(pageIndex) ?: return@ResidentLoopPager
                                val isViewportActive by remember(pagerState, localPageIndex) {
                                    derivedStateOf {
                                        !pagerState.isScrollInProgress && pagerState.settledRealPage == localPageIndex
                                    }
                                }
                                CompositionLocalProvider(
                                    LocalPageViewport provides pageViewports.page(targetPage.id),
                                    LocalPageViewportActive provides isViewportActive
                                ) {
                                    when (targetPage.layoutType) {
                                        PageLayoutType.FREE -> {
                                            FreeLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = pageRenderState,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = onBrightnessAdjusting,
                                                onRequestClose = closePanelForExternalApp,
                                                onOpenMainApp = openMainAppStable,
                                                onOpenEditDialog = onOpenEditDialog,
                                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                                onOpenAddComponent = onOpenAddComponent
                                            )
                                        }
                                        PageLayoutType.GRID -> {
                                            GridLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = pageRenderState,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = onBrightnessAdjusting,
                                                onRequestClose = closePanelForExternalApp,
                                                onOpenMainApp = openMainAppStable,
                                                onOpenEditDialog = onOpenEditDialog,
                                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                                onOpenAddComponent = onOpenAddComponent,
                                                onAddAboveComponent = onAddAboveComponentTop,
                                                onAddAtGridCell = onOpenAddAtGridCell
                                            )
                                        }
                                        PageLayoutType.LINEAR -> {
                                            LinearLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = pageRenderState,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = onBrightnessAdjusting,
                                                onRequestClose = closePanelForExternalApp,
                                                onOpenMainApp = openMainAppStable,
                                                onOpenEditDialog = onOpenEditDialog,
                                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                                onOpenAddComponent = onOpenAddComponent
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isEditMode) {
                                PanelFooterPagePill(
                                    currentPageProvider = { pagerState.currentRealPage },
                                    pages = visiblePages,
                                    layoutLocked = layoutLocked,
                                    palette = palette,
                                    chromeAlpha = chromeAlpha,
                                    onOpenMenu = onOpenBottomNineKeyBar
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }
                    }

                    // A floating window intercepts only its own bounds. Exposed cards and the
                    // pager remain usable; actual confirmations keep the modal host's scrim.
                }
                if (!isEditMode && !floatingWindowOpen && !showNineKeyMenu && !showAddComponent &&
                    !showThemeAppearance && !showDeletePageConfirm && !showNewPageTypeDialog &&
                    editingComponent == null && subPanelNav.editingSubPanelId == null
                ) {
                    KeyboardCandidateOverlayLayer(
                        host = keyboardCandidateHost,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = latestOpenProgress().coerceIn(0f, 1f)
                            }
                    )
                }
                val darkBrowser = browserSession.nightMode
                val browserWindowPalette = remember(palette, darkBrowser) {
                    palette.copy(
                        isDark = darkBrowser,
                        cardBackground = Color(if (darkBrowser) 0xFF181B22 else 0xFFFFFFFF),
                        cardBorder = Color(if (darkBrowser) 0xFF3E4756 else 0xFFDDE2EA),
                        textPrimary = Color(if (darkBrowser) 0xFFF2F5FA else 0xFF1F2329),
                        textSecondary = Color(if (darkBrowser) 0xFFB5BFCE else 0xFF606975),
                        accent = Color(if (darkBrowser) 0xFF8AB4F8 else 0xFF246BCC)
                    )
                }
                val overlayAnimMs = themeConfig.resolvedModalMenuMs()
                val containerAnimMs = themeConfig.resolvedContainerPopupMs()
                // Floating workspaces use the full overlay, independent of panel width, offset and clipping.
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val windowAlpha = latestOpenProgress().coerceIn(0f, 1f)
                    alpha = windowAlpha
                    compositingStrategy = resolveTransitionCompositingStrategy(windowAlpha)
                }) {
                    AnimatedOverlayPresence(visible = showBrowser, animMs = containerAnimMs) {
                        CompositionLocalProvider(LocalPanelPalette provides browserWindowPalette) {
                            FloatingPanelWindow(
                                windowId = "chrome",
                                title = "浏览器",
                                initialWidthFraction = browserSession.windowWidthFraction,
                                initialHeightFraction = browserSession.windowHeightFraction,
                                initialXFraction = browserSession.windowXFraction,
                                initialYFraction = browserSession.windowYFraction,
                                onClose = { showBrowser = false },
                                onGeometryCommitted = { w, h, x, y ->
                                    browserSession.updateWindowGeometry(w, h, x, y)
                                }
                            ) {
                                BrowserPanel(browserSession, visible = isPanelOpen,
                                    onHostInputAllowed = onBrowserHostInputAllowed,
                                    onClose = { showBrowser = false })
                            }
                        }
                    }
                    AnimatedOverlayPresence(visible = showFileBrowser, animMs = containerAnimMs) {
                        FloatingFileBrowser(repository = repository,
                            onContentVisibilityChanged = { fileWindowContentVisible = it },
                            initialFile = state.pages.firstNotNullOfOrNull { it.components.findComponent(previewFileShortcut?.id.orEmpty()) } ?: previewFileShortcut,
                            onDismiss = { showFileBrowser = false; previewFileShortcut = null })
                    }
                }
                PanelOverlayPortalHost(overlayPortal)

                // Editing tools and panel handles share root coordinates, never the content Column.
                if (isEditMode && isPanelOpen) {
                    if (panelGeometryExpanded) PanelGeometryOverlay(geometryTheme, geometry, screenWidthPx, screenHeightPx, isRightSide,
                        onPreview = { edited ->
                            val latest = repository.state.value
                            if (latest.editMode && !latest.advancedConfig.layoutLocked) previewPanelTheme = edited
                        }, onCommit = { commitPanelGeometry(it) }, onCancel = { previewPanelTheme = null })
                    if (triggerZoneExpanded) TriggerZoneDragOverlay(
                        triggerConfig = state.triggerConfig,
                        suspensionBallConfig = state.suspensionBallConfig,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        activeRightSide = isRightSide,
                        onUpdateTrigger = { transform ->
                            if (allowLayoutEdit()) repository.updateTriggerConfig(transform)
                        },
                        onUpdateSuspensionBall = { transform ->
                            if (allowLayoutEdit()) repository.updateSuspensionBallConfig(transform)
                        },
                        onDone = { triggerZoneExpanded = false }
                    )
            if (isEditMode) {
                val editLocalPage = pagerState.currentRealPage
                val editTruePage = visibleTrueIndices.getOrNull(editLocalPage) ?: 0
                val curPage = pageRenderState.pages.getOrNull(editTruePage)
                val selectedCard = state.selectedComponentId?.let { selectedId -> curPage?.components?.findComponent(selectedId) }
                EditModeTopBanner(
                    currentPage = editLocalPage,
                    totalPages = visiblePages.size.coerceAtLeast(1),
                    isLinearPage = curPage?.layoutType == PageLayoutType.LINEAR,
                    layoutType = curPage?.layoutType ?: PageLayoutType.FREE,
                    onPrevPage = {
                        if (pageCount > 1) navigatePage(Math.floorMod(editLocalPage - 1, pageCount), direction = -1)
                    },
                    onNextPage = {
                        if (pageCount > 1) navigatePage((editLocalPage + 1) % pageCount, direction = 1)
                    },
                    onToggleLayoutType = {
                        if (allowLayoutEdit()) {
                            val pageId = curPage?.id.orEmpty()
                            val result = repository.tryTogglePageLayoutType(pageId)
                            val switched = repository.state.value.pages.firstOrNull { it.id == pageId }?.layoutType?.label ?: "页面布局"
                            reportLayoutMutation(context, result, "已切换为$switched")
                        }
                    },
                    onSelectLayoutType = { chosen ->
                        if (allowLayoutEdit()) {
                            val pageId = curPage?.id.orEmpty()
                            reportLayoutMutation(context, repository.trySetPageLayoutType(pageId, chosen), "已切换为${chosen.label}")
                        }
                    },
                    onAdd = onOpenAddComponent,
                    onDone = {
                        if (triggerZoneExpanded) triggerZoneExpanded = false
                        else requestFinishEditing()
                    },
                    onGlobalProperties = { themeInitialSection = ""; showThemeAppearance = true },
                    onPanelGeometry = {
                        panelGeometryExpanded = !panelGeometryExpanded
                        if (panelGeometryExpanded) triggerZoneExpanded = false
                        if (!panelGeometryExpanded) previewPanelTheme = null
                    },
                    onTriggerZone = {
                        triggerZoneExpanded = !triggerZoneExpanded
                        if (triggerZoneExpanded) {
                            panelGeometryExpanded = false
                            previewPanelTheme = null
                        }
                    },
                    panelGeometryActive = panelGeometryExpanded,
                    triggerZoneActive = triggerZoneExpanded,
                    onPanelNumbers = { panelNumbersOpen = true },
                    onNewPage = { if (allowLayoutEdit()) showNewPageTypeDialog = true },
                    onCompactGrid = if (curPage?.layoutType == PageLayoutType.GRID) ({
                        repository.compactPageGridPlacements(editTruePage)
                    }) else null,
                    pageName = curPage?.name.orEmpty(),
                    onCardProperties = selectedCard?.let { selected -> ({ editingComponent = selected }) },
                    selectedCardTitle = selectedCard?.title?.takeIf { it.isNotBlank() } ?: selectedCard?.type?.label,
                    onClearSelection = { repository.selectComponent(null) },
                    subPanelTitle = activeSubPanel?.name,
                    onExitSubPanel = activeSubPanel?.let { ({ subPanelNav.pop(activeScopeId) }) },
                    onSubPanelSettings = activeSubPanel?.let { sp -> ({ subPanelNav.editingSubPanelId = sp.id }) }
                )
            }

                    if (panelNumbersOpen) PanelGeometryControls(geometryTheme,
                        onCommit = { commitPanelGeometry(it) }, onDismiss = { panelNumbersOpen = false })
                }
                    // Explicit panel tools and dialogs remain above an open workspace.
                        // 底部 9 键操作栏浮层
                        if (showEditExitConfirmation) PanelAlertDialog(
                            onDismissRequest = { showEditExitConfirmation = false; afterEditExit = null },
                            title = { Text("保存编辑更改？") },
                            text = { Text("已调整的卡片、布局和面板外观尚未保存。") },
                            confirmButton = { TextButton(onClick = {
                                finishEditing(save = true); showEditExitConfirmation = false
                            }) { Text("保存并退出") } },
                            dismissButton = { Row {
                                TextButton(onClick = { finishEditing(save = false); showEditExitConfirmation = false }) { Text("放弃更改") }
                                TextButton(onClick = { showEditExitConfirmation = false; afterEditExit = null }) { Text("继续编辑") }
                            } }
                        )
                        AnimatedOverlayPresence(visible = showNineKeyMenu, animMs = overlayAnimMs) {
                            val modalLocalPage = pagerState.currentRealPage
                            val modalRealPage = visibleTrueIndices.getOrNull(modalLocalPage) ?: 0
                            BottomNineKeyModal(
                                state = state.copy(pages = visiblePages, currentPageIndex = modalLocalPage),
                                currentPageIndex = modalLocalPage,
                                onDismiss = { showNineKeyMenu = false },
                                onToggleEditMode = {
                                    if (allowLayoutEdit()) {
                                        val newMode = !isEditMode
                                        if (newMode) repository.setEditMode(true) else requestFinishEditing()
                                        if (newMode) Toast.makeText(
                                            context,
                                            "已进入编辑模式，长按卡片拖动",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showNineKeyMenu = false
                                    }
                                },
                                onOpenAddComponent = {
                                    showNineKeyMenu = false
                                    onOpenAddComponent()
                                },
                                onTempDisable = {
                                    showNineKeyMenu = false
                                    repository.tempDisableTrigger(60_000L)
                                    Toast.makeText(context, "已临时关闭手势条 60 秒", Toast.LENGTH_SHORT).show()
                                    onRequestClose()
                                },
                                onDeleteCurrentPage = {
                                    showNineKeyMenu = false
                                    if (allowLayoutEdit()) showDeletePageConfirm = true
                                },
                                onOpenThemeAppearance = {
                                    showNineKeyMenu = false
                                    themeInitialSection = ""
                                    showThemeAppearance = true
                                },
                                onOpenSettings = {
                                    showNineKeyMenu = false
                                    onOpenMainApp()
                                },
                                onSwapPageLeft = {
                                    if (allowLayoutEdit()) repository.swapPageLeft(modalRealPage)
                                },
                                onNewPage = {
                                    showNineKeyMenu = false
                                    if (allowLayoutEdit()) showNewPageTypeDialog = true
                                },
                                onSwapPageRight = {
                                    if (allowLayoutEdit()) repository.swapPageRight(modalRealPage)
                                },
                                onOpenFileBrowser = {
                                    showNineKeyMenu = false
                                    previewFileShortcut = null
                                    showBrowser = false; showFileBrowser = true
                                },
                                onToggleLayoutLock = {
                                    val locked = !repository.state.value.advancedConfig.layoutLocked
                                    val applyLock = {
                                        if (repository.setLayoutLocked(locked)) {
                                            Toast.makeText(context, if (locked) "布局已锁定" else "布局已解锁", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    if (locked && repository.state.value.editMode) {
                                        afterEditExit = applyLock
                                        requestFinishEditing()
                                    } else applyLock()
                                },
                                onToggleLayoutType = {
                                    if (allowLayoutEdit()) {
                                        val pageId = state.pages.getOrNull(modalRealPage)?.id.orEmpty()
                                        val result = repository.tryTogglePageLayoutType(pageId)
                                        val switched = repository.state.value.pages.firstOrNull { it.id == pageId }?.layoutType?.label ?: "页面布局"
                                        reportLayoutMutation(context, result, "已切换为$switched")
                                    }
                                },
                                onSelectLayoutType = { chosen ->
                                    if (allowLayoutEdit()) {
                                        val pageId = state.pages.getOrNull(modalRealPage)?.id.orEmpty()
                                        reportLayoutMutation(context, repository.trySetPageLayoutType(pageId, chosen), "已切换为${chosen.label}")
                                    }
                                },
                                onNavigatePage = { target ->
                                    navigatePage(Math.floorMod(target, pageCount), direction = if (target > modalLocalPage) 1 else -1)
                                },
                                onOpenPanelSize = {
                                    showNineKeyMenu = false
                                    themeInitialSection = "面板尺寸与布局"
                                    showThemeAppearance = true
                                },
                                onToggleLockOpenPage = {
                                    val targetId = state.pages.getOrNull(modalRealPage)?.id.orEmpty()
                                    val adv = repository.state.value.advancedConfig
                                    val isCurrentlyLockedHere =
                                        adv.panelOpenPageMode == com.example.globalminusone.data.policy.PanelOpenPageMode.LOCKED_PAGE &&
                                            adv.lockedOpenPageId == targetId
                                    val saved = repository.updateAdvancedConfig {
                                        if (isCurrentlyLockedHere) {
                                            it.copy(
                                                panelOpenPageMode = com.example.globalminusone.data.policy.PanelOpenPageMode.LAST_VISITED
                                            )
                                        } else {
                                            it.copy(
                                                panelOpenPageMode = com.example.globalminusone.data.policy.PanelOpenPageMode.LOCKED_PAGE,
                                                lockedOpenPageId = targetId
                                            )
                                        }
                                    }
                                    val msg = when {
                                        !saved -> "保存唤出页面设置失败，请重试"
                                        isCurrentlyLockedHere -> "已恢复打开上次离开的面板"
                                        else -> "已设为唤出时默认打开此页"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 添加组件分类选择弹窗
                        AnimatedOverlayPresence(visible = showAddComponent && !layoutLocked, animMs = overlayAnimMs) {
                            val modalRealPage = visibleTrueIndices.getOrNull(pagerState.currentRealPage) ?: 0
                            AddComponentModal(
                                currentPageIndex = modalRealPage,
                                repository = repository,
                                targetContainerId = addTargetContainerId,
                                beforeComponentId = addBeforeComponentId,
                                targetGridCell = addTargetGridCell,
                                onRequestBindWidget = { pageId ->
                                    showAddComponent = false; folderReturnState.clear()
                                    if (allowLayoutEdit()) {
                                        onRequestBindWidget(pageId, addTargetContainerId, addBeforeComponentId, addTargetGridCell)
                                    }
                                    addTargetContainerId = null; addBeforeComponentId = null; addTargetGridCell = null
                                },
                                onDismiss = {
                                    showAddComponent = false; addTargetContainerId = null; addBeforeComponentId = null; addTargetGridCell = null
                                    folderReturnState.triggerPendingReopen()
                                }
                            )
                        }

                        // 主题与外观微调弹窗
                        AnimatedOverlayPresence(visible = showThemeAppearance, animMs = overlayAnimMs) {
                            val modalRealPage = visibleTrueIndices.getOrNull(pagerState.currentRealPage) ?: 0
                            ThemeAppearanceModal(
                                currentPageIndex = modalRealPage,
                                state = state,
                                repository = repository,
                                initialSection = themeInitialSection,
                                onDismiss = { showThemeAppearance = false; themeInitialSection = "" }
                            )
                        }

                        // 删除页面确认弹窗
                        if (showDeletePageConfirm && !layoutLocked) {
                            val modalRealPage = visibleTrueIndices.getOrNull(pagerState.currentRealPage) ?: 0
                            DeletePageConfirmModal(
                                onConfirm = {
                                    showDeletePageConfirm = false
                                    if (allowLayoutEdit()) repository.deletePage(modalRealPage)
                                },
                                onDismiss = { showDeletePageConfirm = false }
                            )
                        }

                        // 新建页面类型与实用预设选择弹窗
                        if (showNewPageTypeDialog && !layoutLocked) {
                            NewPageTypeModal(
                                onSelectType = { layoutType ->
                                    showNewPageTypeDialog = false
                                    if (allowLayoutEdit()) {
                                        repository.addPage(layoutType, subPanelId = activeScopeId)
                                    }
                                },
                                onSelectPreset = { preset, customName ->
                                    showNewPageTypeDialog = false
                                    if (allowLayoutEdit()) {
                                        repository.addPagePreset(preset, customName, subPanelId = activeScopeId)
                                    }
                                },
                                onImportFromBackup = {
                                    showNewPageTypeDialog = false
                                    val launchImport: () -> Unit = {
                                        onRequestClose()
                                        com.example.globalminusone.system.AppAndIntentLauncher.launchMainSettings(context, "CONFIG_IMPORT_PAGE")
                                    }
                                    if (repository.state.value.editMode) {
                                        afterEditExit = launchImport
                                        requestFinishEditing()
                                    } else {
                                        launchImport()
                                    }
                                },
                                onDismiss = { showNewPageTypeDialog = false }
                            )
                        }

                        // 组件属性微调弹窗
                        AnimatedOverlayTarget(
                            targetState = editingComponent?.takeUnless { layoutLocked },
                            animMs = overlayAnimMs,
                            keyOf = { it.id }
                        ) { comp ->
                            val modalRealPage = visibleTrueIndices.getOrNull(pagerState.currentRealPage) ?: 0
                            ComponentEditModal(
                                item = comp,
                                pageIndex = modalRealPage,
                                repository = repository,
                                systemController = systemController,
                                onDismiss = { editingComponent = null }
                            )
                        }
                        if (!layoutLocked && isPanelOpen) {
                            SubPanelEditorOverlayHost(
                                controller = subPanelNav,
                                state = state,
                                currentScopeId = activeScopeId,
                                currentTruePageIndex = visibleTrueIndices.getOrNull(pagerState.currentRealPage) ?: 0,
                                animMs = overlayAnimMs,
                                repository = repository
                            )
                        }
                    // Workspace dialogs (tabs, bookmarks, copy/move, dirty editor) use this same host.
                    // It must remain composed when a floating browser or file workspace is visible.
                    val modalPalette = if (showBrowser) browserWindowPalette else palette
                    CompositionLocalProvider(LocalPanelPalette provides modalPalette) {
                        com.example.globalminusone.theme.SettingsTheme(darkTheme = modalPalette.isDark) {
                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onSurface) {
                                PanelModalHost(modalHost)
                            }
                        }
                    }
        }
    }
}
