package app.visiblesound.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.visiblesound.core.Alert
import app.visiblesound.core.AlertKind
import app.visiblesound.core.Direction
import app.visiblesound.core.LightRhythm
import app.visiblesound.core.Screen
import app.visiblesound.core.UiState
import app.visiblesound.ui.theme.ColorTokens
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.LocalFormFactor
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.fdp
import app.visiblesound.ui.theme.dp as tokenDp

enum class Accent { SIREN, TALK, OUTSIDE, VEHICLE }

fun ColorTokens.accent(accent: Accent): Color = when (accent) {
    Accent.SIREN -> siren
    Accent.TALK -> talk
    Accent.OUTSIDE -> outside
    Accent.VEHICLE -> vehicle
}

fun LightRhythm.accent(): Accent = when (this) {
    LightRhythm.SIREN, LightRhythm.HORN -> Accent.SIREN
    LightRhythm.PASSENGER -> Accent.TALK
    LightRhythm.OUTSIDE -> Accent.OUTSIDE
    LightRhythm.VEHICLE -> Accent.VEHICLE
}

fun AlertKind.accent(): Accent = when (this) {
    AlertKind.SIREN, AlertKind.HORN -> Accent.SIREN
    AlertKind.VEHICLE -> Accent.VEHICLE
}

data class BandStyle(val accent: Accent, val thick: Boolean)

/**
 * A steady colored band says which kind of sound is active. It depends only on the state, so it
 * cannot blink: blinking is left to the cabin light.
 */
fun bandStyle(state: UiState): BandStyle? {
    val alert = state.alert ?: state.notice
    return when {
        alert != null -> BandStyle(alert.kind.accent(), thick = true)
        state.screen == Screen.WINDOW -> BandStyle(Accent.OUTSIDE, thick = false)
        state.screen == Screen.TALK -> BandStyle(Accent.TALK, thick = false)
        else -> null
    }
}

/** How far down the system's blind spot camera reaches over this window; zero while it is closed. */
val LocalSystemCameraHeight = staticCompositionLocalOf { 0.dp }

/** Keeps the cards under the camera while the page's tabs stay in view beside it. */
private val LocalNavigationMinHeight = staticCompositionLocalOf { 0.dp }

/**
 * A wide app window: context on the left, the main content on the right. The camera opens at the
 * window's top left, over the left pane, so the main content takes the whole width below it.
 */
@Composable
fun TwoPaneFrame(
    band: BandStyle?,
    side: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalColors.current
    val camera = LocalSystemCameraHeight.current
    val topPadding = 24.fdp
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxSize()) {
            if (camera == 0.dp) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.fdp),
                    modifier = Modifier
                        .width(Tokens.SIDE_PANE_WIDTH.fdp)
                        .fillMaxHeight()
                        .padding(vertical = 32.fdp, horizontal = Tokens.windowPadding.tokenDp),
                    content = side,
                )
                Spacer(Modifier.width(1.dp).fillMaxHeight().background(colors.dividerStrong))
            }
            CompositionLocalProvider(LocalNavigationMinHeight provides (camera - topPadding).coerceAtLeast(0.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.fdp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = topPadding, horizontal = Tokens.windowPadding.tokenDp),
                    content = content,
                )
            }
        }
        if (band != null) TopBand(band)
    }
}

/** A narrow app window; the camera nearly fills its width, so everything starts below it. */
@Composable
fun OnePaneFrame(band: BandStyle?, content: @Composable ColumnScope.() -> Unit) {
    val camera = LocalSystemCameraHeight.current
    Box(Modifier.fillMaxSize().background(LocalColors.current.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.fdp),
            modifier = Modifier.fillMaxSize().padding(
                top = camera + 24.fdp,
                bottom = 24.fdp,
                start = Tokens.windowPadding.tokenDp,
                end = Tokens.windowPadding.tokenDp,
            ),
            content = content,
        )
        if (band != null) TopBand(band)
    }
}

@Composable
private fun TopBand(band: BandStyle) {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(if (band.thick) 8.fdp else 4.fdp)
            .background(LocalColors.current.accent(band.accent)),
    )
}

/** The guide's Page Navigation: icon and primary menu, then tabs and a suffix slot. */
@Composable
fun PageNavigation(
    icon: List<String>,
    iconColor: Color,
    title: String,
    subtitle: String?,
    tabs: @Composable () -> Unit = {},
    suffix: @Composable () -> Unit = {},
) {
    val colors = LocalColors.current
    Box(Modifier.fillMaxWidth().heightIn(min = LocalNavigationMinHeight.current)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.fdp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.fdp),
                modifier = Modifier.weight(1f),
            ) {
                LineIcon(icon, iconColor, 40.fdp)
                Label(title, Tokens.titleSmall, maxLines = 1)
                if (subtitle != null) {
                    Label(subtitle, Tokens.bodySmall, colors.textTertiary, maxLines = 1)
                }
            }
            tabs()
            suffix()
        }
    }
}

