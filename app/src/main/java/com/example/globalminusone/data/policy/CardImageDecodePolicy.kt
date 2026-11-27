package com.example.globalminusone.data.policy

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

internal const val CARD_IMAGE_MAX_ENCODED_BYTES = 32 * 1024 * 1024
internal const val CARD_IMAGE_MAX_EDGE = 1440

internal enum class EncodedImageStrategy { DIRECT_DECODE, READ_BOUNDED }

internal fun classifyEncodedImageLength(
    lengthBytes: Long,
    limit: Int = CARD_IMAGE_MAX_ENCODED_BYTES
): EncodedImageStrategy {
    if (lengthBytes > limit.toLong()) error("图片文件超过 32 MiB，请选择较小图片")
    return if (lengthBytes >= 0L) EncodedImageStrategy.DIRECT_DECODE else EncodedImageStrategy.READ_BOUNDED
}

internal fun cardImageCacheKey(rawUri: String, maxEdge: Int, revision: Long): String =
    "$revision|$maxEdge|${rawUri.trim()}"

/** Cap decoded allocation without enlarging small images or changing their aspect ratio. */
internal fun cardImageTargetSize(width: Int, height: Int, maxEdge: Int = CARD_IMAGE_MAX_EDGE): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxEdge > 0) { "图片尺寸无效" }
    val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(width, height))
    return (width * ratio).roundToInt().coerceAtLeast(1) to
        (height * ratio).roundToInt().coerceAtLeast(1)
}

/** One bounded read also supports providers that return pipes or short reads. Caller owns the stream. */
internal fun readCardImageBytes(input: InputStream, limit: Int = CARD_IMAGE_MAX_ENCODED_BYTES): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - output.size() + 1).toInt())
        if (count < 0) return output.toByteArray()
        if (count == 0) {
            val single = input.read()
            if (single < 0) return output.toByteArray()
            check(output.size() < limit) { "图片文件超过 32 MiB，请选择较小图片" }
            output.write(single)
        } else {
            check(count <= limit - output.size()) { "图片文件超过 32 MiB，请选择较小图片" }
            output.write(buffer, 0, count)
        }
    }
}
