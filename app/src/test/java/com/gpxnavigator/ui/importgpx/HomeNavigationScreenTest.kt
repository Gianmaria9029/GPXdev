@file:Suppress("DEPRECATION")
package com.gpxnavigator.ui.importgpx

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.navigation.*
import com.gpxnavigator.ui.theme.NavigatorTheme
import com.gpxnavigator.ui.components.GpxExportViewModel
import com.gpxnavigator.ui.settings.DisplaySettingsViewModel
import com.gpxnavigator.ui.settings.OfflineMapSettingsViewModel
import com.gpxnavigator.domain.model.ScreenOrientation
import java.io.File
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.shadow.api.Shadow
import com.gpxnavigator.domain.model.TrackSortOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = NavigationTestApplication::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class HomeNavigationScreenTest : NavigationTestSupport() {
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            Thread.sleep(5)
        }
        settle()
        assertTrue("Timed out waiting for page data", condition())
    }

    private class Page(val view: View, val root: ViewRootForTest, val models: ViewModelProvider, val cockpits: () -> Int,
        val windows: () -> List<View>) {
        fun nodes(): List<SemanticsNode> {
            root.measureAndLayoutForTest()
            return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        }
        fun text(label: String): SemanticsNode = nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        }
        fun click(label: String) { assertTrue(text(label).config[SemanticsActions.OnClick].action!!.invoke()) }
        fun clickDescription(label: String) {
            assertTrue(nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true }
                .config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun type(value: String) {
            assertTrue(nodes().first { it.config.contains(SemanticsActions.SetText) }
                .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
        }
        fun windowNodes(): List<SemanticsNode> {
            fun roots(view: View): List<ViewRootForTest> = if (view is ViewRootForTest) listOf(view) else
                (view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { roots(group.getChildAt(it)) } }.orEmpty()
            val views = windows() + listOfNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)
            return views.flatMap { roots(it) }.distinct().flatMap {
                it.measureAndLayoutForTest()
                it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
            }
        }
        fun popupClick(label: String) {
            assertTrue(windowNodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true &&
                it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun back() { nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Indietro") == true }
            .config[SemanticsActions.OnClick].action!!.invoke() }
        fun reveal(label: String) {
            repeat(20) {
                val node = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
                val bounds = node?.boundsInRoot
                if (bounds != null && bounds.height > 0 && bounds.top >= 128 && bounds.bottom <= view.height) return
                val amount = if (bounds != null && bounds.height > 0)
                    if (bounds.top < 128) bounds.top - 140 else bounds.bottom - view.height + 24 else 300f
                nodes().first { it.config.contains(SemanticsActions.ScrollBy) }
                    .config[SemanticsActions.ScrollBy].action!!.invoke(0f, amount)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            }
            fail("Could not reveal $label")
        }
        fun save(name: String) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/navigation-redesign/$name.png").apply {
                parentFile?.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    private fun page(theme: AppTheme = AppTheme.SOFT, width: Int = 400, height: Int = 800, scale: Float = 1f,
        check: (Page, ImportGpxViewModel, () -> Int) -> Unit) {
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
        var opens = 0
        var cockpits = 0
        try {
            app.getSharedPreferences("offline_map", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("display_settings", Context.MODE_PRIVATE).edit().clear().commit()
            // AndroidViewModelFactory's singleton can retain the Application of another Robolectric test.
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
                    ImportGpxViewModel::class.java -> ImportGpxViewModel(app, SavedStateHandle())
                    DisplaySettingsViewModel::class.java -> DisplaySettingsViewModel(app)
                    OfflineMapSettingsViewModel::class.java -> OfflineMapSettingsViewModel(app)
                    else -> GpxExportViewModel(app, SavedStateHandle())
                } as T
            }
            val models = ViewModelProvider(activity.get(), factory)
            val model = models[ImportGpxViewModel::class.java]
            val export = models[GpxExportViewModel::class.java]
            val display = models[DisplaySettingsViewModel::class.java]
            models[OfflineMapSettingsViewModel::class.java]
            try {
                await { model.state.value.catalogLoaded && model.state.value.session.status != SessionStatus.RESTORING &&
                    display.state.value.settings != null }
            } catch (failure: AssertionError) {
                throw AssertionError("Initial state: ${model.state.value}; controller: ${controller.session.value}", failure)
            }
            activity.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, scale)) {
                    NavigatorTheme(theme) {
                        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            ImportGpxScreen({ opens++ }, theme, {}, false, model, export, onOpenCockpit = { cockpits++ })
                        }
                    }
                }
            }
            settle()
            val view = activity.get().findViewById<View>(android.R.id.content)
            view.measure(View.MeasureSpec.makeMeasureSpec(width * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height * 2, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width * 2, height * 2)
            settle()
            fun findRoot(node: View): ViewRootForTest? = if (node is ViewRootForTest) node else
                (node as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { findRoot(group.getChildAt(it)) } }
            check(Page(view, requireNotNull(findRoot(view)), models, { cockpits },
                { Shadow.extract<ShadowWindowManagerImpl>(activity.get().windowManager).views }), model, { opens })
        } finally {
            try {
                activity.pause().stop()
                fun dispose(view: View) {
                    if (view is AbstractComposeView) view.disposeComposition()
                    else if (view is ViewGroup) for (i in 0 until view.childCount) dispose(view.getChildAt(i))
                }
                dispose(activity.get().findViewById(android.R.id.content))
                activity.get().viewModelStore.clear()
                shadowOf(Looper.getMainLooper()).idle()
                activity.destroy()
                shadowOf(Looper.getMainLooper()).idle()
            } finally { Thread.currentThread().contextClassLoader = previousLoader }
        }
    }

    private fun openOriginal(page: Page, model: ImportGpxViewModel) {
        page.click("Segui una traccia"); settle()
        page.text("Importa GPX")
        page.reveal("Breve percorso a Firenze")
        page.click("Breve percorso a Firenze"); settle()
        await { model.detail.value.id == trackId && !model.detail.value.loading }
        page.text("Dettaglio traccia")
    }

    @Test fun librarySearchFavoritesAndBackNavigationKeepTheUsersContext() {
        runBlocking { repository.import(fixture("m10-single-point.gpx")) }
        page { page, model, opens ->
            page.click("Segui una traccia"); settle()
            page.type("  BREVE FIRENZE "); settle()
            assertEquals(listOf(trackId), model.state.value.visibleTracks.map { it.id })
            page.clickDescription("Aggiungi Breve percorso a Firenze ai preferiti")
            await { model.state.value.tracks.single { it.id == trackId }.isFavorite && !model.state.value.working }
            page.text("Le mie tracce")
            assertEquals(0, opens())
            page.click("Solo preferiti"); settle()
            page.click("Breve percorso a Firenze"); settle()
            await { !model.detail.value.loading }
            page.back(); settle()
            assertEquals("  BREVE FIRENZE ", model.state.value.query)
            assertTrue(model.state.value.favoritesOnly)
            page.text("Breve percorso a Firenze")
            page.clickDescription("Rimuovi Breve percorso a Firenze dai preferiti")
            await { model.state.value.visibleTracks.isEmpty() && !model.state.value.working }
            page.text("Nessuna traccia trovata")
            page.click("Cancella ricerca e filtri"); settle()
            assertEquals(2, model.state.value.visibleTracks.size)
            assertEquals("", model.state.value.query)
            assertFalse(model.state.value.favoritesOnly)
        }
    }

    @Test fun librarySortMenuPersistsTheChosenOrder() {
        // Popup position tracking awaits each frame; a nonzero interval keeps virtual time advancing.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        page { page, model, _ ->
            page.click("Segui una traccia"); settle()
            page.click("Aggiunte di recente"); settle()
            page.popupClick("Nome A–Z")
            await { model.state.value.sortOrder == TrackSortOrder.NAME && !model.state.value.working }
            page.text("Nome A–Z")
            page.back(); settle()
            page.click("Segui una traccia"); settle()
            page.text("Nome A–Z")
        }
    }

    @Test fun renameDialogRejectsEmptyNamesAndSavesTheArchiveName() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        page { page, model, _ ->
            openOriginal(page, model)
            page.clickDescription("Azioni della traccia"); settle()
            page.popupClick("Rinomina traccia"); settle()
            fun input(value: String) {
                page.windowNodes().first { it.config.contains(SemanticsActions.SetText) }
                    .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))
                settle()
            }
            input(" ")
            assertTrue(page.windowNodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Salva nome" } == true }
                .config.contains(SemanticsProperties.Disabled))
            input("Giro del sabato")
            page.popupClick("Salva nome")
            await { model.state.value.tracks.single().title == "Giro del sabato" && !model.state.value.working }
            page.text("Giro del sabato")
            assertFalse(ShadowDialog.getLatestDialog().isShowing)
            page.back(); settle()
            page.text("Giro del sabato")
        }
    }

    @Test fun libraryRendersInBothThemesAndOrientationsAndWithLargeFonts() {
        runBlocking {
            repository.setFavorite(trackId, true)
            repository.import(fixture("m10-single-point.gpx"))
        }
        for (theme in AppTheme.entries) for (landscape in listOf(false, true)) {
            page(theme, if (landscape) 840 else 400, if (landscape) 400 else 800) { page, _, _ ->
                page.click("Segui una traccia"); settle()
                page.text("Cerca una traccia…")
                page.text("Solo preferiti")
                page.save("137-${theme.name.lowercase()}-archivio-${if (landscape) "landscape" else "verticale"}")
                page.reveal("Breve percorso a Firenze")
            }
        }
        page(width = 380, height = 320, scale = 1.6f) { page, _, _ ->
            page.click("Segui una traccia"); settle()
            page.reveal("Solo preferiti")
            page.save("137-archivio-caratteri-grandi-filtri")
            page.reveal("Breve percorso a Firenze")
            page.save("137-archivio-caratteri-grandi-tracce")
        }
    }

    @Test fun returningFromDetailsRestoresTheScrolledPosition() {
        runBlocking {
            repeat(12) { index ->
                val id = repository.import(fixture("short-track.gpx")).imported.id!!
                repository.rename(id, "Giro ${index.toString().padStart(2, '0')}")
            }
            repository.setSortOrder(TrackSortOrder.NAME)
        }
        page { page, model, _ ->
            page.click("Segui una traccia"); settle()
            page.reveal("Giro 07")
            val before = page.text("Giro 07").boundsInRoot.top
            page.click("Giro 07"); settle()
            await { !model.detail.value.loading }
            page.back(); settle()
            assertEquals(before, page.text("Giro 07").boundsInRoot.top, 2f)
        }
    }

    @Test fun browseDoesNotSelectAndStartUsesTheChosenTrackAndRecordingCheckbox() {
        val selected = runBlocking { repository.import(fixture("m10-single-point.gpx")).imported.id }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        page { page, model, opens ->
            openOriginal(page, model)
            assertEquals(selected, runBlocking { repository.loadSelected()!!.id })
            assertEquals(0, opens())
            assertNull(shadowOf(app).nextStartedService)
            page.click("Registra anche il mio percorso"); settle()
            assertTrue(model.state.value.recordGpx)
            page.click("Avvia navigazione"); settle()
            assertEquals(1, opens())
            val request = requireNotNull(shadowOf(app).nextStartedService)
            assertEquals(trackId, request.getStringExtra(NavigationService.EXTRA_TRACK_ID))
            assertTrue(request.getBooleanExtra(NavigationService.EXTRA_RECORD, false))
            assertNull(model.detail.value.id)
        }
    }

    @Test fun browsingAnActiveTrackKeepsItsSessionAndDoesNotOfferAnotherStart() {
        runBlocking {
            controller.attachService()
            controller.startSession(trackId)
        }
        page { page, model, opens ->
            page.text("Navigazione in corso")
            page.text("Torna alla mappa")
            openOriginal(page, model)
            assertTrue(page.nodes().none { node -> node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == "Avvia navigazione" || it.text == "Registra anche il mio percorso"
            } == true })
            page.click("Torna alla mappa"); settle()
            assertEquals(1, opens())
            assertEquals(SessionStatus.ACTIVE, controller.session.value.status)
            assertEquals(trackId, controller.session.value.trackId)
        }
    }

    @Test fun homeAndDetailsRenderInBothOrientationsAndThemes() {
        for (theme in AppTheme.entries) for (landscape in listOf(false, true)) {
            page(theme, if (landscape) 840 else 400, if (landscape) 400 else 800) { page, model, _ ->
                val logo = page.nodes().single {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("GPXdev by gdev") == true
                }.boundsInRoot
                val settings = page.text("Impostazioni").boundsInRoot
                assertTrue("Logo must be to the left of settings", logo.right <= settings.left)
                assertEquals("Logo and settings must share a row", logo.center.y, settings.center.y, 1f)
                assertTrue("The introduction must be below the logo", page.text("Dashboard").boundsInRoot.top >= logo.bottom)
                for (label in listOf("Segui una traccia", "Registra un giro", "Cockpit", "Esplora mappa")) {
                    val bounds = page.text(label).boundsInRoot
                    assertTrue("Home action must be fully visible: $label $bounds", bounds.top >= 0 && bounds.bottom <= page.view.height)
                }
                page.save("${theme.name.lowercase()}-home-${if (landscape) "landscape" else "verticale"}")
                openOriginal(page, model)
                page.text("Distanza del percorso")
                val start = page.text("Avvia navigazione").boundsInRoot
                assertTrue("Start remains in the viewport: $start", start.top >= 0 && start.bottom <= page.view.height)
                page.save("${theme.name.lowercase()}-dettaglio-${if (landscape) "landscape" else "verticale"}")
                page.back(); settle()
                page.text("Le mie tracce")
                page.back(); settle()
                page.text("Dashboard")
                page.click("Impostazioni"); settle()
                await { !page.models[OfflineMapSettingsViewModel::class.java].state.value.busy }
                page.text("Soft"); page.text("Cyber")
                page.text("Mappe offline")
                page.save("${theme.name.lowercase()}-impostazioni-${if (landscape) "landscape" else "verticale"}")
            }
        }
    }

    @Test fun largeFontsInShortWindowsCanScrollToTheDepartureControls() {
        page(width = 380, height = 320, scale = 1.6f) { page, model, _ ->
            openOriginal(page, model)
            val scroll = page.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                it.config.contains(SemanticsActions.ScrollBy) }
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 30_000f)
            settle()
            val start = page.text("Avvia navigazione").boundsInRoot
            assertTrue("Start: $start", start.top >= 0 && start.bottom <= page.view.height)
            page.save("dettaglio-caratteri-grandi-finestra-bassa")
        }
    }

    @Test fun cockpitPreparationDoesNotRecordUntilStartAndThenRequestsAFreeRecording() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        page { page, _, opens ->
            page.click("Cockpit"); settle()
            page.text("Prepara Cockpit")
            assertNull(shadowOf(app).nextStartedService)
            assertEquals(0,page.cockpits())
            assertEquals(SessionStatus.IDLE,controller.session.value.status)
            page.click("Avvia Cockpit"); settle()
            assertEquals(1,page.cockpits())
            assertEquals(0,opens())
            val request = requireNotNull(shadowOf(app).nextStartedService)
            assertTrue(request.getBooleanExtra(NavigationService.EXTRA_RECORD,false))
            assertNull(request.getStringExtra(NavigationService.EXTRA_TRACK_ID))
            assertNotNull(request.getStringExtra(NavigationService.EXTRA_RECORDING_NAME))
            assertEquals("COCKPIT",request.getStringExtra(NavigationService.EXTRA_ACTIVITY_MODE))
        }
    }

    @Test fun openingCockpitReusesTheRunningRecordingWithoutSendingAnotherStart() {
        controller = newController(com.gpxnavigator.data.navigation.RecordingRepository(database))
        runBlocking { controller.attachService();controller.startSession(null,true,mode = com.gpxnavigator.domain.model.ActivityMode.COCKPIT) }
        val id = controller.recording.value.id
        page { page, _, opens ->
            page.click("Riprendi Cockpit"); settle()
            assertEquals(1,page.cockpits())
            assertEquals(0,opens())
            assertNull(shadowOf(app).nextStartedService)
            assertEquals(id,controller.recording.value.id)
            assertEquals(SessionStatus.ACTIVE,controller.session.value.status)
        }
    }

    @Test fun anotherActivityKeepsCockpitDisabledUntilThePreviousTripEnds() {
        controller = newController(com.gpxnavigator.data.navigation.RecordingRepository(database))
        runBlocking { controller.attachService();controller.startSession(null,true) }
        page { page, _, _ ->
            assertTrue(page.text("Cockpit").config.contains(SemanticsProperties.Disabled))
            assertEquals(0,page.cockpits())
            page.text("Termina attività")
            assertEquals(com.gpxnavigator.domain.model.ActivityMode.FREE_RECORDING,controller.session.value.mode)
        }
    }

    @Test fun screenSettingsCanBeChangedAndStoredAndTheRecordingPageUsesTheNewTitle() {
        page { page, _, opens ->
            page.text("Dashboard")
            page.text("Scegli una funzione")
            page.click("Registra un giro"); settle()
            page.text("Registra il tuo giro")
            assertEquals(0, opens())
            page.nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Home" } == true }
                .config[SemanticsActions.OnClick].action!!.invoke()
            settle()
            page.click("Impostazioni"); settle()
            await { !page.models[OfflineMapSettingsViewModel::class.java].state.value.busy }
            page.text("Importa o sostituisci mappa")
            val preferences = app.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
            page.reveal("Sempre orizzontale")
            page.click("Sempre orizzontale"); settle()
            await { preferences.all["orientation"] == ScreenOrientation.LANDSCAPE.preferenceValue }
            page.save("impostazioni-schermo-e-orientamento")
            page.click("Sempre verticale"); settle()
            await { preferences.all["orientation"] == ScreenOrientation.PORTRAIT.preferenceValue }
            page.reveal("Automatico")
            page.click("Automatico"); settle()
            await { preferences.all["orientation"] == ScreenOrientation.AUTOMATIC.preferenceValue }
        }
    }

    @Test fun settingsRemainReachableWithLargeFontsAndAShortWindow() {
        page(width = 380, height = 320, scale = 1.6f) { page, _, _ ->
            page.click("Impostazioni"); settle()
            await { !page.models[OfflineMapSettingsViewModel::class.java].state.value.busy }
            page.reveal("Importa o sostituisci mappa")
            page.reveal("Sempre orizzontale")
            page.click("Sempre orizzontale"); settle()
            assertEquals(ScreenOrientation.LANDSCAPE, page.models[DisplaySettingsViewModel::class.java].state.value.settings!!.orientation)
            page.save("impostazioni-caratteri-grandi-finestra-bassa")
        }
    }
}
