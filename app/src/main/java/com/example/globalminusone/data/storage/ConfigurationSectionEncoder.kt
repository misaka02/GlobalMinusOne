package com.example.globalminusone.data.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import com.example.globalminusone.data.ClipboardRecord
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.TodoItem

/** Serialization only: the repository still owns transactions and state publication.
 * The final result remains a complete v5 JSON value, so SharedPreferences still rewrites
 * its XML file. Only sections from the last successful write are reusable.
 */
internal class ConfigurationSectionEncoder(
    private val codec: ConfigurationSectionCodec = DefaultConfigurationSectionCodec
) {
    private val owner = Any()
    private var revision = 0L
    private var accepted: Cache? = null

    fun prepare(state: GlobalMinusOneState): Prepared {
        val previous = accepted
        // Keep every other current or future model field in the core automatically.
        val coreState = state.copy(
            currentPageIndex = 0,
            todos = emptyList(),
            clipboardHistory = emptyList(),
            noteText = ""
        )
        val core = previous?.core?.takeIf { it.state == coreState } ?: Core(
            coreState,
            codec.encodeCore(coreState).map { (name, value) ->
                Field(name, ConfigurationCodec.json.encodeToString(String.serializer(), name), value)
            }.also { fields ->
                val names = fields.map { it.name }
                require(names.distinct().size == names.size && HOT_FIELDS.all { it in names }) {
                    "Configuration sections must retain the complete v5 field layout"
                }
            }
        )
        val todos = previous?.todos?.takeIf { it.source == state.todos }
            ?: Section(state.todos, codec.encodeTodos(state.todos))
        val clipboard = previous?.clipboard?.takeIf { it.source == state.clipboardHistory }
            ?: Section(state.clipboardHistory, codec.encodeClipboard(state.clipboardHistory))
        val note = previous?.note?.takeIf { it.source == state.noteText }
            ?: Section(state.noteText, codec.encodeNote(state.noteText))
        val candidate = Cache(core, todos, clipboard, note)
        val encoded = buildString {
            append('{')
            core.fields.forEachIndexed { index, field ->
                if (index > 0) append(',')
                append(field.encodedName)
                append(':')
                append(when (field.name) {
                    "currentPageIndex" -> state.currentPageIndex.toString()
                    "todos" -> todos.encoded
                    "clipboardHistory" -> clipboard.encoded
                    "noteText" -> note.encoded
                    else -> field.encoded
                })
            }
            append('}')
        }
        return Prepared(encoded, owner, revision) { accepted = candidate }
    }

    /** Call only after the prepared value was accepted by the physical store. */
    fun accept(prepared: Prepared) {
        prepared.accept(owner, revision)
        revision++
    }

    /** Explicit import, restore or reload invalidates both cached values and pending work. */
    fun clear() {
        accepted = null
        revision++
    }

    internal class Prepared internal constructor(
        val encoded: String,
        private val owner: Any,
        private val revision: Long,
        private val installCache: () -> Unit
    ) {
        internal fun accept(expectedOwner: Any, expectedRevision: Long) {
            require(owner === expectedOwner && revision == expectedRevision) {
                "Prepared configuration belongs to another encoder or an obsolete cache revision"
            }
            installCache()
        }
    }

    private data class Field(val name: String, val encodedName: String, val encoded: String)
    private data class Core(val state: GlobalMinusOneState, val fields: List<Field>)
    private data class Section<T>(val source: T, val encoded: String)
    private data class Cache(
        val core: Core,
        val todos: Section<List<TodoItem>>,
        val clipboard: Section<List<ClipboardRecord>>,
        val note: Section<String>
    )

    private companion object {
        val HOT_FIELDS = setOf("currentPageIndex", "todos", "clipboardHistory", "noteText")
    }
}

/** Small test seam for observing actual section serialization, without timings or disk mocks. */
internal interface ConfigurationSectionCodec {
    fun encodeCore(state: GlobalMinusOneState): List<Pair<String, String>>
    fun encodeTodos(todos: List<TodoItem>): String
    fun encodeClipboard(clipboard: List<ClipboardRecord>): String
    fun encodeNote(note: String): String
}

internal object DefaultConfigurationSectionCodec : ConfigurationSectionCodec {
    override fun encodeCore(state: GlobalMinusOneState): List<Pair<String, String>> =
        TopLevelFieldEncoder().let { encoder ->
            GlobalMinusOneState.serializer().serialize(encoder, state)
            encoder.fields
        }

    override fun encodeTodos(todos: List<TodoItem>): String =
        ConfigurationCodec.json.encodeToString(ListSerializer(TodoItem.serializer()), todos)

    override fun encodeClipboard(clipboard: List<ClipboardRecord>): String =
        ConfigurationCodec.json.encodeToString(ListSerializer(ClipboardRecord.serializer()), clipboard)

    override fun encodeNote(note: String): String =
        ConfigurationCodec.json.encodeToString(String.serializer(), note)
}

/** Collect only the generated model's top-level fields. Nested values stream through
 * the existing JSON encoder exactly once, without building a configuration JSON DOM.
 */
@OptIn(ExperimentalSerializationApi::class)
private class TopLevelFieldEncoder : AbstractEncoder() {
    override val serializersModule = ConfigurationCodec.json.serializersModule
    val fields = mutableListOf<Pair<String, String>>()
    private var fieldName: String? = null
    private var inlineDescriptor: SerialDescriptor? = null

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = this

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = true

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        check(fieldName == null) { "Previous configuration field was not encoded" }
        fieldName = descriptor.getElementName(index)
        return true
    }

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) = write(serializer, value)

    // AbstractEncoder's primitive element methods dispatch here after encodeElement.
    override fun encodeValue(value: Any) {
        when (value) {
            is Boolean -> write(Boolean.serializer(), value)
            is Byte -> write(Byte.serializer(), value)
            is Short -> write(Short.serializer(), value)
            is Int -> write(Int.serializer(), value)
            is Long -> write(Long.serializer(), value)
            is Float -> write(Float.serializer(), value)
            is Double -> write(Double.serializer(), value)
            is Char -> write(Char.serializer(), value)
            is String -> write(String.serializer(), value)
            else -> error("Unsupported configuration primitive: ${value.javaClass.name}")
        }
    }

    override fun encodeNull() = write(String.serializer().nullable, null)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        write(String.serializer(), enumDescriptor.getElementName(index))

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        inlineDescriptor = descriptor
        return this
    }

    private fun <T> write(serializer: SerializationStrategy<T>, value: T) {
        val name = checkNotNull(fieldName) { "Configuration value has no top-level field" }
        val inline = inlineDescriptor
        val strategy = if (inline == null) serializer else object : SerializationStrategy<T> {
            override val descriptor: SerialDescriptor = inline
            override fun serialize(encoder: Encoder, value: T) {
                // Preserve JSON's inline handling, including unsigned primitive representations.
                serializer.serialize(encoder.encodeInline(inline), value)
            }
        }
        fields += name to ConfigurationCodec.json.encodeToString(strategy, value)
        fieldName = null
        inlineDescriptor = null
    }
}
