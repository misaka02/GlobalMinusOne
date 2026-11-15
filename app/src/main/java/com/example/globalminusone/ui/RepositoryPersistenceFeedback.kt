package com.example.globalminusone.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.globalminusone.data.MinusOneRepository

/** The repository owns failure state; only a visible, started host presents it. */
@Composable
internal fun RepositoryPersistenceFeedback(repository: MinusOneRepository, enabled: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hostState by lifecycle.currentStateFlow.collectAsState()
    val failure by repository.persistenceError.collectAsStateWithLifecycle()
    LaunchedEffect(failure, enabled, hostState) {
        if (enabled && hostState.isAtLeast(Lifecycle.State.STARTED)) {
            failure?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
}
