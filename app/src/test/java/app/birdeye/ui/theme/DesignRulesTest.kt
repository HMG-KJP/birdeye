package app.birdeye.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.birdeye.core.Copy
import app.birdeye.core.Screen
import app.birdeye.core.UiState
import app.birdeye.ui.component.Accent
import app.birdeye.ui.component.bandStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** DESIGN.md turned into checks: breaking a design rule must fail the build. */
class DesignRulesTest {
    private fun contrast(foreground: Color, background: Color): Double {
        val fg = foreground.compositeOver(background).luminance().toDouble()
        val bg = background.luminance().toDouble()
        return (max(fg, bg) + 0.05) / (min(fg, bg) + 0.05)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$name contrast is $ratio, needs 4.5", ratio >= 4.5)
    }

    private val palettes = listOf("dark" to Tokens.dark, "light" to Tokens.light)

    @Test
    fun `text and icons reach 4_5 to 1 on every surface in both themes`() {
        palettes.forEach { (theme, c) ->
            listOf("background" to c.background, "surface" to c.surface, "surfaceHigh" to c.surfaceHigh).forEach { (surface, bg) ->
                listOf(
                    "textPrimary" to c.textPrimary,
                    "textSecondary" to c.textSecondary,
                    "textTertiary" to c.textTertiary,
                ).forEach { (name, fg) -> assertReadable("$theme $name on $surface", fg, bg) }
            }
            listOf("background" to c.background, "surface" to c.surface).forEach { (surface, bg) ->
                listOf(
                    "siren" to c.siren,
                    "talk" to c.talk,
                    "outside" to c.outside,
                    "vehicle" to c.vehicle,
                ).forEach { (name, fg) -> assertReadable("$theme $name on $surface", fg, bg) }
            }
        }
    }

    @Test
    fun `filled controls stay readable in both themes`() {
        palettes.forEach { (theme, c) ->
            assertReadable("$theme onTalk", c.onTalk, c.talk)
            assertReadable("$theme onOutside", c.onOutside, c.outside)
            assertReadable("$theme onFilled", c.onFilled, c.buttonFilled)
            assertReadable("$theme basic button", c.textPrimary, c.buttonBasic.compositeOver(c.surface))
            assertReadable("$theme selected seat", c.textPrimary, c.seatActive)
            assertReadable("$theme toast", c.onToast, c.toast)
            assertReadable("$theme car outline", c.carLine, c.background)
            val track = c.tabBackground.compositeOver(c.background)
            assertReadable("$theme selected tab", c.textPrimary, c.tabSelected.compositeOver(track))
            assertReadable("$theme other tab", c.textSecondary, track)
        }
    }

    @Test
    fun `no text style is below twelve sp`() {
        Tokens.typeScale.forEach { (name, style) ->
            FormFactor.entries.forEach { ff ->
                assertTrue("$name on $ff", style.size.of(ff) >= 12f)
            }
        }
    }

    /** Millimetres per dp on each display, worked out from its size and resolution; hangul is about 0.9em tall. */
    private val mmPerDp = mapOf(
        FormFactor.S to 0.147,
        FormFactor.L to 0.149,
        FormFactor.W to 0.152, // W-Extended, the smallest of the wide displays
        FormFactor.WF to 0.148,
    )

    private fun glyphHeightMm(style: TextToken, ff: FormFactor) = style.size.of(ff) * mmPerDp.getValue(ff) * 0.9

    @Test
    fun `key information is at least five millimetres tall on every display`() {
        listOf(Tokens.headlineLarge, Tokens.headlineSmall, Tokens.alertGuidance).forEach { style ->
            FormFactor.entries.forEach { ff ->
                assertTrue("${style.size} on $ff is ${glyphHeightMm(style, ff)}mm", glyphHeightMm(style, ff) >= 5.0)
            }
        }
    }

    @Test
    fun `general text is at least three millimetres tall on every display`() {
        FormFactor.entries.forEach { ff ->
            assertTrue("body small on $ff", glyphHeightMm(Tokens.bodySmall, ff) >= 3.0)
        }
    }

    @Test
    fun `the screen band depends only on the state so it cannot blink with the cabin light`() {
        assertEquals(null, bandStyle(UiState(screen = Screen.MAIN)))
        assertEquals(Accent.TALK, bandStyle(UiState(screen = Screen.TALK))?.accent)
        assertEquals(Accent.OUTSIDE, bandStyle(UiState(screen = Screen.WINDOW))?.accent)
    }

    private fun mainSources(): List<File> =
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `no animation or auto scrolling except the loader`() {
        val forbidden = Regex("""animate[A-Z]\w*\(|rememberInfiniteTransition|basicMarquee|AnimatedVisibility|AnimatedContent""")
        val hits = mainSources()
            .filterNot { it.name == "Loader.kt" }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `colors and sizes come only from the tokens`() {
        val rawColor = Regex("""Color\(0x""")
        val rawSp = Regex("""\b\d+(\.\d+)?\.sp\b""")
        val hits = File("src/main/java/app/birdeye").walkTopDown()
            .filter { it.extension == "kt" && it.name != "Tokens.kt" }
            .filter { rawColor.containsMatchIn(it.readText()) || rawSp.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `no brand names appear in anything shown to the driver`() {
        val brand = Regex("""현대|기아|제네시스|Hyundai|Kia|Genesis|Pleos""", RegexOption.IGNORE_CASE)
        val shown = listOf(File("src/main/java/app/birdeye/core/Copy.kt")) +
            File("src/main/java/app/birdeye/ui").walkTopDown().filter { it.extension == "kt" } +
            File("src/main/res/values/strings.xml")

        val hits = shown.flatMap { file ->
            Regex("\"([^\"]*)\"").findAll(file.readText())
                .map { it.groupValues[1] }
                .filter { brand.containsMatchIn(it) }
                .map { "${file.name}: $it" }
        }

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `driver-facing words never mention the script behind a demo`() {
        val script = Regex("\"[^\"\n]*대본[^\"\n]*\"")
        val hits = mainSources().flatMap { file ->
            script.findAll(file.readText()).map { "${file.name}: ${it.value}" }.toList()
        }

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `no emoji anywhere in the app`() {
        val emoji = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]")
        val scripts = File("src/main/assets/demo").listFiles().orEmpty().toList()
        assertEquals(2, scripts.size)
        val hits = (mainSources() + scripts)
            .filter { emoji.containsMatchIn(it.readText()) }
            .map { it.name }

        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `fixed wording stays under the line length limit`() {
        val copy = Copy.DEFAULT_REPLIES + Copy.WINDOW_REPLIES + Copy.WINDOW_INTRO
        copy.forEach { assertTrue("too long: $it", it.length <= 80) }
    }

    @Test
    fun `the app uses the typeface the guide names`() {
        val tokens = File("src/main/java/app/birdeye/ui/theme/Tokens.kt").readText()

        assertTrue(tokens.contains("R.font.asta_sans"))
        assertFalse(File("src/main/res/font/noto_sans_kr.ttf").exists())
        assertTrue(File("src/main/res/font/asta_sans.ttf").exists())
    }

    @Test
    fun `the app does not lock its orientation`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("screenOrientation"))
    }
}
