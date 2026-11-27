package com.example.globalminusone.ui

import android.icu.text.AlphabeticIndex
import android.icu.text.Transliterator
import android.os.Build
import com.example.globalminusone.data.policy.PinyinIndexProvider
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Android ICU Pinyin lookup bridge kept in `ui/` so `data/` remains pure Kotlin.
 */
object PlatformPinyinProvider {
    private val charCache = ConcurrentHashMap<Char, String>()

    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Transliterator.getInstance("Han-Latin; NFD; [:Nonspacing Mark:] Remove") }.getOrNull()
        } else {
            null
        }
    }

    private val alphabeticIndex by lazy {
        runCatching {
            AlphabeticIndex<Any>(Locale.SIMPLIFIED_CHINESE)
                .addLabels(Locale.ENGLISH)
                .buildImmutableIndex()
        }.getOrNull()
    }

    private val diacriticRegex = Regex("\\p{M}+")

    val instance: PinyinIndexProvider by lazy {
        PinyinIndexProvider { ch ->
            if (ch !in '\u4E00'..'\u9FFF') return@PinyinIndexProvider null
            charCache[ch]?.let { cached -> return@PinyinIndexProvider cached.takeIf { it.isNotEmpty() } }
            val resolved = resolveCharPinyin(ch).orEmpty()
            charCache.putIfAbsent(ch, resolved)
            resolved.takeIf { it.isNotEmpty() }
        }
    }

    private fun resolveCharPinyin(ch: Char): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val tr = transliterator
            if (tr != null) {
                val latin = runCatching { tr.transliterate(ch.toString()) }.getOrNull()
                if (!latin.isNullOrBlank()) {
                    val norm = Normalizer.normalize(latin, Normalizer.Form.NFD)
                        .replace(diacriticRegex, "")
                        .lowercase(Locale.ROOT)
                    val ascii = buildString {
                        for (c in norm) {
                            if (c in 'a'..'z') append(c)
                        }
                    }
                    if (ascii.isNotEmpty()) return ascii
                }
            }
        }
        val bucket = alphabeticIndex?.let { idx ->
            runCatching { idx.getBucket(idx.getBucketIndex(ch.toString()))?.label }.getOrNull()
        } ?: return null
        val letter = Normalizer.normalize(bucket, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .lowercase(Locale.ROOT)
            .firstOrNull { it in 'a'..'z' }
        return letter?.toString()
    }
}
