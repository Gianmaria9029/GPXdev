package com.gpxnavigator.ui.navigation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.NavigationSessionState
import com.gpxnavigator.navigation.RecordingState
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.theme.NavigatorTheme
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class FreeRecordingLayoutTest {
    private val free = NavigationUiState(
        loading = false,
        session = NavigationSessionState(SessionStatus.ACTIVE, title = "Giro del lago", recordingId = "free"),
        recording = RecordingState(id = "free", active = true, distanceMeters = 12_500.0, startedAtMillis = 100_000),
        location = LocationState(LocationStatus.ACTIVE, LocationPoint(43.7, 11.2, 5f, 20f, null, 423.0), true),
        maximumSpeedMetersPerSecond = 25f, recordingElapsedMillis = 3_723_000,
    )
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    private class Page(val view: View, val root: ViewRootForTest, val stops: () -> Int) {
        fun nodes(): List<SemanticsNode> {
            root.measureAndLayoutForTest()
            return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        }
        fun text(label: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
        fun save(name: String) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/free-recording-layout/$name.png").apply {
                parentFile?.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    private fun page(state: NavigationUiState = free, theme: AppTheme = AppTheme.SOFT,
        width: Int = 800, height: Int = 400, fontScale: Float = 1f, check: (Page) -> Unit) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-${if (width > height) "land" else "port"}-xhdpi")
        val previousLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = object : ClassLoader(previousLoader) {
            override fun getResource(name: String): java.net.URL? {
                if (name.startsWith("fonts/")) parent.getResources(name).asSequence()
                    .firstOrNull { it.protocol == "jar" }?.let { return it }
                return super.getResource(name)
            }
        }
        val activity = try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
        catch (exception: Throwable) { Thread.currentThread().contextClassLoader = previousLoader; throw exception }
        var stops = 0
        try {
            activity.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                    NavigatorTheme(theme) {
                        Surface(Modifier.fillMaxSize()) {
                            NavigationContent(state, onBack = {}, onChooseMap = {}, onReloadMap = {}, onToggleFollow = {},
                                onMapOrientation = {}, onMapAppearance = {}, onZoomIn = {}, onZoomOut = {}, onUserZoomChanged = {},
                                onManualCameraMove = {}, onRenderError = {}, onRetryLocation = {}, onLocationSettings = {},
                                onStopNavigation = { stops++; true })
                        }
                    }
                }
            }
            settle()
            val view = activity.get().findViewById<View>(android.R.id.content)
            fun root(node: View): ViewRootForTest? = if (node is ViewRootForTest) node else
                (node as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) } }
            check(Page(view, requireNotNull(root(view)), { stops }))
        } finally {
            try { activity.pause().stop().destroy(); settle() }
            finally { Thread.currentThread().contextClassLoader = previousLoader }
        }
    }

    @Test fun landscapeHasTheNavigationInstrumentLayoutWithRecordingValuesInBothThemes() {
        for (theme in AppTheme.entries) page(theme = theme) { page ->
            val title = page.text("Giro del lago").boundsInRoot
            val gps = page.text("GPS · ±5 m").boundsInRoot
            assertEquals("Title and GPS must share one row", title.center.y, gps.center.y, 1f)
            assertTrue(gps.left >= title.right)
            assertTrue("Toolbar must fit in 48 dp", maxOf(title.bottom, gps.bottom) <= 96f)
            val speed = page.text("Velocità").boundsInRoot
            val time = page.text("Tempo giro").boundsInRoot
            val distance = page.text("Percorsi").boundsInRoot
            val elevation = page.text("Quota").boundsInRoot
            assertTrue(speed.left >= 1_200)
            assertTrue(time.top >= speed.bottom)
            assertTrue(distance.top >= time.bottom)
            assertEquals(distance.top, elevation.top, 1f)
            assertTrue(elevation.left >= distance.right)
            assertTrue(elevation.bottom <= page.view.height)
            for (label in listOf("72", "Vmax 90 km/h", "01:02:03", "12.5", "423")) page.text(label)
            assertTrue(page.nodes().none { it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                text.text == "Arrivo" || text.text == "Rimanenti" || text.text == "Prosegui"
            } == true })
            val stop = page.nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Termina") == true }
            stop.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, page.stops())
            page.save("${theme.name.lowercase()}-landscape")
        }
    }

    @Test fun longTitlesKeepGpsAndStopVisibleOnNarrowLandscapeScreens() {
        for (width in listOf(400, 568)) page(width = width, height = 280,
            state = free.copy(session = free.session.copy(title = "Giro del 1 novembre 2026 con titolo molto lungo"))) { page ->
            val gps = page.text("GPS · ±5 m").boundsInRoot
            val stop = page.nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Termina") == true
            }.boundsInRoot
            assertTrue(gps.width > 0)
            assertTrue(gps.right <= stop.left)
            assertTrue(stop.right <= page.view.width)
            assertTrue(stop.height >= 96f)
            assertTrue("Compact toolbar must stay 48 dp tall", stop.bottom <= 96f)
        }
    }

    @Test fun portraitShowsTheSameRecordingDataAndElapsedTime() {
        for (theme in AppTheme.entries) for (width in listOf(360, 400)) page(theme = theme, width = width, height = 800) { page ->
            for (label in listOf("Percorsi", "Velocità", "Tempo giro", "Quota", "01:02:03")) page.text(label)
            val title = page.text("Giro del lago").boundsInRoot
            val gps = page.text("GPS · ±5 m").boundsInRoot
            val home = page.nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Home") == true
            }
            assertTrue(home.config.contains(SemanticsActions.OnClick))
            assertTrue(page.nodes().none {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Home" } == true
            })
            assertTrue(title.width > 0)
            assertEquals(title.center.y, gps.center.y, 1f)
            assertTrue(gps.left >= title.right)
            assertTrue("Portrait toolbar must fit in 48 dp", maxOf(title.bottom, gps.bottom) <= 96f)
            page.save("${theme.name.lowercase()}-verticale${if (width == 400) "" else "-$width"}")
        }
    }

    @Test fun missingGpsKeepsTimeAndDistanceWithoutInventingSpeedOrAltitude() {
        page(state = free.copy(location = LocationState(), maximumSpeedMetersPerSecond = null)) { page ->
            page.text("01:02:03")
            page.text("12.5")
            page.text("Vmax — km/h")
            assertTrue(page.text("Velocità").config[SemanticsProperties.Text].any { it.text == "—" })
            assertTrue(page.text("Quota").config[SemanticsProperties.Text].any { it.text == "—" })
            page.save("gps-assente")
        }
    }

    @Test fun guidedRecordingKeepsArrivalAndRemainingDistance() {
        page(state = free.copy(session = free.session.copy(trackId = "gpx"))) { page ->
            page.text("Arrivo"); page.text("Rimanenti")
            assertTrue(page.nodes().none { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Tempo giro" } == true })
        }
    }

    @Test fun largeFontsInAShortWindowCanScrollToTheLowerInstruments() {
        page(width = 800, height = 300, fontScale = 1.6f) { page ->
            val scroll = page.nodes().last { it.config.contains(SemanticsActions.ScrollBy) }
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f)
            settle()
            val bounds = page.text("Quota").boundsInRoot
            assertTrue(bounds.height > 0 && bounds.bottom <= page.view.height)
            page.save("caratteri-grandi-finestra-bassa")
        }
    }
}
