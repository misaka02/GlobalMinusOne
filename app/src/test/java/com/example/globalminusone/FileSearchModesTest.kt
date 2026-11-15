package com.example.globalminusone

import com.example.globalminusone.system.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FileSearchModesTest {
    private val root = BrowserLocation("root", path = "/synthetic")
    private val sub = BrowserLocation("sub", path = "/synthetic/sub")
    private fun file(name: String, parent: BrowserLocation = root, directory: Boolean = false) =
        BrowserEntry(BrowserLocation(name, path = "${parent.path}/$name"), name, directory, "text/plain", 1, null)
    @Test fun currentDirectoryAndRecursiveScopesHaveCorrectParentsAndQueries() = runBlocking {
        val reads = mutableListOf<String>()
        val list: suspend (BrowserLocation) -> List<BrowserEntry> = {
            reads += it.key
            if (it == root) listOf(file("sub", directory = true), file("A.TXT")) else listOf(file("中文.txt", sub))
        }
        val current = searchFileTree(root, "txt", FileCategory.DOCUMENT, null, { "read" }, {}, list, {}, false)
        assertEquals(listOf("A.TXT"), current.entries.map { it.name }); assertEquals(1, reads.size)
        reads.clear()
        val recursive = searchFileTree(root, "txt", FileCategory.DOCUMENT, null, { "read" }, {}, list, {}, true)
        assertEquals(2, recursive.entries.size); assertEquals(2, reads.size)
        assertEquals(sub.key, recursive.entries.last().parentLocation!!.key)
    }
    @Test fun regexUsesFindUnicodeAndLinearEngineWithBoundedInput() {
        assertTrue(FileSearchPattern("\\.txt$", true).matches("中文.TXT"))
        assertFalse(FileSearchPattern("^txt$", true).matches("notes.txt"))
        assertTrue(FileSearchPattern("[x]", false).matches("notes[x].txt"))
        assertFalse(FileSearchPattern("(a+)+$", true).matches("a".repeat(4000) + "!"))
        listOf("(?=a)", "(a)\\1", "[", "x".repeat(257), "a{129}", "(a{128}){128}").forEach {
            assertTrue("Accepted $it", runCatching { FileSearchPattern(it, true) }.isFailure)
        }
        assertTrue(runCatching { FileSearchPattern("a", true).matches("a".repeat(4097)) }.isFailure)
    }
    @Test fun invalidRegexDoesNotReadOrCheckProviderAndCancelDoesNotPublish() = runBlocking {
        var calls = 0
        val invalid = runCatching {
            searchFileTree(root, "[", FileCategory.ALL, null, { calls++; "read" }, { calls++ }, { calls++; emptyList() }, {}, true, true)
        }
        assertTrue(invalid.isFailure); assertEquals(0, calls)
        val canceled = runCatching {
            searchFileTree(root, "", FileCategory.ALL, null, { "read" }, {}, { calls++; emptyList() }, { throw CancellationException("cancel") })
        }
        assertTrue(canceled.exceptionOrNull() is CancellationException); assertEquals(0, calls)
    }
    @Test fun pagingRetainsModeAndRejectsChangedModeWithoutRereading() = runBlocking {
        var reads = 0
        val list: suspend (BrowserLocation) -> List<BrowserEntry> = { reads++; (1..1005).map { file("$it.txt") } }
        val first = searchFileTree(root, "\\.txt$", FileCategory.DOCUMENT, null, { "read" }, {}, list, {}, false, true)
        val cursor = first.continuation!!
        assertTrue(runCatching { searchFileTree(root, "\\.txt$", FileCategory.DOCUMENT, cursor, { "read" }, {}, list, {}, true, true) }.isFailure)
        val last = searchFileTree(root, "\\.txt$", FileCategory.DOCUMENT, cursor, { "read" }, {}, list, {}, false, true)
        assertEquals(5, last.entries.size); assertNull(last.continuation); assertEquals(1, reads)
    }
}
