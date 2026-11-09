package app.visiblesound.source.demo

import app.visiblesound.core.AlertKind
import app.visiblesound.core.AppEvent
import app.visiblesound.core.EngineState
import app.visiblesound.core.EntryKind
import app.visiblesound.core.GearPos
import app.visiblesound.core.RuleEngine
import app.visiblesound.core.Screen
import app.visiblesound.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Replays the shipped demo through the real rules: the presentation order in spec 6.2 must hold. */
class DemoScenarioTest {
    private val json = File("src/main/assets/demo/evening_commute.json").readText()

    private fun replay(): List<UiState> {
        val engine = RuleEngine()
        var state = EngineState()
        val timeline = mutableListOf<UiState>()
        val events = DemoScript.parse(json, startAt = 0)
        var clock = 0L
        val end = events.last().t + 10_000
        var next = 0
        while (clock <= end) {
            while (next < events.size && events[next].t <= clock) {
                state = engine.reduce(state, events[next].event).state
                next++
            }
            state = engine.reduce(state, AppEvent.Tick(clock)).state
            timeline += state.ui
            clock += 100
        }
        return timeline
    }

    @Test
    fun `the show runs siren then conversation then window then recap`() {
        val timeline = replay()

        val sirenAt = timeline.indexOfFirst { it.alert?.kind == AlertKind.SIREN }
        val talkAt = timeline.indexOfFirst { it.alert == null && it.screen == Screen.TALK && it.captions.isNotEmpty() }
        val replyAt = timeline.indexOfFirst { state -> state.captions.any { it.speaker.label == "운전자 (음성 답장)" } }
        val windowAt = timeline.indexOfFirst { it.screen == Screen.WINDOW && it.outside != null }

        assertTrue("siren should fire", sirenAt >= 0)
        assertTrue("conversation follows the siren", talkAt > sirenAt)
        assertTrue("reply follows the conversation", replyAt > talkAt)
        assertTrue("window follows the reply", windowAt > replyAt)
    }

    @Test
    fun `the car ends up parked with a recap worth showing`() {
        val last = replay().last()

        assertEquals(GearPos.P, last.gear)
        val counts = last.trip.counts()
        assertEquals(1, counts[EntryKind.SIREN])
        assertTrue((counts[EntryKind.TALK] ?: 0) >= 2)
        assertTrue((counts[EntryKind.VEHICLE] ?: 0) >= 1)
        assertTrue((counts[EntryKind.HORN] ?: 0) >= 1)
        assertTrue((counts[EntryKind.OUTSIDE] ?: 0) >= 1)
        assertTrue(last.trip.distanceMeters > 0)
    }

    @Test
    fun `the script speaks to a friend and never mentions a taxi`() {
        assertFalse(json.contains("기사"))
        assertFalse(json.contains("택시"))
        assertFalse(json.contains("결제"))
    }

    @Test
    fun `captions shown while driving fit on one line`() {
        val tooLong = DemoScript.parse(json, 0)
            .map { it.event }
            .filterIsInstance<AppEvent.Caption>()
            .filter { it.text.length > 30 }

        assertTrue("scripted captions over 30 characters: $tooLong", tooLong.isEmpty())
    }
}
