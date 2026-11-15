package com.example.globalminusone

import com.example.globalminusone.system.BrowserLocation
import com.example.globalminusone.system.normalizePublicStoragePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PublicStorageKeyTest {
    @Test fun sdcardUsesTheSuppliedProfileRootWithoutCollapsingUsers() {
        val owner = normalizePublicStoragePath("/sdcard/Documents/a.txt", "/storage/emulated/0")
        val work = normalizePublicStoragePath("/sdcard/Documents/a.txt", "/storage/emulated/10")
        assertEquals("/storage/emulated/0/Documents/a.txt", owner)
        assertEquals("/storage/emulated/10/Documents/a.txt", work)
        assertNotEquals(owner, work)
        assertNotEquals(BrowserLocation("owner", path = owner).key, BrowserLocation("work", path = work).key)
    }

    @Test fun rootBoundaryAndUnknownProfileStayLiteral() {
        assertEquals("/storage/emulated/10", normalizePublicStoragePath("/sdcard", "/storage/emulated/10"))
        assertEquals("/sdcarded/file", normalizePublicStoragePath("/sdcarded/file", "/storage/emulated/10"))
        assertEquals("/sdcard/file", normalizePublicStoragePath("/sdcard/file", null))
        assertEquals("/sdcard/file", normalizePublicStoragePath("/sdcard/file", "/storage/self/primary"))
    }
}
