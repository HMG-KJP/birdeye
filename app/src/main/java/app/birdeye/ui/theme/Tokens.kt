package app.birdeye.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birdeye.R

/** The columns of the official tables. [scale] sizes the app's own spacing to match them. */
enum class FormFactor(val scale: Float) {
    S(1f),
    L(0.88f),
    W(0.84f),
    WF(1.06f);

    companion object {
        /** Picks the column from the size of the whole display, which the app window can't change. */
        fun ofDisplay(widthDp: Int): FormFactor = when (widthDp) {
            in 3600..4200 -> W
            in 2900..3300 -> WF
            in 2400..2800 -> S
            in 1800..2300 -> L
            else -> S
        }
    }
}

/** One value per form factor, in the guide's S, L, W, WF order. */
@Immutable
data class Scaled(val s: Float, val l: Float, val w: Float, val wf: Float) {
    constructor(s: Int, l: Int, w: Int, wf: Int) : this(s.toFloat(), l.toFloat(), w.toFloat(), wf.toFloat())

    fun of(formFactor: FormFactor): Float = when (formFactor) {
        FormFactor.S -> s
        FormFactor.L -> l
        FormFactor.W -> w
        FormFactor.WF -> wf
    }
}

@Immutable
data class TextToken(val size: Scaled, val lineHeight: Scaled, val weight: FontWeight = FontWeight.Normal) {
    fun withWeight(weight: FontWeight) = copy(weight = weight)
}

@Immutable
data class ButtonToken(
    val height: Scaled,
    val paddingH: Scaled,
    val radius: Scaled,
    val icon: Scaled,
    val gap: Scaled,
    val minWidth: Scaled,
    val label: TextToken,
)

@Immutable
data class ColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val onFilled: Color,
    val buttonBasic: Color,
    val buttonBasicPressed: Color,
    val buttonBasicDisabled: Color,
    val buttonFilled: Color,
    val buttonFilledPressed: Color,
    val tabBackground: Color,
    val tabSelected: Color,
    val dividerStrong: Color,
    val fieldBackground: Color,
    val fieldFocused: Color,
    val switchOn: Color,
    val switchOff: Color,
    val knobOn: Color,
    val knobOff: Color,
    val toast: Color,
    val onToast: Color,
    val siren: Color,
    val talk: Color,
    val outside: Color,
    val vehicle: Color,
    val onTalk: Color,
    val onOutside: Color,
    val sirenIconBg: Color,
    val seatActive: Color,
    val carLine: Color,
    val carGlass: Color,
)

