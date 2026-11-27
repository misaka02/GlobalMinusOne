package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.SubPanelNavStack
import com.example.globalminusone.data.policy.resolveInitialSubPanelStack
import com.example.globalminusone.data.policy.resolvePanelOpenTarget
import com.example.globalminusone.data.policy.resolveSubPanelEntryPageMemory
import com.example.globalminusone.data.policy.resolveSubPanelStackForId
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.data.SubPanelTransitionStyle
import com.example.globalminusone.ui.motion.AnimatedOverlayTarget
import com.example.globalminusone.ui.motion.SubPanelPortalTransform
import com.example.globalminusone.ui.motion.SubPanelTransitionDirection
import com.example.globalminusone.ui.motion.computeSubPanelPortalTransform
import com.example.globalminusone.ui.motion.subPanelPortalSpring
import com.example.globalminusone.ui.panel.editor.SubPanelEditorModal
import com.example.globalminusone.ui.panel.layout.resolveTransitionCompositingStrategy

/** Owns sub-panel stack navigation, portal anchor transition state, per-scope page memory, and editor target. */
internal class SubPanelNavigationController(
    private val repository: MinusOneRepository,
    private val overlayPortal: PanelOverlayPortalState,
    private val onScopeWillChange: () -> Unit = {}
) {
    var stack by mutableStateOf(resolveInitialSubPanelStack(repository.state.value))
        private set
    var editingSubPanelId by mutableStateOf<String?>(null)
    var transitionSequence by mutableIntStateOf(0)
        private set
    var transitionDirection by mutableStateOf(SubPanelTransitionDirection.NONE)
        private set
    var transitionAnchorBounds by mutableStateOf<Rect?>(null)
        private set

    private var pageMemory by mutableStateOf(
        resolveSubPanelEntryPageMemory(repository.state.value, stack)
    )
    private var suppressPageAnimUntilMs: Long = 0L

    fun shouldSnapPageTransition(): Boolean =
        android.os.SystemClock.uptimeMillis() <= suppressPageAnimUntilMs

    fun activeSubPanel(state: GlobalMinusOneState): SubPanelConfig? =
        stack.currentId?.let { targetId -> state.subPanels.firstOrNull { it.id == targetId } }

    fun pruneMissing(validIds: Set<String>) {
        val nextStack = stack.pruneMissing(validIds)
        if (nextStack != stack) {
            stack = nextStack
            transitionDirection = SubPanelTransitionDirection.NONE
            transitionAnchorBounds = null
            val targetScopeId = nextStack.currentId.orEmpty()
            val latest = repository.state.value
            val curPage = latest.pages.getOrNull(latest.currentPageIndex)
            if (curPage == null || curPage.subPanelId != targetScopeId) {
                val rememberedId = pageMemory[targetScopeId]
                val targetIdx = latest.pages.indexOfFirst { it.id == rememberedId && it.subPanelId == targetScopeId }
                    .takeIf { it >= 0 }
                    ?: latest.pages.indexOfFirst { it.subPanelId == targetScopeId }
                if (targetIdx >= 0 && targetIdx != latest.currentPageIndex) {
                    repository.setCurrentPageIndex(targetIdx)
                }
            }
        }
        if (editingSubPanelId != null && editingSubPanelId !in validIds) editingSubPanelId = null
    }

    fun switchScope(currentScopeId: String, nextScopeId: String, nextStack: SubPanelNavStack) {
        overlayPortal.clear()
        onScopeWillChange()
        editingSubPanelId = null
        val latest = repository.state.value
        val curPage = latest.pages.getOrNull(latest.currentPageIndex)
        if (curPage != null) {
            pageMemory = pageMemory + (currentScopeId to curPage.id)
        }
        val rememberedId = pageMemory[nextScopeId]
        val targetIdx = latest.pages.indexOfFirst { it.id == rememberedId && it.subPanelId == nextScopeId }
            .takeIf { it >= 0 }
            ?: latest.pages.indexOfFirst { it.subPanelId == nextScopeId }
        stack = nextStack
        if (targetIdx >= 0 && targetIdx != latest.currentPageIndex) {
            repository.setCurrentPageIndex(targetIdx)
        }
    }

    fun open(currentScopeId: String, targetSubId: String, anchorBounds: Rect? = null) {
        val latest = repository.state.value
        val validIds = latest.subPanels.mapTo(HashSet()) { it.id }
        val nextStack = stack.push(targetSubId, validIds)
        if (nextStack != stack) {
            transitionDirection = SubPanelTransitionDirection.ENTER_FROM_CARD
            transitionAnchorBounds = anchorBounds
            transitionSequence += 1
            switchScope(currentScopeId, nextStack.currentId.orEmpty(), nextStack)
        }
    }

    fun pop(currentScopeId: String): Boolean {
        if (stack.depth <= 0) return false
        val parentStack = stack.pop()
        transitionDirection = SubPanelTransitionDirection.EXIT_TO_PARENT
        transitionAnchorBounds = null
        transitionSequence += 1
        switchScope(currentScopeId, parentStack.currentId.orEmpty(), parentStack)
        return true
    }

    fun onPanelClosed() {
        editingSubPanelId = null
        transitionDirection = SubPanelTransitionDirection.NONE
        transitionAnchorBounds = null
        suppressPageAnimUntilMs = 0L
    }

    fun applyOpenPagePolicyIfLocked() = reconcileActiveScope(applyLocked = true)

    fun reconcileActiveScope(applyLocked: Boolean = true) {
        val latest = repository.state.value
        val target = if (applyLocked) resolvePanelOpenTarget(latest) else null
        if (target != null) {
            if (target.subPanelStack != stack) {
                stack = target.subPanelStack
                pageMemory = pageMemory + resolveSubPanelEntryPageMemory(latest, target.subPanelStack)
                transitionDirection = SubPanelTransitionDirection.NONE
                transitionAnchorBounds = null
            }
            if (target.pageIndex != latest.currentPageIndex) {
                suppressPageAnimUntilMs = android.os.SystemClock.uptimeMillis() + 180L
                repository.setCurrentPageIndex(target.pageIndex)
            }
            return
        }
        if (latest.advancedConfig.panelOpenPageMode == com.example.globalminusone.data.policy.PanelOpenPageMode.LAST_VISITED) {
            val activeSubId = latest.pages.getOrNull(latest.currentPageIndex)?.subPanelId.orEmpty()
            if (activeSubId != stack.currentId.orEmpty()) {
                val restoredStack = resolveSubPanelStackForId(latest, activeSubId)
                stack = restoredStack
                pageMemory = pageMemory + resolveSubPanelEntryPageMemory(latest, restoredStack)
                transitionDirection = SubPanelTransitionDirection.NONE
                transitionAnchorBounds = null
            }
        }
    }
}

