package com.example.globalminusone.ui.panel

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.AppearanceConfig
import com.example.globalminusone.data.CardConfig
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemController

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 12,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardColors = LocalCardColors.current
    val shape = RoundedCornerShape(cornerRadiusDp.dp)
    val bg = backgroundColor ?: Color.White
    val border = borderColor ?: cardColors.trackColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = border, shape = shape),
        shape = shape,
        color = bg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
fun HeaderGlanceCard(
    state: GlobalMinusOneState,
    appearance: AppearanceConfig,
    repository: MinusOneRepository,
    onRequestClose: () -> Unit
) {
    HitokotoCardComponent(state = state, repository = repository)
}

@Composable
fun QuickActionsCard(
    card: CardConfig? = null,
    repository: MinusOneRepository? = null,
    state: GlobalMinusOneState,
    onRequestClose: () -> Unit
) {
    val cardColors = LocalCardColors.current
    val context = LocalContext.current

    GlassCard {
        Text(text = "快捷方式", color = cardColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        val actionsScroll = rememberScrollState()
        val actionsScrollEnabled = shouldEnableChildHorizontalScroll(true, actionsScroll.maxValue)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .ownPanelScrollGestures(actionsScrollEnabled, axis = PanelGestureAxis.Horizontal)
                .horizontalScroll(actionsScroll, enabled = actionsScrollEnabled),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.quickActions.forEach { item ->
                CustomShortcutComponent(
                    item = ComponentItem(
                        type = ComponentType.CUSTOM_SHORTCUT,
                        title = item.title,
                        subtitle = item.subtitle,
                        iconName = item.iconName,
                        colorHex = try {
            android.graphics.Color.parseColor(item.colorHex).toLong() and 0xFFFFFFFFL
                        } catch (_: Exception) {
                            0xFF2196F3
                        },
                        intentUri = item.intentUri,
                        packageName = item.packageName
                    ),
                    onClick = {
                        AppAndIntentLauncher.launchQuickAction(context, item)
                        onRequestClose()
                    }
                )
            }
        }
    }
}

@Composable
fun SystemControlCard(
    card: CardConfig? = null,
    repository: MinusOneRepository? = null,
    systemController: SystemController,
    onRequestClose: () -> Unit
) {
    GlassCard {
        BrightnessBarComponent(systemController = systemController, onBrightnessAdjusting = {})
        Spacer(modifier = Modifier.height(4.dp))
        VolumeBarComponent(systemController = systemController)
    }
}

@Composable
fun AppGridCard(
    card: CardConfig? = null,
    repository: MinusOneRepository? = null,
    state: GlobalMinusOneState,
    appearance: AppearanceConfig,
    onOpenMainApp: () -> Unit,
    onRequestClose: () -> Unit
) {
    GlassCard {
        AppDrawerComponent(isEditMode = false, onRequestClose = onRequestClose)
    }
}

@Composable
fun TodoNotesCard(
    card: CardConfig? = null,
    state: GlobalMinusOneState,
    repository: MinusOneRepository
) {
    GlassCard {
        NoteCardComponent(
            item = ComponentItem(type = ComponentType.NOTE_CARD, textContent = state.noteText),
            pageIndex = 0,
            repository = repository
        )
    }
}

@Composable
fun ClipboardCard(
    card: CardConfig? = null,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    systemController: SystemController
) {
    GlassCard {
        ClipboardCardComponent(state = state, repository = repository, systemController = systemController)
    }
}

@Composable
fun SystemMonitorCard(
    card: CardConfig? = null,
    repository: MinusOneRepository? = null
) {
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.weight(1f)) { StatRamComponent() }
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.weight(1f)) { StatRomComponent() }
        }
    }
}

@Composable
fun AppWidgetCard(
    card: CardConfig,
    state: GlobalMinusOneState,
    widgetHostManager: WidgetHostManager,
    repository: MinusOneRepository
) {
    GlassCard {
        AppWidgetComponent(
            item = ComponentItem(type = ComponentType.APP_WIDGET, widgetId = card.widgetId ?: -1),
            widgetHostManager = widgetHostManager
        )
    }
}

@Composable
fun CustomImageCard(
    card: CardConfig,
    appearance: AppearanceConfig,
    repository: MinusOneRepository? = null,
    onOpenMainApp: () -> Unit
) {
    GlassCard {
        SingleImageComponent(
            item = ComponentItem(type = ComponentType.SINGLE_IMAGE, imageUri = card.customImageUri ?: ""),
            onOpenMainApp = onOpenMainApp
        )
    }
}
