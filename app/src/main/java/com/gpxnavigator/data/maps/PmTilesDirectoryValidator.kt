package com.gpxnavigator.data.maps

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream

/** Validates the complete root and one reachable leaf path, without scanning GB of tile payloads. */
internal object PmTilesDirectoryValidator {
    private const val MAX_DIRECTORY_BYTES = 4 * 1024 * 1024
    private const val MAX_ENTRIES = 100_000
    private const val MAX_DEPTH = 8
    private data class Entry(val tileId: Long, val run: Long, val length: Long, val offset: Long)

    fun validate(file: RandomAccessFile, header: PmTilesHeader, checkActive: () -> Unit = {}) {
        val maxTileId = ((1L shl (2 * (header.maxZoom + 1))) - 1) / 3 - 1
        val visited = mutableSetOf<Pair<Long, Long>>()
        var offset = header.rootOffset
        var length = header.rootLength
        repeat(MAX_DEPTH) {
            checkActive()
            if (!visited.add(offset to length)) invalid("riferimenti circolari")
            val entries = decode(read(file, offset, length, header.compression, checkActive), maxTileId)
            entries.forEach { entry ->
                val sectionLength = if (entry.run == 0L) header.leafLength else header.tileDataLength
                if (entry.offset > sectionLength || entry.length > sectionLength - entry.offset)
                    invalid("riferimento fuori dalla sezione")
            }
            if (entries.any { it.run > 0 }) return
            val first = entries.first()
            offset = header.leafOffset + first.offset
            length = first.length
        }
        invalid("troppi livelli di directory")
    }

    private fun read(file: RandomAccessFile, offset: Long, length: Long, compression: Int,
        checkActive: () -> Unit): ByteArray {
        if (length !in 1..MAX_DIRECTORY_BYTES.toLong()) invalid("directory troppo grande o vuota")
        file.seek(offset)
        val compressed = ByteArray(length.toInt())
        file.readFully(compressed)
        if (compression == 1) return compressed
        try {
            return GZIPInputStream(compressed.inputStream()).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > MAX_DIRECTORY_BYTES - output.size()) invalid("directory decompressa troppo grande")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (exception: IOException) {
            throw InvalidMapException("Directory PMTiles non leggibile: ${exception.message}")
        }
    }

    private fun decode(bytes: ByteArray, maxTileId: Long): List<Entry> {
        var cursor = 0
        fun variable(): Long {
            var value = 0L
            for (shift in 0..56 step 7) {
                if (cursor >= bytes.size) invalid("directory troncata")
                val byte = bytes[cursor++].toInt() and 255
                value = value or ((byte and 127).toLong() shl shift)
                if (byte < 128) return value
            }
            invalid("numero fuori intervallo")
        }
        val size = variable()
        if (size !in 1..MAX_ENTRIES.toLong() || size > (bytes.size - cursor) / 4)
            invalid("numero di voci non valido")
        val count = size.toInt()
        val ids = LongArray(count)
        var previous = 0L
        repeat(count) { index ->
            val delta = variable()
            if ((index > 0 && delta == 0L) || delta > maxTileId - previous) invalid("ordine dei tile non valido")
            previous += delta
            ids[index] = previous
        }
        val runs = LongArray(count) { variable() }
        val lengths = LongArray(count) { variable().also { if (it == 0L) invalid("lunghezza nulla") } }
        val offsets = LongArray(count)
        repeat(count) { index ->
            val encoded = variable()
            offsets[index] = if (encoded > 0) encoded - 1 else {
                if (index == 0 || lengths[index - 1] > Long.MAX_VALUE - offsets[index - 1])
                    invalid("offset non valido")
                offsets[index - 1] + lengths[index - 1]
            }
        }
        if (cursor != bytes.size) invalid("dati inattesi nella directory")
        return List(count) { index ->
            val run = runs[index]
            if (run > 0 && run - 1 > maxTileId - ids[index]) invalid("intervallo tile non valido")
            if (run > 0 && index + 1 < count && run > ids[index + 1] - ids[index])
                invalid("intervalli tile sovrapposti")
            Entry(ids[index], run, lengths[index], offsets[index])
        }
    }

    private fun invalid(reason: String): Nothing = throw InvalidMapException("Directory PMTiles non valida: $reason.")
}
