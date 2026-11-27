package com.example.globalminusone.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.storage.ConfigurationSectionCodec
import com.example.globalminusone.data.storage.ConfigurationSectionEncoder
import com.example.globalminusone.data.storage.DefaultConfigurationSectionCodec

class ConfigurationSectionEncoderTest {
    @Test fun completeV5MatchesExistingBytesAndValidatedRoundTripForEmptyAndPopulatedSections() {
        val encoder = ConfigurationSectionEncoder()
        val fixtures = listOf(
            fixture().copy(todos = emptyList(), clipboardHistory = emptyList(), noteText = ""),
            fixture(),
            fixture().copy(favoriteApps = listOf("test.app"), activeProfileId = "chosen",
                appAppearanceOverrides = mapOf("test.app" to AppAppearanceOverride("Custom", "content://icon")),
                advancedConfig = AdvancedConfig(layoutLocked = true),
                cards = listOf(CardConfig(id = "legacy", type = CardType.TODO_NOTES)),
                quickActions = listOf(QuickActionItem(id = "action", title = "Action", intentUri = "action:screenshot")))
        )
        fixtures.forEach { state ->
            val prepared = encoder.prepare(state)
            assertEquals(ConfigurationCodec.encode(state), prepared.encoded)
            assertEquals(state, ConfigurationCodec.decodeValidatedState(prepared.encoded))
            encoder.accept(prepared)
        }
    }

    @Test fun escapedTextAndNestedMatchingFieldNamesAreNotReplaced() {
        val text = "中文\n\"todos\":[{\"noteText\":\"x\"}],\\路径\t\r\u0000😀"
        val state = fixture().copy(
            noteText = text,
            todos = listOf(TodoItem("todo", text, createdAt = 1)),
            clipboardHistory = listOf(ClipboardRecord("clip", text, timestamp = 1)),
            appAppearanceOverrides = mapOf(
                "todos" to AppAppearanceOverride(text),
                "clipboardHistory" to AppAppearanceOverride(text),
                "noteText" to AppAppearanceOverride(text)
            ),
            profiles = listOf(ConfigProfile("profile", "Nested", updatedAt = 1,
                stateJson = ConfigurationCodec.encode(fixture().copy(noteText = text))))
        )
        val encoder = ConfigurationSectionEncoder()
        val initial = encoder.prepare(state)
        assertEquals(ConfigurationCodec.encode(state), initial.encoded)
        assertEquals(state, ConfigurationCodec.decodeValidatedState(initial.encoded))
        encoder.accept(initial)
        assertEquals(ConfigurationCodec.encode(state.copy(noteText = "replacement")),
            encoder.prepare(state.copy(noteText = "replacement")).encoded)
    }

