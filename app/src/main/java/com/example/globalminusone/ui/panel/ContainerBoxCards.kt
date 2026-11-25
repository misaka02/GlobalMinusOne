package com.example.globalminusone.ui.panel

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.containerRowGapDp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.system.SystemController

@Composable
internal fun ContainerQuickLayoutPill(
    item: ComponentItem,
    pageIndex: Int,
    repository: MinusOneRepository?,
    onAddChild: (() -> Unit)? = null
) {
    ContainerQuickLayoutContent(item, pageIndex, repository?.let { rememberCardLayoutActions(it) }, onAddChild)
}

@Composable
internal fun ContainerQuickLayoutContent(
    item: ComponentItem, pageIndex: Int, layoutActions: CardLayoutActions?, onAddChild: (() -> Unit)? = null
) {
    if (layoutActions == null) return
    val panelPalette = LocalPanelPalette.current
    val cardPalette = LocalCardColors.current
    val locked = LocalLayoutLocked.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    var requestedInput by remember(item.id) { mutableStateOf<Int?>(null) }
    val isGrid = item.containerLayout == ContainerLayoutType.GRID
    val cols = item.containerColumns.coerceIn(1, 8)
    requestedInput?.let { kind ->
        GridDimensionInput(
            kind = kind,
            columnCount = cols,
            iconSizeDp = item.containerIconSizeDp,
            gapDp = item.containerItemGapDp,
            onColumns = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerColumns = value) } },
            onIconSize = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerIconSizeDp = value) } },
            onGap = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerItemGapDp = value) } },
            onDismiss = { requestedInput = null },
            maxColumns = 8,
            allowAutoColumns = false,
            rowGapDp = containerRowGapDp(item),
            onRowGap = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerRowGapDp = value) } }
        )
    }
    Box {
        Surface(
            onClick = { if (!locked) expanded = true },
            enabled = !locked,
            shape = RoundedCornerShape(6.dp),
            color = if (isGrid) panelPalette.accent.copy(alpha = 0.16f) else panelPalette.accent.copy(alpha = 0.08f)
        ) {
            Text(
                text = if (isGrid) "${cols}列网格 ▾" else "自由 ▾",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isGrid) panelPalette.accent else cardPalette.textSecondary,
                maxLines = 1
            )
        }
        PanelDropdownMenu(
            expanded = expanded && !locked,
            onDismissRequest = { expanded = false }
        ) {
            com.example.globalminusone.ui.Android12WindowKeyGuard()
            val menuPalette = checkNotNull(LocalPanelMenuPalette.current)
            Text(
                text = "容器内部排版",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = menuPalette.secondary
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    ContainerLayoutType.GRID to "网格布局",
                    ContainerLayoutType.FREE to "自由布局"
                ).forEach { (mode, label) ->
                    val selected = item.containerLayout == mode
                    Surface(
                        onClick = {
                            layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerLayout = mode) }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) menuPalette.selectedChip else menuPalette.chip
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) menuPalette.accent else menuPalette.text
                        )
                    }
                }
            }
            if (isGrid) {
                GridDimensionControls(
                    columnCount = cols,
                    iconSizeDp = item.containerIconSizeDp,
                    gapDp = item.containerItemGapDp,
                    onColumns = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerColumns = value) } },
                    onIconSize = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerIconSizeDp = value) } },
                    onGap = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerItemGapDp = value) } },
                    modifier = Modifier.width(264.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                    maxColumns = 8,
                    allowAutoColumns = false,
                    rowGapDp = containerRowGapDp(item),
                    onRowGap = { value -> layoutActions.updateComponent(pageIndex, item.id) { it.copy(containerRowGapDp = value) } },
                    onRequestInput = { kind ->
                        expanded = false
                        requestedInput = kind
                    }
                )
            }
            if (onAddChild != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                PanelMenuItem(
                    text = { Text("＋ 添加子卡片", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    onClick = {
                        expanded = false
                        onAddChild()
                    }
                )
            }
        }
    }
}
@Composable
fun ContainerBoxComponent(
    item: ComponentItem,
    pageIndex: Int = 0,
    state: GlobalMinusOneState = GlobalMinusOneState(),
    repository: MinusOneRepository? = null,
    systemController: SystemController? = null,
    isEditMode: Boolean = LocalIsEditMode.current,
    onRequestClose: () -> Unit = {},
    onOpenMainApp: () -> Unit = {},
    nestedContent: (@Composable () -> Unit)? = null
) {
    ContainerBoxContent(item, pageIndex, state.cardLayoutSnapshot(), repository?.let { rememberCardLayoutActions(it) },
        repository?.let { rememberCardPropertyEditors(it, systemController) }, repository?.let { rememberCardDestinationPicker(it) },
        rememberLegacyContainerActions(repository, systemController), state.advancedConfig.eyeProtectionEnabled,
        isEditMode, onRequestClose, onOpenMainApp, nestedContent)
}

