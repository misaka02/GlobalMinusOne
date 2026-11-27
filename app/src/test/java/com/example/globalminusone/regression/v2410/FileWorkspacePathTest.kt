package com.example.globalminusone.regression.v2410

import com.example.globalminusone.system.file.BrowserEntry
import com.example.globalminusone.system.file.BrowserLocation
import com.example.globalminusone.ui.panel.file.FileWorkspacePathPolicy
import com.example.globalminusone.ui.panel.file.resolveFileWorkspacePath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FileWorkspacePathTest {
    private val root = BrowserLocation("样本", path = "/storage/emulated/0/Samples")
    private fun folder(name: String) = BrowserLocation(name, path = "${root.path}/$name")
    private fun entry(location: BrowserLocation, directory: Boolean = true, blocked: String? = null) = BrowserEntry(location, location.title, directory,
        if (directory) "vnd.android.document/directory" else "text/plain", null, null, blocked)

    @Test fun absoluteAddressStaysWithinTheSelectedRoot() {
        val plan = FileWorkspacePathPolicy.plan("${root.path}/A/B", listOf(root))
        assertEquals(listOf(root), plan.base)
        assertEquals(listOf("A", "B"), plan.segments)
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("/storage/emulated/0/Samples-other/A", listOf(root)) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("${root.path}/../private", listOf(root)) }
    }
    @Test fun relativeParentKeepsItsRealAncestorIdentity() {
        val a = folder("A"); val b = folder("B")
        val plan = FileWorkspacePathPolicy.plan("../sibling", listOf(root, a, b))
        assertEquals(listOf(root, a), plan.base)
        assertEquals(listOf("sibling"), plan.segments)
    }
    @Test fun normalizationCannotEscapeAnAuthorizedRoot() {
        assertEquals(listOf("B"), FileWorkspacePathPolicy.plan("A/../B/.", listOf(root)).segments)
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("../../B", listOf(root)) }
        assertThrows(IllegalArgumentException::class.java) { FileWorkspacePathPolicy.plan("", listOf(root)) }
    }
    @Test fun safAddressDoesNotConstructOrGuessDocumentIds() = runBlocking {
        val saf = BrowserLocation("工作", rootUri = "content://provider/tree/primary%3ADocuments", documentId = "primary:Documents")
        val opaqueChild = BrowserLocation("报告", saf.rootUri, documentId = "opaque-provider-generated-id")
        val resolved = resolveFileWorkspacePath(FileWorkspacePathPolicy.plan("/Documents/报告", listOf(saf))) {
            if (it.key == saf.key) listOf(entry(opaqueChild)) else emptyList()
        }
        assertEquals(opaqueChild, resolved.last())
    }
    @Test fun everyDirectoryIncludingTheFinalTargetIsValidated() = runBlocking {
        val a = folder("A"); val calls = mutableListOf<String>()
        val resolved = resolveFileWorkspacePath(FileWorkspacePathPolicy.plan("A", listOf(root))) {
            calls += it.key; if (it.key == root.key) listOf(entry(a)) else emptyList()
        }
        assertEquals(listOf(root.key, a.key), calls)
        assertEquals(listOf(root, a), resolved)
    }
    @Test fun deniedOrNonDirectoryMatchesCannotBecomeNavigationTargets() {
        val a = folder("A")
        assertThrows(IllegalStateException::class.java) { runBlocking {
            resolveFileWorkspacePath(FileWorkspacePathPolicy.plan("A", listOf(root))) { listOf(entry(a, blocked = "无权读取")) }
        } }
        assertThrows(IllegalStateException::class.java) { runBlocking {
            resolveFileWorkspacePath(FileWorkspacePathPolicy.plan("A", listOf(root))) { listOf(entry(a, directory = false)) }
        } }
    }
}
