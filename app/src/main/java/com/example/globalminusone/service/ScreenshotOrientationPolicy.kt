package com.example.globalminusone.service

internal fun screenshotOrientationMatches(bitmapWidth: Int, bitmapHeight: Int, displayWidth: Int, displayHeight: Int): Boolean {
    if (minOf(bitmapWidth, bitmapHeight, displayWidth, displayHeight) <= 0) return false
    return (bitmapWidth > bitmapHeight) == (displayWidth > displayHeight)
}
