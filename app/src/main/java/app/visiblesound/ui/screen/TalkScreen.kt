package app.visiblesound.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import app.visiblesound.core.CaptionLine
import app.visiblesound.core.Screen
import app.visiblesound.core.Seat
import app.visiblesound.core.Speaker
import app.visiblesound.core.UiState
import app.visiblesound.ui.component.CaptionCard
import app.visiblesound.ui.component.ConditionBar
import app.visiblesound.ui.component.HistoryRow
import app.visiblesound.ui.component.Label
import app.visiblesound.ui.component.LineIcons
import app.visiblesound.ui.component.NoticeBar
import app.visiblesound.ui.component.OnePaneFrame
import app.visiblesound.ui.component.PageNavigation
import app.visiblesound.ui.component.ReplyButtons
import app.visiblesound.ui.component.SeatMap
import app.visiblesound.ui.component.SegmentedTabs
import app.visiblesound.ui.component.TwoPaneFrame
import app.visiblesound.ui.component.bandStyle
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.Tokens

/**
 * The big card always holds what the other person said; a reply the driver just sent must not
 * push their words off the screen.
 */
internal fun splitConversation(captions: List<CaptionLine>): Pair<CaptionLine?, List<CaptionLine>> {
    val latest = captions.lastOrNull { it.speaker != Speaker.DriverReply } ?: captions.lastOrNull()
    val history = captions.filter { it !== latest }.takeLast(2)
    return latest to history
}

@Composable
private fun TalkNavigation(state: UiState, actions: UiActions) {
    val colors = LocalColors.current
    val passengers = (state.occupiedSeats - Seat.DRIVER).size
    PageNavigation(
        icon = LineIcons.chat,
        iconColor = colors.talk,
        title = "대화 자막",
        subtitle = if (passengers > 0) "승객 ${passengers}명 · 듣는 중" else "듣는 중",
        tabs = { SegmentedTabs(mainTabs(state), Screen.TALK, actions.onSelectScreen) },
        suffix = { SettingsEntry(state, actions) },
    )
}

@Composable
private fun WaitingForSpeech() {
    Label("말소리가 들리면 여기에 글자로 보여 드릴게요", Tokens.titleMedium, LocalColors.current.textTertiary)
}

@Composable
fun TalkScreen(state: UiState, onePane: Boolean, draft: MutableState<String?>, actions: UiActions) {
    val (latest, history) = splitConversation(state.captions)
    val latestCard = @Composable {
        if (latest != null) {
            CaptionCard(latest, state.parked, fresh = latest == state.recentCaption)
        } else {
            WaitingForSpeech()
        }
        if (!(onePane && state.systemCameraUp)) state.condition?.let { ConditionBar(it) }
    }
    if (onePane) {
        OnePaneFrame(bandStyle(state)) {
            if (state.parked) TalkNavigation(state, actions)
            state.notice?.let { NoticeBar(it) }
            latestCard()
            if (state.parked) {
                Spacer(Modifier.weight(1f))
                ReplyButtons(state.replies, parked = true, state.replying, draft, actions.onReply)
            }
        }
        return
    }
    TwoPaneFrame(
        band = bandStyle(state),
        side = { SeatMap(state.activeSeat) },
    ) {
        TalkNavigation(state, actions)
        state.notice?.let { NoticeBar(it) }
        latestCard()
        history.forEach { HistoryRow(it, state.parked) }
        Spacer(Modifier.weight(1f))
        ReplyButtons(state.replies, state.parked, state.replying, draft, actions.onReply)
    }
}
