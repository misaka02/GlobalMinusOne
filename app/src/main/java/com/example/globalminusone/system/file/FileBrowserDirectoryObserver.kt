package com.example.globalminusone.system.file

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.FileObserver
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

internal enum class DirectoryObservationSource { LOCAL, PROVIDER, NONE }
internal enum class DirectoryChangeSignal { LOCAL_READY, PROVIDER_READY, CHANGED, INVALIDATED, UNAVAILABLE }

/** Never turn a privileged Root session into an ordinary filesystem watcher. */
internal fun directoryObservationSource(location: BrowserLocation): DirectoryObservationSource = when {
    location.rootAccess || location.singleDocumentUri != null -> DirectoryObservationSource.NONE
    location.rootUri != null && location.documentId != null -> DirectoryObservationSource.PROVIDER
    location.path != null -> DirectoryObservationSource.LOCAL
    else -> DirectoryObservationSource.NONE
}

/** One non-recursive watch per visible pane. No polling, scans or new grants. */
internal fun observeBrowserDirectory(
    context: Context,
    location: BrowserLocation,
    checkAccess: (BrowserLocation) -> Unit
): Flow<DirectoryChangeSignal> = callbackFlow {
    var stop: (() -> Unit)? = null
    try {
        checkAccess(location)
        when (directoryObservationSource(location)) {
            DirectoryObservationSource.LOCAL -> {
                val directory = File(requireNotNull(location.path))
                check(directory.isDirectory && directory.canRead())
                @Suppress("DEPRECATION")
                val observer = object : FileObserver(directory.absolutePath,
                    FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
                        FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) {
                    override fun onEvent(event: Int, path: String?) {
                        if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                            trySend(DirectoryChangeSignal.INVALIDATED); close()
                        } else trySend(DirectoryChangeSignal.CHANGED)
                    }
                }
                stop = observer::stopWatching
                observer.startWatching()
                trySend(DirectoryChangeSignal.LOCAL_READY)
            }
            DirectoryObservationSource.PROVIDER -> {
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    Uri.parse(location.rootUri), requireNotNull(location.documentId))
                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) { trySend(DirectoryChangeSignal.CHANGED) }
                }
                stop = { context.contentResolver.unregisterContentObserver(observer) }
                // Provider notifications are optional. The UI explicitly keeps manual refresh available.
                context.contentResolver.registerContentObserver(children, true, observer)
                trySend(DirectoryChangeSignal.PROVIDER_READY)
            }
            DirectoryObservationSource.NONE -> trySend(DirectoryChangeSignal.UNAVAILABLE)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        trySend(DirectoryChangeSignal.UNAVAILABLE)
    }
    awaitClose { runCatching { stop?.invoke() } }
}.buffer(Channel.CONFLATED).flowOn(Dispatchers.IO)
