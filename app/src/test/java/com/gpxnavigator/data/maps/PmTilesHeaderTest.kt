package com.gpxnavigator.data.maps

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PmTilesHeaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture(): ByteArray = javaClass.getResourceAsStream("/firenze.pmtiles")!!.use { it.readBytes() }
    private fun read(bytes: ByteArray): PmTilesHeader {
        val file = temporary.newFile()
        file.writeBytes(bytes)
        return RandomAccessFile(file, "r").use(PmTilesHeader::read)
    }

    @Test fun readsOfficialFirenzeArchive() {
        val header = read(fixture())
        assertEquals(1, header.tileType)
        assertEquals(2, header.compression)
        assertEquals(15, header.maxZoom)
        assertTrue(43.77 in header.south..header.north)
        assertTrue(11.25 in header.west..header.east)
    }

    @Test fun rejectsEmptyOrTruncatedArchives() {
        assertThrows(InvalidMapException::class.java) { read(byteArrayOf()) }
        assertThrows(InvalidMapException::class.java) { read(fixture().copyOf(200)) }
    }

    @Test fun rejectsOtherFileTypesAndVersions() {
        for (index in listOf(0, 7)) {
            val bytes = fixture()
            bytes[index] = 0
            assertThrows(InvalidMapException::class.java) { read(bytes) }
        }
    }

    @Test fun rejectsOffsetsThatOverflowOrLeaveTheFile() {
        for (offset in listOf(-1L, Long.MAX_VALUE)) {
            val bytes = fixture()
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(24, offset)
            assertThrows(InvalidMapException::class.java) { read(bytes) }
        }
    }

    @Test fun rejectsUnsupportedCompressionAndTileTypes() {
        for (index in listOf(97, 98, 99)) {
            val bytes = fixture()
            bytes[index] = 99
            assertThrows(InvalidMapException::class.java) { read(bytes) }
        }
    }

    @Test fun rejectsInvalidBoundsAndZoom() {
        val bytes = fixture()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(106, 1_000_000_000)
        assertThrows(InvalidMapException::class.java) { read(bytes) }
        val zoom = fixture()
        zoom[100] = 20
        assertThrows(InvalidMapException::class.java) { read(zoom) }
    }

}
