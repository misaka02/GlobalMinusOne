package com.example.globalminusone.ui.panel.file

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.VideoView
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.system.file.BrowserPreview
import com.example.globalminusone.system.file.FileBrowserIo
import com.example.globalminusone.system.media.ImageClipboard
import com.example.globalminusone.ui.AppIcons as Icons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.example.globalminusone.ui.panel.LocalFloatingWindowContentVisible
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures

/** Fit uses the bitmap's aspect ratio; letterboxing is not draggable image content. */
internal fun imagePanLimit(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int, scale: Float): Offset {
    if (minOf(viewWidth, viewHeight, imageWidth, imageHeight) <= 0) return Offset.Zero
    val fit = minOf(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    val zoom = scale.takeIf { it.isFinite() }?.coerceIn(1f, 8f) ?: 1f
    return Offset(((imageWidth * fit * zoom - viewWidth) / 2f).coerceAtLeast(0f),
        ((imageHeight * fit * zoom - viewHeight) / 2f).coerceAtLeast(0f))
}

internal data class PdfPageControls(val page: Int, val pages: Int, val loading: Boolean,
    val onPrevious: () -> Unit, val onNext: () -> Unit)

@Composable
internal fun ZoomableFileImage(preview: BrowserPreview.Image, title: String, gif: Boolean,
    pdfControls: PdfPageControls? = null) {
    val context = LocalContext.current
    val copyScope = rememberCoroutineScope()
    var imageMenu by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf(false) }
    var scale by remember(preview.bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(preview.bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun reset() { scale = 1f; pan = Offset.Zero }
    fun clampPan(value: Offset): Offset {
        val limit = imagePanLimit(viewport.width, viewport.height, preview.bitmap.width, preview.bitmap.height, scale)
        return Offset(value.x.coerceIn(-limit.x, limit.x), value.y.coerceIn(-limit.y, limit.y))
    }
    LaunchedEffect(viewport, preview.bitmap) { pan = clampPan(pan) }
    Column(Modifier.fillMaxSize()) {
        if (pdfControls == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${(scale * 100).toInt()}% · 双指缩放/拖移 · 双击复位${if (gif) " · GIF首帧" else ""}",
                    Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { reset() }) { Text("复位") }
                Box {
                    TextButton(onClick = { imageMenu = true }) { Text("更多") }
                    DropdownMenu(expanded = imageMenu, onDismissRequest = { imageMenu = false }) {
                        DropdownMenuItem(text = { Text("复制图片") }, enabled = !copying, onClick = {
                            imageMenu = false
                            // Snapshot while the preview still owns its pixels; later close may release them.
                            val owned = runCatching {
                                preview.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            }.getOrNull()
                            if (owned == null) {
                                android.widget.Toast.makeText(context, "无法读取图片", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                copying = true
                                copyScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    try {
                                        ensureActive()
                                        ImageClipboard.copy(context, owned)
                                        android.widget.Toast.makeText(context, "已复制图片", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "复制图片失败", android.widget.Toast.LENGTH_SHORT).show()
                                    } finally {
                                        owned.recycle()
                                        copying = false
                                    }
                                }
                            }
                        })
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = pdfControls.onPrevious, enabled = pdfControls.page > 1 && !pdfControls.loading,
                    modifier = Modifier.size(48.dp)) { Icon(Icons.ArrowBack, "上一页") }
                Text("${pdfControls.page} / ${pdfControls.pages} · ${(scale * 100).toInt()}% · 双指",
                    Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = pdfControls.onNext, enabled = pdfControls.page < pdfControls.pages && !pdfControls.loading,
                    modifier = Modifier.size(48.dp)) { Icon(Icons.ArrowForward, "下一页") }
                IconButton(onClick = { reset() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.RestartAlt, "复位缩放")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(preview.bitmap) { detectTapGestures(onDoubleTap = { reset() }) }
            .pointerInput(preview.bitmap) {
                detectTransformGestures { centroid, movement, zoom, _ ->
                    val old = scale
                    val next = (old * zoom).coerceIn(1f, 8f)
                    val center = Offset(viewport.width / 2f, viewport.height / 2f)
                    val shifted = (pan + center - centroid) * (next / old) + centroid - center + movement
                    scale = next
                    pan = clampPan(shifted)
                }
            }) {
            Image(preview.bitmap.asImageBitmap(), title, Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
            }, contentScale = ContentScale.Fit)
        }
    }
}

@Composable
internal fun PdfFilePreview(preview: BrowserPreview.Pdf, io: FileBrowserIo) {
    var index by remember(preview) { mutableIntStateOf(0) }
    var bitmap by remember(preview) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember(preview) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(preview, index) {
        loading = true; error = null; bitmap = null
        try { bitmap = io.renderPdfPage(preview, index) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "PDF 页面渲染失败，请使用其他应用打开" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        if (bitmap == null) FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { index-- }, enabled = index > 0 && !loading) { Text("上一页") }
            Text("${index + 1} / ${preview.pages}", Modifier.padding(12.dp))
            TextButton(onClick = { index++ }, enabled = index + 1 < preview.pages && !loading) { Text("下一页") }
        }
        if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
        error?.let { Text(it, Modifier.padding(16.dp)) }
        bitmap?.let { ZoomableFileImage(BrowserPreview.Image(it), "PDF 第 ${index + 1} 页", gif = false,
            pdfControls = PdfPageControls(index + 1, preview.pages, loading,
                onPrevious = { index-- }, onNext = { index++ })) }
    }
}

/** Playback is opt-in. Closing/hiding the panel releases it; titlebar collapse keeps it composed. */
@Composable
internal fun MediaFilePreview(preview: BrowserPreview.Media) {
    if (preview.video) VideoFilePreview(preview) else AudioFilePreview(preview)
}

/** Audio does not need (or wait for) a VideoView surface. Playback is always explicit. */
@Composable
private fun AudioFilePreview(preview: BrowserPreview.Media) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var player by remember(preview.uri) { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(preview.uri) { mutableStateOf(false) }
    var preparing by remember(preview.uri) { mutableStateOf(false) }
    var playing by remember(preview.uri) { mutableStateOf(false) }
    var duration by remember(preview.uri) { mutableIntStateOf(0) }
    var position by remember(preview.uri) { mutableIntStateOf(0) }
    var seekPosition by remember(preview.uri) { mutableStateOf<Float?>(null) }
    var error by remember(preview.uri) { mutableStateOf<String?>(null) }
    val attributes = remember { AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build() }
    val focusRequest = remember(preview.uri, audioManager) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change < 0) {
                    runCatching { player?.pause() }
                    playing = false
                }
            }.build()
    }
    fun releasePlayer() {
        val old = player
        player = null // Invalidate asynchronous callbacks before releasing.
        ready = false; preparing = false; playing = false
        duration = 0; position = 0; seekPosition = null
        runCatching { old?.setOnPreparedListener(null) }
        runCatching { old?.setOnCompletionListener(null) }
        runCatching { old?.setOnSeekCompleteListener(null) }
        runCatching { old?.setOnErrorListener(null) }
        runCatching { old?.release() }
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }
    fun startPrepared(mp: MediaPlayer) {
        try {
            val manager = audioManager ?: run {
                error = "设备暂不支持音频播放"
                playing = false
                return
            }
            if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                error = "暂时无法取得音频播放焦点，请稍后重试"
                playing = false
                return
            }
            if (duration > 0 && position >= duration) { mp.seekTo(0); position = 0 }
            mp.start()
            playing = mp.isPlaying
            android.util.Log.d("PanelAudioPreview", "started isPlaying=$playing durationMs=$duration")
            error = null
        } catch (e: Exception) {
            releasePlayer()
            error = "音频播放失败：${e.message ?: "请重试"}"
        }
    }
    fun play() {
        error = null
        val current = player
        if (ready && current != null) { startPrepared(current); return }
        releasePlayer()
        preparing = true
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attributes)
            mp.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    duration = runCatching { prepared.duration }.getOrDefault(0).coerceAtLeast(0)
                    ready = true; preparing = false
                    android.util.Log.d("PanelAudioPreview", "prepared durationMs=$duration")
                    startPrepared(prepared)
                }
            }
            mp.setOnCompletionListener { completed ->
                if (player === completed) {
                    playing = false; position = duration
                    android.util.Log.d("PanelAudioPreview", "completed durationMs=$duration")
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
            }
            mp.setOnSeekCompleteListener { completed ->
                if (player === completed) position = runCatching { completed.currentPosition }.getOrDefault(position)
            }
            mp.setOnErrorListener { failed, what, extra ->
                if (player === failed) {
                    releasePlayer()
                    error = when (extra) {
                        MediaPlayer.MEDIA_ERROR_IO -> "音频读取失败，请检查文件是否存在和读取权限"
                        MediaPlayer.MEDIA_ERROR_UNSUPPORTED, MediaPlayer.MEDIA_ERROR_MALFORMED -> "设备不支持此音频格式，或文件已损坏"
                        else -> "音频播放失败（$what / $extra），可重试播放"
                    }
                }
                true
            }
            // Android resolves persisted SAF single-document/tree URIs and FileProvider URIs here.
            mp.setDataSource(context, preview.uri)
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            error = "无法读取音频：${e.message ?: "请检查文件权限后重试"}"
        }
    }
    DisposableEffect(preview.uri, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) releasePlayer() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { releasePlayer() }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            releasePlayer()
            lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    val windowContentVisible = LocalFloatingWindowContentVisible.current
    LaunchedEffect(playing, player, windowContentVisible) {
        if (!windowContentVisible) return@LaunchedEffect
        position = runCatching { player?.currentPosition ?: position }.getOrDefault(position).coerceIn(0, duration.coerceAtLeast(0))
        while (playing) {
            position = runCatching { player?.currentPosition ?: position }.getOrDefault(position).coerceIn(0, duration.coerceAtLeast(0))
            delay(250) // Only the visible, actively playing audio updates progress.
        }
    }
    LaunchedEffect(preparing) {
        if (preparing) {
            delay(15_000)
            if (preparing) { releasePlayer(); error = "音频准备超时，请检查读取权限后重试" }
        }
    }
    Column(Modifier.fillMaxSize().ownPanelScrollGestures(true).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("音频预览", style = MaterialTheme.typography.titleSmall)
        Text(if (preparing) "正在读取音频…" else if (playing) "正在播放" else if (ready && duration > 0 && position >= duration) "播放完毕" else if (ready) "已暂停" else "点击播放以读取音频",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow {
            TextButton(onClick = {
                if (playing) {
                    runCatching { player?.pause() }
                    playing = false
                    android.util.Log.d("PanelAudioPreview", "paused positionMs=$position")
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                } else play()
            }, enabled = !preparing) { Text(if (playing) "暂停" else if (error != null) "重试播放" else "播放") }
            TextButton(onClick = { releasePlayer(); error = null }, enabled = player != null) { Text("停止") }
        }
        Slider(
            value = (seekPosition ?: position.toFloat()).coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { seekPosition = it },
            onValueChangeFinished = {
                seekPosition?.toInt()?.let { target ->
                    runCatching { player?.seekTo(target); position = target }
                        .onFailure { error = "无法调整播放进度，请重试" }
                }
                seekPosition = null
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = ready && duration > 0
        )
        Text("${audioTime(position)} / ${audioTime(duration)}", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun audioTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}

@Composable
private fun VideoFilePreview(preview: BrowserPreview.Media) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var view by remember(preview) { mutableStateOf<VideoView?>(null) }
    var ready by remember(preview) { mutableStateOf(false) }
    var playing by remember(preview) { mutableStateOf(false) }
    var error by remember(preview) { mutableStateOf<String?>(null) }
    val windowContentVisible = LocalFloatingWindowContentVisible.current
    val latestContentVisible by rememberUpdatedState(windowContentVisible)
    var lastContentVisible by remember(preview) { mutableStateOf(true) }
    var restoreAfterCollapse by remember(preview) { mutableStateOf(false) }
    var collapsedPosition by remember(preview) { mutableIntStateOf(0) }
    var resumeAfterCollapse by remember(preview) { mutableStateOf(false) }
    fun stop() {
        restoreAfterCollapse = false; resumeAfterCollapse = false; collapsedPosition = 0
        view?.stopPlayback(); ready = false; playing = false
    }
    DisposableEffect(preview, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stop() }
        lifecycle.addObserver(observer)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { stop() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            stop(); lifecycle.removeObserver(observer); runCatching { context.unregisterReceiver(receiver) }
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(if (preview.video) "视频预览 · 播放能力取决于设备解码器" else "音频预览 · 播放能力取决于设备解码器")
        FlowRow {
            TextButton(onClick = {
                error = null
                try {
                    val player = view ?: return@TextButton
                    if (playing) { player.pause(); playing = false }
                    else if (ready) { player.start(); playing = true }
                    else { player.setVideoURI(preview.uri); player.start(); playing = true }
                } catch (e: Exception) { error = e.message ?: "无法播放，请使用其他应用打开"; stop() }
            }) { Text(if (playing) "暂停" else "播放") }
            TextButton(onClick = { stop() }) { Text("停止") }
            TextButton(onClick = { view?.let { it.seekTo(maxOf(0, it.currentPosition - 10000)) } }, enabled = ready) { Text("后退10秒") }
            TextButton(onClick = { view?.let { it.seekTo(minOf(it.duration, it.currentPosition + 10000)) } }, enabled = ready) { Text("前进10秒") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LaunchedEffect(windowContentVisible, view) {
            val player = view ?: return@LaunchedEffect
            if (lastContentVisible != windowContentVisible) {
                lastContentVisible = windowContentVisible
                if (!windowContentVisible) {
                    // SurfaceView may release its decoder when hidden. Snapshot before
                    // changing visibility, then prepare/seek explicitly on expansion.
                    if (!restoreAfterCollapse) {
                        restoreAfterCollapse = ready || playing
                        collapsedPosition = runCatching { player.currentPosition }.getOrDefault(0).coerceAtLeast(0)
                        resumeAfterCollapse = playing
                    }
                    runCatching { player.pause() }
                    ready = false; playing = false
                    player.clearFocus()
                } else if (restoreAfterCollapse) {
                    runCatching { player.setVideoURI(preview.uri) }.onFailure {
                        error = "视频恢复失败，请重新播放"; stop()
                    }
                }
            }
        }
        AndroidView(
            update = { player ->
                player.visibility = if (windowContentVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
            },
            factory = { ctx -> VideoView(ctx).apply {
                view = this
                setOnPreparedListener {
                    if (!latestContentVisible) {
                        // A pre-collapse asynchronous prepare may finish while hidden.
                        pause(); ready = false; playing = false
                    } else {
                        ready = true
                        if (restoreAfterCollapse) {
                            seekTo(collapsedPosition)
                            if (resumeAfterCollapse) start() else pause()
                            playing = resumeAfterCollapse
                            restoreAfterCollapse = false
                        }
                    }
                }
                setOnCompletionListener { playing = false }
                setOnErrorListener { _, _, _ ->
                    error = "设备无法解码此媒体，请使用其他应用打开"; stop(); true
                }
            } },
            onRelease = { released ->
                released.setOnPreparedListener(null)
                released.setOnCompletionListener(null)
                released.setOnErrorListener(null)
                runCatching { released.stopPlayback() }
                if (view === released) { view = null; ready = false; playing = false }
            },
            modifier = (if (preview.video) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(160.dp)).ownPanelScrollGestures(true)
        )
    }
}
