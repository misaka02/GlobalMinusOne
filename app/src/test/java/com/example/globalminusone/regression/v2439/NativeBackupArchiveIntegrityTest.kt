package com.example.globalminusone.regression.v2439

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.backup.DecodedNativeBackup
import com.example.globalminusone.data.backup.NativeBackupCodec
import com.example.globalminusone.data.backup.NativeBackupPayload
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class NativeBackupArchiveIntegrityTest {
    private val image = byteArrayOf(3, 7, 11, 19)
    private val assetName = "assets/${NativeBackupCodec.sha256(image)}.bin"
    private val payload = NativeBackupPayload(
        GlobalMinusOneState(pages = listOf(PageConfig(id = "page", name = "备份校验", components = listOf(
            ComponentItem(id = "image", type = ComponentType.SINGLE_IMAGE, imageUri = "file:///owned/image.png")
        )))),
        assets = mapOf("file:///owned/image.png" to assetName)
    )

    private fun archive(): ByteArray = ByteArrayOutputStream().also {
        NativeBackupCodec.write(it, DecodedNativeBackup(payload, mapOf(assetName to image)))
    }.toByteArray()

    private fun assertRejected(bytes: ByteArray) {
        assertTrue(runCatching { NativeBackupCodec.read(bytes.inputStream()) }.isFailure)
        assertTrue(runCatching {
            NativeBackupCodec.readCompatible(bytes.inputStream(), emptyMap(), emptyMap())
        }.isFailure)
    }

    @Test fun missingEndRecordIsRejectedEvenWhenEveryLocalEntryAndHashIsIntact() {
        val good = archive()
        val truncated = good.copyOf(good.size - 22)
        val localNames = mutableListOf<String>()
        ZipInputStream(truncated.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                localNames += entry.name
                zip.readBytes()
            }
        }
        assertEquals(setOf("manifest.json", "configuration.json", assetName), localNames.toSet())
        assertRejected(truncated)
    }

    @Test fun lastEightyBytesRemovedAndTruncatedLocalAssetAreRejected() {
        val good = archive()
        assertRejected(good.copyOf(good.size - 80))
        val assetHeader = good.indices.first { index ->
            index + assetName.length < good.size &&
                good.copyOfRange(index, index + assetName.length).contentEquals(assetName.toByteArray())
        } - 30
        // Cut inside the final local entry, before its CRC and the directory exist.
        assertRejected(good.copyOf(assetHeader + 30 + assetName.length + 1))
    }

    @Test fun damagedDirectorySignatureAndOutOfBoundsDirectoryOffsetAreRejected() {
        val good = archive()
        val eocd = good.size - 22
        fun unsigned32(offset: Int): Int = (0..3).fold(0) { value, i ->
            value or ((good[offset + i].toInt() and 255) shl (8 * i))
        }
        val directoryStart = unsigned32(eocd + 16)
        assertRejected(good.copyOf().apply { this[directoryStart] = 0 })
        assertRejected(good.copyOf().apply { repeat(4) { this[eocd + 16 + it] = 0x7f } })
    }

    @Test fun validZipWithCommentAndReorderedEntriesStillRestoresAllAssets() {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(archive().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        val rebuilt = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.setComment("瀹屾暣鐨勬棫澶囦唤锛沜omment can contain PK\u0005\u0006")
                entries.entries.reversed().forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val restored = NativeBackupCodec.readCompatible(rebuilt.inputStream(), emptyMap(), emptyMap())
        assertEquals(payload, restored.payload)
        assertArrayEquals(image, restored.assets.getValue(assetName))
    }
}
