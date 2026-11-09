package app.birdeye.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.birdeye.core.Copy
import app.birdeye.core.Screen
import app.birdeye.core.UiState
import app.birdeye.ui.component.AlertCard
import app.birdeye.ui.component.CaptionCard
import app.birdeye.ui.component.CarDirectionDiagram
import app.birdeye.ui.component.ConditionBar
import app.birdeye.ui.component.IdleCard
import app.birdeye.ui.component.Label
import app.birdeye.ui.component.LineIcons
import app.birdeye.ui.component.OnePaneFrame
import app.birdeye.ui.component.PageNavigation
import app.birdeye.ui.component.RhythmLegend
import app.birdeye.ui.component.SegmentedTabs
import app.birdeye.ui.component.TwoPaneFrame
import app.birdeye.ui.component.accent
import app.birdeye.ui.component.bandStyle
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens

@Composable
private fun MainNavigation(state: UiState, actions: UiActions) {
    val colors = LocalColors.current
    PageNavigation(
        icon = LineIcons.wave,
        iconColor = colors.textPrimary,
        title = "birdeye",
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
        if (alert != null) {
            AlertCard(alert, modifier)
        } else {
            IdleCard(state.listening, state.gear != null, state.condition?.muffles == true, modifier)
        }
        state.recentCaption?.let { CaptionCard(it, state.parked, fresh = true) }
        // A narrow window has no room for it below the system's camera window.
        if (!(onePane && state.systemCameraUp)) state.condition?.let { ConditionBar(it) }
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
