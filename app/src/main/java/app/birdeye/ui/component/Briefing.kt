package app.birdeye.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** A second sound under the main card, so one never hides the other. */
@Composable
fun AlsoHeardCard(alert: Alert) {
    val colors = LocalColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 20.fdp, horizontal = 32.fdp),
    ) {
        Label(alert.headline, Tokens.titleMedium.withWeight(FontWeight.Bold), colors.siren, maxLines = 1)
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

@Composable
fun BriefingCard(lines: List<String>) {
    if (lines.isEmpty()) return
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(32.fdp))
            .padding(vertical = 24.fdp, horizontal = 32.fdp),
    ) {
        Label("지금 브리핑", Tokens.titleSmall, maxLines = 1)
        lines.forEach { Label(it, Tokens.bodyLarge, colors.textSecondary, maxLines = 2) }
    }
}

/** Fills the rest of the pane with what the driver may have missed, newest first. */
@Composable
fun RecentSoundsCard(state: UiState, actions: UiActions, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val sounds = state.trip.entries
        .filter { it.kind == EntryKind.SIREN || it.kind == EntryKind.HORN }
        .takeLast(RECENT_SOUNDS)
        .reversed()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.fdp),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(32.fdp))
            .padding(vertical = 24.fdp, horizontal = 32.fdp),
    ) {
        Label("최근 소리", Tokens.titleSmall, maxLines = 1)
        if (sounds.isEmpty()) Label("아직 들린 사이렌과 경적이 없어요", Tokens.bodySmall, colors.textTertiary, maxLines = 1)
        sounds.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.fdp)) {
                Label(actions.timeText(entry.at), Tokens.labelMedium, colors.textTertiary, maxLines = 1, modifier = Modifier.width(88.fdp))
                Label(entry.text, Tokens.bodySmall.withWeight(FontWeight.Bold), maxLines = 1, modifier = Modifier.weight(1f))
                entry.place?.let { Label(it, Tokens.labelMedium, colors.textTertiary, maxLines = 1) }
            }
        }
    }
}
