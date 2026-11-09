package app.visiblesound.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.visiblesound.core.Copy
import app.visiblesound.core.Screen
import app.visiblesound.core.UiState
import app.visiblesound.ui.component.AlertCard
import app.visiblesound.ui.component.CaptionCard
import app.visiblesound.ui.component.CarDirectionDiagram
import app.visiblesound.ui.component.IdleCard
import app.visiblesound.ui.component.Label
import app.visiblesound.ui.component.LineIcons
import app.visiblesound.ui.component.OnePaneFrame
import app.visiblesound.ui.component.PageNavigation
import app.visiblesound.ui.component.RhythmLegend
import app.visiblesound.ui.component.SegmentedTabs
import app.visiblesound.ui.component.TwoPaneFrame
import app.visiblesound.ui.component.accent
import app.visiblesound.ui.component.bandStyle
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.Tokens

@Composable
private fun MainNavigation(state: UiState, actions: UiActions) {
    val colors = LocalColors.current
    PageNavigation(
        icon = LineIcons.wave,
        iconColor = colors.textPrimary,
        title = "보이는 소리",
        subtitle = Copy.listeningSubtitle(state.listening),
        tabs = { SegmentedTabs(mainTabs(state), Screen.MAIN, actions.onSelectScreen) },
        suffix = { SettingsEntry(state, actions) },
    )
}

@Composable
fun MainScreen(state: UiState, onePane: Boolean, actions: UiActions) {
    val colors = LocalColors.current
    val alert = state.alert ?: state.notice
    val band = bandStyle(state.copy(screen = Screen.MAIN))
    val cards = @Composable {
        val modifier = Modifier.fillMaxWidth()
        // A script's first event reports the gear; until then none has played.
        if (alert != null) AlertCard(alert, modifier) else IdleCard(state.listening, state.gear != null, modifier)
        state.recentCaption?.let { CaptionCard(it, state.parked, fresh = true) }
    }
    if (onePane) {
        OnePaneFrame(band) {
            if (state.parked) MainNavigation(state, actions)
            cards()
        }
        return
    }
    TwoPaneFrame(
        band = band,
        side = {
            Label("소리 방향", Tokens.bodySmall, colors.textTertiary)
            CarDirectionDiagram(
                direction = state.alert?.direction,
                showArcs = state.alert != null,
                color = alert?.let { colors.accent(it.kind.accent()) } ?: colors.siren,
            )
            RhythmLegend(alert?.rhythm)
        },
    ) {
        MainNavigation(state, actions)
        cards()
    }
}
