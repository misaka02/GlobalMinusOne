package com.example.globalminusone.ui.panel.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.example.globalminusone.data.FileBrowserConfig
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Lightweight, cold configuration projection; no independent scope or repository writes. */
internal class FileBrowserConfigStream(private val source: StateFlow<GlobalMinusOneState>) {
    val current: FileBrowserConfig get() = source.value.fileBrowserConfig
    val values: Flow<FileBrowserConfig> = source.map { it.fileBrowserConfig }.distinctUntilChanged()
}

@Composable
internal fun observeFileBrowserConfig(repository: MinusOneRepository): FileBrowserConfig = key(repository) {
    val stream = remember(repository) { FileBrowserConfigStream(repository.state) }
    stream.values.collectAsState(initial = stream.current).value
}
