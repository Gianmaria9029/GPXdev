@file:Suppress("DEPRECATION")

package com.gpxnavigator.qa

import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import com.gpxnavigator.navigation.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class QaNavigationFreeRecordingSpeedTest : NavigationTestSupport() {
    private lateinit var recordings: RecordingRepository

    @Before fun enableRecordings() {
        recordings = RecordingRepository(database)
        controller = newController(recordings)
    }

    private suspend fun fix(speed: Float) {
        now += 1_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.7696, 11.2558, 5f, speed, null, 50.0, now * 1_000_000L), true)
        withTimeout(5_000) {
            controller.data.first { it.location.point?.elapsedRealtimeNanos == now * 1_000_000L }
        }
    }

    @Test fun freeRecordingMaximumExcludesSpeedMeasuredBeforeStart(): Unit = runBlocking {
        controller.setScreenVisible(true)
        fix(70f)
        assertNull(controller.data.value.maximumSpeedMetersPerSecond)
        now += 1_000
        controller.attachService()
        controller.startSession(null, true)
        val inherited = withTimeoutOrNull(1_000) {
            controller.data.first { it.maximumSpeedMetersPerSecond != null }
        }
        assertNull("No new GPS fix has occurred since recording began; inherited maximum=" +
            inherited?.maximumSpeedMetersPerSecond, inherited?.maximumSpeedMetersPerSecond)
        fix(5f)
        assertEquals("Only the 5 m/s fix occurred during free recording; pre-session 70 m/s must be excluded",
            5f, controller.data.value.maximumSpeedMetersPerSecond!!, 0f)
    }

    @Test fun gpxNavigationMaximumExcludesTheSamePreStartSpeed(): Unit = runBlocking {
        controller.setScreenVisible(true)
        fix(70f)
        now += 1_000
        controller.attachService()
        controller.startSession(trackId)
        fix(5f)
        assertEquals(5f, controller.data.value.maximumSpeedMetersPerSecond!!, 0f)
    }

    @Test fun secondFreeRecordingAlsoExcludesTheLatestExplorationFix(): Unit = runBlocking {
        controller.setScreenVisible(true)
        controller.attachService()
        controller.startSession(null, true)
        fix(30f)
        withTimeout(5_000) { controller.recording.first { it.pointCount > 0 } }
        controller.stopSession()
        controller.discardRecording()
        fix(70f)
        now += 1_000
        controller.startSession(null, true)
        val inherited = withTimeoutOrNull(1_000) {
            controller.data.first { it.maximumSpeedMetersPerSecond != null }
        }
        assertNull("A second recording must also reset its starting time", inherited?.maximumSpeedMetersPerSecond)
        fix(5f)
        assertEquals(5f, controller.data.value.maximumSpeedMetersPerSecond!!, 0f)
    }

    @Test fun discardingFinishedRecordingAfterSessionClearFailureDoesNotRestoreAPhantomSession(): Unit = runBlocking {
        controller.attachService()
        controller.startSession(null, true)
        fix(2f)
        withTimeout(5_000) { controller.recording.first { it.pointCount > 0 } }
        store.failClear = true
        controller.stopSession()
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertTruePendingRecording()

        // Storage recovers before the user resolves the saved-recording decision.
        store.failClear = false
        controller.discardRecording()
        assertNull(recordings.current())
        assertNull(controller.recording.value.id)
        controller.detachService()
        controller = newController(recordings)
        val restored = withTimeout(5_000) {
            controller.session.first { it.status != SessionStatus.RESTORING }
        }
        assertEquals("The finished recording was discarded, so reopening must not offer an unusable resume; actual=$restored",
            SessionStatus.IDLE, restored.status)
    }

    private fun assertTruePendingRecording() {
        org.junit.Assert.assertTrue("The failed preference clear must still leave a finished recording to resolve",
            controller.recording.value.pending)
    }
}
