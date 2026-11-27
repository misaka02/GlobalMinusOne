package com.example.globalminusone

import com.example.globalminusone.data.policy.EncodedImageStrategy
import com.example.globalminusone.data.policy.CARD_IMAGE_MAX_ENCODED_BYTES
import com.example.globalminusone.data.policy.classifyEncodedImageLength
import com.example.globalminusone.system.AppAndIntentLauncher
import com.example.globalminusone.ui.settings.SliderDragThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditV24128FixesTest {

    @Test
    fun sliderDragThrottleCoalescesBurstEventsAndResetsCleanly() {
        val throttle = SliderDragThrottle(intervalMs = 80L)
        assertTrue(throttle.shouldEmit(100L))
        assertFalse(throttle.shouldEmit(140L))
        assertFalse(throttle.shouldEmit(179L))
        assertTrue(throttle.shouldEmit(180L))
        throttle.reset()
        assertTrue(throttle.shouldEmit(185L))
    }

    @Test
    fun appShortcutPackageLockRejectsMismatchedDeclaredPackage() {
        assertTrue(AppAndIntentLauncher.isAppShortcutPackageAllowed("", "com.foo.app"))
        assertTrue(AppAndIntentLauncher.isAppShortcutPackageAllowed("com.foo.app", "com.foo.app"))
        assertFalse(AppAndIntentLauncher.isAppShortcutPackageAllowed("com.foo.app", "com.evil.other"))
    }

    @Test
    fun classifyEncodedImageLengthEnforcesMaxLimitOnKnownAndUnknownSizes() {
        assertEquals(EncodedImageStrategy.DIRECT_DECODE, classifyEncodedImageLength(1024L))
        assertEquals(
            EncodedImageStrategy.DIRECT_DECODE,
            classifyEncodedImageLength(CARD_IMAGE_MAX_ENCODED_BYTES.toLong())
        )
        assertEquals(EncodedImageStrategy.READ_BOUNDED, classifyEncodedImageLength(-1L))
        assertThrows(IllegalStateException::class.java) {
            classifyEncodedImageLength(CARD_IMAGE_MAX_ENCODED_BYTES.toLong() + 1L)
        }
    }
}
