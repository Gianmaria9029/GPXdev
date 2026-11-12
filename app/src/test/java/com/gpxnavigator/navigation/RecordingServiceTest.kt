@file:Suppress("DEPRECATION")
package com.gpxnavigator.navigation

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class RecordingServiceTest : NavigationTestSupport() {
    private lateinit var service: ServiceController<NavigationService>
    @Before fun createService() {
        controller = newController(RecordingRepository(database))
        service = Robolectric.buildService(NavigationService::class.java).create()
    }
    @After fun destroyService() { service.destroy() }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(condition())
    }
    private fun start(id: String? = null) {
        service.get().onStartCommand(Intent(app, NavigationService::class.java)
            .setAction(NavigationService.ACTION_START).putExtra(NavigationService.EXTRA_RECORD, true)
            .putExtra(NavigationService.EXTRA_TRACK_ID, id), 0, 1)
        await { controller.session.value.status == SessionStatus.ACTIVE }
    }
    @Test fun freeRecordingStartsForegroundWithoutGpxAndStopRequestsConfirmationInApp() {
        start()
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        await { notifications.getNotification(NavigationService.NOTIFICATION_ID)?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)?.contains("REC") == true }
        assertTrue(provider.running)
        val stop = shadowOf(notifications.getNotification(NavigationService.NOTIFICATION_ID)!!.actions[1].actionIntent)
        assertTrue(stop.isActivity)
        assertTrue(stop.isImmutable)
        assertEquals(StopNavigationActivity::class.java.name, stop.savedIntent.component!!.className)
        assertNull(controller.session.value.trackId)
    }

    @Test fun requestedNameIsPassedToTheServiceAndPersistedInTheDraft() {
        assertTrue(NavigationService.requestStart(app, null, true, "Giro del lago"))
        val request = shadowOf(app).nextStartedService
        assertEquals("Giro del lago", request.getStringExtra(NavigationService.EXTRA_RECORDING_NAME))
        service.get().onStartCommand(request, 0, 1)
        await { controller.session.value.status == SessionStatus.ACTIVE }
        assertEquals("Giro del lago", controller.session.value.title)
        kotlinx.coroutines.runBlocking {
            assertEquals("Giro del lago", RecordingRepository(database).current()!!.trackName)
        }
    }

    @Test fun cockpitNotificationIdentifiesTheActivityAndKeepsTheRecordedSummaryOnStop() {
        assertTrue(NavigationService.requestStart(app,null,true,"Cockpit prova",com.gpxnavigator.domain.model.ActivityMode.COCKPIT))
        val request = shadowOf(app).nextStartedService
        assertEquals("COCKPIT",request.getStringExtra(NavigationService.EXTRA_ACTIVITY_MODE))
        service.get().onStartCommand(request,0,1)
        val notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        await { notifications.getNotification(NavigationService.NOTIFICATION_ID)?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Cockpit in corso" }
        val notification = notifications.getNotification(NavigationService.NOTIFICATION_ID)!!
        assertTrue(shadowOf(notification.contentIntent).savedIntent.getBooleanExtra(NavigationService.EXTRA_OPEN_NAVIGATION,false))
        service.get().onStartCommand(Intent(app,NavigationService::class.java).setAction(NavigationService.ACTION_STOP),0,2)
        await { controller.recording.value.pending }
        assertEquals(com.gpxnavigator.domain.model.ActivityMode.COCKPIT,controller.recording.value.mode)
        assertNotNull(controller.recording.value.endedAtMillis)
        assertEquals("Cockpit prova",controller.recording.value.title)
    }
    @Test fun stopPreservesDraftForTheYesNoDecisionAndDoesNotCreateCatalogEntry() {
        start(trackId)
        now += 3_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.7696, 11.2558, 5f, 2f, null, null, now * 1_000_000), true)
        await { controller.recording.value.pointCount == 1 }
        service.get().onStartCommand(Intent(app, NavigationService::class.java).setAction(NavigationService.ACTION_STOP), 0, 2)
        await { shadowOf(service.get()).isStoppedBySelf }
        assertTrue(controller.recording.value.pending)
        assertFalse(controller.recording.value.active)
        assertFalse(provider.running)
        assertNull(controller.session.value.recordingId)
    }
}
