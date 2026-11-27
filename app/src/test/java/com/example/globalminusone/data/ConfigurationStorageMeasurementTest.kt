package com.example.globalminusone.data

import android.content.SharedPreferences
import com.example.globalminusone.MemoryPreferences
import java.io.File
import java.time.Instant
import kotlin.system.measureNanoTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec

/** A bounded JVM baseline, not Android disk I/O or a frame-time benchmark. */
class ConfigurationStorageMeasurementTest {
    @Test fun measureSyntheticV5EncodingAndRepositorySubmissions() {
        val fixtures = listOf(
            Fixture("small", pages = 2, cardsPerPage = 12, todos = 20, clipboard = 4, textLength = 128),
            Fixture("large-canvas", pages = 10, cardsPerPage = 100, todos = 100, clipboard = 4, textLength = 128),
            Fixture("long-content", pages = 2, cardsPerPage = 12, todos = 2000, clipboard = 25, textLength = 16384),
            Fixture("combined", pages = 10, cardsPerPage = 100, todos = 2000, clipboard = 25, textLength = 16384)
        )
        val measurements = fixtures.map { fixture ->
            val state = fixture.state()
            val raw = ConfigurationCodec.encode(state)
            // Exercise the actual v5 validator, rather than timing an unsupported fixture.
            assertEquals(state, ConfigurationCodec.decodeValidatedState(raw))
            repeat(3) { assertEquals(raw.length, ConfigurationCodec.encode(state).length) }
            val encodingSamples = (1..7).map {
                lateinit var encoded: String
                val nanos = measureNanoTime { encoded = ConfigurationCodec.encode(state) }
                assertEquals(raw, encoded)
                nanos
            }
            FixtureMeasurement(
                fixture = fixture, payloadUtf8Bytes = raw.toByteArray(Charsets.UTF_8).size,
                encodingNanoseconds = encodingSamples,
                operations = listOf(
                    measureOperation("toggle-todo", raw, expectedSubmissions = 1) {
                        it.toggleTodo("todo-0")
                    },
                    measureOperation("append-clipboard", raw, expectedSubmissions = 1) {
                        it.recordClipboardText("synthetic additional clipboard record")
                        assertTrue(it.state.value.clipboardHistory.size <= 25)
                    },
                    measureOperation("unchanged-note", raw, expectedSubmissions = 0) {
                        it.updateNoteText(it.state.value.noteText)
                    },
                    measureOperation("missing-todo", raw, expectedSubmissions = 0) {
                        it.toggleTodo("nonexistent-todo")
                    },
                    measureOperation("20-layout-previews", raw, expectedSubmissions = 0) {
                        previewLayout(it)
                        assertTrue(it.state.value.editMode)
                    },
                    measureOperation("20-previews-then-save", raw, expectedSubmissions = 1) {
                        previewLayout(it)
                        assertTrue(it.finishPanelEdit(save = true))
                        assertEquals(20, it.state.value.pages.first().components.first().y)
                    },
                    measureOperation("20-previews-then-discard", raw, expectedSubmissions = 0) {
                        val pages = it.state.value.pages
                        previewLayout(it)
                        assertTrue(it.finishPanelEdit(save = false))
                        assertEquals(pages, it.state.value.pages)
                    },
                    measureOperation("20-previews-then-live-todo", raw, expectedSubmissions = 1) {
                        previewLayout(it)
                        it.toggleTodo("todo-0")
                        assertTrue(it.state.value.editMode)
                    }
                )
            )
        }
        val report = MeasurementReport(
            recordedAt = Instant.now().toString(), javaVersion = System.getProperty("java.version"),
            vm = System.getProperty("java.vm.name"), os = System.getProperty("os.name"),
            callerThread = Thread.currentThread().name, fixtures = measurements
        )
        val destination = File("build/reports/storage-measurement/v5-jvm-baseline.json")
        destination.parentFile.mkdirs()
        destination.writeText(Json { prettyPrint = true }.encodeToString(report), Charsets.UTF_8)
        assertTrue(destination.isFile)
    }

    private fun previewLayout(repository: MinusOneRepository) {
        assertTrue(repository.setEditMode(true))
        repeat(20) {
            assertTrue(repository.updateComponent(0, "card-0-0") { item -> item.copy(y = item.y + 1) })
        }
        assertEquals(20, repository.state.value.pages.first().components.first().y)
    }