@Composable
internal fun ContainerBoxContent(
    item: ComponentItem, pageIndex: Int, state: CardLayoutSnapshot, layoutActions: CardLayoutActions?,
    propertyEditors: CardPropertyEditors?, destinationPicker: CardDestinationPicker?, legacyActions: LegacyContainerActions,
    eyeProtectionEnabled: Boolean, isEditMode: Boolean, onRequestClose: () -> Unit, onOpenMainApp: () -> Unit,
    nestedContent: (@Composable () -> Unit)?
) {
    val cardColors = LocalCardColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cols = item.containerColumns.coerceIn(2, 6)
    if (item.type == ComponentType.FOLDER) {
        FolderContainerContent(item, pageIndex, state, layoutActions, propertyEditors, destinationPicker, legacyActions, eyeProtectionEnabled, isEditMode,
            onRequestClose, onOpenMainApp, nestedContent)
        return
    }
    val actions = item.containerActions
    var editingAction by remember(item.id) { mutableStateOf<QuickActionItem?>(null) }
    val addToContainer = LocalPanelActions.current.addToContainer
    editingAction?.let { action ->
        if (layoutActions != null) propertyEditors?.LegacyAction(pageIndex, item.id, action) { editingAction = null }
    }

    if (item.containerCompact) {
        CompactContainerContent(
            item, state, layoutActions, propertyEditors, legacyActions, eyeProtectionEnabled, isEditMode, onRequestClose, onOpenMainApp, pageIndex, nestedContent
        )
        return
    }

    if (actions.isEmpty() && item.containerComponents.isEmpty()) {
        val modeLabel = if (item.containerLayout == ContainerLayoutType.GRID) {
            "${item.containerColumns.coerceIn(1, 8)}列网格"
        } else {
            "自由布局"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !LocalLayoutLocked.current && addToContainer != null) { addToContainer?.invoke(item.id) }
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (LocalLayoutLocked.current) "暂无子卡片" else "＋ 添加子卡片",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (LocalLayoutLocked.current) cardColors.textSecondary else cardColors.accent
            )
            Text(
                text = "当前：$modeLabel",
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = cardColors.textSecondary
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(
        if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).coerceAtLeast(0).dp else 8.dp)) {
        if (actions.isNotEmpty() && layoutActions != null) LegacyContainerGridContent(item, pageIndex, state.layoutLocked, eyeProtectionEnabled, layoutActions,
            legacyActions, isEditMode, onRequestClose, onOpenMainApp, onEdit = { editingAction = it })
        else if (actions.isNotEmpty()) actions.forEach { Text(it.title, color = cardColors.textPrimary) }
        if (item.containerComponents.isNotEmpty()) nestedContent?.invoke()
    }
}

