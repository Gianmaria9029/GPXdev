@file:Suppress("DEPRECATION")

package com.gpxnavigator.navigation

import com.gpxnavigator.data.navigation.PreferencesNavigationSessionStore
import com.gpxnavigator.data.navigation.SavedNavigationSession
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class NavigationControllerTest : NavigationTestSupport() {
    private suspend fun expectIoError(block: suspend () -> Unit) {
        try { block(); fail("Expected controlled I/O failure") } catch (_: IOException) { }
    }

    private suspend fun fix(latitude: Double, longitude: Double, speed: Float? = null) {
        val point = LocationPoint(latitude, longitude, accuracyMeters = 5f, speedMetersPerSecond = speed, bearingDegrees = null, elevationMeters = null,
            elapsedRealtimeNanos = now * 1_000_000L)
        provider.state.value = LocationState(LocationStatus.ACTIVE, point, precise = true)
        withTimeout(5_000) { controller.data.first { it.location.point == point } }
    }

    @Test fun explorationStopsGpsWhenMapIsHidden(): Unit = runBlocking {
        controller.prepareMap()
        controller.setScreenVisible(true)
        controller.setScreenVisible(true)
        assertTrue(provider.running)
        assertEquals(1, provider.starts)
        controller.setScreenVisible(false)
        assertFalse(provider.running)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertNull(store.saved)
    }

    @Test fun activeSessionSurvivesHiddenScreenAndTerminatesExplicitly(): Unit = runBlocking {
        controller.setScreenVisible(true)
        controller.attachService()
        controller.startSession(trackId)
        controller.setScreenVisible(false)
        assertTrue(provider.running)
        assertEquals(1, provider.starts)
        assertEquals(trackId, store.saved!!.trackId)
        controller.stopSession()
        assertFalse(provider.running)
        assertNull(store.saved)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        repository.delete(trackId)
    }

    @Test fun stoppingOnVisibleMapReturnsToExploration(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        controller.setScreenVisible(true)
        controller.stopSession()
        controller.detachService()
        assertTrue(provider.running)
        controller.setScreenVisible(false)
        assertFalse(provider.running)
    }

    @Test fun serviceDestructionKeepsResumeDataButStopsBackgroundGps(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        controller.detachService()
        assertFalse(provider.running)
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertEquals(trackId, store.saved!!.trackId)
        repository.delete(trackId)
    }

    @Test fun savedSessionRequiresExplicitResume(): Unit = runBlocking {
        store.saved = SavedNavigationSession(trackId, "Escursione")
        controller = newController()
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertFalse(provider.running)
        controller.attachService()
        assertFalse(provider.running)
        controller.startSession(trackId)
        assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
        assertTrue(provider.running)
    }

    @Test fun startRequiresPermissionAndService(): Unit = runBlocking {
        expectIoError { controller.startSession(trackId) }
        controller.attachService()
        provider.permission = false
        expectIoError { controller.startSession(trackId) }
        assertFalse(provider.running)
        assertNull(store.saved)
    }

    @Test fun failedSaveReleasesTrackAndDoesNotLeaveGpsRunning(): Unit = runBlocking {
        store.failSave = true
        controller.attachService()
        expectIoError { controller.startSession(trackId) }
        assertFalse(provider.running)
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertNotNull(controller.session.value.error)
        repository.delete(trackId)
    }

    @Test fun missingTrackDoesNotStartSession(): Unit = runBlocking {
        repository.delete(trackId)
        controller.attachService()
        expectIoError { controller.startSession(trackId) }
        assertFalse(provider.running)
        assertNull(store.saved)
    }

    @Test fun duplicateStartKeepsOneGpsSourceAndOtherTrackCannotReplaceIt(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        controller.startSession(trackId)
        val other = repository.import(fixture("m10-single-point.gpx")).imported.id!!
        expectIoError { controller.startSession(other) }
        controller.prepareMap()
        withTimeout(5_000) { controller.data.first { it.trackId == trackId } }
        assertEquals(trackId, repository.observeCatalog().first().selectedTrackId)
        assertEquals(1, provider.starts)
    }

    @Test fun progressAndOffTrackContinueWithoutVisibleScreen(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        fix(43.7696, 11.2558)
        assertEquals(0.0, controller.data.value.progress!!.travelledDistanceMeters!!, 1.0)
        assertEquals(GuidanceStatus.ACTIVE, controller.data.value.guidance.status)
        assertEquals(Maneuver.STRAIGHT, controller.data.value.guidance.maneuver)
        assertEquals(ArrivalStatus.CALCULATING, controller.data.value.arrival.status)
        now += 2_000
        fix(43.78, 11.27)
        assertEquals(OffTrackStatus.CHECKING, controller.data.value.offTrack.status)
        assertEquals(GuidanceStatus.CHECKING_TRACK, controller.data.value.guidance.status)
        assertEquals(ArrivalStatus.OFF_TRACK, controller.data.value.arrival.status)
        assertNull(controller.data.value.guidance.maneuver)
        now += 3_000
        fix(43.78, 11.27)
        now += 3_000
        fix(43.78, 11.27)
        assertEquals(OffTrackStatus.OFF_TRACK, controller.data.value.offTrack.status)
        assertEquals(GuidanceStatus.OFF_TRACK, controller.data.value.guidance.status)
        assertTrue(provider.running)
    }

    @Test fun oldFixClearsPositionAndDerivedMetrics(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.7696, 11.2558, accuracyMeters = 5f, speedMetersPerSecond = null, bearingDegrees = null, elevationMeters = null, elapsedRealtimeNanos = 1), true)
        withTimeout(5_000) { controller.data.first { it.location.status == LocationStatus.UNAVAILABLE } }
        assertNull(controller.data.value.location.point)
        assertNull(controller.data.value.progress!!.remainingDistanceMeters)
        assertEquals(OffTrackStatus.UNAVAILABLE, controller.data.value.offTrack.status)
        assertEquals(GuidanceStatus.WAITING_FOR_GPS, controller.data.value.guidance.status)
        assertEquals(ArrivalStatus.WAITING_FOR_GPS, controller.data.value.arrival.status)
        assertNull(controller.data.value.arrival.arrivalTimeMillis)
        assertNull(controller.data.value.guidance.maneuver)
    }

    @Test fun failedClearStopsGpsAndKeepsSessionRecoverable(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        store.failClear = true
        controller.stopSession()
        assertFalse(provider.running)
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertNotNull(controller.session.value.error)
    }

    @Test fun signalLossSuspendsGuidanceAndRecoveryRequiresNewEvidence(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        fix(43.7696, 11.2558)
        provider.state.value = LocationState(LocationStatus.UNAVAILABLE)
        withTimeout(5_000) { controller.data.first { it.location.status == LocationStatus.UNAVAILABLE } }
        assertNull(controller.data.value.progress!!.travelledDistanceMeters)
        now += 2_000
        fix(43.7696, 11.2558)
        assertEquals(GuidanceStatus.MATCHING_TRACK, controller.data.value.guidance.status)
        now += 2_000
        fix(43.7696, 11.2558)
        assertEquals(GuidanceStatus.MATCHING_TRACK, controller.data.value.guidance.status)
        now += 2_000
        fix(43.7696, 11.2558)
        assertEquals(GuidanceStatus.ACTIVE, controller.data.value.guidance.status)
    }

    @Test fun serviceDestroyedDuringSaveLeavesAnInterruptedSession(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        controller.attachService()
        val job = launch { controller.startSession(trackId) }
        withTimeout(5_000) { store.saveEntered.await() }
        controller.detachService()
        job.cancel()
        gate.complete(Unit)
        job.join()
        assertFalse(provider.running)
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertEquals(trackId, store.saved!!.trackId)
        repository.delete(trackId)
    }

    @Test fun arrivalLearnsInBackgroundAndClearsOnStopAndNewSession(): Unit = runBlocking {
        val id = repository.import(fixture("eta-straight.gpx")).imported.id!!
        controller.attachService()
        controller.startSession(id)
        val started = now
        for (second in 0L..32L step 2) {
            now = started + second * 1000
            fix(43.77 + Math.toDegrees(second * 10.0 / EARTH_RADIUS_METERS), 11.25)
        }
        assertTrue(provider.running)
        val estimate = controller.data.value.arrival
        assertEquals(ArrivalStatus.READY, estimate.status)
        assertEquals(10.0, estimate.referenceSpeedMetersPerSecond!!, 0.01)
        assertEquals(controller.data.value.progress!!.remainingDistanceMeters!! / 10.0,
            estimate.remainingSeconds!!.toDouble(), 1.0)
        assertEquals(1_700_000_000_000L + now + estimate.remainingSeconds!! * 1000, estimate.arrivalTimeMillis)
        controller.stopSession()
        withTimeout(5_000) { controller.data.first { it.arrival.status == ArrivalStatus.INACTIVE } }
        assertNull(controller.data.value.arrival.arrivalTimeMillis)
        controller.startSession(id)
        now += 2_000
        fix(43.77, 11.25)
        assertEquals(ArrivalStatus.CALCULATING, controller.data.value.arrival.status)
    }

    @Test fun maximumSpeedSurvivesHiddenScreenAndSignalLossAndResetsForNextSession(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(trackId)
        now += 1_000
        fix(43.7696, 11.2558, 30f)
        controller.setScreenVisible(false)
        now += 1_000
        fix(43.7696, 11.2558, 10f)
        assertEquals(30f, controller.data.value.maximumSpeedMetersPerSecond)
        provider.state.value = LocationState(LocationStatus.UNAVAILABLE)
        withTimeout(5_000) { controller.data.first { it.location.status == LocationStatus.UNAVAILABLE } }
        assertEquals(30f, controller.data.value.maximumSpeedMetersPerSecond)
        controller.setScreenVisible(true)
        controller.startSession(trackId)
        assertEquals(30f, controller.data.value.maximumSpeedMetersPerSecond)
        controller.stopSession()
        withTimeout(5_000) { controller.data.first { it.maximumSpeedMetersPerSecond == null } }
        now += 1_000
        controller.startSession(trackId)
        now += 1_000
        fix(43.7696, 11.2558, 5f)
        assertEquals(5f, controller.data.value.maximumSpeedMetersPerSecond)
    }

    @Test fun explorationSpeedDoesNotBecomeTheNewSessionMaximum(): Unit = runBlocking {
        controller.prepareMap()
        controller.setScreenVisible(true)
        now += 1_000
        fix(43.7696, 11.2558, 70f)
        assertNull(controller.data.value.maximumSpeedMetersPerSecond)
        now += 1_000
        controller.attachService()
        controller.startSession(trackId)
        now += 1_000
        fix(43.7696, 11.2558, 5f)
        assertEquals(5f, controller.data.value.maximumSpeedMetersPerSecond)
    }

    @Test fun persistentSessionStoreSurvivesNewInstanceAndClears(): Unit = runBlocking {
        val first = PreferencesNavigationSessionStore(app)
        first.clear()
        first.save(SavedNavigationSession(trackId, "Giro in moto"))
        val second = PreferencesNavigationSessionStore(app)
        assertEquals(SavedNavigationSession(trackId, "Giro in moto"), second.load())
        second.clear()
        assertNull(first.load())
    }
}

