package com.example.globalminusone.ui.panel

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.BrowserEntry
import com.example.globalminusone.system.FileProperties
import com.example.globalminusone.system.FileChecksums

/** Inspector-local draft state. File operations and analyzer caches remain with management. */
internal class FileInspectorState {
    val entry = mutableStateOf<BrowserEntry?>(null)
    val properties = mutableStateOf<FileProperties?>(null)
    val checksums = mutableStateOf<FileChecksums?>(null)
    val hashCompare = mutableStateOf("")
    val renameMode = mutableStateOf(false)
    val renameInput = mutableStateOf("")
    val deleteConfirm = mutableStateOf(false)

    fun open(value: BrowserEntry) {
        entry.value = value
        properties.value = null
        checksums.value = null
        hashCompare.value = ""
        renameMode.value = false
        renameInput.value = value.name
        deleteConfirm.value = false
    }

    fun toggleRename(name: String) {
        deleteConfirm.value = false
        renameInput.value = name
        renameMode.value = !renameMode.value
    }

    fun toggleDelete() {
        renameMode.value = false
        deleteConfirm.value = !deleteConfirm.value
    }
}
