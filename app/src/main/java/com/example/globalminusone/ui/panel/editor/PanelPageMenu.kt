package com.example.globalminusone.ui.panel.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.motion.overlayScrim
import com.example.globalminusone.ui.motion.overlaySheetMotion
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelOverlayRadii
import com.example.globalminusone.ui.panel.panelOverlayTokens

@Composable
fun BottomNineKeyModal(
    state: GlobalMinusOneState,
    currentPageIndex: Int,
    onDismiss: () -> Unit,
    onToggleEditMode: () -> Unit,
    onOpenAddComponent: () -> Unit,
    onTempDisable: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    onOpenThemeAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwapPageLeft: () -> Unit,
    onNewPage: () -> Unit,
    onSwapPageRight: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onToggleLayoutLock: () -> Unit,
    onToggleLayoutType: () -> Unit = {},
    onSelectLayoutType: ((PageLayoutType) -> Unit)? = null,
    onNavigatePage: ((Int) -> Unit)? = null,
    onOpenPanelSize: (() -> Unit)? = null,
    onToggleLockOpenPage: (() -> Unit)? = null
) {
    val layoutLocked = state.advancedConfig.layoutLocked
    val palette = LocalPanelPalette.current
    val tokens = panelOverlayTokens(palette)
    var showPageManage by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .overlayScrim(Color.Black, 0.44f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .overlaySheetMotion()
                .clip(RoundedCornerShape(topStart = PanelOverlayRadii.Sheet, topEnd = PanelOverlayRadii.Sheet))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            color = tokens.sheetBg,
            shadowElevation = 10.dp,
            border = BorderStroke(0.75.dp, tokens.border),
            shape = RoundedCornerShape(topStart = PanelOverlayRadii.Sheet, topEnd = PanelOverlayRadii.Sheet)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(tokens.handle)
                )
                NineKeySheetHeader(
                    currentPageIndex = currentPageIndex,
                    totalPages = state.pages.size,
                    showPageManage = showPageManage,
                    accent = palette.accent,
                    tokens = tokens,
                    onTogglePageManage = { showPageManage = !showPageManage },
                    onDismiss = onDismiss
                )
                NineKeyPageCard(
                    state = state,
                    currentPageIndex = currentPageIndex,
                    layoutLocked = layoutLocked,
                    showPageManage = showPageManage,
                    accent = palette.accent,
                    tokens = tokens,
                    onNavigatePage = onNavigatePage,
                    onSelectLayoutType = onSelectLayoutType,
                    onToggleLayoutType = onToggleLayoutType,
                    onSwapPageLeft = onSwapPageLeft,
                    onNewPage = onNewPage,
                    onSwapPageRight = onSwapPageRight,
                    onDeleteCurrentPage = onDeleteCurrentPage,
                    onToggleLockOpenPage = onToggleLockOpenPage
                )
                NineKeyActionGridCard(
                    editMode = state.editMode,
                    layoutLocked = layoutLocked,
                    tokens = tokens,
                    hasPanelSize = onOpenPanelSize != null,
                    onToggleEditMode = onToggleEditMode,
                    onOpenAddComponent = onOpenAddComponent,
                    onOpenThemeAppearance = onOpenThemeAppearance,
                    onOpenPanelSize = { onOpenPanelSize?.invoke() },
                    onToggleLayoutLock = onToggleLayoutLock,
                    onOpenFileBrowser = onOpenFileBrowser,
                    onTempDisable = onTempDisable,
                    onOpenSettings = onOpenSettings
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun NineKeyButton(
    title: String,
    icon: ImageVector,
    active: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    val tokens = panelOverlayTokens(palette)
    val contentColor = when {
        !enabled -> tokens.textDisabled
        destructive -> tokens.danger
        active -> palette.accent
        else -> tokens.textPrimary
    }
    val circleBg = when {
        active -> palette.accent.copy(alpha = 0.18f)
        destructive && enabled -> tokens.danger.copy(alpha = 0.12f)
        else -> tokens.chipBg
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(PanelOverlayRadii.Control),
        color = if (active) palette.accent.copy(alpha = 0.10f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(circleBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
