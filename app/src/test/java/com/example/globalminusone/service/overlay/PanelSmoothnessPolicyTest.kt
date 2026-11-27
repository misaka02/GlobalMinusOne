package com.example.globalminusone.service.overlay

import android.content.ComponentCallbacks2
import com.example.globalminusone.MemoryTrimAction
import com.example.globalminusone.resolveMemoryTrimAction
import com.example.globalminusone.system.AppAndIntentLauncher
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSmoothnessPolicyTest {

    @Test
    fun `resolveMemoryTrimAction ignores screen-off UI_HIDDEN and normal background trims`() {
        @Suppress("DEPRECATION")
        val ignoredLevels = listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE
        )
        ignoredLevels.forEach { level ->
            assertEquals("Expected NONE for trim level $level", MemoryTrimAction.NONE, resolveMemoryTrimAction(level))
        }
        assertEquals(
            MemoryTrimAction.SOFT_TRIM,
            resolveMemoryTrimAction(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        )
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.SOFT_TRIM,
            resolveMemoryTrimAction(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        )
    }

    @Test
    fun `trimMemoryCaches non-severe does not bump cacheGeneration`() {
        val before = AppAndIntentLauncher.cacheGeneration.value
        AppAndIntentLauncher.trimMemoryCaches(severe = false)
        assertEquals(before, AppAndIntentLauncher.cacheGeneration.value)

        AppAndIntentLauncher.trimMemoryCaches(severe = true)
        assertEquals(before + 1, AppAndIntentLauncher.cacheGeneration.value)
    }
}
