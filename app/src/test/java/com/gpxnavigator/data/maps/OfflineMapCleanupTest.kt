package com.gpxnavigator.data.maps

import android.app.Application
import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class OfflineMapCleanupTest {
    private lateinit var app: Application
    private lateinit var scope: CoroutineScope
    private lateinit var repository: OfflineMapRepository
    private lateinit var bytes: ByteArray
    private val uri = Uri.parse("content://cleanup-test/map.pmtiles")

    @Before fun prepare() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = OfflineMapRepository(app, scope)
        bytes = javaClass.getResourceAsStream("/firenze.pmtiles")!!.use { it.readBytes() }
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
    }

    @After fun close() { scope.cancel() }

    private fun mapFiles() = File(app.filesDir, "maps").listFiles()!!.filter { it.name.endsWith(".pmtiles") }

    @Test fun replacementReclaimsTheOldPrivateCopyAndKeepsTheOriginalDownload(): Unit = runBlocking {
        val download = File(app.cacheDir, "download.pmtiles").apply { writeBytes(bytes) }
        val first = repository.importMap(Uri.fromFile(download))
        val second = repository.importMap(uri)
        assertFalse(first.file.exists())
        assertEquals(listOf(second.file), mapFiles())
        assertEquals(bytes.size.toLong(), mapFiles().sumOf { it.length() })
        assertEquals(second.file, repository.loadSelected()!!.file)
        assertArrayEquals(bytes, download.readBytes())
    }

    @Test fun oldMapWaitsForAllUsersAndDoubleReleaseIsSafe(): Unit = runBlocking {
        val first = repository.importMap(uri)
        val screen = repository.retainMap(first)
        val renderer = repository.retainMap(first)
        try {
            val second = repository.importMap(uri)
            assertTrue(first.file.exists())
            assertEquals(second.file, repository.loadSelected()!!.file)
            screen.close()
            screen.close()
            repository.cleanupUnusedMaps()
            assertTrue(first.file.exists())
            renderer.close()
            repository.cleanupUnusedMaps()
            assertFalse(first.file.exists())
            assertTrue(second.file.exists())
        } finally { screen.close(); renderer.close() }
    }

    @Test fun acquiredMapRemainsProtectedAcrossAnotherImportUntilItsOwnerClosesIt(): Unit = runBlocking {
        val acquired = repository.acquireMap(uri)!!
        try {
            val newer = repository.importMap(uri)
            assertTrue(acquired.map.file.isFile)
            assertEquals(newer.file, repository.loadSelected()!!.file)
            acquired.close()
            repository.cleanupUnusedMaps()
            assertFalse(acquired.map.file.exists())
            assertTrue(newer.file.isFile)
        } finally { acquired.close() }
    }

    @Test fun openingTheSelectedMapCleansLegacyCopiesWithoutTouchingOtherFiles(): Unit = runBlocking {
        val selected = repository.importMap(uri)
        val legacy = File(selected.file.parentFile, "legacy.pmtiles").apply { writeBytes(bytes) }
        val note = File(selected.file.parentFile, "notes.txt").apply { writeText("keep") }
        val reopened = OfflineMapRepository(app, scope)
        assertEquals(selected.file, reopened.loadSelected()!!.file)
        assertFalse(legacy.exists())
        assertEquals("keep", note.readText())
        assertTrue(selected.file.exists())
    }

    @Test fun failedDeletionDoesNotRejectTheNewMapAndCanBeRetried(): Unit = runBlocking {
        var obsolete: File? = null
        var fail = true
        repository = OfflineMapRepository(app, scope) { file ->
            if (file == obsolete && fail) false else file.delete()
        }
        val first = repository.importMap(uri)
        obsolete = first.file
        val second = repository.importMap(uri)
        assertEquals(second.file, repository.loadSelected()!!.file)
        assertTrue(first.file.exists())
        assertFalse(repository.cleanupUnusedMaps())
        fail = false
        assertTrue(repository.cleanupUnusedMaps())
        assertFalse(first.file.exists())
        assertTrue(second.file.exists())
    }

    @Test fun aDeletionSecurityFailureKeepsTheSelectionAndRetriesLater(): Unit = runBlocking {
        var obsolete: File? = null
        var deny = true
        repository = OfflineMapRepository(app, scope) { file ->
            if (file == obsolete && deny) throw SecurityException("Temporarily denied")
            file.delete()
        }
        val first = repository.importMap(uri)
        obsolete = first.file
        val second = repository.importMap(uri)
        assertEquals(second.file, repository.loadSelected()!!.file)
        assertTrue(first.file.exists())
        deny = false
        assertTrue(repository.cleanupUnusedMaps())
        assertFalse(first.file.exists())
    }

    @Test fun cancellationDuringCopyKeepsTheCurrentMapAndRemovesThePartialCopy(): Unit = runBlocking {
        val selected = repository.importMap(uri)
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val slow = Uri.parse("content://cleanup-test/slow.pmtiles")
        shadowOf(app.contentResolver).registerInputStreamSupplier(slow) {
            object : ByteArrayInputStream(bytes) {
                private var reads = 0
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (++reads == 2) {
                        entered.countDown()
                        if (!gate.await(5, TimeUnit.SECONDS)) throw IOException("Test timed out")
                    }
                    return super.read(buffer, offset, length)
                }
            }
        }
        val job = async(Dispatchers.IO) { repository.importMap(slow) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally { gate.countDown() }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(listOf(selected.file), mapFiles())
        assertEquals(selected.file, repository.loadSelected()!!.file)
    }

    @Test fun concurrentImportsCannotDeleteEachOthersPartialCopies(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val slow = Uri.parse("content://cleanup-test/concurrent.pmtiles")
        shadowOf(app.contentResolver).registerInputStreamSupplier(slow) {
            object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    entered.countDown()
                    if (!gate.await(5, TimeUnit.SECONDS)) throw IOException("Test timed out")
                    return super.read(buffer, offset, length)
                }
            }
        }
        val first = async(Dispatchers.IO) { repository.importMap(slow) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { repository.importMap(uri) }
        gate.countDown()
        first.await()
        val selected = second.await()
        assertEquals(selected.file, repository.loadSelected()!!.file)
        assertEquals(listOf(selected.file), mapFiles())
    }
}
