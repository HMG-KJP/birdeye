package app.birdeye.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import app.birdeye.R
import app.birdeye.core.AlertKind
import app.birdeye.core.Copy
import app.birdeye.core.Direction
import app.birdeye.core.DrivingCondition
import app.birdeye.core.Seat
import app.birdeye.core.UiState
import app.birdeye.ui.theme.ColorTokens
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp

/** One sound on the map. [side] is null when the direction is unknown, which is drawn as such. */
data class MapSound(val side: Direction?, val color: Color, val name: String, val strong: Boolean = true)

/** The sounds in [state], loudest news first. The map never shows a side the app does not know. */
fun mapSounds(state: UiState, colors: ColorTokens): List<MapSound> {
    fun name(kind: AlertKind) = when (kind) {
        AlertKind.SIREN -> "사이렌"
        AlertKind.HORN -> "경적"
        AlertKind.TRAFFIC -> "차량"
        AlertKind.VEHICLE -> ""
    }
    val main = state.alert?.let { MapSound(it.direction, colors.accent(it.kind.accent()), name(it.kind)) }
    val beside = state.alsoHeard?.let { MapSound(it.direction, colors.siren, name(it.kind), strong = false) }
    return listOfNotNull(main, beside)
}

private const val RING_INNER = 0.234f
private const val RING_OUTER = 0.32f
private const val SECTOR = 0.367f
private const val CAR_HEIGHT = 0.44f
private const val CAR_ASPECT = 270f / 617f
private const val SPREAD_DEGREES = 40f

private fun Direction.centerDegrees() = when (this) {
    Direction.RIGHT -> 0f
    Direction.REAR -> 90f
    Direction.LEFT -> 180f
    Direction.FRONT -> 270f
}

/** Short strokes around the car, as fractions of the map. They never move: the guide forbids it. */
private val RAIN = listOf(
    0.23f to 0.19f, 0.39f to 0.14f, 0.62f to 0.15f, 0.78f to 0.20f, 0.14f to 0.36f, 0.30f to 0.31f,
    0.73f to 0.34f, 0.87f to 0.37f, 0.17f to 0.56f, 0.31f to 0.52f, 0.75f to 0.53f, 0.89f to 0.58f,
    0.25f to 0.70f, 0.41f to 0.77f, 0.61f to 0.75f, 0.78f to 0.69f,
)

/**
 * The car seen from above with what is heard around it. The car is the guide's own render, so it
 * matches the driving view beside it; it is a still image, and nothing on the map ever moves.
 */
@Composable
fun BirdEyeMap(
    sounds: List<MapSound>,
    modifier: Modifier = Modifier,
    seat: Seat? = null,
    windowOpen: Boolean = false,
    rain: Boolean = false,
) {
    val colors = LocalColors.current
    val car = if (colors.background == Tokens.dark.background) R.drawable.car_top_dark else R.drawable.car_top_light
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Box(Modifier.size(side), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val ringColor = colors.dividerStrong
                drawCircle(ringColor, size.width * SECTOR, style = Stroke(size.width * 0.004f))
                drawCircle(ringColor, size.width * RING_INNER, style = Stroke(size.width * 0.004f))
                if (rain) drawRain(colors.textTertiary)
                sounds.forEach { drawSound(it) }
            }
            Image(painterResource(car), contentDescription = null, modifier = Modifier.height(side * CAR_HEIGHT).aspectRatio(CAR_ASPECT))
            Canvas(Modifier.fillMaxSize()) { drawOnCar(seat, windowOpen, colors) }
            SideLabels(sounds)
        }
    }
}