@Composable
internal fun rememberSubPanelNavigationController(
    repository: MinusOneRepository,
    overlayPortal: PanelOverlayPortalState,
    onScopeWillChange: () -> Unit = {}
): SubPanelNavigationController = remember(repository, overlayPortal) {
    SubPanelNavigationController(repository, overlayPortal, onScopeWillChange)
}

internal fun resolveSubPanelPortalProgress(
    sequence: Int,
    startedSequence: Int,
    direction: SubPanelTransitionDirection,
    animMs: Int,
    animatedValue: Float
): Float = when {
    sequence == 0 || direction == SubPanelTransitionDirection.NONE || animMs <= 0 -> 1f
    startedSequence != sequence -> 0f
    else -> animatedValue
}

@Composable
internal fun rememberSubPanelPortalProgress(
    controller: SubPanelNavigationController,
    animMs: Int
): () -> Float {
    val anim = remember { Animatable(1f) }
    var startedSequence by remember { mutableIntStateOf(controller.transitionSequence) }
    LaunchedEffect(controller.transitionSequence) {
        val seq = controller.transitionSequence
        if (seq == 0 ||
            controller.transitionDirection == SubPanelTransitionDirection.NONE ||
            animMs <= 0
        ) {
            anim.snapTo(1f)
            startedSequence = seq
        } else {
            anim.snapTo(0f)
            startedSequence = seq
            anim.animateTo(1f, subPanelPortalSpring(animMs, controller.transitionDirection))
        }
    }
    return {
        resolveSubPanelPortalProgress(
            sequence = controller.transitionSequence,
            startedSequence = startedSequence,
            direction = controller.transitionDirection,
            animMs = animMs,
            animatedValue = anim.value
        )
    }
}

