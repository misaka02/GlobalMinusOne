package com.example.globalminusone.ui.panel.editor

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelOverlayRadii
import com.example.globalminusone.ui.panel.panelOverlayTokens

/** Docked 2-row editing workbench with direct inline controls and zero nested dropdowns. */
@Composable
fun EditModeTopBanner(
    currentPage: Int,
    totalPages: Int,
    isLinearPage: Boolean,
    layoutType: PageLayoutType = if (isLinearPage) PageLayoutType.LINEAR else PageLayoutType.FREE,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleLayoutType: () -> Unit,
    onSelectLayoutType: ((PageLayoutType) -> Unit)? = null,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onGlobalProperties: () -> Unit,
    onPanelGeometry: () -> Unit,
    onTriggerZone: () -> Unit = {},
    onCardProperties: (() -> Unit)? = null,
    selectedCardTitle: String? = null,
    onClearSelection: (() -> Unit)? = null,
    panelGeometryActive: Boolean = false,
    triggerZoneActive: Boolean = false,
    onPanelNumbers: () -> Unit = {},
    onNewPage: (() -> Unit)? = null,
    onCompactGrid: (() -> Unit)? = null,
    pageName: String = "",
    subPanelTitle: String? = null,
    onExitSubPanel: (() -> Unit)? = null,
    onSubPanelSettings: (() -> Unit)? = null
) {
    val palette = LocalPanelPalette.current
    val tokens = panelOverlayTokens(palette)
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("edit_tools", Context.MODE_PRIVATE) }
    var collapsed by remember(prefs) {
        mutableStateOf(runCatching { prefs.getBoolean("collapsed", false) }.getOrDefault(false))
    }
    var dockTop by remember(prefs) {
        mutableStateOf(runCatching { prefs.getBoolean("dock_top", false) }.getOrDefault(false))
    }
    var toolsExpanded by remember(prefs) {
        mutableStateOf(runCatching { prefs.getBoolean("tools_expanded", true) }.getOrDefault(true))
    }
    val surfaceColor = tokens.elevatedSurface.copy(alpha = 0.96f)
    val workbenchAlign = if (dockTop) Alignment.TopCenter else Alignment.BottomCenter

    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .align(workbenchAlign)
                .then(if (collapsed) Modifier.wrapContentWidth() else Modifier.widthIn(max = 440.dp).fillMaxWidth()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!dockTop && !collapsed && onCardProperties != null) {
                EditBannerSelectedCardBar(
                    selectedCardTitle = selectedCardTitle,
                    accent = palette.accent,
                    surfaceColor = surfaceColor,
                    groupChipColor = tokens.chipBg,
                    tokens = tokens,
                    onCardProperties = onCardProperties,
                    onClearSelection = onClearSelection
                )
            }
            Surface(
                shape = RoundedCornerShape(PanelOverlayRadii.Card),
                color = surfaceColor,
                border = BorderStroke(0.8.dp, tokens.border),
                shadowElevation = 6.dp
            ) {
                if (collapsed) {
                    CollapsedWorkbenchRow(
                        accent = palette.accent,
                        tokens = tokens,
                        onExpand = {
                            collapsed = false
                            prefs.edit().putBoolean("collapsed", false).apply()
                        },
                        onDone = onDone
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EditWorkbenchPrimaryRow(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            pageName = pageName,
                            subPanelTitle = subPanelTitle,
                            toolsExpanded = toolsExpanded,
                            panelGeometryActive = panelGeometryActive,
                            triggerZoneActive = triggerZoneActive,
                            accent = palette.accent,
                            tokens = tokens,
                            onPrevPage = onPrevPage,
                            onNextPage = onNextPage,
                            onAdd = onAdd,
                            onToggleTools = {
                                toolsExpanded = !toolsExpanded
                                prefs.edit().putBoolean("tools_expanded", toolsExpanded).apply()
                            },
                            onFinishActiveTool = { if (triggerZoneActive) onTriggerZone() else onPanelGeometry() },
                            onExitSubPanel = onExitSubPanel,
                            onDone = onDone
                        )
                        if (toolsExpanded) {
                            HorizontalDivider(color = tokens.divider, thickness = 0.6.dp)
                            EditWorkbenchToolsStrip(
                                layoutType = layoutType,
                                dockTop = dockTop,
                                panelGeometryActive = panelGeometryActive,
                                triggerZoneActive = triggerZoneActive,
                                accent = palette.accent,
                                tokens = tokens,
                                onSelectLayoutType = onSelectLayoutType,
                                onToggleLayoutType = onToggleLayoutType,
                                onPanelGeometry = onPanelGeometry,
                                onPanelNumbers = onPanelNumbers,
                                onGlobalProperties = onGlobalProperties,
                                onTriggerZone = onTriggerZone,
                                onCompactGrid = onCompactGrid,
                                onNewPage = onNewPage,
                                onSubPanelSettings = onSubPanelSettings,
                                onToggleDock = {
                                    dockTop = !dockTop
                                    prefs.edit().putBoolean("dock_top", dockTop).apply()
                                },
                                onCollapse = {
                                    collapsed = true
                                    prefs.edit().putBoolean("collapsed", true).apply()
                                }
                            )
                        }
                    }
                }
            }
            if (dockTop && !collapsed && onCardProperties != null) {
                EditBannerSelectedCardBar(
                    selectedCardTitle = selectedCardTitle,
                    accent = palette.accent,
                    surfaceColor = surfaceColor,
                    groupChipColor = tokens.chipBg,
                    tokens = tokens,
                    onCardProperties = onCardProperties,
                    onClearSelection = onClearSelection
                )
            }
        }
    }
}

@Composable
private fun CollapsedWorkbenchRow(
    accent: Color,
    tokens: com.example.globalminusone.ui.panel.PanelOverlayTokens,
    onExpand: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.heightIn(min = 42.dp).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(onClick = onExpand, shape = RoundedCornerShape(PanelOverlayRadii.Chip), color = tokens.chipBg) {
            Text(
                "展开工作台",
                color = tokens.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
        Surface(onClick = onDone, shape = RoundedCornerShape(PanelOverlayRadii.Chip), color = accent) {
            Text(
                "完成",
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

internal data class EditToolsPosition(val x: Float, val y: Float)

internal fun clampEditToolsPosition(
    position: EditToolsPosition,
    width: Float,
    height: Float,
    hostWidth: Float,
    hostHeight: Float
) = EditToolsPosition(
    position.x.coerceIn(0f, (hostWidth - width).coerceAtLeast(0f)),
    position.y.coerceIn(0f, (hostHeight - height).coerceAtLeast(0f))
)
