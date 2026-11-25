package com.example.globalminusone.regression.v2482

import com.example.globalminusone.screenassistant.ScreenCaptureHub
import com.example.globalminusone.system.boundedRootLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2482RootShizukuStabilityAndFastTieredCacheTest {

    @Test
    fun shizukuUserServiceVersionIsBumped() {
        assertTrue(ScreenCaptureHub.USER_SERVICE_VERSION >= 10)
    }

    @Test
    fun boundedRootLineHandlesLargePayloadsAndBoundaries() {
        val oneMiB = "A".repeat(1_048_576)
        assertEquals(oneMiB, boundedRootLine((oneMiB + "\n").byteInputStream(), 2 * 1024 * 1024))
        assertEquals("payload_line", boundedRootLine("payload_line\r\n".byteInputStream(), 64))
        assertEquals("abcd", boundedRootLine("abcd\n".byteInputStream(), 4))

        val overflow = runCatching { boundedRootLine("abcde\n".byteInputStream(), 4) }.exceptionOrNull()
        assertTrue(overflow?.message?.contains("Root协议行超限") == true)

        val eof = runCatching { boundedRootLine("unterminated".byteInputStream(), 32) }.exceptionOrNull()
        assertTrue(eof?.message?.contains("Root管道提前关闭") == true)
    }
}
