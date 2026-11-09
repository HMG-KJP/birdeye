package app.birdeye.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The numbers below are copied from the official design files; see DESIGN.md. */
class TokensTest {
    @Test
    fun `dark colors match the official system tokens`() {
        val c = Tokens.dark
        assertEquals(Color(0xFF131417), c.background)
        assertEquals(Color(0xFF1D1E21), c.surface)
        assertEquals(Color(0xFF313236), c.surfaceHigh)
        assertEquals(Color(0xFFFFFFFF), c.textPrimary)
        assertEquals(Color(0xD6FFFFFF), c.textSecondary)
        assertEquals(Color(0xA3FFFFFF), c.textTertiary)
        assertEquals(Color(0x52FFFFFF), c.textDisabled)
        assertEquals(Color(0x0DFFFFFF), c.buttonBasic)
        assertEquals(Color(0x1AFFFFFF), c.buttonBasicPressed)
        assertEquals(Color(0xFFE0E1E6), c.buttonFilled)
        assertEquals(Color(0xFF131417), c.onFilled)
        assertEquals(Color(0x33FFFFFF), c.tabSelected)
        assertEquals(Color(0xFFE0E1E6), c.toast)
        assertEquals(Color(0xFF131417), c.onToast)
    }

    @Test
    fun `light colors match the official system tokens`() {
        val c = Tokens.light
        assertEquals(Color(0xFFF7F8FA), c.background)
        assertEquals(Color(0xFFFFFFFF), c.surface)
        assertEquals(Color(0xFFEDEEF2), c.surfaceHigh)
        assertEquals(Color(0xFF131417), c.textPrimary)
        assertEquals(Color(0xA3131417), c.textTertiary)
        assertEquals(Color(0xFF44464E), c.buttonFilled)
        assertEquals(Color(0xFFFFFFFF), c.onFilled)
        assertEquals(Color(0xFFFFFFFF), c.tabSelected)
        assertEquals(Color(0xFF44464E), c.toast)
    }

    @Test
    fun `the meaning colors are the ones in the design document`() {
        assertEquals(Color(0xFFFF9A3C), Tokens.dark.siren)
        assertEquals(Color(0xFF3FD0E8), Tokens.dark.talk)
        assertEquals(Color(0xFFF5C542), Tokens.dark.outside)
        assertEquals(Color(0xFFC9D3DE), Tokens.dark.vehicle)
    }

    @Test
    fun `type sizes and line heights follow the official scale on every form factor`() {
        fun check(style: TextToken, sizes: List<Int>, heights: List<Int>) {
            FormFactor.entries.forEachIndexed { i, ff ->
                assertEquals(sizes[i].toFloat(), style.size.of(ff))
                assertEquals(heights[i].toFloat(), style.lineHeight.of(ff))
            }
        }
        check(Tokens.headlineLarge, listOf(60, 53, 50, 64), listOf(80, 65, 66, 84))
        check(Tokens.headlineSmall, listOf(48, 42, 40, 51), listOf(64, 56, 53, 68))
        check(Tokens.titleSmall, listOf(32, 28, 27, 35), listOf(40, 35, 33, 42))
        check(Tokens.bodyLarge, listOf(30, 26, 25, 34), listOf(38, 33, 32, 41))
        check(Tokens.bodySmall, listOf(26, 23, 22, 28), listOf(34, 28, 28, 36))
        check(Tokens.labelMedium, listOf(24, 21, 20, 26), listOf(28, 25, 23, 29))
    }

    @Test
    fun `basic button sizes follow the official table`() {
        val medium = Tokens.buttonMedium
        assertEquals(80f, medium.height.of(FormFactor.S))
        assertEquals(66f, medium.height.of(FormFactor.W))
        assertEquals(32f, medium.paddingH.of(FormFactor.S))
        assertEquals(24f, medium.radius.of(FormFactor.S))
        assertEquals(40f, medium.icon.of(FormFactor.S))

        val small = Tokens.buttonSmall
        assertEquals(64f, small.height.of(FormFactor.S))
        assertEquals(16f, small.radius.of(FormFactor.S))
    }

    @Test
    fun `no button is smaller than the smallest official touch target on the default display`() {
        assertTrue(Tokens.buttonSmall.height.of(FormFactor.S) >= 64f)
        assertTrue(Tokens.buttonMedium.height.of(FormFactor.S) >= 64f)
        assertTrue(Tokens.tabSegmentHeight.of(FormFactor.S) >= 64f)
    }
}

class FormFactorTest {
    @Test
    fun `each official display size selects its token column`() {
        assertEquals(FormFactor.S, FormFactor.ofDisplay(widthDp = 2560))
        assertEquals(FormFactor.L, FormFactor.ofDisplay(widthDp = 2048)) // S-14.6
        assertEquals(FormFactor.L, FormFactor.ofDisplay(widthDp = 1920))
        assertEquals(FormFactor.W, FormFactor.ofDisplay(widthDp = 3840))
        assertEquals(FormFactor.W, FormFactor.ofDisplay(widthDp = 3840))
        assertEquals(FormFactor.WF, FormFactor.ofDisplay(widthDp = 3072))
    }

    @Test
    fun `an unknown display uses the default column`() {
        assertEquals(FormFactor.S, FormFactor.ofDisplay(widthDp = 1000))
    }
}
