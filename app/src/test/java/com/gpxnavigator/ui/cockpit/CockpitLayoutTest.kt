package com.gpxnavigator.ui.cockpit

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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.domain.model.ActivityMode
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import com.gpxnavigator.navigation.*
import com.gpxnavigator.data.database.RecordingPointEntity
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
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class CockpitLayoutTest {
    private val example = CockpitUiState(
        session = NavigationSessionState(SessionStatus.ACTIVE, title = "Giro sui colli", recordingId = "trip", mode = ActivityMode.COCKPIT),
        recording = RecordingState(id = "trip", active = true, distanceMeters = 46_800.0, startedAtMillis = 1_700_000_000_000L,
            mode = ActivityMode.COCKPIT, title = "Giro sui colli", endedAtMillis = 1_700_004_354_000L, pointCount = 1532,
            statistics = RecordingStatistics(maximumSpeedPoint = RecordingPointEntity("trip", 0, 0, 43.0, 11.0,
                846.0, 1_700_000_000_000L, 1_000L, 4f, 104f / 3.6f, true),
                minimumElevationMeters = 214.0, maximumElevationMeters = 1_128.0,
                elevationDifferenceMeters = 632.0, movingAverageSpeedMetersPerSecond = 52.6 / 3.6,
                startElevationMeters = 214.0, endElevationMeters = 846.0)),
        location = LocationState(LocationStatus.ACTIVE, LocationPoint(43.0,11.0,4f,73f / 3.6f,null,846.0),true),
        elapsedMillis = 4_354_000,
    )
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    private class Page(val view: View, val root: ViewRootForTest, val stops: () -> Int, val maps: () -> Int, val homes: () -> Int) {
        fun nodes() : List<SemanticsNode> {
            root.measureAndLayoutForTest()
            return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        }
        fun text(label: String) = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true }
        fun save(name: String) {
            val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/cockpit/$name.png").apply {
                parentFile?.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            bitmap.recycle()
        }
    }

    private fun page(theme: AppTheme = AppTheme.SOFT, width: Int = 360, height: Int = 720,
        scale: Float = 1f, state: CockpitUiState = example, summary: Boolean = false, check: (Page) -> Unit) {
        RuntimeEnvironment.setQualifiers("w${width}dp-h${height}dp-${if(width > height) "land" else "port"}-xhdpi")
        val loader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = object : ClassLoader(loader) {
            override fun getResource(name: String): java.net.URL? {
                if (name.startsWith("fonts/")) parent.getResources(name).asSequence().firstOrNull { it.protocol == "jar" }?.let { return it }
                return super.getResource(name)
            }
        }
        val activity = try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
        catch (failure: Throwable) { Thread.currentThread().contextClassLoader = loader; throw failure }
        var stops = 0
        var maps = 0
        var homes = 0
        try {
            activity.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f,scale)) {
                    NavigatorTheme(theme) { Surface(Modifier.fillMaxSize()) {
                        if (summary) CockpitSummaryScreen(state.recording, false, null, { maps++ }, { stops++ })
                        else CockpitContent(state, { homes++ }, { stops++;true }, {}, {})
                    } }
                }
            }
            settle()
            val view = activity.get().findViewById<View>(android.R.id.content)
            fun root(node: View): ViewRootForTest? = if (node is ViewRootForTest) node else
                (node as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) } }
            check(Page(view,requireNotNull(root(view)),{stops},{maps},{homes}))
        } finally {
            try { activity.pause().stop().destroy();settle() }
            finally { Thread.currentThread().contextClassLoader = loader }
        }
    }

    @Test fun allTenValuesFitWithoutAnyScrollInBothThemesOrientationsAndLargeFonts() {
        for (theme in AppTheme.entries) for ((width,height) in listOf(360 to 720, 320 to 640, 800 to 360, 640 to 320)) {
            for (scale in listOf(1f,1.6f)) page(theme,width,height,scale) { page ->
                val nodes = page.nodes()
                page.save("latest-${theme.name.lowercase()}-${width}-${height}-$scale")
                assertFalse(nodes.any { it.config.contains(SemanticsActions.ScrollBy) ||
                    it.config.contains(SemanticsProperties.VerticalScrollAxisRange) || it.config.contains(SemanticsProperties.HorizontalScrollAxisRange) })
                val metrics = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("cockpit-metric-") == true }
                assertEquals(10, metrics.size)
                metrics.forEach { node ->
                    val bounds = node.boundsInRoot
                    assertTrue("Metric outside screen: $bounds", bounds.width > 0 && bounds.height > 0 && bounds.top >= 0 &&
                        bounds.left >= 0 && bounds.right <= page.view.width && bounds.bottom <= page.view.height)
                }
                nodes.filter { node -> metrics.any { it.boundsInRoot.contains(node.boundsInRoot.center) } }.forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { result ->
                        assertFalse("Clipped ${result.layoutInput.text} at $width x $height, font $scale", result.hasVisualOverflow)
                    }
                }
                if (scale == 1f && (width == 360 || width == 800)) page.save("${theme.name.lowercase()}-${if(width>height) "landscape" else "portrait"}")
            }
        }
    }

    @Test fun missingGpsKeepsAccumulatedValuesButDoesNotShowStaleSpeedOrAltitude() {
        page(state = example.copy(location = LocationState(LocationStatus.UNAVAILABLE))) { page ->
            assertEquals(2,page.nodes().count { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "—" } == true })
            page.text("01:12:34")
            page.text("GPS assente")
            assertTrue(page.nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "104" } == true })
        }
    }

    @Test fun cockpitHasOnlyHomeAndStopWithoutAnyMapAccess() {
        page { page ->
            assertFalse(page.nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Apri la mappa") == true })
            page.text("Home").config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1,page.homes())
            assertEquals(0,page.stops())
            page.text("Termina").config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1,page.stops())
        }
    }

    @Test fun summaryShowsTwelveRecordedValuesWithoutScrollingAndOffersSaveOrDiscard() {
        for (theme in AppTheme.entries) for ((width,height) in listOf(360 to 720,640 to 320))
            for (scale in listOf(1f,1.6f)) page(theme,width,height,scale,summary = true) { page ->
                val nodes = page.nodes()
                val metrics = nodes.filter { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("cockpit-metric-") == true }
                assertEquals(12,metrics.size)
                assertFalse(nodes.any { it.config.contains(SemanticsActions.ScrollBy) })
                page.text("Riepilogo Cockpit")
                page.text("Quota di partenza"); page.text("Quota finale"); page.text("Ora di fine")
                page.text("01:12:34")
                metrics.forEach { assertTrue(it.boundsInRoot.bottom <= page.view.height && it.boundsInRoot.height > 0) }
                nodes.filter { node -> metrics.any { it.boundsInRoot.contains(node.boundsInRoot.center) } }.forEach { node ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { assertFalse("Clipped summary ${it.layoutInput.text} at $width x $height / $scale",it.hasVisualOverflow) }
                }
                page.save("summary-${theme.name.lowercase()}-${width}-$scale")
                page.text("Scarta giro").config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1,page.stops())
                assertEquals(0,page.maps())
                assertFalse(page.text("Salva giro").config.contains(SemanticsProperties.Disabled))
                page.text("Salva giro").config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1,page.maps())
            }
    }

    @Test fun emptyCockpitSummaryKeepsItsTimesAndOnlyOffersDiscard() {
        page(width = 640,height = 320,scale = 1.6f,
            state = example.copy(recording = example.recording.copy(pointCount = 0,statistics = RecordingStatistics(),distanceMeters = 0.0)),
            summary = true) { page ->
            assertEquals(4,page.nodes().count { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("cockpit-metric-") == true })
            assertTrue(page.text("Salva giro").config.contains(SemanticsProperties.Disabled))
            assertFalse(page.text("Scarta giro").config.contains(SemanticsProperties.Disabled))
            page.text("01:12:34")
            assertFalse(page.nodes().any { it.config.contains(SemanticsActions.ScrollBy) })
        }
    }
}
