package app.visiblesound.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import app.visiblesound.core.EntryKind
import app.visiblesound.core.Screen
import app.visiblesound.core.TripEntry
import app.visiblesound.core.TripSummary
import app.visiblesound.core.UiState
import app.visiblesound.ui.component.Label
import app.visiblesound.ui.component.LineIcons
import app.visiblesound.ui.component.NoticeBar
import app.visiblesound.ui.component.OnePaneFrame
import app.visiblesound.ui.component.PageNavigation
import app.visiblesound.ui.component.SegmentedTabs
import app.visiblesound.ui.component.TwoPaneFrame
import app.visiblesound.ui.component.bandStyle
import app.visiblesound.ui.theme.ColorTokens
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.fdp
import java.util.Locale

private fun ColorTokens.entryColor(kind: EntryKind): Color = when (kind) {
    EntryKind.SIREN, EntryKind.HORN, EntryKind.TRAFFIC -> siren
    EntryKind.TALK -> talk
    EntryKind.OUTSIDE -> outside
    EntryKind.VEHICLE -> vehicle
}

private fun entryLabel(kind: EntryKind): String = when (kind) {
    EntryKind.SIREN -> "긴급차량 사이렌"
    EntryKind.HORN -> "경적"
    EntryKind.TALK -> "승객 대화"
    EntryKind.OUTSIDE -> "창문 너머 대화"
    EntryKind.VEHICLE -> "차량 경고"
    EntryKind.TRAFFIC -> "주변 차량 경고"
}

private fun countText(kind: EntryKind, count: Int): String = when (kind) {
    EntryKind.TALK, EntryKind.OUTSIDE -> "${count}문장"
    else -> "${count}회"
}

internal fun tripTotals(trip: TripSummary, now: Long): String {
    val started = trip.startedAt
    val minutes = if (started == null) 0 else ((trip.endedAt ?: now) - started) / 60_000
    val km = trip.distanceMeters / 1000
    val distance = if (km >= 10 || km == 0.0) "${km.toInt()}km" else String.format(Locale.US, "%.1fkm", km)
    return "${minutes}분 · $distance"
}

@Composable
private fun TripTotals(trip: TripSummary, now: Long) {
    val colors = LocalColors.current
    Column(verticalArrangement = Arrangement.spacedBy(24.fdp)) {
        Column {
            Label("이번 운행", Tokens.bodySmall, colors.textTertiary)
            Label(tripTotals(trip, now), Tokens.titleLarge, maxLines = 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.fdp)) {
            val counts = trip.counts()
            listOf(EntryKind.SIREN, EntryKind.TRAFFIC, EntryKind.HORN, EntryKind.TALK, EntryKind.OUTSIDE, EntryKind.VEHICLE).forEach { kind ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Label(entryLabel(kind), Tokens.bodySmall, colors.entryColor(kind), maxLines = 1)
                    Label(countText(kind, counts[kind] ?: 0), Tokens.bodySmall.withWeight(FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EntryList(trip: TripSummary, actions: UiActions, modifier: Modifier) {
    val colors = LocalColors.current
    if (trip.entries.isEmpty()) {
        Box(contentAlignment = Alignment.TopCenter, modifier = modifier.padding(top = 48.fdp)) {
            Label("이번 운행에 놓친 소리가 없어요", Tokens.titleMedium, colors.textTertiary)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.fdp), modifier = modifier) {
            items(trip.entries) { EntryRow(it, actions.timeText(it.at)) }
        }
    }
}

@Composable
fun RecapScreen(state: UiState, onePane: Boolean, actions: UiActions) {
    val colors = LocalColors.current
    val navigation = @Composable {
        PageNavigation(
            icon = LineIcons.clock,
            iconColor = colors.textPrimary,
            title = "놓친 소리 다시보기",
            subtitle = "정차 중에만 열려요",
            tabs = { SegmentedTabs(mainTabs(state), Screen.RECAP, actions.onSelectScreen) },
            suffix = { SettingsEntry(state, actions) },
        )
    }
    if (onePane) {
        OnePaneFrame(bandStyle(state)) {
            navigation()
            state.notice?.let { NoticeBar(it) }
            TripTotals(state.trip, actions.now())
            EntryList(state.trip, actions, Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    TwoPaneFrame(
        band = bandStyle(state),
        side = { TripTotals(state.trip, actions.now()) },
    ) {
        navigation()
        state.notice?.let { NoticeBar(it) }
        EntryList(state.trip, actions, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun EntryRow(entry: TripEntry, time: String) {
    val colors = LocalColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 20.fdp, horizontal = 28.fdp),
    ) {
        Label(time, Tokens.bodySmall, colors.textTertiary, maxLines = 1, modifier = Modifier.width(84.fdp))
        Spacer(Modifier.size(14.fdp).background(colors.entryColor(entry.kind), CircleShape))
        Label(entry.text, Tokens.bodyLarge, maxLines = 2, modifier = Modifier.weight(1f))
        if (entry.place != null) {
            Label(entry.place, Tokens.bodySmall, colors.textTertiary, maxLines = 1)
        }
    }
}
