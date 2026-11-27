package com.example.globalminusone.ui.panel

import android.content.ComponentName
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.globalminusone.browser.ChromeSurfaceView
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures

/**
 * Place inside the movable floating window or panel workspace.
 * Keeps the output `Box` height 100% invariant before, during, and after connection or minimize/restore
 * so the inner canvas never shifts or overflows its locked viewport bounds.
 */
@Composable
internal fun EmbeddedAppPanel(
    component: ComponentName,
    modifier: Modifier = Modifier,
    backend: ChromeSurfaceView.Backend? = null,
    visible: Boolean = true,
    showBottomBackBar: Boolean = true,
    rootAuthorizer: ChromeSurfaceView.RootAuthorizer? = null,
    onHostInputAllowed: (Boolean) -> Unit = {},
    onExternalAuthorization: (Boolean) -> Unit = {},
    onBackHandlerChanged: ((() -> Boolean)?) -> Unit = {},
    minimized: Boolean = false,
    onConnectionChanged: (Boolean) -> Unit = {},
    onNativeViewChanged: (ChromeSurfaceView?) -> Unit = {}
) {
    var native by remember { mutableStateOf<ChromeSurfaceView?>(null) }
    var connected by remember { mutableStateOf(false) }
    var outputAttached by remember { mutableStateOf(false) }
    var externalOverlayNotice by remember(component) { mutableStateOf(false) }
    var dismissedExternalNotice by remember(component) { mutableStateOf<String?>(null) }
    var status by remember(component) { mutableStateOf("选择已授权后端，在面板内新开应用任务") }
    var connectionLabel by remember(component) { mutableStateOf("") }
    val hostInput by rememberUpdatedState(onHostInputAllowed)
    val authorization by rememberUpdatedState(onExternalAuthorization)
    val connectionChanged by rememberUpdatedState(onConnectionChanged)
    val nativeChanged by rememberUpdatedState(onNativeViewChanged)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(native, component, backend) {
        if (backend != null) native?.connectApp(component, backend) else native?.resumeAuthorizedApp(component)
    }
    LaunchedEffect(native, visible, minimized) {
        native?.setPresentationSuspended(minimized || !visible)
    }
    DisposableEffect(native, owner) {
        val view = native
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { view?.onHostStopped() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(native, connected, minimized, visible) {
        onBackHandlerChanged(if (connected && visible && !minimized) ({ native?.back(); true }) else null)
        onDispose { onBackHandlerChanged(null) }
    }
    Column(modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.White)) {
            AndroidView(
                factory = { context ->
                    val surfaceView = ChromeSurfaceView(context).also { view ->
                        view.setHostInputListener { hostInput(it) }
                        view.setExternalAuthorizationListener { authorization(it) }
                        view.setConnectionListener {
                            connected = it
                            outputAttached = view.hasOutput()
                            connectionLabel = if (it) view.activeBackend()?.name.orEmpty() else ""
                            connectionChanged(it)
                        }
                        view.setStatusListener {
                            outputAttached = view.hasOutput()
                            externalOverlayNotice = view.hasExternalOverlayNotice()
                            status = if (it.startsWith("display=") || it == "accepted") "应用已连接" else it
                        }
                        view.setRootAuthorizer(rootAuthorizer)
                        view.setPresentationSuspended(minimized || !visible)
                        nativeChanged(view)
                        native = view
                    }
                    // Jetpack Compose AndroidViewsHandler sets clipChildren=false on its native host;
                    // enforce hardware BOUNDS outline clipping on an explicit native FrameLayout wrapper.
                    FrameLayout(context).apply {
                        clipChildren = true
                        clipToPadding = true
                        outlineProvider = ViewOutlineProvider.BOUNDS
                        clipToOutline = true
                        tag = surfaceView
                        addView(surfaceView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    }
                },
                update = { container ->
                    val view = container.tag as? ChromeSurfaceView ?: return@AndroidView
                    view.setRootAuthorizer(rootAuthorizer)
                    view.setPresentationSuspended(minimized || !visible)
                },
                onRelease = { container ->
                    val view = container.tag as? ChromeSurfaceView ?: return@AndroidView
                    view.release()
                    view.setRootAuthorizer(null)
                    view.setStatusListener(null)
                    view.setConnectionListener(null)
                    view.setHostInputListener(null)
                    view.setExternalAuthorizationListener(null)
                    nativeChanged(null)
                    if (native === view) native = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .ownPanelScrollGestures(visible && !minimized, lockFromDown = true)
            )
            if (connected && visible && !minimized && !outputAttached) {
                Card(Modifier.align(Alignment.Center)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("恢复画面…")
                        TextButton(onClick = { native?.retryPresentation() }) { Text("重试显示") }
                    }
                }
            }
            if (!connected) {
                Card(Modifier.align(Alignment.Center).fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(component.packageName, style = MaterialTheme.typography.titleSmall)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth()) {
                            listOf(ChromeSurfaceView.Backend.SHIZUKU, ChromeSurfaceView.Backend.ROOT).forEach { mode ->
                                TextButton(
                                    onClick = { native?.connectApp(component, mode) },
                                    enabled = visible,
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text(if (mode == ChromeSurfaceView.Backend.ROOT) "Root" else "Shizuku")
                                }
                            }
                        }
                    }
                }
            }
            if (connected && status != "应用已连接" && !minimized && (!externalOverlayNotice || dismissedExternalNotice != status)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            status,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { if (externalOverlayNotice) dismissedExternalNotice = status else native?.retryPresentation() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text(if (externalOverlayNotice) "知道了" else "恢复显示") }
                    }
                }
            }
        }
        if (showBottomBackBar && connected) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { native?.back() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("返回") }
                Spacer(Modifier.weight(1f))
                Text(
                    connectionLabel,
                    Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