private fun DrawScope.drawRain(color: Color) {
    val length = size.width * 0.036f
    RAIN.forEach { (x, y) ->
        val start = Offset(size.width * x, size.height * y)
        drawLine(color, start, start + Offset(-length * 0.45f, length), strokeWidth = size.width * 0.006f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSound(sound: MapSound) {
    val stroke = size.width * 0.022f
    val side = sound.side
    if (side == null) {
        drawCircle(
            sound.color,
            size.width * RING_OUTER,
            style = Stroke(stroke * 0.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.6f))),
        )
        return
    }
    val start = side.centerDegrees() - SPREAD_DEGREES
    val sweep = SPREAD_DEGREES * 2
    fun arc(radius: Float, style: Stroke?, alpha: Float = 1f) {
        val r = size.width * radius
        val topLeft = Offset(center.x - r, center.y - r)
        if (style == null) {
            drawArc(sound.color.copy(alpha = alpha), start, sweep, useCenter = true, topLeft = topLeft, size = Size(r * 2, r * 2))
        } else {
            drawArc(sound.color, start, sweep, useCenter = false, topLeft = topLeft, size = Size(r * 2, r * 2), style = style)
        }
    }
    arc(SECTOR, style = null, alpha = 0.16f)
    arc(RING_INNER, Stroke(stroke, cap = StrokeCap.Round))
    if (sound.strong) arc(RING_OUTER, Stroke(stroke, cap = StrokeCap.Round))
}

/** Seat positions as fractions of the car image, read from the render. */
private fun DrawScope.drawOnCar(seat: Seat?, windowOpen: Boolean, colors: ColorTokens) {
    val carHeight = size.width * CAR_HEIGHT
    val carWidth = carHeight * CAR_ASPECT
    val carLeft = center.x - carWidth / 2
    val carTop = center.y - carHeight / 2
    val cell = when (seat) {
        Seat.DRIVER -> 0.30f to 0.48f
        Seat.PASSENGER -> 0.53f to 0.48f
        Seat.REAR_LEFT -> 0.30f to 0.60f
        Seat.REAR_RIGHT -> 0.53f to 0.60f
        null -> null
    }
    if (cell != null) {
        drawRoundRect(
            colors.talk,
            Offset(carLeft + carWidth * cell.first, carTop + carHeight * cell.second),
            Size(carWidth * 0.17f, carHeight * 0.10f),
            CornerRadius(carWidth * 0.04f),
        )
    }
    if (windowOpen) {
        val x = carLeft - size.width * 0.012f
        drawLine(
            colors.outside,
            Offset(x, carTop + carHeight * 0.44f),
            Offset(x, carTop + carHeight * 0.60f),
            strokeWidth = size.width * 0.014f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SideLabels(sounds: List<MapSound>) {
    val colors = LocalColors.current
    val style = Tokens.bodySmall.withWeight(FontWeight.Bold)
    fun heard(direction: Direction) = sounds.firstOrNull { it.side == direction }
    val unknown = sounds.firstOrNull { it.side == null }
    @Composable
    fun Line(text: String, color: Color) = Label(text, style, color, maxLines = 1)
    Box(Modifier.fillMaxSize()) {
        val front = heard(Direction.FRONT)
        Box(Modifier.align(Alignment.TopCenter)) {
            Line(if (front != null) "앞 · ${front.name}" else "앞", front?.color ?: colors.textTertiary)
        }
        val rear = heard(Direction.REAR)
        Box(Modifier.align(Alignment.BottomCenter)) {
            when {
                rear != null -> Line("뒤 · ${rear.name}", rear.color)
                unknown != null -> Line("${unknown.name} · ${Copy.UNKNOWN_SIDE}", unknown.color)
                else -> Line("뒤", colors.textTertiary)
            }
        }
        val left = heard(Direction.LEFT)
        Column(Modifier.align(Alignment.CenterStart), horizontalAlignment = Alignment.CenterHorizontally) {
            Line("왼쪽", left?.color ?: colors.textTertiary)
            if (left != null) Line(left.name, left.color)
        }
        val right = heard(Direction.RIGHT)
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.CenterHorizontally) {
            Line("오른쪽", right?.color ?: colors.textTertiary)
            if (right != null) Line(right.name, right.color)
        }
    }
}

/** The left pane of a wide window: the map, what the light means right now, and how well sounds carry. */
@Composable
fun ColumnScope.MapPane(state: UiState, sounds: List<MapSound>, seat: Seat? = null, windowOpen: Boolean = false) {
    val colors = LocalColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Label("주변 지도", Tokens.titleSmall, maxLines = 1)
        Label("차를 위에서 내려다본 소리", Tokens.labelMedium, colors.textTertiary, maxLines = 1)
    }
    BirdEyeMap(
        sounds = sounds,
        seat = seat,
        windowOpen = windowOpen,
        rain = state.condition == DrivingCondition.RAIN || state.condition == DrivingCondition.THUNDER,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    RhythmLegend((state.alert ?: state.notice)?.rhythm)
    HearingRow(state)
}

@Composable
private fun HearingRow(state: UiState) {
    val colors = LocalColors.current
    val status = Copy.hearingStatus(state.listening, state.condition)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 16.fdp, horizontal = 24.fdp),
    ) {
        Label("듣기 상태", Tokens.bodySmall, colors.textTertiary, maxLines = 1)
        Label(status, Tokens.bodySmall.withWeight(FontWeight.Bold), if (status == "좋음") colors.textPrimary else colors.siren, maxLines = 1)
    }
}
