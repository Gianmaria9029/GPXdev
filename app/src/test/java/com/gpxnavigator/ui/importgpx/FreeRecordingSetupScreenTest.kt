package com.gpxnavigator.ui.importgpx

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.gpxnavigator.domain.model.AppTheme
import com.gpxnavigator.ui.theme.NavigatorTheme
import java.io.File
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalComposeUiApi::class)
class FreeRecordingSetupScreenTest {
    private class Page(val view: View, val root: ViewRootForTest) {
        fun nodes(): List<SemanticsNode> {
            root.measureAndLayoutForTest()
            return root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true)
        }
        fun text(value: String): SemanticsNode = nodes().first { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun click(value: String) {
            assertTrue(text(value).config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun save(name: String) {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/recording-setup/$name.png").apply {
                parentFile?.mkdirs()
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        }
    }

    private fun settle() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)) }

    private fun preview(theme: AppTheme = AppTheme.SOFT, widthDp: Int = 400, heightDp: Int = 800,
        fontScale: Float = 1f, initialName: String = "Giro del lago",
        check: (Page, () -> String, () -> Int, () -> Int) -> Unit) {
        val previousLoader = Thread.currentThread().contextClassLoader
        // The app's offline PBF glyphs also live in fonts/. Native graphics needs the SDK's TTF
        // resources from the Android jar rather than the glyph directory on the test classpath.
        Thread.currentThread().contextClassLoader = object : ClassLoader(previousLoader) {
            override fun getResource(name: String): java.net.URL? {
                if (name.startsWith("fonts/")) parent.getResources(name).asSequence()
                    .firstOrNull { it.protocol == "jar" }?.let { return it }
                return super.getResource(name)
            }
        }
        val activity = try { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
        catch (exception: Throwable) { Thread.currentThread().contextClassLoader = previousLoader; throw exception }
        val name = mutableStateOf(initialName)
        var starts = 0
        var backs = 0
        try {
            activity.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                    NavigatorTheme(theme) {
                        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            FreeRecordingSetupScreen(name.value, { name.value = it }, { backs++ }, { starts++ },
                                enabled = true, awaitingPermission = false, error = null)
                        }
                    }
                }
            }
            settle()
            val view = activity.get().findViewById<View>(android.R.id.content)
            view.measure(View.MeasureSpec.makeMeasureSpec(widthDp * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightDp * 2, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, widthDp * 2, heightDp * 2)
            settle()
            fun findRoot(node: View): ViewRootForTest? = if (node is ViewRootForTest) node else
                (node as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { findRoot(group.getChildAt(it)) } }
            val root = requireNotNull(findRoot(view))
            check(Page(view, root), { name.value }, { starts }, { backs })
        } finally {
            try { activity.pause().stop().destroy(); settle() }
            finally { Thread.currentThread().contextClassLoader = previousLoader }
        }
    }

    @Test fun editsTheNameAndStartsOnlyAfterExplicitConfirmation() {
        preview { page, name, starts, _ ->
            assertEquals(0, starts())
            val field = page.nodes().first { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("Giro del passo")))
            settle()
            assertEquals("Giro del passo", name())
            assertEquals(0, starts())
            page.click("Inizia registrazione")
            assertEquals(1, starts())
        }
    }

    @Test fun invalidNameDisablesStartButKeepsTheWayBackAvailable() {
        preview(initialName = "Giro/lago") { page, _, starts, backs ->
            assertTrue(page.text("Inizia registrazione").config.contains(SemanticsProperties.Disabled))
            page.text("Usa un nome senza barre, virgolette o simboli riservati ai file.")
            page.click("Home")
            assertEquals(1, backs())
            assertEquals(0, starts())
        }
    }

    @Test fun portraitAndLandscapeHaveReadableSummariesAndVisibleStartInBothThemes() {
        for (theme in AppTheme.entries) for (landscape in listOf(false, true)) {
            preview(theme, if (landscape) 800 else 400, if (landscape) 400 else 800) { page, _, starts, _ ->
                for (title in listOf("Percorso e distanza", "Velocità", "Altitudine", "Orari del giro")) page.text(title)
                val start = page.text("Inizia registrazione").boundsInRoot
                assertTrue(start.height > 0)
                assertTrue(start.top >= 0 && start.bottom <= page.view.height)
                assertEquals(0, starts())
                page.save("${theme.name.lowercase()}-${if (landscape) "landscape" else "verticale"}")
            }
        }
    }

    @Test fun largeFontsInAShortWindowCanScrollToTheStartButton() {
        preview(widthDp = 380, heightDp = 300, fontScale = 1.6f) { page, _, starts, _ ->
            val scroll = page.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                it.config.contains(SemanticsActions.ScrollBy) }
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f))
            settle()
            page.save("caratteri-grandi-finestra-bassa")
            val start = page.text("Inizia registrazione").boundsInRoot
            assertTrue("Start: $start, viewport height: ${page.view.height}",
                start.height > 0 && start.top >= 0 && start.bottom <= page.view.height)
            page.click("Inizia registrazione")
            assertEquals(1, starts())
        }
    }
}
