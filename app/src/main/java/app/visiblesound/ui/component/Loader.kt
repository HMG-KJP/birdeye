package app.visiblesound.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The guide's linear loading animation: the one moving element the app allows, shown only while
 * something is in progress. It runs inside a button the driver just pressed.
 */
@Composable
fun Loader(color: Color, size: Dp, description: String) {
    val transition = rememberInfiniteTransition(label = "loader")
    val start by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "loader-angle",
    )
    Canvas(
        Modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val stroke = this.size.minDimension * 0.11f
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
