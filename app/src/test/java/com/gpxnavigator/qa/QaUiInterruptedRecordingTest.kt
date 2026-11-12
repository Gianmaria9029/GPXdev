@file:Suppress("DEPRECATION")

package com.gpxnavigator.qa

import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import com.gpxnavigator.data.navigation.RecordingRepository
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import com.gpxnavigator.navigation.SessionStatus
import com.gpxnavigator.navigation.NavigationService
import com.gpxnavigator.domain.model.LocationPoint
import com.gpxnavigator.location.LocationState
import com.gpxnavigator.location.LocationStatus
import android.Manifest
import com.gpxnavigator.ui.importgpx.ImportGpxScreen
import com.gpxnavigator.ui.importgpx.ImportGpxViewModel
import com.gpxnavigator.ui.theme.NavigatorTheme
import androidx.lifecycle.ViewModelProvider
import java.time.Duration
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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

/** Exercises the actual Resume command with a persisted recording and a host Android service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class QaUiInterruptedRecordingTest : NavigationTestSupport() {
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))

    private fun awaitReady(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for $description", condition())
    }

    private fun disposeComposition(view: View) {
        if (view is AbstractComposeView) view.disposeComposition()
        else if (view is ViewGroup) for (index in 0 until view.childCount)
            disposeComposition(view.getChildAt(index))
    }

    private fun findRoot(view: View): ViewRootForTest? = if (view is ViewRootForTest) view else
        (view as? ViewGroup)?.let { group ->
            (0 until group.childCount).firstNotNullOfOrNull { findRoot(group.getChildAt(it)) }
        }

    private fun showDashboard(onOpenNavigation: () -> Unit, check: (ViewRootForTest) -> Unit) {
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
        try {
            val importModel = ViewModelProvider(activity.get())[ImportGpxViewModel::class.java]
            // Wait for Room and controller restoration before Compose installs its frame dispatcher.
            awaitReady("catalog and session restoration") {
                importModel.state.value.catalogLoaded && !importModel.state.value.loading &&
                    importModel.state.value.session.status != SessionStatus.RESTORING
            }
            activity.get().setContent {
                NavigatorTheme(AppTheme.SOFT) {
                    Surface(Modifier.fillMaxSize()) {
                        ImportGpxScreen(onOpenNavigation, AppTheme.SOFT, {}, false, viewModel = importModel)
                    }
                }
            }
            val view = activity.get().findViewById<View>(android.R.id.content)
            settle()
            view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 800, 1600)
            settle()
            check(requireNotNull(findRoot(view)))
        } finally {
            try {
                activity.pause().stop()
                // A failed assertion must also cancel Compose collectors and Activity ViewModels.
                disposeComposition(activity.get().findViewById(android.R.id.content))
                activity.get().viewModelStore.clear()
                shadowOf(Looper.getMainLooper()).idle()
                activity.destroy()
                shadowOf(Looper.getMainLooper()).idle()
            }
            finally { Thread.currentThread().contextClassLoader = previousLoader }
        }
    }

    private fun node(root: ViewRootForTest, label: String): SemanticsNode {
        root.measureAndLayoutForTest()
        return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
    }

    @Test fun interruptedFreeRecordingCanBeResumedFromDashboard() = runBlocking {
        val recordings = RecordingRepository(database)
        controller = newController(recordings)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION)
        controller.attachService()
        controller.startSession(null, record = true, recordingName = "QA synthetic ride")
        now += 3_000
        provider.state.value = LocationState(LocationStatus.ACTIVE,
            LocationPoint(43.7696, 11.2558, 5f, 2f, null, 50.0, now * 1_000_000L), true)
        withTimeout(5_000) { controller.recording.first { it.pointCount == 1 } }
        val originalId = controller.recording.value.id
        controller.detachService()
        controller = newController(recordings)
        withTimeout(5_000) { controller.session.first { it.status == SessionStatus.INTERRUPTED } }
        assertEquals(SessionStatus.INTERRUPTED, controller.session.value.status)
        assertNotNull(originalId)
        var opens = 0
        showDashboard({ opens++ }) { root ->
            val resume = node(root, "Riprendi")
            assertFalse("The offered Resume command must initially be available",
                resume.config.contains(SemanticsProperties.Disabled))
            assertTrue(resume.config[SemanticsActions.OnClick].action!!.invoke())
            settle()
            assertEquals("Resume must open navigation directly", 1, opens)
            val intent = shadowOf(app).nextStartedService
            assertNotNull("Resume must request the foreground navigation service", intent)
            assertEquals(NavigationService.ACTION_START, intent.action)
            assertNull(intent.getStringExtra(NavigationService.EXTRA_TRACK_ID))
            assertTrue(intent.getBooleanExtra(NavigationService.EXTRA_RECORD, false))
            assertNull("Resume must not replace the recording name", intent.getStringExtra(NavigationService.EXTRA_RECORDING_NAME))
            val service = Robolectric.buildService(NavigationService::class.java).create()
            try {
                service.get().onStartCommand(intent, 0, 1)
                awaitReady("resumed recording") { controller.session.value.status == SessionStatus.ACTIVE }
                assertEquals(originalId, controller.session.value.recordingId)
                assertEquals(originalId, controller.recording.value.id)
                assertEquals("QA synthetic ride", controller.session.value.title)
                assertEquals(1, controller.recording.value.pointCount)
                assertTrue(provider.running)
                assertTrue(controller.recording.value.active)
                val rendered = root as View
                val bitmap = Bitmap.createBitmap(rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
                rendered.draw(Canvas(bitmap))
                File("build/outputs/qa/ui-resumed-recording.png").apply {
                    parentFile?.mkdirs()
                    outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                bitmap.recycle()
            } finally {
                service.destroy()
            }
        }
        assertEquals(originalId, recordings.current()!!.id)
        assertEquals("QA synthetic ride", recordings.current()!!.trackName)
        assertEquals(1, recordings.points(originalId!!).size)
    }

}