@Composable
fun RenderContainerActionIcon(
    action: QuickActionItem,
    eyeCareActive: Boolean,
    modifier: Modifier = Modifier.size(38.dp)
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val title = action.title
    val iconName = action.iconName.lowercase()
    val pkg = action.packageName ?: ""
    val uri = action.intentUri ?: ""
    val effectivePkg = remember(pkg, uri) { AppAndIntentLauncher.resolveEffectivePackageName(pkg, uri) }
    val cacheGen by AppAndIntentLauncher.cacheGeneration.collectAsState()
    val cachedBitmap = remember(effectivePkg, cacheGen) {
        if (effectivePkg.isNotBlank()) AppAndIntentLauncher.peekAppIconBitmap(effectivePkg) else null
    }
    var loadedBitmap by remember(effectivePkg) { mutableStateOf(cachedBitmap) }
    val resolvedBitmap = cachedBitmap ?: loadedBitmap
    LaunchedEffect(context, effectivePkg) {
        if (effectivePkg.isNotBlank() && resolvedBitmap == null &&
            !AppAndIntentLauncher.isKnownMissingPackage(effectivePkg)
        ) {
            AppAndIntentLauncher.getAppInfoCached(context, effectivePkg)
            loadedBitmap = AppAndIntentLauncher.peekAppIconBitmap(effectivePkg)
        }
    }

    when {
        iconName.startsWith("custom:") -> Icon(getQuickActionIcon(iconName), contentDescription = title,
            tint = cardColors.accent, modifier = modifier)
        title == "QQ" || iconName == "qq" || pkg == "com.tencent.mobileqq" -> {
            QqAvatarIcon(modifier = modifier)
        }
        title == "微信" && (pkg == "com.tencent.mm" || uri.startsWith("weixin://")) -> {
            WechatAvatarIcon(modifier = modifier)
        }
        title == "酷安" || iconName == "coolapk" || pkg == "com.coolapk.market" -> {
            CoolapkAvatarIcon(modifier = modifier)
        }
        title == "全局负一屏" || pkg == "com.example.globalminusone" -> {
            GlobalMinusOneAvatarIcon(modifier = modifier)
        }
        title == "区域截屏" || iconName == "crop" -> {
            CropShortcutIcon(modifier = modifier)
        }
        title == "全局护眼" || iconName == "visibility" || iconName == "eye" -> {
            EyeShortcutIcon(active = eyeCareActive, modifier = modifier)
        }
        title == "播放/暂停" || iconName == "play_pause" || iconName == "playarrow" -> {
            PlayPauseShortcutIcon(modifier = modifier)
        }
        title == "语音助手" || iconName == "mic" -> {
            VoiceShortcutIcon(modifier = modifier)
        }
        (title == "扫一扫" || title == "付款码") && (pkg.contains("alipay", ignoreCase = true) || uri.contains("alipay", ignoreCase = true) || iconName.contains("alipay")) -> {
            AlipayPayAvatarIcon(modifier = modifier)
        }
        (title == "扫一扫" || title == "付款码") && (pkg.contains("tencent", ignoreCase = true) || uri.contains("weixin", ignoreCase = true) || iconName.contains("wechat")) -> {
            WechatPayAvatarIcon(modifier = modifier)
        }
        resolvedBitmap != null -> {
            Image(
                bitmap = resolvedBitmap,
                contentDescription = title,
                modifier = modifier.clip(RoundedCornerShape(10.dp))
            )
        }
        title in listOf("百度", "微博", "京东", "淘宝") || uri.startsWith("http://") || uri.startsWith("https://") -> {
            WebLinkAvatarIcon(modifier = modifier)
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(CircleShape)
                    .background(cardColors.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = title,
                    tint = cardColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

fun handleContainerActionClick(
    action: QuickActionItem,
    context: Context,
    repository: MinusOneRepository?,
    systemController: SystemController?,
    scope: kotlinx.coroutines.CoroutineScope,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit
) {
    executeLegacyContainerAction(action, context,
        repository?.let { repo -> { repo.updateAdvancedConfig { it.copy(eyeProtectionEnabled = !it.eyeProtectionEnabled) }; Unit } },
        systemController?.let { legacyContainerSystemActions(it) }, scope, onRequestClose, onOpenMainApp)
}

internal fun executeLegacyContainerAction(
    action: QuickActionItem, context: Context, toggleEyeProtection: (() -> Unit)?,
    systemActions: LegacyContainerSystemActions?, scope: kotlinx.coroutines.CoroutineScope,
    onRequestClose: () -> Unit, onOpenMainApp: () -> Unit
) {
    when (action.intentUri.trim()) {
        "action:com.example.globalminusone.MAIN" -> {
            onRequestClose()
            onOpenMainApp()
        }
        "action:screenshot_region" -> {
            if (systemActions != null) {
                systemActions.takeRegionScreenshot()
            } else {
                AppAndIntentLauncher.launchQuickAction(context, action)
            }
        }
        "action:screen_assistant" -> {
            if (systemActions != null) {
                systemActions.launchScreenAssistant()
            } else {
                AppAndIntentLauncher.launchQuickAction(context, action)
            }
        }
        "action:breeno_touch" -> {
            if (systemActions != null) {
                systemActions.launchBreenoTouch()
            } else {
                AppAndIntentLauncher.launchQuickAction(context, action)
            }
        }
        "action:eye_protection" -> {
            toggleEyeProtection?.invoke()
            Toast.makeText(context, "已切换护眼模式", Toast.LENGTH_SHORT).show()
        }
        "action:media_play_pause" -> {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                val wasActive = audioManager?.isMusicActive == true
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                systemActions?.performTickHaptic()
                if (!wasActive) {
                    Toast.makeText(context, "已发送播放/暂停指令（当前无活跃媒体）", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "媒体控制失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        "action:voice_assist" -> {
            onRequestClose()
            systemActions?.launchVoiceAssistant()
        }
        else -> {
            if (AppAndIntentLauncher.launchQuickAction(context, action)) onRequestClose()
        }
    }
}
