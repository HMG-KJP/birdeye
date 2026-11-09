package app.birdeye.ui.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.birdeye.core.Briefing
import app.birdeye.core.Copy
import app.birdeye.core.Screen
import app.birdeye.core.UiState
import app.birdeye.ui.component.AlertCard
import app.birdeye.ui.component.CaptionCard
import app.birdeye.ui.component.AlsoHeardCard
import app.birdeye.ui.component.BirdEyeMap
import app.birdeye.ui.component.BriefingAndRecent
import app.birdeye.ui.component.BriefingCard
import app.birdeye.ui.component.CaptionsHeldCard
import app.birdeye.ui.component.IdleCard
import app.birdeye.ui.component.LineIcons
import app.birdeye.ui.component.LocalSystemCameraHeight
import app.birdeye.ui.component.MapPane
import app.birdeye.ui.component.OnePaneFrame
import app.birdeye.ui.component.PageNavigation
import app.birdeye.ui.component.SegmentedTabs
import app.birdeye.ui.component.TwoPaneFrame
import app.birdeye.ui.component.bandStyle
import app.birdeye.ui.component.mapSounds
import app.birdeye.ui.theme.LocalColors

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
    val cameraUp = LocalSystemCameraHeight.current > 0.dp
    // A notice shown on the big card needs no repeating in the briefing just below it.
    val briefing = Briefing.lines(if (state.alert == null) state.copy(notice = null) else state, actions.hourOf(actions.now()), actions.timeText)
    val cards = @Composable {
        val modifier = Modifier.fillMaxWidth()
        // A script's first event reports the gear; until then none has played.
        if (alert != null) {
            AlertCard(alert, modifier)
        } else {
            IdleCard(state.listening, state.gear != null, state.condition?.muffles == true, modifier)
        }
        state.alsoHeard?.let { AlsoHeardCard(it) }
        state.recentCaption?.let {
            if (state.captionsHeld) CaptionsHeldCard() else CaptionCard(it, state.parked, fresh = true)
        }
    }
    if (onePane) {
        OnePaneFrame(band) {
            if (state.parked) MainNavigation(state, actions)
            cards()
            if (!cameraUp) {
                BirdEyeMap(mapSounds(state, colors), Modifier.weight(1f).fillMaxWidth())
                BriefingCard(briefing.take(1))
            }
        }
        return
    }
    TwoPaneFrame(
        band = band,
        side = { MapPane(state, mapSounds(state, colors)) },
    ) {
        MainNavigation(state, actions)
        cards()
        if (!cameraUp) {
            BriefingAndRecent(briefing, state, actions, Modifier.weight(1f))
        }
    }
}
