package app.birdeye.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.birdeye.core.Copy
import app.birdeye.core.Speaker
import app.birdeye.core.UiState
import app.birdeye.ui.component.BasicButton
import app.birdeye.ui.component.ButtonKind
import app.birdeye.ui.component.Label
import app.birdeye.ui.component.LineIcon
import app.birdeye.ui.component.LineIcons
import app.birdeye.ui.component.NoticeBar
import app.birdeye.ui.component.OnePaneFrame
import app.birdeye.ui.component.PageNavigation
import app.birdeye.ui.component.TwoPaneFrame
import app.birdeye.ui.component.bandStyle
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp

@Composable
private fun OutsideCard(state: UiState) {
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(32.fdp))
            .padding(vertical = 32.fdp, horizontal = 36.fdp),
    ) {
        Label(Speaker.Outside.label, Tokens.bodySmall.withWeight(FontWeight.Bold), colors.outside)
        Label(
            state.outside?.text ?: "말씀을 기다리고 있어요",
            Tokens.headlineSmall,
            color = if (state.outside != null) colors.textPrimary else colors.textTertiary,
            maxLines = 3,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowActions(state: UiState, actions: UiActions, showIntroText: Boolean) {
    val colors = LocalColors.current
    val introButton = @Composable {
        BasicButton(
            "안내 문장 말하기",
            onClick = { actions.onReply(Copy.WINDOW_INTRO) },
            kind = ButtonKind.ACCENT_OUTSIDE,
            enabled = state.replying == null,
            loading = state.replying == Copy.WINDOW_INTRO,
            loadingDescription = "말하는 중",
        )
    }
    if (showIntroText) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.fdp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.fdp))
                .padding(vertical = 16.fdp, horizontal = 20.fdp),
        ) {
            introButton()
            Label("\"${Copy.WINDOW_INTRO}\"", Tokens.bodySmall, colors.textSecondary, maxLines = 3)
        }
    } else {
        introButton()
    }
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(14.fdp),
        horizontalArrangement = Arrangement.spacedBy(14.fdp),
    ) {
        Copy.WINDOW_REPLIES.forEach { reply ->
            BasicButton(
                reply,
                onClick = { actions.onReply(reply) },
                enabled = state.replying == null,
                loading = state.replying == reply,
                loadingDescription = "말하는 중",
            )
        }
    }
}

@Composable
fun WindowScreen(state: UiState, onePane: Boolean, actions: UiActions) {
    val colors = LocalColors.current
    val navigation = @Composable {
        PageNavigation(
            icon = LineIcons.outside,
            iconColor = colors.outside,
            title = "창문 너머 대화",
            subtitle = "차 밖 사람의 말을 보여 드려요",
        )
    }
    if (onePane) {
        OnePaneFrame(bandStyle(state)) {
            navigation()
            state.notice?.let { NoticeBar(it) }
            OutsideCard(state)
            Spacer(Modifier.weight(1f))
            WindowActions(state, actions, showIntroText = false)
        }
        return
    }
    TwoPaneFrame(
        band = bandStyle(state),
        side = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.fdp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(24.fdp))
                    .padding(20.fdp),
            ) {
                LineIcon(LineIcons.window, colors.outside, 44.fdp)
                Column {
                    Label("운전석 창문 열림", Tokens.bodySmall.withWeight(FontWeight.Bold), maxLines = 1)
                    Label("바깥 소리 모드", Tokens.bodySmall, colors.textTertiary, maxLines = 1)
                }
            }
        },
    ) {
        navigation()
        state.notice?.let { NoticeBar(it) }
        OutsideCard(state)
        Spacer(Modifier.weight(1f))
        WindowActions(state, actions, showIntroText = true)
    }
}
