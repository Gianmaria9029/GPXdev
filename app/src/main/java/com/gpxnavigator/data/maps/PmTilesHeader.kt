package com.gpxnavigator.data.maps

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InvalidMapException(message: String) : IOException(message)

data class PmTilesHeader(
    val metadataOffset: Long,
    val metadataLength: Int,
    val compression: Int,
    val tileType: Int,
    val minZoom: Int,
    val maxZoom: Int,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val rootOffset: Long = 0,
    val rootLength: Long = 0,
    val leafOffset: Long = 0,
    val leafLength: Long = 0,
    val tileDataOffset: Long = 0,
    val tileDataLength: Long = 0,
) {
    val isVector: Boolean get() = tileType == 1

    companion object {
        const val SIZE = 127
        const val MAX_METADATA_BYTES = 1_048_576

        fun read(file: RandomAccessFile): PmTilesHeader {
            if (file.length() < SIZE) throw InvalidMapException("File PMTiles vuoto o incompleto.")
            val bytes = ByteArray(SIZE)
            file.seek(0)
            file.readFully(bytes)
            if (String(bytes, 0, 7, Charsets.US_ASCII) != "PMTiles" || bytes[7].toInt() != 3) {
                throw InvalidMapException("Seleziona un archivio PMTiles versione 3.")
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun unsigned(index: Int) = bytes[index].toInt() and 255
            // Validate every section before any offset or allocation is used.
            for (offsetIndex in listOf(8, 24, 40, 56)) {
                val offset = buffer.getLong(offsetIndex)
                val length = buffer.getLong(offsetIndex + 8)
                if (length < 0 || offset < 0 || offset > file.length() || length > file.length() - offset ||
                    (length > 0 && offset < SIZE)
                ) throw InvalidMapException("Archivio PMTiles incompleto o danneggiato.")
            }
            if (buffer.getLong(16) <= 0 || buffer.getLong(64) <= 0 ||
                buffer.getLong(8) + buffer.getLong(16) > 16_384
            ) throw InvalidMapException("Archivio PMTiles privo di tile validi.")
            val metadataLength = buffer.getLong(32)
            if (metadataLength !in 0..MAX_METADATA_BYTES.toLong()) {
                throw InvalidMapException("Metadati della mappa troppo grandi.")
            }
            if (unsigned(97) !in 1..2 || unsigned(98) !in 1..2) {
                throw InvalidMapException("Compressione non supportata: usa PMTiles senza compressione o Gzip.")
            }
            if (unsigned(99) !in 1..4) {
                throw InvalidMapException("Tipo di mappa non supportato: usa MVT, PNG, JPEG o WebP.")
            }
            val minZoom = unsigned(100)
            val maxZoom = unsigned(101)
            val west = buffer.getInt(102) / 10_000_000.0
            val south = buffer.getInt(106) / 10_000_000.0
            val east = buffer.getInt(110) / 10_000_000.0
            val north = buffer.getInt(114) / 10_000_000.0
            if (minZoom > maxZoom || maxZoom > 24 || west !in -180.0..180.0 || east !in -180.0..180.0 ||
                south !in -90.0..90.0 || north !in -90.0..90.0 || west >= east || south >= north
            ) throw InvalidMapException("Estensione geografica o livelli di zoom non supportati.")
            return PmTilesHeader(buffer.getLong(24), metadataLength.toInt(), unsigned(97), unsigned(99),
                minZoom, maxZoom, west, south, east, north, buffer.getLong(8), buffer.getLong(16),
                buffer.getLong(40), buffer.getLong(48), buffer.getLong(56), buffer.getLong(64))
        }
    }
}
