package app.birdeye.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {
    private class RecordingOutputs : Outputs {
        val effects = mutableListOf<Effect>()
        override fun handle(effect: Effect) {
            effects += effect
        }
    }

    @Test
    fun `posted events update the state and side effects reach the outputs`() = runTest {
        val bus = EventBus()
        val outputs = RecordingOutputs()
        val app = AppState(bus, outputs, clock = { testScheduler.currentTime }, scope = backgroundScope)
        app.start()
        runCurrent()

        bus.post(AppEvent.Reply("응, 알겠어", at = 0))
        runCurrent()

        assertEquals("응, 알겠어", app.state.value.captions.last().text)
        assertTrue(outputs.effects.contains(Effect.Speak("응, 알겠어")))
    }

    @Test
    fun `time passing alone clears an alert`() = runTest {
        val bus = EventBus()
        val app = AppState(bus, RecordingOutputs(), clock = { testScheduler.currentTime }, scope = backgroundScope)
        app.start()
        runCurrent()

        repeat(3) { i ->
            bus.post(AppEvent.Sound(SoundKind.SIREN, Direction.REAR, 0.9f, at = testScheduler.currentTime))
            runCurrent()
            if (i < 2) advanceTimeBy(500)
        }
        assertEquals(AlertKind.SIREN, app.state.value.alert?.kind)

        advanceTimeBy(4500)
        runCurrent()

        assertNull(app.state.value.alert)
    }

    @Test
    fun `an output failure does not stop later events`() = runTest {
        val bus = EventBus()
        val outputs = object : Outputs {
            override fun handle(effect: Effect) = error("car service went away")
        }
        val app = AppState(bus, outputs, clock = { testScheduler.currentTime }, scope = backgroundScope)
        app.start()
        runCurrent()

        bus.post(AppEvent.Reply("하나", at = 0))
        runCurrent()
        bus.post(AppEvent.Reply("둘", at = 1))
        runCurrent()

        assertEquals(listOf("하나", "둘"), app.state.value.captions.map { it.text })
    }

    @Test
    fun `replies can be replaced from settings`() = runTest {
        val bus = EventBus()
        val app = AppState(bus, RecordingOutputs(), clock = { testScheduler.currentTime }, scope = backgroundScope)
        app.start()
        runCurrent()

        bus.post(AppEvent.SetReplies(listOf("가", "나", "다", "라"), at = 0))
        runCurrent()

        assertEquals(listOf("가", "나", "다", "라"), app.state.value.replies)
    }
}