/** The only place colors, sizes and the typeface are defined. Values come from the guide; see DESIGN.md. */
object Tokens {
    val dark = ColorTokens(
        background = Color(0xFF131417),
        surface = Color(0xFF1D1E21),
        surfaceHigh = Color(0xFF313236),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xD6FFFFFF),
        textTertiary = Color(0xA3FFFFFF),
        textDisabled = Color(0x52FFFFFF),
        onFilled = Color(0xFF131417),
        buttonBasic = Color(0x0DFFFFFF),
        buttonBasicPressed = Color(0x1AFFFFFF),
        buttonBasicDisabled = Color(0x33131417),
        buttonFilled = Color(0xFFE0E1E6),
        buttonFilledPressed = Color(0xFFC7C9CE),
        tabBackground = Color(0x0DFFFFFF),
        tabSelected = Color(0x33FFFFFF),
        dividerStrong = Color(0x1AFFFFFF),
        fieldBackground = Color(0xFF131417),
        fieldFocused = Color(0xFFEDEEF2),
        switchOn = Color(0xFF00E074),
        switchOff = Color(0x1AFFFFFF),
        knobOn = Color(0xFFFFFFFF),
        knobOff = Color(0xFFA4A8AE),
        toast = Color(0xFFE0E1E6),
        onToast = Color(0xFF131417),
        siren = Color(0xFFFF9A3C),
        talk = Color(0xFF3FD0E8),
        outside = Color(0xFFF5C542),
        vehicle = Color(0xFFC9D3DE),
        onTalk = Color(0xFF06232A),
        onOutside = Color(0xFF2A2105),
        sirenIconBg = Color(0xFF2E2013),
        seatActive = Color(0xFF0E3A44),
        carLine = Color(0xFFA4A8AE),
        carGlass = Color(0xFF44464E),
    )

    val light = ColorTokens(
        background = Color(0xFFF7F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFEDEEF2),
        textPrimary = Color(0xFF131417),
        textSecondary = Color(0xD6131417),
        textTertiary = Color(0xA3131417),
        textDisabled = Color(0x52131417),
        onFilled = Color(0xFFFFFFFF),
        buttonBasic = Color(0x0D131417),
        buttonBasicPressed = Color(0x1A131417),
        buttonBasicDisabled = Color(0x33FFFFFF),
        buttonFilled = Color(0xFF44464E),
        buttonFilledPressed = Color(0xFF313236),
        tabBackground = Color(0x0D131417),
        tabSelected = Color(0xFFFFFFFF),
        dividerStrong = Color(0x1A131417),
        fieldBackground = Color(0xFFFFFFFF),
        fieldFocused = Color(0xFF313236),
        switchOn = Color(0xFF02C265),
        switchOff = Color(0x4D131417),
        knobOn = Color(0xFFFFFFFF),
        knobOff = Color(0xFFFFFFFF),
        toast = Color(0xFF44464E),
        onToast = Color(0xFFFFFFFF),
        siren = Color(0xFFA34A06),
        talk = Color(0xFF0B6677),
        outside = Color(0xFF735600),
        vehicle = Color(0xFF4F5B6A),
        onTalk = Color(0xFFFFFFFF),
        onOutside = Color(0xFFFFFFFF),
        sirenIconBg = Color(0xFFFBE9DA),
        seatActive = Color(0xFFCDEEF4),
        carLine = Color(0xFF686A72),
        carGlass = Color(0xFFC7C9CE),
    )

    val headlineLarge = TextToken(Scaled(60, 53, 50, 64), Scaled(80, 65, 66, 84), FontWeight.ExtraBold)
    val headlineMedium = TextToken(Scaled(56, 49, 46, 59), Scaled(72, 65, 60, 77), FontWeight.ExtraBold)
    val headlineSmall = TextToken(Scaled(48, 42, 40, 51), Scaled(64, 56, 53, 68), FontWeight.Bold)
    val titleLarge = TextToken(Scaled(40, 35, 33, 42), Scaled(52, 46, 43, 55), FontWeight.ExtraBold)
    val titleMedium = TextToken(Scaled(36, 32, 30, 38), Scaled(44, 39, 37, 47), FontWeight.Normal)
    val titleSmall = TextToken(Scaled(32, 28, 27, 35), Scaled(40, 35, 33, 42), FontWeight.Bold)
    val bodyLarge = TextToken(Scaled(30, 26, 25, 34), Scaled(38, 33, 32, 41), FontWeight.Normal)
    val bodyMedium = TextToken(Scaled(28, 25, 23, 29), Scaled(36, 32, 30, 38), FontWeight.Bold)
    val bodySmall = TextToken(Scaled(26, 23, 22, 28), Scaled(34, 28, 28, 36), FontWeight.Normal)
    val labelMedium = TextToken(Scaled(24, 21, 20, 26), Scaled(28, 25, 23, 29), FontWeight.Normal)

    /** Alert guidance is key information too, so it must clear the same height as the headline. */
    val alertGuidance = headlineSmall.withWeight(FontWeight.Normal)

    val typeScale: Map<String, TextToken> = mapOf(
        "headlineLarge" to headlineLarge,
        "headlineMedium" to headlineMedium,
        "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge,
        "titleMedium" to titleMedium,
        "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge,
        "bodyMedium" to bodyMedium,
        "bodySmall" to bodySmall,
        "labelMedium" to labelMedium,
    )

    val buttonMedium = ButtonToken(
        height = Scaled(80, 70, 66, 84),
        paddingH = Scaled(32, 28, 27, 35),
        radius = Scaled(24, 21, 20, 26),
        icon = Scaled(40, 35, 33, 42),
        gap = Scaled(14, 12, 12, 15),
        minWidth = Scaled(120, 106, 100, 128),
        label = bodyLarge.withWeight(FontWeight.Bold),
    )
    val buttonSmall = ButtonToken(
        height = Scaled(64, 56, 54, 68),
        paddingH = Scaled(24, 21, 20, 26),
        radius = Scaled(16, 14, 13, 17),
        icon = Scaled(32, 28, 27, 35),
        gap = Scaled(12, 11, 10, 13),
        minWidth = Scaled(100, 88, 84, 106),
        label = bodySmall.withWeight(FontWeight.Bold),
    )
    val buttonBorderDisabled = Scaled(2, 2, 2, 3)

    val tabTrackHeight = Scaled(80, 70, 66, 84)
    val tabTrackPadding = Scaled(8, 7, 8, 9)
    val tabTrackRadius = Scaled(24, 21, 20, 26)
    val tabSegmentHeight = Scaled(64, 56, 54, 68)
    val tabSegmentPaddingH = Scaled(18, 16, 16, 19)
    val tabSegmentRadius = Scaled(16, 14, 13, 17)
    val tabSegmentGap = Scaled(8, 7, 8, 9)
    val tabLabel = bodyLarge.withWeight(FontWeight.Bold)

    val fieldHeight = Scaled(80, 70, 66, 84)
    val fieldPaddingH = Scaled(24, 21, 20, 26)
    val fieldRadius = Scaled(24, 21, 20, 26)
    val fieldBorder = Scaled(2, 2, 2, 3)
    val fieldBorderFocused = Scaled(3, 3, 3, 3)

    val toastRadius = Scaled(16, 14, 13, 17)
    val toastPaddingH = Scaled(40, 35, 34, 42)
    val toastPaddingV = Scaled(24, 21, 20, 26)
    val toastMaxWidth = Scaled(792, 696, 760, 840)
    val toastMargin = Scaled(24, 21, 20, 26)
    val toastLabel = bodySmall.withWeight(FontWeight.Bold)

    val windowPadding = Scaled(20, 18, 20, 24)

    /** Shadow Level 1, for the selected tab. */
    val shadowLevel1 = Color(0x14000000)
    const val SHADOW_LEVEL1_ELEVATION = 6

    /** Own values, sized on the default display and scaled by [FormFactor.scale]. */
    const val SIDE_PANE_WIDTH = 440
    const val ICON_STROKE = 2f

    /**
     * How much of the app window's height the system's blind spot camera covers. Measured on the
     * default display only (424 of 1210); a share of the height keeps the cards inside shorter windows.
     */
    const val SYSTEM_CAMERA_HEIGHT_RATIO = 0.36f

    val font = FontFamily(
        weighted(FontWeight.Normal),
        weighted(FontWeight.Bold),
        weighted(FontWeight.ExtraBold),
    )

    @OptIn(ExperimentalTextApi::class)
    private fun weighted(weight: FontWeight) = Font(
        R.font.asta_sans,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

val LocalColors = staticCompositionLocalOf { Tokens.dark }
val LocalFormFactor = staticCompositionLocalOf { FormFactor.S }

/** An official size on the current display. */
val Scaled.dp: Dp
    @Composable @ReadOnlyComposable
    get() = of(LocalFormFactor.current).dp

val Scaled.sp: TextUnit
    @Composable @ReadOnlyComposable
    get() = of(LocalFormFactor.current).sp

/** One of the app's own sizes, given for the default display and scaled like the official ones. */
val Int.fdp: Dp
    @Composable @ReadOnlyComposable
    get() = (this * LocalFormFactor.current.scale).dp

/** Follows the system day and night setting. */
@Composable
fun BirdeyeTheme(
    formFactor: FormFactor = FormFactor.S,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColors provides if (dark) Tokens.dark else Tokens.light,
        LocalFormFactor provides formFactor,
        content = content,
    )
}
