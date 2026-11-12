package com.gpxnavigator.data.maps

import android.app.Application
import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class OfflineMapRepositoryTest {
    @Test fun failedValidationKeepsThePreviousMapAndRemovesTheBadCopy(): Unit = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        val bytes = javaClass.getResourceAsStream("/firenze.pmtiles")!!.use { it.readBytes() }
        val uri = Uri.parse("content://maps-test/firenze.pmtiles")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        val repository = OfflineMapRepository(app)
        val valid = repository.importMap(uri)
        val broken = bytes.copyOf()
        val b = ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN)
        java.util.Arrays.fill(broken, b.getLong(8).toInt(), (b.getLong(8) + b.getLong(16)).toInt(), 0.toByte())
        val badUri = Uri.parse("content://maps-test/broken.pmtiles")
        shadowOf(app.contentResolver).registerInputStreamSupplier(badUri) { ByteArrayInputStream(broken) }
        try { repository.importMap(badUri); fail("Corrupt root accepted") } catch (_: InvalidMapException) { }
        assertEquals(valid.file, repository.loadSelected()!!.file)
        assertTrue(valid.file.isFile)
        assertEquals(listOf(valid.file.name), File(app.filesDir, "maps").listFiles()!!.map { it.name })
    }
}
