package app.visiblesound.source.demo

import app.visiblesound.core.AlertKind
import app.visiblesound.core.AppEvent
import app.visiblesound.core.Effect
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

    private fun replay(speaksAloud: Boolean = false): List<UiState> {
        val engine = RuleEngine()
        var state = EngineState()
        val timeline = mutableListOf<UiState>()
        val events = DemoScript.parse(json, startAt = 0)
        var clock = 0L
        val end = events.last().t + 10_000
        var next = 0
        var speechEndsAt: Long? = null
        while (clock <= end) {
            while (next < events.size && events[next].t <= clock) {
                val reduction = engine.reduce(state, events[next].event)
                state = reduction.state
                val spoken = reduction.effects.filterIsInstance<Effect.Speak>().lastOrNull()
                if (speaksAloud && spoken != null) {
                    state = engine.reduce(state, AppEvent.Tts(playing = true, at = clock)).state
                    speechEndsAt = clock + spoken.text.length * SLOW_VOICE_MS_PER_CHAR
                }
                next++
            }
            if (speechEndsAt != null && clock >= speechEndsAt) {
                state = engine.reduce(state, AppEvent.Tts(playing = false, at = clock)).state
                speechEndsAt = null
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
    fun `no scripted sentence is mistaken for the car's own voice`() {
        val logged = replay(speaksAloud = true).last().trip.entries.map { it.text }
        val scripted = DemoScript.parse(json, 0).mapNotNull {
            when (val event = it.event) {
                is AppEvent.Caption -> event.text
                is AppEvent.Outside -> event.text
                else -> null
            }
        }

        val dropped = scripted.filter { sentence -> logged.none { sentence in it } }

        assertTrue("dropped while the car was speaking: $dropped", dropped.isEmpty())
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

    private companion object {
        /** The emulator's voice took up to 7.1 s for the 46-character window sentence; this allows for a slower one. */
        const val SLOW_VOICE_MS_PER_CHAR = 220L
    }
}
