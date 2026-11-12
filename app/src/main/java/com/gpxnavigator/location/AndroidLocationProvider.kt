package com.gpxnavigator.location

import android.annotation.SuppressLint

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.Build
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gpxnavigator.domain.model.LocationPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val mutableState = MutableStateFlow(LocationState())
    override val state = mutableState.asStateFlow()
    private var running = false
    private var callback: LocationCallback? = null
    private var observingSettings = false
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!observingSettings) return
            removeUpdates()
            start()
        }
    }

    override fun hasPermission(): Boolean = hasFinePermission() || hasCoarsePermission()
    override fun hasPrecisePermission(): Boolean = hasFinePermission()

    override fun start() {
        observeSettings()
        val precise = hasFinePermission()
        if (!precise && !hasCoarsePermission()) {
            removeUpdates()
            mutableState.value = LocationState(LocationStatus.DENIED)
            return
        }
        if (!locationManager.isLocationEnabled ||
            (precise && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))) {
            removeUpdates()
            mutableState.value = LocationState(LocationStatus.DISABLED, precise = precise)
            return
        }
        if (running) return
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY
            else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, 1_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .setMaxUpdateAgeMillis(0L)
            .build()
        mutableState.value = LocationState(LocationStatus.WAITING, precise = precise)
        val activeCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!running || callback !== this) return
                val location = result.lastLocation ?: return
                mutableState.value = LocationState(LocationStatus.ACTIVE, location.toPoint(), hasFinePermission())
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (running && callback === this && !availability.isLocationAvailable) {
                    mutableState.value = LocationState(LocationStatus.UNAVAILABLE,
                        precise = hasFinePermission())
                }
            }
        }
        callback = activeCallback
        running = true
        try {
            client.requestLocationUpdates(request, activeCallback, Looper.getMainLooper())
                .addOnFailureListener { exception ->
                    if (running && callback === activeCallback) {
                        running = false
                        callback = null
                        val status = if (exception is SecurityException) LocationStatus.DENIED
                            else LocationStatus.ERROR
                        mutableState.value = LocationState(status, precise = precise)
                    }
                }
        } catch (_: SecurityException) {
            running = false
            callback = null
            mutableState.value = LocationState(LocationStatus.DENIED)
        }
    }

    override fun stop() {
        if (observingSettings) {
            appContext.unregisterReceiver(settingsReceiver)
            observingSettings = false
        }
        removeUpdates()
        mutableState.value = LocationState()
    }

    // Platform logging records exceptional shutdown failures without another logging dependency.
    @SuppressLint("LogNotTimber")
    private fun removeUpdates() {
        if (running) {
            running = false
            val oldCallback = callback
            callback = null
            if (oldCallback != null) {
                try {
                    client.removeLocationUpdates(oldCallback).addOnFailureListener { exception ->
                        android.util.Log.w("AndroidLocationProvider", "Impossibile fermare gli aggiornamenti GPS.", exception)
                    }
                } catch (exception: SecurityException) {
                    android.util.Log.w("AndroidLocationProvider", "Permesso GPS revocato durante l'arresto.", exception)
                }
            }
        }
    }

    private fun observeSettings() {
        if (observingSettings) return
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(settingsReceiver, filter)
        }
        observingSettings = true
    }

    private fun hasFinePermission(): Boolean = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun hasCoarsePermission(): Boolean = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

internal fun Location.toPoint(): LocationPoint = LocationPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy.takeIf { hasAccuracy() && it.isFinite() && it >= 0f },
    speedMetersPerSecond = speed.takeIf { hasSpeed() && it.isFinite() && it >= 0f },
    bearingDegrees = bearing.takeIf { hasBearing() && it.isFinite() },
    elevationMeters = altitude.takeIf { hasAltitude() && it.isFinite() },
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    timestampMillis = time.takeIf { it > 0 },
    bearingAccuracyDegrees = bearingAccuracyDegrees.takeIf {
        hasBearingAccuracy() && it.isFinite() && it >= 0f
    },
)
