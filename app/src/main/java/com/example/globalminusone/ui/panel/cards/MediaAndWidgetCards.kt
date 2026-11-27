package com.example.globalminusone.ui.panel.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.policy.DefaultPresets
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.PanelDropdownMenu
import com.example.globalminusone.ui.panel.PanelMenuItem

@Composable
fun AppWidgetComponent(
    item: ComponentItem,
    widgetHostManager: WidgetHostManager
) {
    AppWidgetCardContent(item, rememberWidgetCardViewFactory(widgetHostManager))
}

@Composable
internal fun AppWidgetCardContent(
    item: ComponentItem,
    factory: WidgetCardViewFactory,
    onLongPressEdit: (() -> Unit)? = null
) {
    val cardColors = LocalCardColors.current
    val context = LocalContext.current
    val isEditMode = LocalIsEditMode.current
    val currentOnLongPressEdit by rememberUpdatedState(onLongPressEdit)

    val hostView = remember(factory, context, item.widgetId) {
        if (item.widgetId > 0) {
            factory.createHostView(context, item.widgetId)
        } else null
    }

    if (hostView != null) {
        val theme = LocalPanelPalette.current.themeConfig
        val provider = hostView.appWidgetInfo?.provider
        val inheritedBackground = Color((theme.defaultCardBgColorHex and 0x00FFFFFFL) or 0xFF000000L)
        // AOSP's legacy analog clock uses transparent white dial/hand resources.
        // Limit the contrast backing to this provider on an inherited opaque light
        // surface; explicit card color or opacity always remains the user's choice.
        val needsClockBacking = provider?.packageName == "com.android.deskclock" &&
            provider?.className == "com.android.alarmclock.AnalogAppWidgetProvider" &&
            item.bgColorHex == null && item.bgAlphaPercent !in 0..100 &&
            theme.cardBackground && theme.cardBgAlphaPercent == 100 &&
            inheritedBackground.luminance() >= 0.8f
        AndroidView(
            factory = { ctx ->
                android.widget.FrameLayout(ctx).also { container ->
                    syncAndAttachWidgetHostView(container, hostView, isEditMode) {
                        currentOnLongPressEdit?.invoke()
                    }
                }
            },
            update = { container ->
                syncAndAttachWidgetHostView(container, hostView, isEditMode) {
                    currentOnLongPressEdit?.invoke()
                }
            },
            onRelease = { container ->
                (hostView as? WidgetHostManager.SafeAppWidgetHostView)?.clearHostCallbacks()
                container.removeAllViews()
            },
            modifier = Modifier.fillMaxSize()
                .background(if (needsClockBacking) Color(0xFF202124) else Color.Transparent)
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "未绑定小部件",
                color = cardColors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}

private fun syncAndAttachWidgetHostView(
    container: android.widget.FrameLayout,
    hostView: android.appwidget.AppWidgetHostView,
    isEditMode: Boolean,
    onLongPressEdit: () -> Unit
) {
    (hostView as? WidgetHostManager.SafeAppWidgetHostView)?.apply {
        blockInteractionForEdit = isEditMode
        onHostLongPress = if (!isEditMode) onLongPressEdit else null
    }
    val previousParent = hostView.parent as? android.view.ViewGroup
    if (previousParent != null && previousParent !== container && !previousParent.isAttachedToWindow) {
        previousParent.removeView(hostView)
    }
    if (hostView.parent == null) {
        container.removeAllViews()
        container.addView(
            hostView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}

@Composable
fun SingleImageComponent(
    item: ComponentItem,
    onOpenMainApp: () -> Unit
) {
    SingleImageCardContent(item, onOpenMainApp) { source -> MinusOneRepository.getInstance(source).imageAccessRevisions }
}

@Composable
internal fun SingleImageCardContent(
    item: ComponentItem, onOpenMainApp: () -> Unit,
    imageRevisions: (android.content.Context) -> kotlinx.coroutines.flow.StateFlow<Map<String, Long>>
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    val rawUri = item.imageUri.trim()
    val accessRevisions by remember(context) { imageRevisions(context) }.collectAsState()
    val accessRevision = accessRevisions[rawUri] ?: 0L
    val cachedBitmap = remember(rawUri, accessRevision) {
        if (rawUri.isNotEmpty()) {
            com.example.globalminusone.system.peekCardImageBitmap(rawUri, revision = accessRevision)
        } else null
    }

    val decodeState by produceState<Triple<String, ImageBitmap?, String?>>(
        initialValue = if (rawUri.isNotEmpty()) Triple(rawUri, cachedBitmap, null) else Triple("", null, null),
        key1 = context,
        key2 = rawUri,
        key3 = accessRevision
    ) {
        if (rawUri.isEmpty()) {
            value = Triple("", null, null)
            return@produceState
        }
        val peeked = com.example.globalminusone.system.peekCardImageBitmap(rawUri, revision = accessRevision)
        if (peeked != null) {
            value = Triple(rawUri, peeked, null)
            return@produceState
        }
        // Keep the last good image while the same resource is refreshed. Never show an old URI's image.
        if (value.first != rawUri || value.second == null) value = Triple(rawUri, null, null)
        val decoded: Pair<ImageBitmap?, String?> = try {
            com.example.globalminusone.system.loadCardImageBitmap(context, rawUri, revision = accessRevision) to null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("CardImage", "Decode failed: ${failure.javaClass.simpleName}")
            null to when (failure) {
                is SecurityException -> "图片读取权限已失效"
                is java.io.FileNotFoundException -> "图片已移动或无法访问"
                else -> "图片内容无法解码或不完整"
            }
        } catch (_: OutOfMemoryError) {
            null to "图片过大，暂时无法加载"
        }
        value = Triple(rawUri, decoded.first, decoded.second)
    }

    val bitmap = cachedBitmap ?: decodeState.second.takeIf { decodeState.first == rawUri }
    val loadFailed = bitmap == null && decodeState.first == rawUri && decodeState.third != null

    when {
        rawUri.isNotEmpty() && bitmap != null -> {
            Image(
                bitmap = bitmap,
                contentDescription = item.title.ifBlank { "图片卡片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        rawUri.isNotEmpty() && loadFailed -> {
            BoxWithConstraints(Modifier.fillMaxSize()
                .clickable(enabled = !isEditMode) { onOpenMainApp() }
                .semantics(mergeDescendants = true) {
                    contentDescription = "${decodeState.third.orEmpty()}，点击重新选择图片"
                }.padding(6.dp)) {
                val compact = maxWidth < 132.dp || maxHeight < 80.dp
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Rounded.Image, null, tint = cardColors.textSecondary, modifier = Modifier.size(20.dp))
                    if (!compact) Text(decodeState.third.orEmpty(), color = cardColors.textPrimary,
                        fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
                    Text(if (compact) "重选图片" else "点击重新选择图片", color = cardColors.accent,
                        fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        rawUri.isNotEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载图片中…", color = cardColors.textSecondary, fontSize = 10.sp)
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !isEditMode) { onOpenMainApp() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = cardColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "点击设置图片",
                        color = cardColors.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun WebsiteLinkComponent(
    item: ComponentItem,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    onRequestClose: () -> Unit
) {
    WebsiteLinkCardContent(state.appearanceConfig, { repository.updateAppearanceConfig(it) }, onRequestClose)
}

@Composable
internal fun WebsiteLinkCardContent(
    appearance: com.example.globalminusone.data.AppearanceConfig,
    onAppearanceChange: ((com.example.globalminusone.data.AppearanceConfig) -> com.example.globalminusone.data.AppearanceConfig) -> Unit,
    onRequestClose: () -> Unit
) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    val isEditMode = LocalIsEditMode.current
    var searchInput by remember { mutableStateOf("") }
    var isEngineMenuOpen by remember { mutableStateOf(false) }

    val onSearch = {
        val query = searchInput.trim()
        if (query.isNotEmpty()) {
            AppAndIntentLauncher.launchSearch(context, appearance.searchEngineUrl, query)
            onRequestClose()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 引擎切换
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = !isEditMode) { isEngineMenuOpen = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appearance.searchEngineName,
                    color = cardColors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = cardColors.accent,
                    modifier = Modifier.size(14.dp)
                )
            }

            PanelDropdownMenu(
                expanded = isEngineMenuOpen,
                onDismissRequest = { isEngineMenuOpen = false }
            ) {
                com.example.globalminusone.ui.Android12WindowKeyGuard()

                DefaultPresets.searchEngines.forEach { (engineName, engineUrl) ->
                    PanelMenuItem(
                        text = { Text(text = engineName, fontSize = 12.sp) },
                        onClick = {
                            isEngineMenuOpen = false
                            onAppearanceChange {
                                it.copy(searchEngineName = engineName, searchEngineUrl = engineUrl)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        BasicTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            enabled = !isEditMode,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = cardColors.textPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(cardColors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                if (searchInput.isEmpty()) {
                    Text(text = "搜索关键词或网址...", color = cardColors.textPlaceholder, fontSize = 11.sp)
                }
                inner()
            }
        )

        IconButton(
            onClick = onSearch,
            enabled = !isEditMode,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "搜索",
                tint = cardColors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// 兼容旧版的通用卡片与老接口
// -----------------------------------------------------------------------------------------
