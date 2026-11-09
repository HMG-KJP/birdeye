package app.birdeye.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.birdeye.core.Alert
import app.birdeye.core.Copy
import app.birdeye.core.EntryKind
import app.birdeye.core.UiState
import app.birdeye.ui.screen.UiActions
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp

private const val RECENT_SOUNDS = 4

/** A second sound in the main card, so one never hides the other. */
@Composable
fun AlsoHeardLine(alert: Alert) {
    val style = if (LocalCompactCards.current) Tokens.titleMedium else Tokens.titleLarge
    Label(
        alert.headline,
        style.withWeight(FontWeight.Bold),
        LocalColors.current.siren,
        maxLines = 1,
        modifier = Modifier.cardExtra(),
    )
}

/** The door lines for the main card, where they sit under the sound in larger type than on the other screens. */
@Composable
fun DoorStatusLines(state: UiState) {
    val lines = doorLines(state)
    if (lines.isEmpty()) return
    val style = if (LocalCompactCards.current) Tokens.bodyLarge else Tokens.titleLarge
    Column(verticalArrangement = Arrangement.spacedBy(4.fdp), modifier = Modifier.cardExtra()) {
        lines.forEach { Label(it, style.withWeight(FontWeight.Bold), LocalColors.current.outside, maxLines = 1) }
    }
}

@Composable
private fun Modifier.cardExtra(): Modifier =
    fillMaxWidth().padding(
        start = cardPaddingH,
        end = cardPaddingH,
        bottom = if (LocalCompactCards.current) 16.fdp else 36.fdp,
    )

private fun doorLines(state: UiState): List<String> =
    state.openDoors.sortedBy { it.ordinal }.map(Copy::doorOpen) +
        listOfNotNull(state.doorJustClosed?.let(Copy::doorClosed))

/** Which door is open, and the moment one shuts: a driver who can't hear it has no other way to tell. */
@Composable
fun DoorStatusCard(state: UiState) {
    val lines = doorLines(state)
    if (lines.isEmpty()) return
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 16.fdp, horizontal = 32.fdp),
    ) {
        lines.forEach { Label(it, Tokens.bodyLarge.withWeight(FontWeight.Bold), colors.outside, maxLines = 1) }
    }
}

/** Stands in for captions during a turn; they come back on their own when the signal goes off. */
@Composable
fun CaptionsHeldCard() {
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 18.fdp, horizontal = 32.fdp),
    ) {
        Label(Copy.CAPTIONS_HELD, Tokens.bodyLarge.withWeight(FontWeight.Bold), maxLines = 1)
        Label(Copy.CAPTIONS_HELD_DETAIL, Tokens.bodySmall, colors.textSecondary, maxLines = 1)
    }
}

/** Under a divider in the sound's own card, so the sound and what it means are read in one go. */
@Composable
fun BriefingSection(lines: List<String>) {
    if (lines.isEmpty()) return
    val colors = LocalColors.current
    val compact = LocalCompactCards.current
    val lineStyle = if (compact) Tokens.bodyLarge else Tokens.titleLarge
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(2.dp).background(colors.dividerStrong))
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.fdp else 18.fdp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 24.fdp else 40.fdp, horizontal = cardPaddingH),
        ) {
            Label(
                "지금 브리핑",
                if (compact) Tokens.titleSmall else Tokens.titleMedium.withWeight(FontWeight.ExtraBold),
                maxLines = 1,
            )
            lines.forEachIndexed { index, line ->
                val first = index == 0
                Label(
                    line,
                    lineStyle.withWeight(if (first) FontWeight.Bold else FontWeight.Normal),
                    if (first) colors.textPrimary else colors.textSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

/** What was heard earlier, kept quiet under the main card. */
@Composable
fun RecentSounds(state: UiState, actions: UiActions) {
    val colors = LocalColors.current
    val sounds = state.trip.entries
        .filter { it.kind == EntryKind.SIREN || it.kind == EntryKind.HORN }
        .takeLast(RECENT_SOUNDS)
        .reversed()
    TitledRows(gapSize = 12.fdp, modifier = Modifier.fillMaxWidth().padding(horizontal = cardPaddingH)) {
        Label("최근 소리", Tokens.labelMedium, colors.textTertiary, maxLines = 1)
        // A sound is written down when it ends, so one playing now is not in the list yet.
        if (sounds.isEmpty()) {
            val empty = if (state.alert != null) "지금 소리가 끝나면 여기에 남아요" else "아직 들린 사이렌과 경적이 없어요"
            Label(empty, Tokens.bodySmall, colors.textTertiary, maxLines = 1)
        }
        sounds.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.fdp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Label(actions.timeText(entry.at), Tokens.labelMedium, colors.textTertiary, maxLines = 1, modifier = Modifier.width(88.fdp))
                Label(entry.text, Tokens.bodySmall, colors.textSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                entry.place?.let { Label(it, Tokens.labelMedium, colors.textTertiary, maxLines = 1) }
            }
        }
    }
}

/** A title, then as many rows as fit whole; with no room for even one row, nothing rather than a bare title. */
@Composable
internal fun TitledRows(gapSize: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = gapSize.roundToPx()
        val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val fitting = mutableListOf<Placeable>()
        var used = 0
        for (measurable in measurables) {
            val part = measurable.measure(free)
            val next = if (fitting.isEmpty()) part.height else used + gap + part.height
            if (next > constraints.maxHeight) break
            fitting += part
            used = next
        }
        if (fitting.size < 2) {
            layout(constraints.minWidth, 0) {}
        } else {
            layout(constraints.maxWidth, used) {
                var y = 0
                fitting.forEach {
                    it.place(0, y)
                    y += it.height + gap
                }
            }
        }
    }
}
