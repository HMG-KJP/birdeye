package app.birdeye.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import app.birdeye.core.Alert
import app.birdeye.core.AlertKind
import app.birdeye.core.Listening
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp
import kotlin.math.max
import kotlin.math.roundToInt

const val SITUATION_CARD = "situation-card"

/** A sound to notice, or a microphone that stopped: a driver who can't hear has no other warning of either. */
fun needsAttention(alert: Alert?, listening: Listening): Boolean =
    if (alert != null) alert.kind != AlertKind.VEHICLE else listening.failed

/**
 * One card holding the sound and the briefing, then the caption, then what was heard earlier.
 *
 * With [recent], the stack fills its height and the card takes about two thirds of it. A card with more
 * to say grows into the rest, so what gives way is decided by importance and never by clipping: the recent
 * list first, row by row, then the briefing. The sound and the caption always stay.
 */
@Composable
fun SituationStack(
    emphasized: Boolean,
    hero: @Composable () -> Unit,
    briefing: @Composable () -> Unit,
    caption: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    recent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalColors.current
    val shape = RoundedCornerShape(32.fdp)
    val gapSize = 24.fdp
    val fill = recent != null
    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .testTag(SITUATION_CARD)
                    .background(if (emphasized) colors.sirenSurface else colors.surface, shape)
                    .let { if (emphasized) it.border(4.dp, colors.siren, shape) else it },
            )
            Box { hero() }
            Box { briefing() }
            Box { caption() }
            Box { recent?.invoke() }
        },
    ) { measurables, constraints ->
        val (surface, heroSlot, briefingSlot, captionSlot, recentSlot) = measurables
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = gapSize.roundToPx()
        val free = Constraints.fixedWidth(width)
        val heroPart = heroSlot.measure(free)
        val briefingPart = briefingSlot.measure(free)
        val captionPart = captionSlot.measure(free)

        val captionRoom = if (captionPart.height > 0) captionPart.height + gap else 0
        val room = height - captionRoom
        val briefingHeight = if (heroPart.height + briefingPart.height <= room) briefingPart.height else 0
        val content = heroPart.height + briefingHeight

        var card = content
        var recentPart: Placeable? = null
        if (fill) {
            card = max(content, (height * Tokens.SITUATION_CARD_SHARE).roundToInt())
            val left = room - card - gap
            if (left > 0) recentPart = recentSlot.measure(Constraints(width, width, 0, left))
            if ((recentPart?.height ?: 0) == 0) card = max(content, room)
        }

        val surfacePart = surface.measure(Constraints.fixed(width, card))
        val total = if (fill) height else card + captionRoom
        layout(width, constraints.constrainHeight(total)) {
            surfacePart.place(0, 0)
            val heroRoom = card - briefingHeight
            heroPart.place(0, (heroRoom - heroPart.height) / 2)
            if (briefingHeight > 0) briefingPart.place(0, heroRoom)
            captionPart.place(0, card + gap)
            recentPart?.place(0, card + captionRoom + gap)
        }
    }
}
