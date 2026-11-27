package com.example.globalminusone.ui.panel.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.ui.panel.LocalPanelMenuPalette
import com.example.globalminusone.ui.panel.PanelOverlayRadii

private data class QuickTileSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
internal fun AppContextQuickActionHeader(
    label: String,
    isPopOutLauncherIcon: Boolean,
    showEditProp: Boolean,
    editPropEnabled: Boolean,
    editDesc: String,
    showIconAction: Boolean,
    showRemoveAction: Boolean,
    editable: Boolean,
    onOpen: () -> Unit,
    onEditProp: () -> Unit,
    onChangeIcon: () -> Unit,
    onRemove: () -> Unit
) {
    val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
    val specs = buildQuickTileSpecs(
        menuPalette, isPopOutLauncherIcon, showEditProp, editPropEnabled, editDesc,
        showIconAction, showRemoveAction, editable, onOpen, onEditProp, onChangeIcon, onRemove
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                color = menuPalette.text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
            AppContextQuickTileRow(specs)
        }
        HorizontalDivider()
    }
}

private fun buildQuickTileSpecs(
    menuPalette: com.example.globalminusone.ui.panel.PanelMenuPalette,
    isPopOutLauncherIcon: Boolean,
    showEditProp: Boolean,
    editPropEnabled: Boolean,
    editDesc: String,
    showIconAction: Boolean,
    showRemoveAction: Boolean,
    editable: Boolean,
    onOpen: () -> Unit,
    onEditProp: () -> Unit,
    onChangeIcon: () -> Unit,
    onRemove: () -> Unit
): List<QuickTileSpec> {
    val openLabel = if (isPopOutLauncherIcon) "展开" else "正常打开"
    val editColor = if (editPropEnabled) menuPalette.text else menuPalette.disabled
    val normalColor = if (editable) menuPalette.text else menuPalette.disabled
    val removeColor = if (editable) menuPalette.danger else menuPalette.disabled
    val removeBg = if (editable) menuPalette.danger.copy(alpha = 0.12f) else menuPalette.chip
    return listOfNotNull(
        QuickTileSpec(openLabel, openLabel, Icons.Rounded.OpenInNew, menuPalette.accent, menuPalette.chip, true, onOpen),
        QuickTileSpec("属性", editDesc, Icons.Rounded.Edit, editColor, menuPalette.chip, editPropEnabled, onEditProp)
            .takeIf { showEditProp },
        QuickTileSpec("换图标", "更换图标", Icons.Rounded.Image, normalColor, menuPalette.chip, editable, onChangeIcon)
            .takeIf { showIconAction },
        QuickTileSpec("移除", "移除卡片", Icons.Rounded.DeleteOutline, removeColor, removeBg, editable, onRemove)
            .takeIf { showRemoveAction }
    )
}

@Composable
private fun AppContextQuickTileRow(specs: List<QuickTileSpec>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        specs.forEach { spec ->
            QuickContextTile(spec, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickContextTile(spec: QuickTileSpec, modifier: Modifier = Modifier) {
    Surface(
        onClick = spec.onClick,
        enabled = spec.enabled,
        shape = RoundedCornerShape(PanelOverlayRadii.Chip),
        color = spec.bg,
        modifier = modifier.heightIn(min = 50.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(spec.icon, contentDescription = spec.description, tint = spec.tint, modifier = Modifier.size(16.dp))
            Text(
                spec.label,
                color = spec.tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
