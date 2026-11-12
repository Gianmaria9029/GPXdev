@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.components

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.gpxnavigator.navigation.NavigationTestApplication
import com.gpxnavigator.navigation.NavigationTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class GpxExportViewModelTest : NavigationTestSupport() {
    @Test fun renameChangesSuggestedExportFilenameButNotGpxContents(): Unit = runBlocking {
        val bytes = repository.loadSelected()!!.file.readBytes()
        repository.rename(trackId, "Giro preferito")
        val model = GpxExportViewModel(app, SavedStateHandle())
        model.request(trackId)
        await { model.state.value.requestName != null }
        assertEquals("Giro preferito.gpx", model.state.value.requestName)
        model.pickerLaunched()
        val target = temporary.newFile("renamed.gpx")
        model.export(Uri.fromFile(target))
        await { model.state.value.message != null }
        assertEquals("GPX esportato.", model.state.value.message)
        assertArrayEquals(bytes, target.readBytes())
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Export timed out", condition())
    }

    @Test fun recreatedPickerExportsTheOriginalTrackEvenIfSelectionChanges(): Unit = runBlocking {
        val original = repository.loadSelected()!!
        val handle = SavedStateHandle()
        val first = GpxExportViewModel(app, handle)
        first.request(trackId)
        await { first.state.value.requestName != null }
        first.pickerLaunched()
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        repository.import(fixture("m10-single-point.gpx"))
        val reopened = GpxExportViewModel(app, restored)
        assertNull(reopened.state.value.requestName)
        val target = temporary.newFile("restored.gpx").apply { writeText("old data".repeat(10_000)) }
        reopened.export(Uri.fromFile(target))
        await { reopened.state.value.message != null }
        assertEquals("GPX esportato.", reopened.state.value.message)
        assertArrayEquals(original.file.readBytes(), target.readBytes())
        assertFalse(reopened.state.value.busy)
    }

    @Test fun cancellingThePickerAllowsAnotherExportWithoutChangingTheSource(): Unit = runBlocking {
        val source = repository.loadSelected()!!.file
        val bytes = source.readBytes()
        val model = GpxExportViewModel(app, SavedStateHandle())
        model.request(trackId)
        await { model.state.value.requestName != null }
        model.pickerLaunched()
        model.export(null)
        model.request(trackId)
        await { model.state.value.requestName != null }
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun missingSourceProducesAnErrorWithoutTruncatingTheDestination(): Unit = runBlocking {
        val model = GpxExportViewModel(app, SavedStateHandle())
        model.request(trackId)
        await { model.state.value.requestName != null }
        model.pickerLaunched()
        repository.delete(trackId)
        val target = temporary.newFile("unchanged.gpx").apply { writeText("keep") }
        model.export(Uri.fromFile(target))
        await { model.state.value.message != null }
        assertNotEquals("GPX esportato.", model.state.value.message)
        assertEquals("keep", target.readText())
        assertFalse(model.state.value.busy)
    }

    @Test fun unavailableDestinationReportsAnErrorAndAllowsRetry(): Unit = runBlocking {
        val model = GpxExportViewModel(app, SavedStateHandle())
        model.request(trackId)
        await { model.state.value.requestName != null }
        model.pickerLaunched()
        model.export(Uri.fromFile(temporary.newFolder("directory")))
        await { model.state.value.message != null }
        assertNotEquals("GPX esportato.", model.state.value.message)
        model.request(trackId)
        await { model.state.value.requestName != null }
    }

    @Test fun recreationDuringCopyReportsInterruptionAndAllowsRetry() {
        val model = GpxExportViewModel(app, SavedStateHandle(mapOf(
            "gpx_export_track_id" to trackId, "gpx_export_copying" to true)))
        assertTrue(model.state.value.message!!.contains("interrotta"))
        model.request(trackId)
        await { model.state.value.requestName != null }
    }
}
