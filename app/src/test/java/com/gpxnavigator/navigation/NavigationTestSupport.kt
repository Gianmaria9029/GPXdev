@file:Suppress("DEPRECATION")

package com.gpxnavigator.navigation

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.gpxnavigator.NavigatorApplication
import com.gpxnavigator.data.database.NavigatorDatabase
import com.gpxnavigator.data.gpx.LocalGpxRepository
import com.gpxnavigator.data.navigation.NavigationSessionStore
import com.gpxnavigator.data.navigation.SavedNavigationSession
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.location.LocationProvider
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

class NavigationTestApplication : NavigatorApplication() {
    lateinit var testRepository: LocalGpxRepository
    lateinit var testController: NavigationController
    override val gpxRepository get() = testRepository
    override val navigationController get() = testController
}

class FakeLocationProvider : LocationProvider {
    override val state = MutableStateFlow(LocationState())
    var permission = true
    var running = false
    var starts = 0
    var stops = 0
    override fun hasPermission() = permission
    override fun start() {
        if (running) return
        starts++
        running = true
        state.value = LocationState(if (permission) LocationStatus.WAITING else LocationStatus.DENIED)
    }
    override fun stop() {
        if (!running) return
        stops++
        running = false
        state.value = LocationState()
    }
}

class MemorySessionStore : NavigationSessionStore {
    var saved: SavedNavigationSession? = null
    var failSave = false
    var failClear = false
    var saveGate: CompletableDeferred<Unit>? = null
    val saveEntered = CompletableDeferred<Unit>()
    override suspend fun load() = saved
    override suspend fun save(session: SavedNavigationSession) {
        saveEntered.complete(Unit)
        saveGate?.await()
        if (failSave) throw IOException("Salvataggio fallito")
        saved = session
    }
    override suspend fun clear() {
        if (failClear) throw IOException("Chiusura fallita")
        saved = null
    }
}

abstract class NavigationTestSupport {
    @get:Rule val temporary = TemporaryFolder()
    protected lateinit var app: NavigationTestApplication
    protected lateinit var database: NavigatorDatabase
    protected lateinit var repository: LocalGpxRepository
    protected lateinit var provider: FakeLocationProvider
    protected lateinit var store: MemorySessionStore
    protected lateinit var controller: NavigationController
    protected lateinit var scope: CoroutineScope
    protected lateinit var trackId: String
    @Volatile protected var now = 100_000L

    @Before fun prepareNavigation() {
        app = RuntimeEnvironment.getApplication() as NavigationTestApplication
        app.getSharedPreferences("imported_gpx", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.databaseBuilder(app, NavigatorDatabase::class.java,
            File(temporary.root, "navigation.db").absolutePath).build()
        repository = LocalGpxRepository(app, database, temporary.newFolder("gpx"))
        app.testRepository = repository
        provider = FakeLocationProvider()
        store = MemorySessionStore()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = newController()
        trackId = runBlocking { repository.import(fixture("short-track.gpx")).imported.id!! }
    }

    protected fun newController(recordings: RecordingRepository? = null): NavigationController = NavigationController(repository, provider, store, scope,
        elapsedMillis = { now }, wallMillis = { 1_700_000_000_000L + now }, recordings = recordings)
        .also { app.testController = it }

    protected fun fixture(name: String): Uri {
        val bytes = javaClass.getResourceAsStream("/$name")!!.use { it.readBytes() }
        val uri = Uri.parse("content://navigation-test/$name")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    @After fun releaseNavigation() {
        scope.cancel()
        provider.stop()
        database.close()
    }
}
