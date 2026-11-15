package com.example.globalminusone

import com.example.globalminusone.browser.EmbeddedOutputTransition
import com.example.globalminusone.browser.EmbeddedSurfaceInput
import org.junit.Assert.*
import org.junit.Test

class EmbeddedOutputTransitionTest {
    private class Display : EmbeddedOutputTransition.Port<String?> {
        var hidden = false
        var output: String? = "host"
        var rejectHost = false
        var rejectParking = false
        var rejectUnhide = false
        override fun setTaskHidden(value: Boolean) {
            if (!value) check(output == "host") // Never expose a parked output as restored.
            if (!value && rejectUnhide) error("unhide rejected")
            hidden = value
        }
        override fun installOutput(value: String?) {
            if (value == "parking") check(hidden)
            if (value == "parking" && rejectParking) error("parking output rejected")
            if (value == "host" && rejectHost) error("host output rejected")
            output = value
        }
    }

    @Test fun outputSwitchOrdersVisibilityAndPropagatesFailures() {
        val display = Display()
        display.rejectParking = true
        try {
            EmbeddedOutputTransition.replace("parking", true, display)
            fail("a rejected parking output must retain the previous valid host output")
        } catch (_: IllegalStateException) { }
        assertTrue(display.hidden)
        assertEquals("host", display.output)
        display.rejectParking = false
        EmbeddedOutputTransition.replace("parking", true, display)
        assertTrue(display.hidden)
        assertEquals("parking", display.output)
        display.rejectHost = true
        try {
            EmbeddedOutputTransition.replace("host", false, display)
            fail("rejected output must not report a successful restore")
        } catch (_: IllegalStateException) { }
        assertTrue(display.hidden)
        assertEquals("parking", display.output)
        display.rejectHost = false
        display.rejectUnhide = true
        try {
            EmbeddedOutputTransition.replace("host", false, display)
            fail("unhide failure must propagate even after the host output was accepted")
        } catch (_: IllegalStateException) { }
        assertTrue(display.hidden)
        assertEquals("host", display.output)
        display.rejectUnhide = false
        EmbeddedOutputTransition.replace("host", false, display)
        assertFalse(display.hidden)
    }

    private class Buffer {
        var releases = 0
        fun release() { releases++ }
    }

    @Test fun unconsumedParcelOrRejectedEngineInputIsReleasedOnce() {
        for (parseFailure in listOf(true, false)) {
            val output = Buffer()
            try {
                EmbeddedSurfaceInput(output, Buffer::release).use { wire ->
                    if (parseFailure) error("malformed output parcel")
                    EmbeddedSurfaceInput(wire.transfer(), Buffer::release).use {
                        error("engine rejected dimensions before installing output")
                    }
                }
                fail("the rejected input must report failure")
            } catch (_: IllegalStateException) { }
            assertEquals(1, output.releases)
        }
    }

    @Test fun installedOutputRemainsOwnedWhenUnhideFails() {
        val output = Buffer()
        var installed: Buffer? = null
        val port = object : EmbeddedOutputTransition.Port<Buffer> {
            override fun installOutput(value: Buffer) { installed = value }
            override fun setTaskHidden(hidden: Boolean) {
                if (!hidden) error("unhide rejected after output installation")
            }
        }
        try {
            EmbeddedSurfaceInput(output, Buffer::release).use { wire ->
                val target = wire.transfer()
                EmbeddedSurfaceInput(target, Buffer::release).use { engine ->
                    try { EmbeddedOutputTransition.replace(target, false, port) }
                    finally { if (installed === target) engine.transfer() }
                }
            }
            fail("unhide rejection must not become a success reply")
        } catch (_: IllegalStateException) { }
        assertSame(output, installed)
        assertEquals(0, output.releases)
        installed!!.release() // Explicit engine close owns the accepted output.
        assertEquals(1, output.releases)
    }
}
