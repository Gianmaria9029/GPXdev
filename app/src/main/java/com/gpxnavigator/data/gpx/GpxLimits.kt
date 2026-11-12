package com.gpxnavigator.data.gpx

import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream

/** Budgets keep imported files bounded while retaining the supported 50,000-point tracks. */
data class GpxLimits(
    val maxBytes: Long = 20L * 1024 * 1024,
    val maxPoints: Int = 100_000,
    val maxSegments: Int = 1_000,
    val maxTextChars: Int = 512,
    val maxDepth: Int = 64,
)

internal class LimitedGpxInputStream(input: InputStream, private val maxBytes: Long,
    private val checkActive: () -> Unit) : FilterInputStream(input) {
    private var used = 0L

    override fun read(): Int {
        checkActive()
        val value = `in`.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkActive()
        val count = `in`.read(buffer, offset, minOf(length.toLong(), maxBytes - used + 1).toInt())
        if (count > 0) account(count)
        return count
    }

    private fun account(count: Int) {
        used += count
        if (used > maxBytes) throw GpxParseException("GPX troppo grande. Limite: ${maxBytes / (1024 * 1024)} MiB.")
    }
}

internal fun copyLimitedGpx(input: InputStream, output: OutputStream, limits: GpxLimits,
    checkActive: () -> Unit) {
    val source = LimitedGpxInputStream(input, limits.maxBytes, checkActive)
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = source.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
}
