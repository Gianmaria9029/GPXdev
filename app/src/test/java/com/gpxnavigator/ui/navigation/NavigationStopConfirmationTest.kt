package com.gpxnavigator.ui.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.ui.theme.NavigatorTheme
import java.io.File
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class NavigationStopConfirmationTest : NavigationTestSupport() {
    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) }

    private inner class Page(val activity: ComponentActivity) {
        var stops = 0
        var dashboards = 0
        fun dialog() = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        fun click(view: View, label: String) {
            fun root(node: View): ViewRootForTest? = if (node is ViewRootForTest) node else
                (node as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { root(group.getChildAt(it)) } }
            val owner = requireNotNull(root(view))
            owner.measureAndLayoutForTest()
            val node = owner.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).first {
                it.config.contains(SemanticsActions.OnClick) &&
                    (it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true ||
                        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true)
            }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            settle()
        }
        fun terminate() = click(activity.findViewById(android.R.id.content), "Termina")
        fun answer(label: String) = click(requireNotNull(dialog()).window!!.decorView, label)
        fun captureDialog(name: String) {
            val view = requireNotNull(dialog()).window!!.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/navigation-stop/$name.png").apply {
                parentFile?.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    private fun start(record: Boolean = false, free: Boolean = false) = runBlocking {
        if (record) controller = newController(RecordingRepository(database))
        controller.attachService()
        controller.startSession(if (free) null else trackId, record)
    }

    private fun showPage(theme: AppTheme = AppTheme.SOFT, landscape: Boolean = false,
        acceptStop: Boolean = true, check: (Page) -> Unit) {
        val previousLoader = Thread.currentThread().contextClassLoader
        // Select SDK TTF fonts for native graphics, rather than the app's offline PBF glyphs.
        Thread.currentThread().contextClassLoader = object : ClassLoader(previousLoader) {
            override fun getResource(name: String): java.net.URL? {
                if (name.startsWith("fonts/")) parent.getResources(name).asSequence()
                    .firstOrNull { it.protocol == "jar" }?.let { return it }
                return super.getResource(name)
            }
        }
        val activity = try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
        catch (exception: Throwable) { Thread.currentThread().contextClassLoader = previousLoader; throw exception }
        try {
            val page = Page(activity.get())
            activity.get().setContent {
                val session by controller.session.collectAsState()
                val recording by controller.recording.collectAsState()
                NavigatorTheme(theme) {
                    Surface(Modifier.fillMaxSize()) {
                        NavigationContent(NavigationUiState(session = session, recording = recording, loading = false),
                            onBack = { page.dashboards++ }, onChooseMap = {}, onReloadMap = {}, onToggleFollow = {},
                            onMapOrientation = {}, onMapAppearance = {}, onZoomIn = {}, onZoomOut = {}, onUserZoomChanged = {},
                            onManualCameraMove = {}, onRenderError = {}, onRetryLocation = {}, onLocationSettings = {},
                            onStopNavigation = {
                                page.stops++
                                if (!acceptStop) false else { runBlocking { controller.stopSession() }; true }
                            })
                    }
                }
            }
            settle()
            val view = activity.get().findViewById<View>(android.R.id.content)
            val width = if (landscape) 1600 else 800
            val height = if (landscape) 800 else 1600
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            settle()
            check(page)
        } finally {
            try { activity.pause().stop().destroy(); settle() }
            finally { Thread.currentThread().contextClassLoader = previousLoader }
        }
    }

    @Test fun noKeepsNavigationGpsAndSessionActiveInBothOrientations() {
        start()
        for (landscape in listOf(false, true)) showPage(landscape = landscape) { page ->
            page.terminate()
            assertNotNull(page.dialog())
            assertTrue(provider.running)
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertEquals(0, page.stops)
            page.answer("No")
            assertNull(page.dialog())
            assertEquals(0, page.stops)
            assertEquals(0, page.dashboards)
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertNotNull(store.saved)
            assertTrue(provider.running)
        }
    }

    @Test fun yesStopsNavigationAndReturnsToDashboardInBothThemes() {
        for (theme in AppTheme.entries) {
            start()
            showPage(theme, landscape = theme == AppTheme.CYBER) { page ->
                page.terminate()
                page.captureDialog("conferma-${theme.name.lowercase()}")
                assertEquals(0, page.stops)
                page.answer("Sì")
                assertEquals(1, page.stops)
                assertEquals(1, page.dashboards)
                assertEquals(SessionStatus.IDLE, controller.session.value.status)
                assertNull(store.saved)
                assertFalse(provider.running)
            }
        }
    }

    @Test fun backDismissesTheQuestionWithoutStoppingNavigation() {
        start()
        showPage { page ->
            page.terminate()
            (page.dialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            settle()
            assertNull(page.dialog())
            assertEquals(0, page.stops)
            assertEquals(0, page.dashboards)
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertTrue(provider.running)
        }
    }

    @Test fun freeAndGuidedRecordingsKeepTheirExistingSaveDecisionFlow() {
        for (free in listOf(false, true)) {
            start(record = true, free = free)
            showPage { page ->
                page.terminate()
                assertNull(page.dialog())
                assertEquals(1, page.stops)
                assertEquals(0, page.dashboards)
                assertTrue(controller.recording.value.pending)
                assertFalse(provider.running)
            }
            runBlocking { controller.discardRecording() }
        }
    }

    @Test fun failedStopRequestDoesNotNavigateAwayFromTheMap() {
        start()
        showPage(acceptStop = false) { page ->
            page.terminate()
            page.answer("Sì")
            assertEquals(1, page.stops)
            assertEquals(0, page.dashboards)
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertTrue(provider.running)
        }
    }
}
