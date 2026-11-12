package com.gpxnavigator

import android.app.Application
import androidx.room.Room
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.gpx.LocalGpxRepository
import com.gpxnavigator.data.maps.OfflineMapRepository
import com.gpxnavigator.data.navigation.PreferencesNavigationSessionStore
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.location.AndroidLocationProvider
import com.gpxnavigator.navigation.NavigationController
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class NavigatorApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, NavigatorDatabase::class.java, "gpx-catalog.db").build()
    }
    open val gpxRepository by lazy { LocalGpxRepository(this, database) }
    open val recordingRepository by lazy { RecordingRepository(database) }
    open val offlineMapRepository by lazy { OfflineMapRepository(this) }
    open val navigationController by lazy {
        NavigationController(gpxRepository, AndroidLocationProvider(this), PreferencesNavigationSessionStore(this),
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), SystemClock::elapsedRealtime,
            recordings = recordingRepository)
    }
}
