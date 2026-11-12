@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import com.gpxnavigator.data.gpx.GpxParser
import com.gpxnavigator.data.gpx.GpxWriter
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class RecordingSessionBoundaryTest : NavigationTestSupport() {
    private lateinit var recordings: RecordingRepository

    @Before fun enableRecordings() {
        recordings = RecordingRepository(database)
        controller = newController(recordings)
    }

    private suspend fun publish(speed: Float, longitude: Double = 11.2558) {
        val point = LocationPoint(43.7696, longitude, 5f, speed, null, 50.0, now * 1_000_000L)
        provider.state.value = LocationState(LocationStatus.ACTIVE, point, true)
        withTimeout(5_000) { controller.data.first { it.location.point == point } }
    }

    private suspend fun awaitCount(count: Int) {
        withTimeout(5_000) { controller.recording.first { it.pointCount >= count } }
    }

    private suspend fun verifyNewRecording(id: String?, delayBeforeStart: Long) {
        controller.setScreenVisible(true)
        now += 1_000
        publish(70f)
        val cachedTime = now
        now += delayBeforeStart
        val started = now
        controller.attachService()
        controller.startSession(id, true)
        now += 2_000
        val firstTime = now
        publish(5f, 11.2560)
        awaitCount(1)
        now += 2_000
        publish(4f, 11.2562)
        awaitCount(2)
        controller.stopSession() // Also verifies the final flush cannot reintroduce a cached fix.
        val samples = recordings.points(controller.recording.value.id!!)
        assertEquals(listOf(firstTime, now), samples.map { it.elapsedMillis })
        assertTrue(samples.all { it.elapsedMillis > started && it.elapsedMillis > cachedTime })
        assertEquals(5f, RecordingStatistics.calculate(samples).maximumSpeedPoint!!.speedMetersPerSecond)

        val saved = controller.saveRecording()
        assertEquals(2, saved.file.inputStream().use { GpxParser().parse(it) }.segments.single().points.size)
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(saved.file)
        val maximum = document.getElementsByTagNameNS(GpxWriter.EXTENSION_NAMESPACE, "maximumSpeed").item(0)
            as org.w3c.dom.Element
        assertEquals("5.0", maximum.getAttribute("metersPerSecond"))
        val exported = temporary.newFile("recording.gpx")
        repository.export(saved.id!!, android.net.Uri.fromFile(exported))
        assertArrayEquals(saved.file.readBytes(), exported.readBytes())
    }

    @Test fun freeRecordingExcludesPreStartFixFromPointsSavedGpxAndExport(): Unit = runBlocking {
        verifyNewRecording(null, 5_000)
    }

    @Test fun recordingAlongAGpxAlsoExcludesPreStartFixFromAllStatistics(): Unit = runBlocking {
        verifyNewRecording(trackId, 5_000)
    }

    @Test fun cachedFixInStartingMillisecondIsExcludedFromRecording(): Unit = runBlocking {
        verifyNewRecording(null, 0)
    }

    @Test fun resumeExcludesLocationsDuringInterruptionAndKeepsEarlierLegitimateStatistics(): Unit = runBlocking {
        controller.setScreenVisible(true)
        controller.attachService()
        controller.startSession(null, true)
        now += 1_000
        val originalTime = now
        publish(8f)
        awaitCount(1)
        controller.detachService()
        now += 1_000
        publish(70f, 11.2559)
        now += 5_000
        controller.attachService()
        controller.startSession(null, true)
        now += 1_000
        publish(3f, 11.2560)
        awaitCount(2)
        val samples = recordings.points(controller.recording.value.id!!)
        assertEquals(listOf(originalTime, now), samples.map { it.elapsedMillis })
        assertEquals(listOf(0, 1), samples.map { it.segment })
        assertEquals(8f, RecordingStatistics.calculate(samples).maximumSpeedPoint!!.speedMetersPerSecond)
        assertEquals(3f, controller.data.value.maximumSpeedMetersPerSecond)
        assertEquals(0.0, controller.recording.value.distanceMeters, 0.0)
    }

    @Test fun slowSessionSaveCannotRecordStartingLocationsOrInheritTheirMaximum(): Unit = runBlocking {
        controller.setScreenVisible(true)
        controller.attachService()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        val starting = launch { controller.startSession(trackId, true) }
        withTimeout(5_000) { store.saveEntered.await() }
        now += 1_000
        publish(70f)
        now += 1_000
        gate.complete(Unit)
        starting.join()
        now += 1_000
        publish(5f, 11.2560)
        awaitCount(1)
        val samples = recordings.points(controller.recording.value.id!!)
        assertEquals(listOf(now), samples.map { it.elapsedMillis })
        assertEquals(5f, controller.data.value.maximumSpeedMetersPerSecond)
    }
}