internal fun GraphicsLayerScope.applyPanelWithSubPanelPortal(
    baseTranslationX: Float,
    baseAlpha: Float,
    portalProgress: Float,
    direction: SubPanelTransitionDirection,
    anchorBounds: Rect?,
    panelBounds: Rect,
    style: SubPanelTransitionStyle = SubPanelTransitionStyle.PORTAL_EXPAND
) {
    val portal: SubPanelPortalTransform = computeSubPanelPortalTransform(
        progress = portalProgress,
        direction = direction,
        anchorBounds = anchorBounds,
        panelBounds = panelBounds,
        style = style
    )
    val finalAlpha = (baseAlpha * portal.alpha).coerceIn(0f, 1f)
    scaleX = portal.scale
    scaleY = portal.scale
    translationX = baseTranslationX + portal.translationX
    translationY = portal.translationY
    alpha = finalAlpha
    compositingStrategy = resolveTransitionCompositingStrategy(finalAlpha)
}

internal fun Modifier.subPanelPortalRim(
    progressProvider: () -> Float,
    direction: SubPanelTransitionDirection,
    accentColor: Color,
    cornerRadius: Dp,
    style: SubPanelTransitionStyle = SubPanelTransitionStyle.PORTAL_EXPAND
): Modifier = drawWithContent {
    drawContent()
    if (direction == SubPanelTransitionDirection.ENTER_FROM_CARD && style == SubPanelTransitionStyle.PORTAL_EXPAND) {
        val p = progressProvider().coerceIn(0f, 1f)
        val rimAlpha = ((1f - p) * 0.55f).coerceIn(0f, 0.55f)
        if (rimAlpha > 0.01f) {
            val radiusPx = cornerRadius.toPx()
            drawRoundRect(
                color = accentColor.copy(alpha = rimAlpha),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
internal fun SubPanelHeaderPill(
    subPanel: SubPanelConfig,
    isEditMode: Boolean,
    pillDivider: Color,
    palette: PanelPalette,
    onExitSubPanel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier.height(36.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !isEditMode, onClick = onExitSubPanel)
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "返回上级面板" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Rounded.ArrowBack, null, Modifier.size(16.dp), tint = palette.accent)
            Text(
                subPanel.name,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 104.dp)
            )
        }
        Box(Modifier.width(0.8.dp).height(16.dp).background(pillDivider))
        IconButton(
            onClick = onOpenSettings,
            enabled = !isEditMode,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "子面板设置", modifier = Modifier.size(15.dp), tint = palette.accent)
        }
    }
}

@Composable
internal fun SubPanelEditorOverlayHost(
    controller: SubPanelNavigationController,
    state: GlobalMinusOneState,
    currentScopeId: String,
    currentTruePageIndex: Int,
    animMs: Int,
    repository: MinusOneRepository
) {
    val targetSubPanel = controller.editingSubPanelId?.let { targetSubId ->
        state.subPanels.firstOrNull { it.id == targetSubId }
    }
    AnimatedOverlayTarget(
        targetState = targetSubPanel,
        animMs = animMs,
        keyOf = { it.id }
    ) { modalSubPanel ->
        val ownedPages = state.pages.withIndex()
            .filter { it.value.subPanelId == modalSubPanel.id }
            .map { it.index to it.value }
        SubPanelEditorModal(
            subPanel = modalSubPanel,
            ownedPages = ownedPages,
            currentTruePageIndex = currentTruePageIndex,
            repository = repository,
            onSelectPage = { trueIdx ->
                if (trueIdx != repository.state.value.currentPageIndex) {
                    repository.setCurrentPageIndex(trueIdx)
                }
            },
            onDeleteSubPanel = {
                val parentStack = controller.stack.pop()
                controller.editingSubPanelId = null
                controller.switchScope(currentScopeId, parentStack.currentId.orEmpty(), parentStack)
                repository.deleteSubPanel(modalSubPanel.id)
            },
            onDismiss = { controller.editingSubPanelId = null }
        )
    }
}
