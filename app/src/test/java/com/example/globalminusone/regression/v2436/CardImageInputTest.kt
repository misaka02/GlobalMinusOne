package com.example.globalminusone.regression.v2436

import com.example.globalminusone.data.policy.cardImageTargetSize
import com.example.globalminusone.data.policy.readCardImageBytes
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class CardImageInputTest {
    @Test fun sampleSmallImagesWithoutEnlargingOrDistortingThem() {
        assertEquals(32 to 24, cardImageTargetSize(32, 24))
        assertEquals(1440 to 1080, cardImageTargetSize(8000, 6000))
        assertEquals(1 to 1440, cardImageTargetSize(1, Int.MAX_VALUE))
    }

    @Test fun invalidDimensionsFailBeforeAllocation() {
        assertTrue(runCatching { cardImageTargetSize(0, 1) }.isFailure)
        assertTrue(runCatching { cardImageTargetSize(10, -1) }.isFailure)
    }

    @Test fun providerShortReadsReturnEveryByteWithOnlyOneOpenStream() {
        val bytes = ByteArray(1001) { (it % 251).toByte() }
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 3))
        }
        assertArrayEquals(bytes, readCardImageBytes(stream, 2000))
    }

    @Test fun exactByteLimitIsAcceptedAndTheNextByteIsRejected() {
        assertArrayEquals(byteArrayOf(1, 2, 3), readCardImageBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
        assertTrue(runCatching { readCardImageBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3) }.isFailure)
    }

    @Test fun zeroReadCannotSpinForeverAndCallerRetainsStreamOwnership() {
        var closed = false
        val stream = object : InputStream() {
            var offset = 0
            override fun read(): Int = if (offset++ < 2) 7 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (offset < 2) 0 else -1
            override fun close() { closed = true }
        }
        assertArrayEquals(byteArrayOf(7, 7), readCardImageBytes(stream, 2))
        assertFalse(closed)
        stream.close()
        assertTrue(closed)
    }
}
