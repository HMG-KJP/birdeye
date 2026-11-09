package app.visiblesound.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.visiblesound.core.CaptionLine
import app.visiblesound.core.Seat
import app.visiblesound.core.Speaker
import app.visiblesound.core.label
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.fdp

@Composable
fun SeatMap(activeSeat: Seat?) {
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(16.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(20.fdp),
    ) {
        Label("말한 사람 위치", Tokens.bodySmall, colors.textTertiary)
        Column(verticalArrangement = Arrangement.spacedBy(10.fdp)) {
            SeatRow(Seat.DRIVER, Seat.PASSENGER, activeSeat)
            SeatRow(Seat.REAR_LEFT, Seat.REAR_RIGHT, activeSeat)
        }
    }
}

@Composable
private fun SeatRow(left: Seat, right: Seat, activeSeat: Seat?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.fdp), modifier = Modifier.fillMaxWidth()) {
        SeatCell(left, left == activeSeat, Modifier.weight(1f))
        SeatCell(right, right == activeSeat, Modifier.weight(1f))
    }
}

/** Seat names wrap onto two lines so they stay at a readable size in the narrow pane. */
@Composable
private fun SeatCell(seat: Seat, active: Boolean, modifier: Modifier) {
    val colors = LocalColors.current
    val shape = RoundedCornerShape(16.fdp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(104.fdp)
            .background(if (active) colors.seatActive else colors.surfaceHigh, shape)
            .let { if (active) it.border(3.dp, colors.talk, shape) else it }
            .padding(horizontal = 8.fdp),
    ) {
        Label(
            seat.label,
            if (active) Tokens.bodySmall.withWeight(FontWeight.Bold) else Tokens.bodySmall,
            color = if (active) colors.textPrimary else colors.textSecondary,
            maxLines = 2,
        )
    }
}

@Composable
fun HistoryRow(line: CaptionLine, parked: Boolean) {
    val colors = LocalColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.fdp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Label(
            line.speaker.label,
            Tokens.bodySmall,
            if (line.speaker == Speaker.DriverReply) colors.talk else colors.textTertiary,
            maxLines = 1,
            modifier = Modifier.width(250.fdp),
        )
        Label(line.displayText(parked), Tokens.bodyLarge, colors.textSecondary, maxLines = 1)
    }
}

/** The latest sentence: key information, so it uses a headline style and sits near the top. */
@Composable
fun CaptionCard(line: CaptionLine, parked: Boolean, fresh: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.fdp),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(32.fdp))
            .padding(vertical = 28.fdp, horizontal = 36.fdp),
    ) {
        Label(
            if (fresh) "${line.speaker.label} · 방금" else line.speaker.label,
            Tokens.bodySmall.withWeight(FontWeight.Bold),
            colors.talk,
            maxLines = 1,
        )
        Label(line.displayText(parked), Tokens.headlineSmall, maxLines = 3)
    }
}

/** Reply buttons turn unresponsive while a reply plays; the one being spoken shows a loader. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyButtons(
    replies: List<String>,
    parked: Boolean,
    replying: String?,
    draft: MutableState<String?>,
    onReply: (String) -> Unit,
) {
    val typing = draft.value
    if (typing != null && parked) {
        ReplyInput(
            text = typing,
            onTextChange = { draft.value = it },
            onSend = {
                onReply(it)
                draft.value = null
            },
            onCancel = { draft.value = null },
        )
        return
    }
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(14.fdp),
        horizontalArrangement = Arrangement.spacedBy(14.fdp),
    ) {
        replies.forEachIndexed { index, reply ->
            BasicButton(
                reply,
                onClick = { onReply(reply) },
                kind = if (index == 0) ButtonKind.ACCENT_TALK else ButtonKind.BASIC,
                enabled = replying == null,
                loading = replying == reply,
                loadingDescription = "말하는 중",
            )
        }
        if (parked) {
            BasicButton("직접 입력", onClick = { draft.value = "" }, icon = LineIcons.plus, enabled = replying == null)
        }
    }
}

@Composable
private fun ReplyInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.fdp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextFieldBox(text, onTextChange, "답장 입력창", Modifier.weight(1f))
        BasicButton(
            "말하기",
            onClick = { if (text.isNotBlank()) onSend(text.trim()) },
            kind = ButtonKind.ACCENT_TALK,
        )
        BasicButton("취소", onClick = onCancel, icon = LineIcons.close)
    }
}