    @Test fun eachHotDomainReencodesOnlyItsChangedValue() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        var state = fixture()
        encoder.accept(encoder.prepare(state))
        state = state.copy(todos = state.todos.map { it.copy(done = true) })
        encoder.accept(encoder.prepare(state))
        assertEquals(listOf(1, 2, 1, 1), codec.counts())
        state = state.copy(clipboardHistory = state.clipboardHistory + ClipboardRecord("new", "new", timestamp = 2))
        encoder.accept(encoder.prepare(state))
        assertEquals(listOf(1, 2, 2, 1), codec.counts())
        state = state.copy(noteText = "changed")
        val prepared = encoder.prepare(state)
        assertEquals(ConfigurationCodec.encode(state), prepared.encoded)
        encoder.accept(prepared)
        assertEquals(listOf(1, 2, 2, 2), codec.counts())
    }

    @Test fun layoutAndOtherCoreChangesReuseAllHotSections() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val initial = fixture()
        encoder.accept(encoder.prepare(initial))
        val moved = initial.copy(pages = initial.pages.map { it.copy(name = "Renamed") })
        val prepared = encoder.prepare(moved)
        assertEquals(ConfigurationCodec.encode(moved), prepared.encoded)
        encoder.accept(prepared)
        assertEquals(listOf(2, 1, 1, 1), codec.counts())
        val otherCore = moved.copy(favoriteApps = listOf("new.app"))
        assertEquals(ConfigurationCodec.encode(otherCore), encoder.prepare(otherCore).encoded)
        assertEquals(listOf(3, 1, 1, 1), codec.counts())
    }

    @Test fun currentPageIndexChangeReusesCoreAndAllHotSections() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val initial = fixture().let { base ->
            base.copy(
                pages = base.pages + PageConfig(id = "page-2", name = "Page 2") + PageConfig(id = "page-3", name = "Page 3"),
                currentPageIndex = 0
            )
        }
        encoder.accept(encoder.prepare(initial))
        assertEquals(listOf(1, 1, 1, 1), codec.counts())

        for (targetPage in listOf(1, 2, 0)) {
            val paged = initial.copy(currentPageIndex = targetPage)
            val prepared = encoder.prepare(paged)
            assertEquals(ConfigurationCodec.encode(paged), prepared.encoded)
            assertEquals(paged, ConfigurationCodec.decodeValidatedState(prepared.encoded))
            encoder.accept(prepared)
            assertEquals(listOf(1, 1, 1, 1), codec.counts())
        }
    }

    @Test fun unchangedEqualValuesNeedNoSectionReencoding() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val state = fixture()
        encoder.accept(encoder.prepare(state))
        val equal = state.copy(todos = state.todos.map { it.copy() },
            clipboardHistory = state.clipboardHistory.map { it.copy() }, pages = state.pages.map { it.copy() })
        assertEquals(ConfigurationCodec.encode(equal), encoder.prepare(equal).encoded)
        assertEquals(listOf(1, 1, 1, 1), codec.counts())
    }

    @Test fun preparationWithoutSuccessfulWriteDoesNotPopulateOrAdvanceCache() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val state = fixture()
        encoder.prepare(state) // The physical write was rejected.
        val retry = encoder.prepare(state)
        assertEquals(listOf(2, 2, 2, 2), codec.counts())
        encoder.accept(retry)
        val changed = state.copy(noteText = "rejected")
        encoder.prepare(changed) // Another rejected write must not replace the accepted note.
        encoder.prepare(changed)
        assertEquals(listOf(2, 2, 2, 4), codec.counts())
        assertEquals(ConfigurationCodec.encode(state), encoder.prepare(state).encoded)
        assertEquals(listOf(2, 2, 2, 4), codec.counts())
    }

    @Test fun encodingFailurePreservesEveryPreviouslySuccessfulSectionForRetry() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val initial = fixture()
        encoder.accept(encoder.prepare(initial))
        val changed = initial.copy(favoriteApps = listOf("changed.app"),
            todos = emptyList(), clipboardHistory = emptyList(), noteText = "changed")
        codec.failNote = true
        assertTrue(runCatching { encoder.prepare(changed) }.isFailure)
        assertEquals(listOf(2, 2, 2, 2), codec.counts())
        assertEquals(ConfigurationCodec.encode(initial), encoder.prepare(initial).encoded)
        assertEquals(listOf(2, 2, 2, 2), codec.counts())
        codec.failNote = false
        val retry = encoder.prepare(changed)
        assertEquals(ConfigurationCodec.encode(changed), retry.encoded)
        encoder.accept(retry)
        assertEquals(listOf(3, 3, 3, 3), codec.counts())
    }

    @Test fun stalePreparedValueCannotReplaceNewerSuccessfulCacheAndWrongOwnerIsRejected() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val state = fixture()
        encoder.accept(encoder.prepare(state))
        val stale = encoder.prepare(state.copy(noteText = "older"))
        val current = state.copy(noteText = "newer")
        val accepted = encoder.prepare(current)
        val other = ConfigurationSectionEncoder()
        assertTrue(runCatching { other.accept(accepted) }.exceptionOrNull() is IllegalArgumentException)
        encoder.accept(accepted)
        assertTrue(runCatching { encoder.accept(stale) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { encoder.accept(accepted) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(ConfigurationCodec.encode(current), encoder.prepare(current).encoded)
        assertEquals(listOf(1, 1, 1, 3), codec.counts())
    }

    @Test fun clearInvalidatesPreparedValuesAndForcesFreshEncodingAfterImportOrRestore() {
        val codec = CountingCodec()
        val encoder = ConfigurationSectionEncoder(codec)
        val state = fixture()
        encoder.accept(encoder.prepare(state))
        val pending = encoder.prepare(state)
        encoder.clear()
        assertTrue(runCatching { encoder.accept(pending) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(ConfigurationCodec.encode(state), encoder.prepare(state).encoded)
        assertEquals(listOf(2, 2, 2, 2), codec.counts())
    }

    @Test fun coreUsesWholeGeneratedModelSchemaIncludingAllUnlistedFields() {
        val state = fixture()
        val expectedFields = ConfigurationCodec.json.parseToJsonElement(ConfigurationCodec.encode(state)).jsonObject.keys
        val core = DefaultConfigurationSectionCodec.encodeCore(
            state.copy(todos = emptyList(), clipboardHistory = emptyList(), noteText = ""))
        assertEquals(expectedFields.toList(), core.map { it.first })
        val encoded = ConfigurationSectionEncoder().prepare(state).encoded
        assertEquals(expectedFields.toList(), ConfigurationCodec.json.parseToJsonElement(encoded).jsonObject.keys.toList())
        assertEquals(ConfigurationCodec.encode(state), encoded)
    }

    @Test fun streamedCoreRetainsDefaultPrimitiveValuesAndNullableSelectionInSchemaOrder() {
        listOf(null, "note\n\"selected\"\\").forEach { selection ->
            val state = fixture().copy(schemaVersion = CURRENT_STATE_SCHEMA_VERSION,
                editMode = selection != null, selectedComponentId = selection,
                activeProfileId = "profile\t\"chosen\"", favoriteApps = listOf("test.app"))
            val prepared = ConfigurationSectionEncoder().prepare(state)
            assertEquals(ConfigurationCodec.encode(state), prepared.encoded)
            val fields = DefaultConfigurationSectionCodec.encodeCore(state).toMap()
            assertEquals(state.schemaVersion.toString(), fields["schemaVersion"])
            assertEquals(state.editMode.toString(), fields["editMode"])
            assertEquals(ConfigurationCodec.json.parseToJsonElement(ConfigurationCodec.encode(state)).jsonObject["selectedComponentId"].toString(),
                fields["selectedComponentId"])
        }
    }

    private fun fixture() = GlobalMinusOneState(
        pages = listOf(PageConfig(id = "page", name = "Page", components = listOf(
            ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(
                ComponentItem(id = "note", type = ComponentType.NOTE_CARD, title = "Nested")))))),
        profiles = emptyList(), cards = emptyList(), quickActions = emptyList(),
        todos = listOf(TodoItem("todo", "Todo", createdAt = 1)),
        clipboardHistory = listOf(ClipboardRecord("clip", "Clipboard", timestamp = 1)), noteText = "Note"
    )

    private class CountingCodec : ConfigurationSectionCodec {
        private var coreCalls = 0
        private var todoCalls = 0
        private var clipboardCalls = 0
        private var noteCalls = 0
        var failNote = false
        fun counts() = listOf(coreCalls, todoCalls, clipboardCalls, noteCalls)
        override fun encodeCore(state: GlobalMinusOneState): List<Pair<String, String>> {
            coreCalls++
            return DefaultConfigurationSectionCodec.encodeCore(state)
        }
        override fun encodeTodos(todos: List<TodoItem>): String {
            todoCalls++
            return DefaultConfigurationSectionCodec.encodeTodos(todos)
        }
        override fun encodeClipboard(clipboard: List<ClipboardRecord>): String {
            clipboardCalls++
            return DefaultConfigurationSectionCodec.encodeClipboard(clipboard)
        }
        override fun encodeNote(note: String): String {
            noteCalls++
            check(!failNote) { "Synthetic note encoding failure" }
            return DefaultConfigurationSectionCodec.encodeNote(note)
        }
    }
}
