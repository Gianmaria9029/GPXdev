package com.gpxnavigator.data.maps

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PmTilesDirectoryValidatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val tile = byteArrayOf(1, 0, 1, 1, 1)
    private val leaf = byteArrayOf(1, 0, 0, 5, 1)

    private fun archive(root: ByteArray, leaves: ByteArray = byteArrayOf(), compression: Int = 1): ByteArray {
        val bytes = ByteArray(127 + root.size + 2 + leaves.size + 1)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[7] = 3
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.putLong(8, 127).putLong(16, root.size.toLong())
        b.putLong(24, 127L + root.size).putLong(32, 2)
        b.putLong(40, 129L + root.size).putLong(48, leaves.size.toLong())
        b.putLong(56, 129L + root.size + leaves.size).putLong(64, 1)
        bytes[97] = compression.toByte()
        bytes[98] = 1
        bytes[99] = 1
        b.putInt(102, -10_000_000).putInt(106, -10_000_000)
        b.putInt(110, 10_000_000).putInt(114, 10_000_000)
        root.copyInto(bytes, 127)
        "{}".toByteArray().copyInto(bytes, 127 + root.size)
        leaves.copyInto(bytes, 129 + root.size)
        bytes[bytes.lastIndex] = 1
        return bytes
    }

    private fun validate(bytes: ByteArray) {
        val path = temporary.newFile()
        path.writeBytes(bytes)
        RandomAccessFile(path, "r").use { PmTilesDirectoryValidator.validate(it, PmTilesHeader.read(it)) }
    }

    @Test fun acceptsOfficialFirenzeAndUncompressedRootAndLeafDirectories() {
        validate(javaClass.getResourceAsStream("/firenze.pmtiles")!!.use { it.readBytes() })
        validate(archive(tile))
        validate(archive(leaf, tile))
    }

    @Test fun rejectsCorruptGzipRootEvenWithValidHeaderAndMetadata() {
        val bytes = javaClass.getResourceAsStream("/firenze.pmtiles")!!.use { it.readBytes() }
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = b.getLong(8).toInt()
        java.util.Arrays.fill(bytes, offset, offset + b.getLong(16).toInt(), 0.toByte())
        assertThrows(InvalidMapException::class.java) { validate(bytes) }
    }

    @Test fun rejectsEmptyTruncatedOverflowingAndTrailingDirectoryData() {
        for (root in listOf(byteArrayOf(0), byteArrayOf(1, 0, 1), ByteArray(10) { 0x80.toByte() }, tile + 0.toByte())) {
            assertThrows(InvalidMapException::class.java) { validate(archive(root)) }
        }
    }

    @Test fun rejectsReferencesOutsideTileAndLeafSectionsAndCircularLeaves() {
        for (bytes in listOf(archive(byteArrayOf(1, 0, 1, 2, 1)), archive(leaf),
            archive(leaf, byteArrayOf(0, 0, 0, 0, 0)), archive(leaf, leaf))) {
            assertThrows(InvalidMapException::class.java) { validate(bytes) }
        }
    }

    @Test fun rejectsAnOversizedDecompressedDirectory() {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(ByteArray(4 * 1024 * 1024 + 1)) }
        }.toByteArray()
        assertThrows(InvalidMapException::class.java) { validate(archive(compressed, compression = 2)) }
    }
}
