package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.PagePresetOption
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.TriggerSide
import com.example.globalminusone.data.panelBounds
import com.example.globalminusone.data.findComponent
import com.example.globalminusone.data.parentContainerId
import com.example.globalminusone.data.flattenComponents
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.HardwareStatus
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.system.PanelFileBrowser
import com.example.globalminusone.system.SystemMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    onRequestBindWidget: (pageId: String, containerId: String?, beforeId: String?) -> Unit,
    onOpenEmbeddedApplication: (EmbeddedApplicationTarget) -> Unit,
    onBrowserHostInputAllowed: (Boolean) -> Unit = {},
    onFloatingWorkspaceInputMode: (Boolean) -> Unit = {}
) {
    MinusOneOverlayContainer(
        repository = repository,
        systemController = systemController,
        widgetHostManager = widgetHostManager,
        openProgressProvider = { openProgress },
        activeSide = activeSide,
        onRequestClose = onRequestClose,
        onOpenMainApp = onOpenMainApp,
        onRequestBindWidget = onRequestBindWidget,
        onOpenEmbeddedApplication = onOpenEmbeddedApplication,
        onBrowserHostInputAllowed = onBrowserHostInputAllowed,
        onFloatingWorkspaceInputMode = onFloatingWorkspaceInputMode
    )
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
    onRequestBindWidget: (pageId: String, containerId: String?, beforeId: String?) -> Unit,
    onOpenEmbeddedApplication: (EmbeddedApplicationTarget) -> Unit,
    onBrowserHostInputAllowed: (Boolean) -> Unit = {},
    onFloatingWorkspaceInputMode: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val keepAwake by systemController.keepAwake.collectAsState()
    val panelView = LocalView.current
    val latestOpenProgress by rememberUpdatedState(openProgressProvider)
    val isPanelOpen by remember { derivedStateOf { latestOpenProgress() > 0f } }
    SideEffect { panelView.keepScreenOn = keepAwake && isPanelOpen }
    DisposableEffect(panelView) { onDispose { panelView.keepScreenOn = false } }
    val themeConfig = state.themeConfig
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
    val corruptStateNotice by repository.corruptStateRecoveredNotice.collectAsState()
    com.example.globalminusone.ui.RepositoryPersistenceFeedback(repository, enabled = isPanelOpen)
    val gestureOwnership = remember { PanelGestureOwnership() }
    val flingCalibration = rememberPanelFlingCalibration()
    val pageViewports = remember { PageViewportStore() }
    // Non-observable, idempotent bookkeeping must precede child composition: an
    // editing child can immediately replace the deleted first-card anchor.
    if (isEditMode) pageViewports.beginEditing()
    SideEffect { if (!isEditMode) pageViewports.finishEditing(save = true) }
    LaunchedEffect(state.pages.map { it.id }) { pageViewports.retain(state.pages.map { it.id }.toSet()) }
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
    LaunchedEffect(isPanelOpen) { if (isPanelOpen) systemController.refreshBrightness() }
    LaunchedEffect(!isPanelOpen) {
        if (!isPanelOpen) {
            modalHost.dismiss()
            folderReturnState.clear()
        }
    }

    var showFileBrowser by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    val browserSession = remember(context) { IndependentBrowserSession(context.applicationContext) }
    val configurationRestoreGeneration by repository.configurationRestoreGeneration.collectAsState()
    LaunchedEffect(configurationRestoreGeneration) { browserSession.reloadSavedPreferences() }
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
    // Collect only while ordinary panel cards are visible; removal cancels hardware sampling.
    val needsHardware = state.pages.getOrNull(state.currentPageIndex)?.components?.flattenComponents()?.any {
        it.type in setOf(ComponentType.STAT_BATTERY_BAR, ComponentType.STAT_BATTERY_DETAIL, ComponentType.STAT_RAM, ComponentType.STAT_ROM)
    } == true
    val hardwareStatus = if (isPanelOpen && needsHardware) {
        hardwareStatuses.collectAsState().value
    } else {
        HardwareStatus.Empty
    }

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
            showAddComponent -> {
                showAddComponent = false
                addTargetContainerId = null
                addBeforeComponentId = null
                folderReturnState.triggerPendingReopen()
                true
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
            repository.updateThemeConfig { current -> current.copy(
                panelWidthPercent = edited.panelWidthPercent, panelHeightPercent = edited.panelHeightPercent,
                panelHorizontalOffsetPercent = edited.panelHorizontalOffsetPercent,
                panelVerticalPositionPercent = edited.panelVerticalPositionPercent) }
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
        }
    }

    // Adjacent virtual slots wrap to real pages, keeping boundary drags and transitions native.
    val pageCount = state.pages.size.coerceAtLeast(1)
    val pageIds = state.pages.map { it.id }
    val loopPager = remember(pageIds) { LoopPagerPolicy(pageIds) }
    val initialPage = state.currentPageIndex.coerceIn(0, pageCount - 1)
    val pagerState = key(pageIds) {
        rememberPagerState(initialPage = loopPager.initialVirtualPage(initialPage), pageCount = { loopPager.virtualPageCount })
    }
    val realPage = loopPager.realIndex(pagerState.currentPage)
    var pagerNavigation by remember(pagerState) { mutableStateOf<LoopPagerNavigation?>(null) }
    val repositoryPageSelection by rememberUpdatedState(pageIds to state.currentPageIndex)
    fun navigatePage(target: Int, direction: Int = 0) {
        val latest = repository.state.value
        val clamped = target.coerceIn(0, (latest.pages.size - 1).coerceAtLeast(0))
        if (latest.pages.map { it.id } == pageIds && pageCount > 1) {
            pagerNavigation = LoopPagerNavigation((pagerNavigation?.sequence ?: 0L) + 1, clamped, direction)
        }
        if (latest.currentPageIndex != clamped) repository.setCurrentPageIndex(clamped)
    }
    LaunchedEffect(pagerState) {
        val reconciler = LoopPagerReconciler(loopPager, initialPage)
        snapshotFlow {
            // StateFlow.value alone is not a snapshot read: also observe collectAsState's selection.
            val observedSelection = repositoryPageSelection
            val latest = repository.state.value
            if (observedSelection.first != pageIds || latest.pages.map { it.id } != pageIds) null else LoopPagerSnapshot(
                latest.currentPageIndex, pagerState.currentPage, pagerState.settledPage,
                pagerState.isScrollInProgress, pagerNavigation
            )
        }.collect { snapshot ->
            if (snapshot != null) when (val decision = reconciler.reconcile(snapshot)) {
                is LoopPagerDecision.Navigate -> try {
                    val animMs = repository.state.value.themeConfig.animationDurationMs
                    if (animMs <= 0) pagerState.scrollToPage(decision.virtualPage)
                    else pagerState.animateScrollToPage(
                        decision.virtualPage,
                        animationSpec = tween(durationMillis = animMs.coerceIn(100, 600), easing = FastOutSlowInEasing)
                    )
                } catch (_: CancellationException) {
                    // A user drag may interrupt animation; structural/disposal cancellation must propagate.
                    currentCoroutineContext().ensureActive()
                }
                is LoopPagerDecision.Persist -> repository.setCurrentPageIndex(decision.realIndex)
                null -> Unit
            }
        }
    }

    val latestImageRevisions by repository.imageAccessRevisions.collectAsState()
    LaunchedEffect(context, state.pages, state.appAppearanceOverrides) {
        val packages = linkedSetOf<String>()
        val uris = linkedSetOf<String>()
        state.pages.forEach { page ->
            collectComponentTreeIcons(page.components, packages, uris)
        }
        state.appAppearanceOverrides.values.forEach { override ->
            override.iconUri.trim().takeIf { it.isNotEmpty() }?.let { uris.add(it) }
        }
        AppAndIntentLauncher.syncPinnedPackages(packages)
        coroutineScope {
            if (uris.isNotEmpty()) launch { UserIconCache.prewarm(context, uris, latestImageRevisions) }
            if (packages.isNotEmpty()) launch { AppAndIntentLauncher.prewarmPackages(context, packages) }
        }
    }

    CompositionLocalProvider(
        LocalContainerPresentation provides remember { ContainerPresentation() },
        LocalFolderReturnState provides folderReturnState,
        LocalPanelPalette provides palette,
        LocalAppAppearanceOverrides provides state.appAppearanceOverrides,
        LocalHardwareStatus provides hardwareStatus,
        LocalLayoutLocked provides layoutLocked,
        LocalPanelModalHost provides modalHost,
        LocalPanelGestureOwnership provides gestureOwnership,
        LocalPanelFlingCalibration provides flingCalibration,
        LocalOpenBrowserUrl provides { url ->
            val target = browserDestination(url)
            if (target != null && browserSession.openTab(target)) {
                showFileBrowser = false
                showBrowser = true
            } else android.widget.Toast.makeText(context,
                if (target == null) "网址无效" else "标签已满，请先关闭一个标签", android.widget.Toast.LENGTH_SHORT).show()
        },
        LocalClosePanelForExternalApp provides onRequestClose,
        LocalOpenEmbeddedApplication provides { target ->
            showFileBrowser = false; showBrowser = false; previewFileShortcut = null
            onOpenEmbeddedApplication(target)
        },
        LocalOpenGlobalProperties provides { showThemeAppearance = true },
        LocalExitPanelEdit provides { requestFinishEditing() },
        LocalOpenNewPageModal provides { showNewPageTypeDialog = true },
        LocalAddAboveComponent provides { componentId ->
            if (allowLayoutEdit()) {
                val page = repository.state.value.pages.getOrNull(realPage)
                if (page?.components?.findComponent(componentId) != null) {
                    addTargetContainerId = page.components.parentContainerId(componentId)
                    addBeforeComponentId = componentId
                    showAddComponent = true
                }
            }
        },
        LocalAddToContainer provides { containerId ->
            if (allowLayoutEdit()) { addTargetContainerId = containerId; addBeforeComponentId = null; showAddComponent = true }
        },
        LocalOpenFileBrowser provides { previewFileShortcut = null; showBrowser = false; showFileBrowser = true },
        LocalOpenFileShortcut provides { item -> previewFileShortcut = item; showBrowser = false; showFileBrowser = true },
        LocalSelectImage provides { item ->
            val latest = repository.state.value
            val page = latest.pages.firstOrNull { page -> page.components.findComponent(item.id) != null }
            if (latest.advancedConfig.layoutLocked) Toast.makeText(context, "布局已锁定，请先解锁", Toast.LENGTH_SHORT).show()
            else if (page != null) PanelFileBrowser.requestImage(context, page.id, item.id)
        }
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
                        } else if (showNineKeyMenu) {
                            showNineKeyMenu = false
                        } else if (showAddComponent && !layoutLocked) {
                            showAddComponent = false
                            addTargetContainerId = null
                            addBeforeComponentId = null
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

            Box(
                    modifier = Modifier
                        .offset { IntOffset(geometry.x.roundToInt(), geometry.y.roundToInt()) }
                        .size(with(panelDensity) { geometry.width.toDp() }, with(panelDensity) { geometry.height.toDp() })
                        .graphicsLayer {
                            val p = latestOpenProgress().coerceIn(0f, 1f)
                            val distance = if (isRightSide) screenWidthPx - geometry.x else geometry.x + geometry.width
                            translationX = if (isRightSide) {
                                (1f - p) * distance
                            } else {
                                -(1f - p) * distance
                            }
                            alpha = when {
                                isAdjustingBrightness -> 0.25f
                                triggerZoneExpanded -> 0.42f
                                else -> p
                            }
                        }
                        .onGloballyPositioned { coordinates ->
                            val rect = coordinates.boundsInWindow()
                            containerPresentation?.panelBounds = com.example.globalminusone.data.ContainerPopupBounds(
                                rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt())
                        }
                        .clip(panelShape)
                        .background(palette.panelBackgroundBrush)
                ) {
                    val animMs = themeConfig.animationDurationMs.coerceIn(0, 800)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (themeConfig.coverStatusBar) Modifier else Modifier.statusBarsPadding())
                            .navigationBarsPadding()
                    ) {
                            // 编辑模式下仅保留顶部轻量让位区，释放底部空间避免遮挡页面
                            val chromeAlpha by animateFloatAsState(
                                targetValue = if (isEditMode) 0f else 1f,
                                animationSpec = if (animMs <= 0) snap() else tween(animMs.coerceIn(80, 500)),
                                label = "panel-chrome-alpha"
                            )
                            val pillSurface = if (palette.isDark) Color(0xFF1F242D).copy(alpha = 0.88f) else Color(0xFFFDFEFF).copy(alpha = 0.90f)
                            val pillBorder = BorderStroke(0.8.dp, if (palette.isDark) Color(0xFF39414F) else Color(0xFFDDE3EC))
                            val pillDivider = if (palette.isDark) Color(0xFF39414F) else Color(0xFFE3E8F0)
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
                                    // 左侧工具胶囊：浏览器 | 文件
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = pillSurface,
                                        border = pillBorder
                                    ) {
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

                                    // 右侧操作胶囊：添加 | 编辑 | 页面与菜单
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = pillSurface,
                                        border = pillBorder
                                    ) {
                                        Row(
                                            Modifier.height(36.dp).padding(horizontal = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (allowLayoutEdit()) {
                                                        addTargetContainerId = null
                                                        addBeforeComponentId = state.pages.getOrNull(realPage)?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id
                                                        showAddComponent = true
                                                    }
                                                },
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
                                            Box(Modifier.width(0.8.dp).height(16.dp).background(pillDivider))
                                            IconButton(
                                                onClick = { showNineKeyMenu = true },
                                                enabled = !isEditMode,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.GridView,
                                                    contentDescription = "页面与菜单",
                                                    modifier = Modifier.size(17.dp),
                                                    tint = palette.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val pagerFlingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                snapAnimationSpec = if (animMs <= 0) snap() else tween(
                                    durationMillis = animMs.coerceIn(60, 600),
                                    easing = FastOutSlowInEasing
                                )
                            )
                            // 多页横向滑动容器与 12 种转场动画（编辑模式下禁用左右滑动，防止抢占卡片拖拽手势）
                            HorizontalPager(
                                state = pagerState,
                                key = { loopPager.key(it) },
                                beyondViewportPageCount = 1,
                                flingBehavior = pagerFlingBehavior,
                                userScrollEnabled = pageCount > 1 && !isEditMode && modalHost.entry == null && !gestureOwnership.exclusiveHorizontalActive,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { virtualPageIndex ->
                                val pageIndex = loopPager.realIndex(virtualPageIndex)
                                val targetPage = state.pages.getOrNull(pageIndex) ?: return@HorizontalPager
                                val isViewportActive by remember(pagerState, virtualPageIndex) {
                                    derivedStateOf {
                                        pagerState.settledPage == virtualPageIndex ||
                                            (!pagerState.isScrollInProgress && pagerState.currentPage == virtualPageIndex)
                                    }
                                }
                                CompositionLocalProvider(
                                    LocalPageViewport provides pageViewports.page(targetPage.id),
                                    LocalPageViewportActive provides isViewportActive
                                ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val pageOffset = (pagerState.currentPage - virtualPageIndex) +
                                                pagerState.currentPageOffsetFraction
                                            applyPageTransitionAnimation(
                                                transition = themeConfig.pageTransition,
                                                pageOffset = pageOffset,
                                                width = size.width,
                                                height = size.height,
                                                density = density
                                            )
                                        }
                                ) {
                                    when (targetPage.layoutType) {
                                        PageLayoutType.FREE -> {
                                            FreeLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = state,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = { isAdjustingBrightness = it },
                                                onRequestClose = onRequestClose,
                                                onOpenMainApp = onOpenMainApp,
                                                onOpenEditDialog = { comp -> if (allowLayoutEdit()) { repository.selectComponent(comp.id); editingComponent = comp } },
                                                onOpenBottomNineKeyBar = { showNineKeyMenu = true },
                                                onOpenAddComponent = { if (allowLayoutEdit()) { addTargetContainerId = null; addBeforeComponentId = state.pages.getOrNull(realPage)?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id; showAddComponent = true } }
                                            )
                                        }
                                        PageLayoutType.GRID -> {
                                            GridLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = state,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = { isAdjustingBrightness = it },
                                                onRequestClose = onRequestClose,
                                                onOpenMainApp = onOpenMainApp,
                                                onOpenEditDialog = { comp -> if (allowLayoutEdit()) { repository.selectComponent(comp.id); editingComponent = comp } },
                                                onOpenBottomNineKeyBar = { showNineKeyMenu = true },
                                                onOpenAddComponent = { if (allowLayoutEdit()) { addTargetContainerId = null; addBeforeComponentId = state.pages.getOrNull(realPage)?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id; showAddComponent = true } },
                                                onAddAboveComponent = { targetId ->
                                                    if (allowLayoutEdit()) {
                                                        addTargetContainerId = null
                                                        addBeforeComponentId = targetId
                                                        showAddComponent = true
                                                    }
                                                }
                                            )
                                        }
                                        PageLayoutType.LINEAR -> {
                                            LinearLayoutPage(
                                                page = targetPage,
                                                pageIndex = pageIndex,
                                                state = state,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                onBrightnessAdjusting = { isAdjustingBrightness = it },
                                                onRequestClose = onRequestClose,
                                                onOpenMainApp = onOpenMainApp,
                                                onOpenEditDialog = { comp -> if (allowLayoutEdit()) { repository.selectComponent(comp.id); editingComponent = comp } },
                                                onOpenBottomNineKeyBar = { showNineKeyMenu = true },
                                                onOpenAddComponent = { if (allowLayoutEdit()) { addTargetContainerId = null; addBeforeComponentId = state.pages.getOrNull(realPage)?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id; showAddComponent = true } }
                                            )
                                        }
                                    }
                                }
                                }
                            }

                            val activePageForPill = state.pages.getOrNull(realPage)
                            val activeLayoutShort = when (activePageForPill?.layoutType) {
                                PageLayoutType.FREE -> "自由"
                                PageLayoutType.GRID -> "网格"
                                else -> "线性"
                            }
                            if (!isEditMode) {
                                // The whole measured footer is a target, including the caret and
                                // transparent space around the pill. Do not rely on a tiny Surface's
                                // expanded minimum-touch bounds next to the bottom swipe target.
                                Box(Modifier.fillMaxWidth().height(48.dp)
                                    .graphicsLayer { alpha = chromeAlpha }
                                    .clickable(enabled = !isEditMode, onClickLabel = "打开页面与菜单") { showNineKeyMenu = true }
                                    .semantics { contentDescription = "页面与菜单，当前第${realPage + 1}页，共${state.pages.size}页" },
                                    contentAlignment = Alignment.Center) {
                                    Surface(
                                        color = if (palette.isDark) Color(0xFF20242C) else Color(0xFFF7F8FA),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (palette.isDark) Color(0xFF5C6675) else Color(0xFFCDD3DD))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (state.pages.size in 2..6) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    state.pages.indices.forEach { dotIndex ->
                                                        val activeDot = dotIndex == realPage
                                                        Box(
                                                            modifier = Modifier
                                                                .size(width = if (activeDot) 12.dp else 5.dp, height = 5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (activeDot) palette.accent
                                                                    else palette.textSecondary.copy(alpha = 0.35f)
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = (if (layoutLocked) "布局已锁定 · " else "") + "${realPage + 1} / ${state.pages.size} · $activeLayoutShort ▾",
                                                color = if (palette.isDark) Color(0xFFF5F7FA) else Color(0xFF1F2329),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // 底部上滑手势条与呼出感应区
                                BottomSwipeUpBar(
                                    onSwipeUp = { showNineKeyMenu = true },
                                    onClick = { showNineKeyMenu = true }
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }
                    }

                    // A floating window intercepts only its own bounds. Exposed cards and the
                    // pager remain usable; actual confirmations keep the modal host's scrim.
                }
                val browserWindowPalette = if (browserSession.nightMode) {
                    palette.copy(
                        isDark = true,
                        cardBackground = Color(0xFF181B22),
                        cardBorder = Color(0xFF3E4756),
                        textPrimary = Color(0xFFF2F5FA),
                        textSecondary = Color(0xFFB5BFCE),
                        accent = Color(0xFF8AB4F8)
                    )
                } else palette.copy(
                    isDark = false,
                    cardBackground = Color(0xFFFFFFFF),
                    cardBorder = Color(0xFFDDE2EA),
                    textPrimary = Color(0xFF1F2329),
                    textSecondary = Color(0xFF606975),
                    accent = Color(0xFF246BCC)
                )
                // Floating workspaces use the full overlay, independent of panel width, offset and clipping.
                if (floatingWindowOpen) Box(Modifier.fillMaxSize().graphicsLayer { alpha = latestOpenProgress().coerceIn(0f, 1f) }) {
                    if (showBrowser) {
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
                    if (showFileBrowser) {
                        FloatingFileBrowser(repository = repository,
                            onContentVisibilityChanged = { fileWindowContentVisible = it },
                            initialFile = state.pages.firstNotNullOfOrNull { it.components.findComponent(previewFileShortcut?.id.orEmpty()) } ?: previewFileShortcut,
                            onDismiss = { showFileBrowser = false; previewFileShortcut = null })
                    }
                }

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
                val curPage = state.pages.getOrNull(realPage)
                EditModeTopBanner(
                    currentPage = realPage,
                    totalPages = state.pages.size.coerceAtLeast(1),
                    isLinearPage = curPage?.layoutType == PageLayoutType.LINEAR,
                    layoutType = curPage?.layoutType ?: PageLayoutType.FREE,
                    onPrevPage = {
                        if (pageCount > 1) navigatePage(Math.floorMod(realPage - 1, pageCount), direction = -1)
                    },
                    onNextPage = {
                        if (pageCount > 1) navigatePage((realPage + 1) % pageCount, direction = 1)
                    },
                    onToggleLayoutType = {
                        if (allowLayoutEdit()) {
                            repository.togglePageLayoutType(realPage)
                            val switched = repository.state.value.pages.getOrNull(realPage)?.layoutType?.label ?: "页面布局"
                            Toast.makeText(context, "已切换为$switched", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectLayoutType = { chosen ->
                        if (allowLayoutEdit()) {
                            repository.setPageLayoutType(realPage, chosen)
                            Toast.makeText(context, "已切换为${chosen.label}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAdd = { if (allowLayoutEdit()) { addTargetContainerId = null; addBeforeComponentId = curPage?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id; showAddComponent = true } },
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
                        repository.compactPageGridPlacements(realPage)
                    }) else null,
                    pageName = curPage?.name.orEmpty(),
                    onCardProperties = state.selectedComponentId?.let { selectedId ->
                        curPage?.components?.findComponent(selectedId)?.let { selected -> ({ editingComponent = selected }) }
                    }
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
                        if (showNineKeyMenu) {
                            BottomNineKeyModal(
                                state = state,
                                currentPageIndex = realPage,
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
                                    if (allowLayoutEdit()) { addTargetContainerId = null; addBeforeComponentId = state.pages.getOrNull(realPage)?.components?.lastOrNull { it.type == ComponentType.OPERATION_BAR }?.id; showAddComponent = true }
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
                                    if (allowLayoutEdit()) repository.swapPageLeft(realPage)
                                },
                                onNewPage = {
                                    showNineKeyMenu = false
                                    if (allowLayoutEdit()) showNewPageTypeDialog = true
                                },
                                onSwapPageRight = {
                                    if (allowLayoutEdit()) repository.swapPageRight(realPage)
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
                                        repository.togglePageLayoutType(realPage)
                                        val switched = repository.state.value.pages.getOrNull(realPage)?.layoutType?.label ?: "页面布局"
                                        Toast.makeText(context, "已切换为$switched", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSelectLayoutType = { chosen ->
                                    if (allowLayoutEdit()) {
                                        repository.setPageLayoutType(realPage, chosen)
                                        Toast.makeText(context, "已切换为${chosen.label}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onNavigatePage = { target ->
                                    navigatePage(Math.floorMod(target, pageCount), direction = if (target > realPage) 1 else -1)
                                },
                                onOpenPanelSize = {
                                    showNineKeyMenu = false
                                    themeInitialSection = "面板尺寸与布局"
                                    showThemeAppearance = true
                                }
                            )
                        }

                        // 添加组件分类选择弹窗
                        if (showAddComponent && !layoutLocked) {
                            AddComponentModal(
                                currentPageIndex = realPage,
                                repository = repository,
                                targetContainerId = addTargetContainerId,
                                beforeComponentId = addBeforeComponentId,
                                onRequestBindWidget = { pageId ->
                                    showAddComponent = false
                                    folderReturnState.clear()
                                    if (allowLayoutEdit()) {
                                        onRequestBindWidget(pageId, addTargetContainerId, addBeforeComponentId)
                                    }
                                    addTargetContainerId = null
                                    addBeforeComponentId = null
                                },
                                onDismiss = {
                                    showAddComponent = false
                                    addTargetContainerId = null
                                    addBeforeComponentId = null
                                    folderReturnState.triggerPendingReopen()
                                }
                            )
                        }

                        // 主题与外观微调弹窗
                        if (showThemeAppearance) {
                            ThemeAppearanceModal(
                                currentPageIndex = realPage,
                                state = state,
                                repository = repository,
                                initialSection = themeInitialSection,
                                onDismiss = { showThemeAppearance = false; themeInitialSection = "" }
                            )
                        }

                        // 删除页面确认弹窗
                        if (showDeletePageConfirm && !layoutLocked) {
                            DeletePageConfirmModal(
                                onConfirm = {
                                    showDeletePageConfirm = false
                                    if (allowLayoutEdit()) repository.deletePage(realPage)
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
                                        repository.addPage(layoutType)
                                    }
                                },
                                onSelectPreset = { preset, customName ->
                                    showNewPageTypeDialog = false
                                    if (allowLayoutEdit()) {
                                        repository.addPagePreset(preset, customName)
                                    }
                                },
                                onDismiss = { showNewPageTypeDialog = false }
                            )
                        }

                        // 组件属性微调弹窗
                        editingComponent?.takeUnless { layoutLocked }?.let { comp ->
                            ComponentEditModal(
                                item = comp,
                                pageIndex = realPage,
                                repository = repository,
                                systemController = systemController,
                                onDismiss = { editingComponent = null }
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

// -----------------------------------------------------------------------------------------
// 12 种页面转场动画 (Page Transition Animations)
// -----------------------------------------------------------------------------------------

internal data class PageTransitionSpec(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val alpha: Float = 1f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val pivotFractionX: Float = 0.5f,
    val pivotFractionY: Float = 0.5f,
    val cameraDistance: Float
)

internal fun computePageTransitionSpec(
    transition: PageTransition,
    pageOffset: Float,
    width: Float,
    height: Float,
    density: Float
): PageTransitionSpec {
    val defaultCamera = 8f * density.coerceAtLeast(1f)
    val perspectiveCamera = 16f * density.coerceAtLeast(1f)
    val absOffset = abs(pageOffset)
    val clampedOffset = pageOffset.coerceIn(-1f, 1f)
    return when (transition) {
        PageTransition.TRANSLATION -> PageTransitionSpec(
            alpha = (1f - 0.18f * absOffset).coerceIn(0.65f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.STACK -> PageTransitionSpec(
            translationX = if (pageOffset > 0f) pageOffset * width else 0f,
            alpha = if (pageOffset > 0f) (1f - pageOffset).coerceIn(0f, 1f) else 1f,
            cameraDistance = defaultCamera
        )
        PageTransition.STACK_SCALE -> {
            val scale = if (pageOffset > 0f) (1f - 0.16f * pageOffset).coerceIn(0.84f, 1f) else 1f
            PageTransitionSpec(
                translationX = if (pageOffset > 0f) pageOffset * width else 0f,
                scaleX = scale,
                scaleY = scale,
                alpha = if (pageOffset > 0f) (1f - pageOffset).coerceIn(0f, 1f) else 1f,
                cameraDistance = defaultCamera
            )
        }
        PageTransition.SCALE -> {
            val scale = (1f - 0.22f * absOffset).coerceIn(0.78f, 1f)
            PageTransitionSpec(
                scaleX = scale,
                scaleY = scale,
                alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f),
                cameraDistance = defaultCamera
            )
        }
        PageTransition.PAGE -> if (pageOffset > 0f) {
            PageTransitionSpec(
                translationX = pageOffset * width,
                pivotFractionX = 0f,
                pivotFractionY = 0.5f,
                rotationY = (-65f * pageOffset).coerceIn(-65f, 0f),
                alpha = (1f - pageOffset).coerceIn(0f, 1f),
                cameraDistance = perspectiveCamera
            )
        } else {
            PageTransitionSpec(cameraDistance = perspectiveCamera)
        }
        PageTransition.TURN_TABLE -> PageTransitionSpec(
            rotationZ = -22f * clampedOffset,
            pivotFractionX = 0.5f,
            pivotFractionY = 1.6f,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0.45f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.ROTATION -> PageTransitionSpec(
            rotationY = -45f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f),
            cameraDistance = perspectiveCamera
        )
        PageTransition.COMPRESS -> PageTransitionSpec(
            scaleX = (1f - 0.4f * absOffset).coerceIn(0.6f, 1f),
            alpha = if (absOffset >= 1f) 0f else (1f - 0.3f * absOffset).coerceIn(0.5f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.UP -> PageTransitionSpec(
            translationY = -pageOffset * height * 0.45f,
            alpha = (1f - absOffset).coerceIn(0f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.DOWN -> PageTransitionSpec(
            translationY = pageOffset * height * 0.45f,
            alpha = (1f - absOffset).coerceIn(0f, 1f),
            cameraDistance = defaultCamera
        )
        PageTransition.OUT_CUBE -> PageTransitionSpec(
            pivotFractionX = if (pageOffset > 0f) 1f else 0f,
            pivotFractionY = 0.5f,
            rotationY = -90f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f),
            cameraDistance = perspectiveCamera
        )
        PageTransition.IN_CUBE -> PageTransitionSpec(
            pivotFractionX = if (pageOffset > 0f) 1f else 0f,
            pivotFractionY = 0.5f,
            rotationY = 90f * clampedOffset,
            alpha = if (absOffset >= 1f) 0f else (1f - 0.35f * absOffset).coerceIn(0f, 1f),
            cameraDistance = perspectiveCamera
        )
    }
}

fun androidx.compose.ui.graphics.GraphicsLayerScope.applyPageTransitionAnimation(
    transition: PageTransition,
    pageOffset: Float,
    width: Float,
    height: Float,
    density: Float
) {
    val spec = computePageTransitionSpec(transition, pageOffset, width, height, density)
    translationX = spec.translationX
    translationY = spec.translationY
    scaleX = spec.scaleX
    scaleY = spec.scaleY
    alpha = spec.alpha
    rotationX = spec.rotationX
    rotationY = spec.rotationY
    rotationZ = spec.rotationZ
    cameraDistance = spec.cameraDistance
    transformOrigin = TransformOrigin(spec.pivotFractionX, spec.pivotFractionY)
}

// -----------------------------------------------------------------------------------------
// 32 等分点阵「自由布局」引擎 (FreeLayoutPage)
// -----------------------------------------------------------------------------------------

@Composable
fun BottomSwipeUpBar(
    onSwipeUp: () -> Unit,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val latestSwipe by rememberUpdatedState(onSwipeUp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                var distance = 0f
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragCancel = { distance = 0f },
                    onDragEnd = { if (distance <= -30.dp.toPx()) latestSwipe(); distance = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    distance += dragAmount
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(palette.textSecondary.copy(alpha = 0.35f))
        )
    }
}

// -----------------------------------------------------------------------------------------
// 底部 9 键操作栏浮层 (BottomNineKeyModal)
// -----------------------------------------------------------------------------------------

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Int = 0, maximum: Int = Int.MAX_VALUE
) {
    val palette = LocalPanelPalette.current
    var inputOpen by remember { mutableStateOf(false) }
    if (inputOpen) ExactNumberInput(label, value, minimum, maximum, onValueChange) { inputOpen = false }
    val slotBg = if (palette.isDark) Color(0xFF282E38) else Color(0xFFF3F5F8)
    val slotBorder = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.55f))
    val valuePillBg = palette.accent.copy(alpha = 0.12f)

    Surface(
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
        color = slotBg,
        shape = RoundedCornerShape(10.dp),
        border = slotBorder
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            val inlineRow = maxWidth >= 210.dp
            val stackNumber = maxWidth < 144.dp
            val number: @Composable (Modifier) -> Unit = { numberModifier ->
                Box(
                    numberModifier
                        .height(48.dp)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(valuePillBg)
                        .clickable { inputOpen = true }
                        .semantics { contentDescription = "输入$label" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$value",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            val step: @Composable (Boolean, Modifier) -> Unit = { increase, stepModifier ->
                val canStep = if (increase) value < maximum else value > minimum
                IconButton(
                    enabled = canStep,
                    onClick = {
                        val next = value.toLong() + if (increase) 1L else -1L
                        onValueChange(next.coerceIn(minimum.toLong(), maximum.toLong()).toInt())
                    },
                    modifier = stepModifier.height(48.dp)
                ) {
                    Icon(
                        if (increase) Icons.Rounded.Add else Icons.Rounded.Remove,
                        "$label ${if (increase) "增加" else "减少"}",
                        tint = palette.textPrimary.copy(alpha = if (canStep) 1f else .35f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (inlineRow) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 2.dp, end = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    step(false, Modifier.width(42.dp))
                    number(Modifier.widthIn(min = 52.dp))
                    step(true, Modifier.width(42.dp))
                }
            } else {
                Column {
                    Text(
                        text = label,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    // Every hit target keeps its own 48dp height region; narrow paired fields stack cleanly.
                    if (stackNumber) {
                        number(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth()) {
                            step(false, Modifier.weight(1f)); step(true, Modifier.weight(1f))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            step(false, Modifier.width(48.dp))
                            number(Modifier.weight(1f))
                            step(true, Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 主题外观微调弹窗 (ThemeAppearanceModal)
// -----------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// 删除该页确认弹窗 (DeletePageConfirmModal)
// -----------------------------------------------------------------------------------------

@Composable
fun DeletePageConfirmModal(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PanelAlertDialog(onDismissRequest = onDismiss,
        title = { Text("删除页面？") },
        text = { Text("确定删除吗？这将无法恢复！") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun NewPageTypeModal(
    onSelectType: (PageLayoutType) -> Unit = {},
    onSelectPreset: ((PagePresetOption, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var pageNameInput by remember { mutableStateOf("") }
    val palette = LocalPanelPalette.current
    val presets = remember { PagePresetOption.entries.filter { it.isPreset } }
    val blanks = remember { PagePresetOption.entries.filter { !it.isPreset } }
    PanelAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建一页") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardBackground.copy(alpha = 0.72f),
                    border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = pageNameInput,
                        onValueChange = { pageNameInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = palette.textPrimary, fontSize = 12.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                        decorationBox = { inner ->
                            Box {
                                if (pageNameInput.isEmpty()) {
                                    Text(
                                        "可选：输入新页面名称（留空自动命名）",
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                }

                Text(
                    "实用布局预设",
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                presets.forEach { preset ->
                    Surface(
                        onClick = {
                            if (onSelectPreset != null) onSelectPreset(preset, pageNameInput)
                            else onSelectType(preset.layoutType)
                        },
                        shape = RoundedCornerShape(11.dp),
                        color = palette.cardBackground,
                        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.75f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    preset.title,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    preset.subtitle,
                                    color = palette.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = palette.accent.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    preset.badge,
                                    color = palette.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    "空白页面",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                blanks.forEach { blank ->
                    OutlinedButton(
                        onClick = {
                            if (onSelectPreset != null) onSelectPreset(blank, pageNameInput)
                            else onSelectType(blank.layoutType)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${blank.title}（${blank.subtitle.substringBefore('·').trim()}）", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
