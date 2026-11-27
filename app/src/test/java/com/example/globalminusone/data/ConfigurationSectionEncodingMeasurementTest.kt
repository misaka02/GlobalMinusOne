package com.example.globalminusone.data

import java.io.File
import java.time.Instant
import kotlin.system.measureNanoTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.storage.ConfigurationSectionEncoder

/** Desktop encoding samples only: includes JSON assembly, excludes preferences and disk I/O. */
class ConfigurationSectionEncodingMeasurementTest {
    @Test fun compareWholeAndSectionEncodingWithEmptyAndWarmCaches() {
        val fixtures = listOf("small" to fixture(2, 12, 20, 4, 128),
            "combined" to fixture(10, 100, 2000, 25, 16384))
        val reports = fixtures.map { (name, state) ->
            val full = ConfigurationCodec.encode(state)
            assertEquals(state, ConfigurationCodec.decodeValidatedState(full))
            repeat(3) {
                assertEquals(full, ConfigurationCodec.encode(state))
                assertEquals(full, ConfigurationSectionEncoder().prepare(state).encoded)
            }
            val whole = (1..7).map {
                var encoded = ""
                val nanos = measureNanoTime { encoded = ConfigurationCodec.encode(state) }
                assertEquals(full, encoded)
                nanos
            }
            val emptyCache = (1..7).map {
                var encoded = ""
                val nanos = measureNanoTime { encoded = ConfigurationSectionEncoder().prepare(state).encoded }
                assertEquals(full, encoded)
                nanos
            }
            val encoder = ConfigurationSectionEncoder()
            encoder.accept(encoder.prepare(state))
            val warmCache = (1..7).map { index ->
                val changed = state.copy(todos = state.todos.mapIndexed { i, item ->
                    if (i == 0) item.copy(done = index % 2 == 1) else item
                })
                var encoded = ""
                val nanos = measureNanoTime {
                    val prepared = encoder.prepare(changed)
                    encoded = prepared.encoded
                    encoder.accept(prepared)
                }
                assertEquals(ConfigurationCodec.encode(changed), encoded)
                nanos
            }
            buildJsonObject {
                put("fixture", name)
                put("wireUtf8Bytes", full.toByteArray(Charsets.UTF_8).size)
                put("wholeEncodingNanoseconds", JsonArray(whole.map { JsonPrimitive(it) }))
                put("emptySectionCacheNanoseconds", JsonArray(emptyCache.map { JsonPrimitive(it) }))
                put("warmTodoChangeNanoseconds", JsonArray(warmCache.map { JsonPrimitive(it) }))
            }
        }
        val report = buildJsonObject {
            put("recordedAt", Instant.now().toString())
            put("javaVersion", System.getProperty("java.version"))
            put("os", System.getProperty("os.name"))
            put("callerThread", Thread.currentThread().name)
            put("scope", "desktop JVM serialization and assembly; no disk IO or Android timing")
            put("fixtures", JsonArray(reports))
        }
        val output = File("build/reports/storage-measurement/v5-section-encoding.json")
        output.parentFile.mkdirs()
        output.writeText(Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), report))
        assertTrue(output.isFile)
    }

    private fun fixture(pages: Int, cards: Int, todos: Int, clips: Int, textLength: Int): GlobalMinusOneState {
        val text = "abc/中文\n\\\"".repeat(textLength / 8 + 1).take(textLength)
        return GlobalMinusOneState(
            pages = List(pages) { page -> PageConfig(id = "page-$page", name = "Page $page",
                components = List(cards) { index -> ComponentItem(id = "card-$page-$index",
                    type = ComponentType.APP_ICON, title = "Synthetic $index", packageName = "synthetic.app$index",
                    x = index % 4 * 8, y = index / 4 * 8, w = 8, h = 8) }) },
            todos = List(todos) { TodoItem(id = "todo-$it", content = "Synthetic todo $it", createdAt = 1L) },
            clipboardHistory = List(clips) { ClipboardRecord(id = "clipboard-$it", text = "$it:$text", timestamp = 1L) },
            profiles = emptyList(), cards = emptyList(), quickActions = emptyList(), noteText = "Synthetic note"
        )
    }
}
