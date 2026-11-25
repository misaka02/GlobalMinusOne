package com.example.globalminusone.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons as Icons
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import kotlinx.coroutines.delay

fun nextPhotoCubeIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1).mod(count)

/** Composition visibility is controlled by the panel owner; inactive cards release their bitmap. */
@Composable
fun PhotoCubeCard(item: ComponentItem, active: Boolean, onChoose: () -> Unit, onIntervalChange: (Int) -> Unit = {},
    playing: Boolean = active) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val running = active && resumed
    val uris = remember(item.photoCubeUris) { item.photoCubeUris.distinct() }
    var index by remember(item.id, uris) { mutableIntStateOf(0) }
    var interval by remember(item.photoCubeIntervalSeconds) { mutableIntStateOf(item.photoCubeIntervalSeconds.coerceIn(2, 60)) }
    val revisions by remember(context) { MinusOneRepository.getInstance(context).imageAccessRevisions }.collectAsState()
    val revisionKeys = remember(uris, revisions) { uris.map { revisions[it] ?: 0L } }
    val requestedUri = uris.getOrNull(index.coerceIn(0, (uris.size - 1).coerceAtLeast(0)))
    val currentRevision = requestedUri?.let { revisions[it] ?: 0L } ?: 0L
    val cachedBitmap = remember(requestedUri, currentRevision) {
        if (running && requestedUri != null) {
            com.example.globalminusone.system.peekCardImageBitmap(requestedUri, maxEdge = 1024, revision = currentRevision)
        } else null
    }
    var failed by remember(item.id, uris, revisionKeys) { mutableStateOf(emptySet<Int>()) }
    val frame by produceState<Pair<String, ImageBitmap>?>(
        initialValue = cachedBitmap?.let { requestedUri!! to it },
        running,
        uris,
        index,
        revisionKeys
    ) {
        if (!running || requestedUri == null) {
            value = null
            return@produceState
        }
        if (cachedBitmap != null) {
            value = requestedUri to cachedBitmap
            failed = failed - index
            return@produceState
        }
        if (value?.first !in uris) value = null
        val loaded = try {
            com.example.globalminusone.system.loadCardImageBitmap(
                context,
                requestedUri,
                maxEdge = 1024,
                revision = currentRevision
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
        value = loaded?.let { requestedUri to it }
        failed = if (loaded == null) failed + index else failed - index
    }
    val bitmap = cachedBitmap ?: frame?.second
    LaunchedEffect(running, playing, uris, index, interval, failed, bitmap) {
        if (running && playing && uris.size > 1 && failed.size < uris.size && (bitmap != null || index in failed)) {
            delay(if (index in failed) 500L else interval * 1000L)
            index = nextPhotoCubeIndex(index, uris.size)
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clickable {
            if (uris.isEmpty() || failed.size == uris.size) onChoose()
            else if (uris.size > 1) index = nextPhotoCubeIndex(index, uris.size)
        }, contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, "图片 ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (bitmap == null) Text(if (uris.isEmpty()) "点击选择多张图片" else if (failed.size == uris.size) "图片不可读取，点击重选" else if (index in failed) "正在跳过坏图" else "图片载入中",
                Modifier.padding(horizontal = 8.dp), fontSize = 11.sp)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = .45f))) {
                Icon(Icons.MoreVert, "图片魔方设置", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            PanelDropdownMenu(menuOpen, { menuOpen = false }) {
                com.example.globalminusone.ui.Android12WindowKeyGuard()

                PanelMenuItem(text = { Text("选择/替换图片") }, onClick = { menuOpen = false; onChoose() })
                listOf(2, 5, 10, 30).forEach { seconds ->
                    PanelMenuItem(text = { Text("${if (interval == seconds) "✓ " else ""}每 ${seconds} 秒切换") },
                        onClick = { interval = seconds; onIntervalChange(seconds); menuOpen = false })
                }
            }
        }
        if (uris.isNotEmpty()) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = .55f)).height(32.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}/${uris.size}", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { index = nextPhotoCubeIndex(index, uris.size) }, enabled = active && uris.size > 1,
                modifier = Modifier.size(32.dp)) { Icon(Icons.ArrowForward, "下一张图片", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
