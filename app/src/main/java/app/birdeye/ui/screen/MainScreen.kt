package app.birdeye.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.birdeye.core.Briefing
import app.birdeye.core.Copy
import app.birdeye.core.Screen
import app.birdeye.core.UiState
import app.birdeye.ui.component.AlertHero
import app.birdeye.ui.component.AlsoHeardLine
import app.birdeye.ui.component.BirdEyeMap
import app.birdeye.ui.component.BriefingSection
import app.birdeye.ui.component.CaptionCard
import app.birdeye.ui.component.CaptionsHeldCard
import app.birdeye.ui.component.DoorStatusLines
import app.birdeye.ui.component.IdleHero
import app.birdeye.ui.component.LineIcons
import app.birdeye.ui.component.LocalSystemCameraHeight
import app.birdeye.ui.component.MapPane
import app.birdeye.ui.component.OnePaneFrame
import app.birdeye.ui.component.PageNavigation
import app.birdeye.ui.component.RecentSounds
import app.birdeye.ui.component.SegmentedTabs
import app.birdeye.ui.component.SituationStack
import app.birdeye.ui.component.TwoPaneFrame
import app.birdeye.ui.component.bandStyle
import app.birdeye.ui.component.mapSounds
import app.birdeye.ui.component.needsAttention
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
    val hero = @Composable {
        Column(Modifier.fillMaxWidth()) {
            // A script's first event reports the gear; until then none has played.
            if (alert != null) {
                AlertHero(alert)
            } else {
                IdleHero(state.listening, state.gear != null, state.condition?.muffles == true)
            }
            state.alsoHeard?.let { AlsoHeardLine(it) }
            DoorStatusLines(state)
        }
    }
    val caption = @Composable {
        val line = state.recentCaption
        if (line != null) {
            if (state.captionsHeld) CaptionsHeldCard() else CaptionCard(line, state.parked, fresh = true)
        }
    }
    val emphasized = needsAttention(alert, state.listening)
    if (onePane) {
        OnePaneFrame(band) {
            if (state.parked) MainNavigation(state, actions)
            SituationStack(
                emphasized = emphasized,
                hero = hero,
                briefing = { if (!cameraUp) BriefingSection(briefing.take(1)) },
                caption = caption,
            )
            if (!cameraUp) {
                BirdEyeMap(mapSounds(state, colors), Modifier.weight(1f).fillMaxWidth(), doors = state.openDoors)
            }
        }
        return
    }
    TwoPaneFrame(
        band = band,
        side = { MapPane(state, mapSounds(state, colors)) },
    ) {
        MainNavigation(state, actions)
        SituationStack(
            emphasized = emphasized,
            hero = hero,
            briefing = { if (!cameraUp) BriefingSection(briefing) },
            caption = caption,
            recent = if (cameraUp) null else { { RecentSounds(state, actions) } },
            modifier = Modifier.weight(1f),
        )
    }
}
