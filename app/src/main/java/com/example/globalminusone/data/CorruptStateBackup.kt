package com.example.globalminusone.data

/** Unparsed recovery evidence; this is deliberately never presented as a valid panel configuration. */
data class CorruptStateBackup(val preferenceKey: String, val raw: String)