    private fun measureOperation(
        name: String, raw: String, expectedSubmissions: Int, operation: (MinusOneRepository) -> Unit
    ): OperationMeasurement {
        val preferences = CountingPreferences(raw)
        val repository = MinusOneRepository(preferences)
        // Prime the repository's existing last-accepted-state cache before measurement.
        repository.toggleTodo("todo-0")
        assertEquals(1, preferences.submitted.size)
        val baseline = repository.state.value
        preferences.submitted.clear()
        val nanos = measureNanoTime { operation(repository) }
        assertNull(repository.lastPersistenceFailure)
        assertEquals("$name submissions", expectedSubmissions, preferences.submitted.size)
        val finalState = ConfigurationCodec.decodeValidatedState(
            requireNotNull(preferences.disk.getString(STATE_KEY, null))
        )
        if (name == "20-previews-then-save") {
            assertEquals(repository.state.value.pages, finalState.pages)
        } else {
            // Non-layout edits and draft previews cannot rewrite the saved geometry.
            assertEquals(baseline.pages, finalState.pages)
        }
        if (name == "toggle-todo" || name == "20-previews-then-live-todo") {
            assertEquals(!baseline.todos.first().done, finalState.todos.first().done)
        }
        if (name == "append-clipboard") assertEquals(repository.state.value.clipboardHistory, finalState.clipboardHistory)
        return OperationMeasurement(
            operation = name, stateValueSubmissions = preferences.submitted.size,
            submittedUtf8Bytes = preferences.submitted.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
            repositoryCallNanoseconds = nanos
        )
    }

    private class CountingPreferences(
        raw: String, val disk: MemoryPreferences = MemoryPreferences(mapOf(STATE_KEY to raw))
    ) : SharedPreferences by disk {
        val submitted = mutableListOf<String>()
        override fun edit(): SharedPreferences.Editor {
            val delegate = disk.edit()
            var stateValue: String? = null
            return object : SharedPreferences.Editor by delegate {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    if (key == STATE_KEY) stateValue = value
                    delegate.putString(key, value)
                    return this
                }
                override fun apply() {
                    stateValue?.let(submitted::add)
                    delegate.apply()
                }
            }
        }
    }

    @Serializable private data class Fixture(
        val name: String, val pages: Int, val cardsPerPage: Int, val todos: Int, val clipboard: Int, val textLength: Int
    ) {
        fun state(): GlobalMinusOneState {
            val pattern = "abc/中文\n\\\""
            val longText = pattern.repeat((textLength + pattern.length - 1) / pattern.length).take(textLength)
            return GlobalMinusOneState(
                pages = List(pages) { page -> PageConfig(
                    id = "page-$page", name = "Page $page",
                    components = List(cardsPerPage) { index -> ComponentItem(
                        id = "card-$page-$index", type = ComponentType.APP_ICON,
                        title = "Synthetic $index", packageName = "synthetic.app$index", x = index % 4 * 8,
                        y = index / 4 * 8, w = 8, h = 8
                    ) }
                ) },
                todos = List(todos) { TodoItem(id = "todo-$it", content = "Synthetic todo $it", createdAt = 1L) },
                clipboardHistory = List(clipboard) {
                    ClipboardRecord(id = "clipboard-$it", text = "$it:$longText", timestamp = 1L)
                },
                profiles = emptyList(), cards = emptyList(), quickActions = emptyList(), noteText = "Synthetic note"
            )
        }
    }

    @Serializable private data class FixtureMeasurement(
        val fixture: Fixture, val payloadUtf8Bytes: Int,
        val encodingNanoseconds: List<Long>, val operations: List<OperationMeasurement>
    )
    @Serializable private data class OperationMeasurement(
        val operation: String, val stateValueSubmissions: Int,
        val submittedUtf8Bytes: Long, val repositoryCallNanoseconds: Long
    )
    @Serializable private data class MeasurementReport(
        val recordedAt: String, val javaVersion: String, val vm: String, val os: String,
        val callerThread: String, val fixtures: List<FixtureMeasurement>
    )

    private companion object { const val STATE_KEY = "state_json_v5" }
}