data class TabItem(val screen: Screen, val label: String, val enabled: Boolean = true)

/** The guide's Tabs, Solid type at Medium size. */
@Composable
fun SegmentedTabs(tabs: List<TabItem>, selected: Screen, onSelect: (Screen) -> Unit) {
    val colors = LocalColors.current
    val view = LocalView.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.tabSegmentGap.tokenDp),
        modifier = Modifier
            .background(colors.tabBackground, RoundedCornerShape(Tokens.tabTrackRadius.tokenDp))
            .padding(Tokens.tabTrackPadding.tokenDp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.screen == selected
            val shape = RoundedCornerShape(Tokens.tabSegmentRadius.tokenDp)
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(Tokens.tabSegmentHeight.tokenDp)
                    .let {
                        if (isSelected) {
                            it.shadow(Tokens.SHADOW_LEVEL1_ELEVATION.dp, shape, ambientColor = Tokens.shadowLevel1, spotColor = Tokens.shadowLevel1)
                        } else {
                            it
                        }
                    }
                    .background(
                        when {
                            isSelected -> colors.tabSelected
                            pressed -> colors.buttonBasicPressed
                            else -> Color.Transparent
                        },
                        shape,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = tab.enabled,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSelect(tab.screen)
                        },
                    )
                    .padding(horizontal = Tokens.tabSegmentPaddingH.tokenDp),
            ) {
                Label(
                    tab.label,
                    Tokens.tabLabel,
                    when {
                        !tab.enabled -> colors.textDisabled
                        isSelected -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** The guide's Toast, placed in the app panel with its stated margins and no shadow. */
@Composable
fun BoxScope.Toast(text: String) {
    val colors = LocalColors.current
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(Tokens.toastMargin.tokenDp)
            .widthIn(max = Tokens.toastMaxWidth.tokenDp)
            .background(colors.toast, RoundedCornerShape(Tokens.toastRadius.tokenDp))
            .padding(horizontal = Tokens.toastPaddingH.tokenDp, vertical = Tokens.toastPaddingV.tokenDp),
    ) {
        Label(text, Tokens.toastLabel, colors.onToast, maxLines = 2)
    }
}

/** A short vehicle notice on screens whose main job is something else, so it can't go unseen. */
@Composable
fun NoticeBar(notice: Alert) {
    val colors = LocalColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 18.fdp, horizontal = 24.fdp),
    ) {
        LineIcon(LineIcons.warning, colors.vehicle, 40.fdp)
        Label(
            listOfNotNull(notice.headline, notice.guidance).joinToString(" · "),
            Tokens.bodyLarge.withWeight(FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** The car seen from above; arcs on the side the sound comes from. */
@Composable
fun CarDirectionDiagram(direction: Direction?, showArcs: Boolean, color: Color) {
    val colors = LocalColors.current
    val unit = LocalFormFactor.current.scale * 1.2f
    Canvas(Modifier.fillMaxWidth().height(290.fdp)) {
        fun u(value: Int): Float = (value * unit).dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        if (showArcs) {
            val degrees = when (direction) {
                Direction.REAR -> 0f
                Direction.LEFT -> 90f
                Direction.FRONT -> 180f
                Direction.RIGHT -> 270f
                null -> null
            }
            if (degrees != null) {
                rotate(degrees, Offset(cx, cy)) {
                    val outer = u(110)
                    drawArc(
                        color = color,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - outer, cy + u(15) - outer),
                        size = Size(outer * 2, outer * 2),
                        style = Stroke(u(3)),
                    )
                    val rx = u(75)
                    val ry = u(70)
                    drawArc(
                        color = color,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy + u(37) - ry),
                        size = Size(rx * 2, ry * 2),
                        style = Stroke(u(2)),
                    )
                }
            } else {
                drawCircle(color, u(110), Offset(cx, cy), style = Stroke(u(3)))
            }
        }
        val carW = u(70)
        val carH = u(140)
        val carTop = Offset(cx - carW / 2, cy - carH / 2)
        drawRoundRect(colors.surfaceHigh, carTop, Size(carW, carH), CornerRadius(u(24)))
        drawRoundRect(colors.carLine, carTop, Size(carW, carH), CornerRadius(u(24)), style = Stroke(u(2)))
        val glassInset = u(10)
        drawRoundRect(
            colors.carGlass,
            Offset(carTop.x + glassInset, carTop.y + u(22)),
            Size(carW - glassInset * 2, u(30)),
            CornerRadius(u(8)),
        )
        drawRoundRect(
            colors.carGlass,
            Offset(carTop.x + glassInset, carTop.y + carH - u(40)),
            Size(carW - glassInset * 2, u(22)),
            CornerRadius(u(6)),
        )
    }
}
