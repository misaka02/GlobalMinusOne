package com.example.globalminusone.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small local vectors; no icon font, downloaded assets or extended icon dependency. */
internal object BrowserIcons {
    private fun outline(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f)
        .build()

    val Bookmark by lazy { outline("BrowserBookmark", "M6 3H18V21L12 17L6 21Z") }
    val Star by lazy { outline("BrowserStar", "M12 3L14.8 8.7L21 9.6L16.5 14L17.6 20.2L12 17.3L6.4 20.2L7.5 14L3 9.6L9.2 8.7Z") }
}
