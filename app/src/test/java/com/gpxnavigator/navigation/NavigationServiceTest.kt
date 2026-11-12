@file:Suppress("DEPRECATION")

package com.gpxnavigator.navigation

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.ContextWrapper
import android.content.ComponentName
import android.content.pm.ServiceInfo
import android.os.Looper
import com.gpxnavigator.MainActivity
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
class NavigationServiceTest : NavigationTestSupport() {
    private lateinit var serviceController: ServiceController<NavigationService>
    private lateinit var service: NavigationService
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before fun createService() {
        serviceController = Robolectric.buildService(NavigationService::class.java).create()
        service = serviceController.get()
    }

    @After fun destroyService() { serviceController.destroy() }

    private fun start() = service.onStartCommand(Intent(app, NavigationService::class.java)
        .setAction(NavigationService.ACTION_START).putExtra(NavigationService.EXTRA_TRACK_ID, trackId), 0, 1)

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Async service operation timed out", condition())
    }

    @Test fun startsForegroundLocationServiceWithOpenAndStopActions() {
        assertEquals(Service.START_NOT_STICKY, start())
        val foreground = shadowOf(service)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.foregroundServiceType)
        assertEquals(NavigationService.NOTIFICATION_ID, foreground.lastForegroundNotificationId)
        await { controller.session.value.status == SessionStatus.ACTIVE }
        await { notifications.getNotification(NavigationService.NOTIFICATION_ID)?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)?.contains("Firenze") == true }
        val notification = notifications.getNotification(NavigationService.NOTIFICATION_ID)!!
        assertEquals(NotificationManager.IMPORTANCE_LOW,
            app.getSystemService(NotificationManager::class.java).getNotificationChannel(NavigationService.CHANNEL_ID).importance)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(2, notification.actions.size)
        val open = shadowOf(notification.actions[0].actionIntent)
        assertTrue(open.isImmutable)
        assertTrue(open.isActivity)
        assertEquals(MainActivity::class.java.name, open.savedIntent.component!!.className)
        assertTrue(open.savedIntent.getBooleanExtra(NavigationService.EXTRA_OPEN_NAVIGATION, false))
        val stop = shadowOf(notification.actions[1].actionIntent)
        assertTrue(stop.isImmutable)
        assertTrue(stop.isService)
        assertEquals(NavigationService.ACTION_STOP, stop.savedIntent.action)
        assertTrue(provider.running)
    }

    @Test fun stopActionRemovesNotificationAndStopsGps() {
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        service.onStartCommand(Intent(app, NavigationService::class.java).setAction(NavigationService.ACTION_STOP), 0, 2)
        await { shadowOf(service).isStoppedBySelf }
        assertFalse(provider.running)
        assertNull(store.saved)
        assertNull(notifications.getNotification(NavigationService.NOTIFICATION_ID))
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test fun acceptedStopRequestReachesTheServiceAndStopsNavigation() {
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        assertTrue(NavigationService.requestStop(app))
        val request = shadowOf(app).nextStartedService
        assertEquals(NavigationService.ACTION_STOP, request.action)
        service.onStartCommand(request, 0, 2)
        await { shadowOf(service).isStoppedBySelf }
        assertEquals(SessionStatus.IDLE, controller.session.value.status)
        assertFalse(provider.running)
        assertNull(store.saved)
    }

    @Test fun rejectedStopRequestsReturnFalseAndKeepAnActionableError() {
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        for (failure in listOf(IllegalStateException("Rejected"), SecurityException("Denied"), null)) {
            val context = object : ContextWrapper(app) {
                override fun startService(service: Intent): ComponentName? {
                    if (failure != null) throw failure
                    return null
                }
            }
            assertFalse(NavigationService.requestStop(context))
            assertNotNull(controller.session.value.error)
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertTrue(provider.running)
        }
    }

    @Test fun deniedLocationDoesNotStartGpsAndReportsError() {
        provider.permission = false
        start()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(provider.running)
        assertNotNull(controller.session.value.error)
        assertNull(notifications.getNotification(NavigationService.NOTIFICATION_ID))
    }

    @Test fun foregroundPromotionFailureIsControlled() {
        shadowOf(service).setThrowInStartForeground(SecurityException("No location permission"))
        start()
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(provider.running)
        assertNotNull(controller.session.value.error)
    }

    @Test fun nullIntentDoesNotAutomaticallyResume() {
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(provider.running)
    }

    @Test fun duplicateStartKeepsCurrentNotificationAndOneGpsSubscription() {
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        await { notifications.getNotification(NavigationService.NOTIFICATION_ID)?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)?.contains("Firenze") == true }
        start()
        assertEquals(1, provider.starts)
        assertTrue(notifications.getNotification(NavigationService.NOTIFICATION_ID)!!.extras
            .getCharSequence(Notification.EXTRA_TITLE)!!.contains("Firenze"))
    }

    @Test fun notificationReportsOffTrackWithoutVisibleActivity() {
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        for (time in listOf(100_000L, 103_000L, 106_000L)) {
            now = time
            provider.state.value = LocationState(LocationStatus.ACTIVE,
                LocationPoint(43.78, 11.27, accuracyMeters = 5f, speedMetersPerSecond = null, bearingDegrees = null, elevationMeters = null, elapsedRealtimeNanos = time * 1_000_000), true)
            await { controller.data.value.location.point?.elapsedRealtimeNanos == time * 1_000_000 }
        }
        await { notifications.getNotification(NavigationService.NOTIFICATION_ID)?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)?.contains("Fuori traccia") == true }
        assertTrue(provider.running)
    }

    @Test fun disabledNotificationsDoNotPreventForegroundNavigation() {
        notifications.setNotificationsEnabled(false)
        start()
        await { controller.session.value.status == SessionStatus.ACTIVE }
        assertTrue(provider.running)
        assertEquals(NavigationService.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
    }
}
