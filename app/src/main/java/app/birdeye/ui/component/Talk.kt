package app.birdeye.ui.component

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
import app.birdeye.core.CaptionLine
import app.birdeye.core.Seat
import app.birdeye.core.Speaker
import app.birdeye.core.label
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp

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
