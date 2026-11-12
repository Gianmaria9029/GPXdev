package com.gpxnavigator.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.gpxnavigator.MainActivity
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.R
import com.gpxnavigator.domain.model.ActivityMode
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller get() = (application as NavigatorApplication).navigationController
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private var starting: Job? = null
    private var foreground = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID,
            getString(R.string.navigation_channel), NotificationManager.IMPORTANCE_LOW))
        controller.attachService()
        scope.launch {
            var previousText: Pair<String, String>? = null
            combine(controller.session, controller.data, controller.recording) { session, data, recording -> Triple(session, data, recording) }
                .collect { (session, data, recording) ->
                    if (foreground && session.status == SessionStatus.ACTIVE) {
                        val base = notificationText(session, data)
                        val text = base.first to (if (recording.error != null) getString(R.string.recording_error_short) + " · " + base.second
                            else if (recording.active) "● REC · " + base.second else base.second)
                        if (text != previousText) {
                            notifications.notify(NOTIFICATION_ID, notification(text.first, text.second))
                            previousText = text
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (stopping || starting?.isActive == true) return START_NOT_STICKY
                if (foreground && controller.session.value.status == SessionStatus.ACTIVE) return START_NOT_STICKY
                val id = intent.getStringExtra(EXTRA_TRACK_ID)
                val record = intent.getBooleanExtra(EXTRA_RECORD, false)
                val recordingName = intent.getStringExtra(EXTRA_RECORDING_NAME)
                val mode = ActivityMode.restored(intent.getStringExtra(EXTRA_ACTIVITY_MODE), id)
                if (id.isNullOrBlank() && !record && mode != ActivityMode.COCKPIT) {
                    controller.reportError(getString(R.string.navigation_start_failed))
                    finishService()
                    return START_NOT_STICKY
                }
                if (!controller.hasLocationPermission()) {
                    controller.reportError(getString(R.string.navigation_permission_required))
                    finishService()
                    return START_NOT_STICKY
                }
                try {
                    startForeground(NOTIFICATION_ID, notification(getString(R.string.navigation_title),
                        getString(R.string.navigation_starting)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    foreground = true
                } catch (exception: SecurityException) {
                    controller.reportError(getString(R.string.navigation_start_failed))
                    finishService()
                    return START_NOT_STICKY
                } catch (exception: IllegalStateException) {
                    controller.reportError(getString(R.string.navigation_start_failed))
                    finishService()
                    return START_NOT_STICKY
                }
                starting = scope.launch {
                    try {
                        controller.startSession(id, record, recordingName, mode)
                    } catch (exception: IOException) {
                        controller.reportError(exception.message ?: getString(R.string.navigation_start_failed))
                    } catch (_: SecurityException) {
                        controller.reportError(getString(R.string.navigation_permission_required))
                    }
                    if (controller.session.value.status != SessionStatus.ACTIVE) finishService()
                }
            }
            ACTION_STOP -> {
                stopping = true
                val job = starting
                job?.cancel()
                scope.launch {
                    job?.join()
                    controller.stopSession()
                    finishService()
                }
            }
            else -> finishService()
        }
        // After process termination the saved session is offered for an explicit resume from the app.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        controller.detachService()
        super.onDestroy()
    }

    private fun finishService() {
        foreground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_NAVIGATION, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // A recording needs a visible activity to ask whether to keep the GPX after stopping.
        val stop = if (controller.session.value.recordingId != null)
            PendingIntent.getActivity(this, 3, Intent(this, StopNavigationActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else PendingIntent.getService(this, 2, Intent(this, NavigationService::class.java)
            .setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation_notification).setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_navigation_notification),
                getString(R.string.notification_open), open).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_navigation_notification),
                getString(R.string.stop_navigation), stop).build())
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun notificationText(session: NavigationSessionState, data: NavigationData): Pair<String, String> {
        val title = if (session.mode == ActivityMode.COCKPIT) getString(R.string.cockpit_running)
            else getString(R.string.notification_navigation_title, session.title ?: getString(R.string.navigation_title))
        val text = when {
            data.offTrack.status == OffTrackStatus.OFF_TRACK -> {
                val distance = data.offTrack.distanceMeters
                getString(R.string.off_track_alert) + if (distance == null) "" else " · " + if (distance < 1_000)
                    getString(R.string.off_track_distance_meters, distance)
                else getString(R.string.off_track_distance_kilometers, distance / 1_000)
            }
            data.location.status == com.gpxnavigator.location.LocationStatus.DISABLED -> getString(R.string.location_disabled)
            data.location.status == com.gpxnavigator.location.LocationStatus.DENIED -> getString(R.string.location_denied)
            data.location.status == com.gpxnavigator.location.LocationStatus.ERROR -> getString(R.string.location_error)
            data.location.status == com.gpxnavigator.location.LocationStatus.UNAVAILABLE -> getString(R.string.location_unavailable)
            data.location.status != com.gpxnavigator.location.LocationStatus.ACTIVE -> getString(R.string.location_waiting)
            data.guidance.status == GuidanceStatus.MATCHING_TRACK -> getString(R.string.guidance_matching)
            data.guidance.status == GuidanceStatus.REVERSE_DIRECTION -> getString(R.string.guidance_reverse)
            data.progress?.remainingDistanceMeters != null ->
                getString(R.string.notification_remaining, data.progress.remainingDistanceMeters / 1_000)
            else -> getString(if (session.trackId == null) R.string.recording_active_status else R.string.navigation_active)
        }
        return title to text
    }

    companion object {
        internal const val CHANNEL_ID = "active_navigation"
        internal const val NOTIFICATION_ID = 42
        internal const val ACTION_START = "com.gpxnavigator.START_NAVIGATION"
        internal const val ACTION_STOP = "com.gpxnavigator.STOP_NAVIGATION"
        internal const val EXTRA_TRACK_ID = "track_id"
        internal const val EXTRA_RECORD = "record"
        internal const val EXTRA_RECORDING_NAME = "recording_name"
        internal const val EXTRA_ACTIVITY_MODE = "activity_mode"
        const val EXTRA_OPEN_NAVIGATION = "open_navigation"

        fun requestStart(context: Context, trackId: String?, record: Boolean = false, recordingName: String? = null,
            mode: ActivityMode = ActivityMode.forTrack(trackId)): Boolean = try {
            context.startForegroundService(Intent(context, NavigationService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_TRACK_ID, trackId).putExtra(EXTRA_RECORD, record)
                .putExtra(EXTRA_RECORDING_NAME, recordingName).putExtra(EXTRA_ACTIVITY_MODE, mode.name))
            true
        } catch (_: SecurityException) {
            (context.applicationContext as NavigatorApplication).navigationController
                .reportError(context.getString(R.string.navigation_permission_required))
            false
        } catch (_: IllegalStateException) {
            (context.applicationContext as NavigatorApplication).navigationController
                .reportError(context.getString(R.string.navigation_start_failed))
            false
        }

        fun requestStop(context: Context): Boolean {
            val accepted = try {
                context.startService(Intent(context, NavigationService::class.java).setAction(ACTION_STOP)) != null
            } catch (_: IllegalStateException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (!accepted) {
                (context.applicationContext as NavigatorApplication).navigationController
                    .reportError(context.getString(R.string.navigation_stop_failed))
            }
            return accepted
        }
    }
}
