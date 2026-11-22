package com.tuna.finlife.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.tuna.finlife.game.*
import com.tuna.finlife.simulation.*
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real Compose rendering with no emulator, native window, or additional test dependency.
 * PNGs in build/qa are visual review artifacts; semantic assertions catch empty/error rendering. */
class ProductLayoutTest {
    private fun session() = SimulationSessions.start(profile = MonthlyProfile.alexia().copy(name = "Maya"), configuration = MonthlyConfiguration(
        randomEvents = RandomEventSettings(enabled = false), annualSalaryGrowth = 0.0))

    private fun render(name: String, width: Int, height: Int, scale: Float = 1f, content: @Composable () -> Unit): List<String> {
        val scene = ImageComposeScene(width, height, Density(1f, scale), content = content)
        try {
            repeat(3) { scene.render(it * 1_000_000_000L).close() }
            val image = scene.render(4_000_000_000L)
            val png = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
            image.close()
            val file = File("build/qa/$name.png")
            file.parentFile.mkdirs(); file.writeBytes(png)
            assertTrue(png.size > 5000)
            fun texts(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap { texts(it) }
            return scene.semanticsOwners.flatMap { texts(it.unmergedRootSemanticsNode) }
        } finally { scene.close() }
    }

    @Test fun adventureRendersWideNarrowDarkAndLargeText() {
        val run = session()
        for ((name, width, height) in listOf(Triple("desktop", 1200, 900), Triple("mobile", 390, 844), Triple("small-window", 600, 480))) {
            val text = render(name, width, height, if (name == "mobile") 1.25f else 1f) {
                FinLifeTheme(if (name == "mobile") ThemeMode.Dark else ThemeMode.Light) {
                    AdventureScreen(run, {}, {}, {}, { _, _ -> }, WhatIfDraft(run), null, StartingData.Default, { true }, { _, _ -> })
                }
            }
            assertTrue(text.any { it.contains("8") })
            assertTrue(text.any { it.contains("FinLife") })
        }
    }

    @Test fun futureComparisonAndDeficitSummaryRender() {
        val run = session()
        val draft = WhatIfDraft(run).toggle(WhatIf.SharedFlat, true).toggle(WhatIf.Internship, true)
        val future = render("future", 900, 1200) {
            FinLifeTheme { GameScreen { FuturePreview(draft) } }
        }
        assertTrue(future.any { it.contains("300") || it.contains("900") })
        val ended = SimulationSessions.runUntilBlocked(run, 12)
        val summary = render("summary-deficit", 390, 844) {
            FinLifeTheme { SummaryScreen(ended, {}, {}, {}) }
        }
        assertTrue(summary.any { it.filter { c -> c.isDigit() }.contains("100528") })
    }

    @Test fun pendingDecisionAndSetupRenderAtPhoneWidth() {
        val waiting = SimulationSessions.advance(SimulationSessions.schedule(session(),
            LifeEvent.RentRise("rise", 0, MonthlyRentRule("initial-home", 0))))
        val pending = render("pending", 390, 844) {
            FinLifeTheme { AdventureScreen(waiting, {}, {}, {}, { _, _ -> }, WhatIfDraft(waiting), null, StartingData.Default, { true }, { _, _ -> }) }
        }
        assertTrue(pending.any { it.contains("800") })
        val setup = render("setup", 390, 844) { FinLifeTheme { SetupScreen(StartingData.Default, {}, {}) } }
        assertTrue(setup.any { it.contains("CHF") })
    }
}
