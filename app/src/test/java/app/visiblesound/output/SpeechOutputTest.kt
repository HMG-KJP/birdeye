package app.visiblesound.output

import app.visiblesound.core.AppEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechOutputTest {
    private class FakeEngine : TtsEngine {
        lateinit var callbacks: TtsCallbacks
        var initResult: Boolean? = true
        val spoken = mutableListOf<String>()
        var released = false
        var failOnSpeak = false
        var stops = 0

        override fun initialize(callbacks: TtsCallbacks) {
            this.callbacks = callbacks
            initResult?.let { callbacks.onInitialized(it) }
        }

        override fun speak(text: String) {
            if (failOnSpeak) error("engine gone")
            spoken += text
        }

        override fun stop() {
            stops++
        }

        override fun release() {
            released = true
        }
    }

    private class FakeFocus : TtsAudioFocus {
        var held = false
        var requests = 0
        var refuse = false
        var onLost: () -> Unit = {}

        override fun request(onLost: () -> Unit): Boolean {
            requests++
            this.onLost = onLost
            held = !refuse
            return held
        }

        override fun abandon() {
            held = false
        }
    }

    private val engine = FakeEngine()
    private val focus = FakeFocus()
    private val events = mutableListOf<AppEvent.Tts>()

    private fun TestScope.output() = SpeechOutput(
        engine,
        focus,
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
        sink = { events += it },
    )

    @Test
    fun `when the car refuses audio focus nothing is spoken and the reply counts as not said`() = runTest {
        focus.refuse = true
        val output = output()
        output.start()

        output.speak("응, 알겠어")

        assertTrue(engine.spoken.isEmpty())
        assertEquals(listOf(false), events.map { it.playing })
        assertEquals(false, events.single().interrupted)
    }

    @Test
    fun `losing audio focus stops the voice and tells the app the reply was cut off`() = runTest {
        val output = output()
        output.start()
        output.speak("응, 알겠어")
        engine.callbacks.onStarted()
        events.clear()

        focus.onLost()

        assertEquals(1, engine.stops)
        assertEquals(false, focus.held)
        assertEquals(true, events.single().interrupted)
    }

    @Test
    fun `the engine's own stop report after a focus loss adds nothing`() = runTest {
        val output = output()
        output.start()
        output.speak("응, 알겠어")
        focus.onLost()
        events.clear()

        engine.callbacks.onFinished()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a focus loss after the reply ended changes nothing`() = runTest {
        val output = output()
        output.start()
        output.speak("응, 알겠어")
        engine.callbacks.onFinished()
        events.clear()

        focus.onLost()

        assertEquals(0, engine.stops)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `speaking asks the engine and takes audio focus so music ducks`() = runTest {
        val output = output()
        output.start()

        output.speak("응, 알겠어")

        assertEquals(listOf("응, 알겠어"), engine.spoken)
        assertTrue(focus.held)
    }

    @Test
    fun `the app learns when playback starts and ends, and gives focus back`() = runTest {
        val output = output()
        output.start()
        output.speak("응")

        engine.callbacks.onStarted()
        assertEquals(listOf(true), events.map { it.playing })

        engine.callbacks.onFinished()
        assertEquals(listOf(true, false), events.map { it.playing })
        assertEquals(false, focus.held)
    }

    @Test
    fun `an engine error ends playback the same way`() = runTest {
        val output = output()
        output.start()
        output.speak("응")
        engine.callbacks.onStarted()

        engine.callbacks.onFinished()

        assertEquals(false, events.last().playing)
    }

    @Test
    fun `a reply pressed before the engine is ready plays once it is`() = runTest {
        engine.initResult = null
        val output = output()
        output.start()

        output.speak("응")
        assertEquals(emptyList<String>(), engine.spoken)

        engine.callbacks.onInitialized(true)

        assertEquals(listOf("응"), engine.spoken)
    }

    @Test
    fun `when the engine cannot start the reply is reported finished so the buttons come back`() = runTest {
        engine.initResult = false
        val output = output()
        output.start()

        output.speak("응")

        assertEquals(emptyList<String>(), engine.spoken)
        assertEquals(listOf(false), events.map { it.playing })
        assertEquals(false, focus.held)
    }

    @Test
    fun `if the engine never reports the end the microphone is not muted forever`() = runTest {
        val output = output()
        output.start()
        output.speak("응")
        engine.callbacks.onStarted()
        runCurrent()

        advanceTimeBy(SpeechOutput.MAX_PLAYBACK_MS + 1)
        runCurrent()

        assertEquals(false, events.last().playing)
        assertEquals(false, focus.held)
    }

    @Test
    fun `the watchdog does not fire after a normal finish`() = runTest {
        val output = output()
        output.start()
        output.speak("응")
        engine.callbacks.onStarted()
        engine.callbacks.onFinished()
        val before = events.size

        advanceTimeBy(SpeechOutput.MAX_PLAYBACK_MS + 1)
        runCurrent()

        assertEquals(before, events.size)
    }

    @Test
    fun `an engine that blows up on start leaves the app running and reports replies as finished`() = runTest {
        val broken = object : TtsEngine {
            override fun initialize(callbacks: TtsCallbacks) = error("service missing")
            override fun speak(text: String) = error("not ready")
            override fun stop() = Unit
            override fun release() = Unit
        }
        val errors = mutableListOf<Throwable>()
        val output = SpeechOutput(
            broken,
            focus,
            scope = backgroundScope,
            clock = { testScheduler.currentTime },
            sink = { events += it },
            onError = { errors += it },
        )

        output.start()
        output.speak("응")

        assertEquals(1, errors.size)
        assertEquals(listOf(false), events.map { it.playing })
    }

    @Test
    fun `an engine that fails while speaking gives the focus back and reports the reply finished`() = runTest {
        engine.failOnSpeak = true
        val output = output()
        output.start()

        output.speak("응")

        assertEquals(listOf(false), events.map { it.playing })
        assertEquals(false, focus.held)
    }

    @Test
    fun `a late start callback after the reply ended cannot leave the app stuck as playing`() = runTest {
        val output = output()
        output.start()
        output.speak("응")
        engine.callbacks.onFinished()

        engine.callbacks.onStarted()

        assertEquals(false, events.last().playing)
    }

    @Test
    fun `an engine that finishes while still inside speak does not leave a watchdog behind`() = runTest {
        val immediate = object : TtsEngine {
            lateinit var callbacks: TtsCallbacks
            override fun initialize(callbacks: TtsCallbacks) {
                this.callbacks = callbacks
                callbacks.onInitialized(true)
            }

            override fun speak(text: String) = callbacks.onFinished()
            override fun stop() = Unit
            override fun release() = Unit
        }
        val output = SpeechOutput(immediate, focus, backgroundScope, { testScheduler.currentTime }, { events += it })
        output.start()
        output.speak("응")
        val before = events.size

        advanceTimeBy(SpeechOutput.MAX_PLAYBACK_MS + 1)
        runCurrent()

        assertEquals(before, events.size)
    }

    @Test
    fun `stop releases the engine`() = runTest {
        val output = output()
        output.start()

        output.stop()

        assertTrue(engine.released)
    }
}
