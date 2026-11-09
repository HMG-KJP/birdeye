package app.visiblesound.source

import app.visiblesound.core.AppEvent
import app.visiblesound.core.NoiseKind
import app.visiblesound.core.SoundKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicPumpTest {
    /** Hands out [chunks] chunks of audio and then reports the end of the stream. */
    private class FakeReader(private val chunks: Int, private val sampleValue: Short = 100) : PcmReader {
        private var served = 0
        override fun read(buffer: ShortArray): Int {
            if (served >= chunks) return -1
            served++
            buffer.fill(sampleValue)
            return buffer.size
        }
    }

    private class FakeSpeech : SpeechFeed {
        val sent = mutableListOf<ByteArray>()
        var completes = 0
        override fun send(pcm: ByteArray) {
            sent += pcm
        }

        override fun complete() {
            completes++
        }
    }

    private val events = mutableListOf<AppEvent>()
    private val speech = FakeSpeech()
    private var classified = 0

    private fun pump(
        chunks: Int,
        chunkSize: Int = 4,
        windowSize: Int = 8,
        hopSize: Int = 4,
        speechOpen: () -> Boolean = { true },
    ) = MicPump(
        reader = FakeReader(chunks),
        classify = {
            classified++
            listOf("Siren" to 0.9f)
        },
        speech = speech,
        speechOpen = speechOpen,
        clock = { 42L },
        sink = { events += it },
        chunkSize = chunkSize,
        windowSize = windowSize,
        hopSize = hopSize,
    )

    @Test
    fun `the same classifier frame also reports rain and wind`() = runTest {
        MicPump(
            reader = FakeReader(2),
            classify = { listOf("Rain" to 0.7f) },
            speech = speech,
            speechOpen = { true },
            clock = { 42L },
            sink = { events += it },
            chunkSize = 4,
            windowSize = 8,
            hopSize = 4,
        ).run()

        assertEquals(
            AppEvent.AmbientNoise(NoiseKind.RAIN, 0.7f, at = 42L),
            events.filterIsInstance<AppEvent.AmbientNoise>().first(),
        )
    }

    @Test
    fun `sound classification sees overlapping windows of the stream`() = runTest {
        pump(chunks = 4).run()

        assertEquals(3, classified)
    }

    @Test
    fun `classifier results become sound events stamped with the clock`() = runTest {
        pump(chunks = 2).run()

        val sound = events.filterIsInstance<AppEvent.Sound>().first { it.kind == SoundKind.SIREN }
        assertEquals(0.9f, sound.confidence, 0.0001f)
        assertEquals(42L, sound.at)
    }

    @Test
    fun `speech recognition gets the same audio as sixteen bit bytes`() = runTest {
        pump(chunks = 3).run()

        assertEquals(3, speech.sent.size)
        assertEquals(8, speech.sent.first().size)
    }

    @Test
    fun `speech recognition gets nothing while the route is closed but sound keeps being classified`() = runTest {
        pump(chunks = 4, speechOpen = { false }).run()

        assertEquals(0, speech.sent.size)
        assertTrue(classified > 0)
    }

    @Test
    fun `closing the route tells the recognizer the sentence is over, once`() = runTest {
        var chunk = 0
        pump(chunks = 6, speechOpen = { chunk++ < 2 }).run()

        assertEquals(2, speech.sent.size)
        assertEquals(1, speech.completes)
    }

    @Test
    fun `opening the route again feeds it again`() = runTest {
        var chunk = 0
        pump(chunks = 6, speechOpen = { chunk++ != 2 }).run()

        assertEquals(5, speech.sent.size)
        assertEquals(2, speech.completes) // once when it closed, once at the end of the stream
    }

    @Test
    fun `the end of the stream closes an open recognition`() = runTest {
        pump(chunks = 2).run()

        assertEquals(1, speech.completes)
    }

    @Test
    fun `a classifier failure does not stop the microphone or the captions`() = runTest {
        val failing = MicPump(
            reader = FakeReader(3),
            classify = { error("native crash") },
            speech = speech,
            speechOpen = { true },
            clock = { 0L },
            sink = { events += it },
            chunkSize = 4,
            windowSize = 4,
            hopSize = 4,
        )

        failing.run()

        assertEquals(3, speech.sent.size)
        assertFalse(events.any { it is AppEvent.Sound })
    }
}

class SpeechSessionTest {
    private val events = mutableListOf<AppEvent>()

    private class FakeEngine : SttEngine {
        lateinit var callbacks: SttCallbacks
        override fun initialize(callbacks: SttCallbacks) {
            this.callbacks = callbacks
        }

        override fun send(pcm: ByteArray) = Unit
        override fun complete() = Unit
        override fun release() = Unit
    }

    private val engine = FakeEngine()
    private var occupied = setOf(app.visiblesound.core.Seat.DRIVER, app.visiblesound.core.Seat.PASSENGER)
    private var outsideMode = false
    private val session = SpeechSession(
        engine,
        occupied = { occupied },
        outsideMode = { outsideMode },
        clock = { 9L },
        sink = { events += it },
    ).also { it.start() }

    @Test
    fun `recognized text goes to the passenger when there is one`() {
        engine.callbacks.onText("저기", isFinal = false)

        assertEquals(AppEvent.Caption(app.visiblesound.core.Seat.PASSENGER, "저기", false, 9L), events.single())
    }

    @Test
    fun `a window conversation goes to the person outside`() {
        outsideMode = true

        engine.callbacks.onText("면허증 주세요", isFinal = true)

        assertEquals(AppEvent.Outside("면허증 주세요", true, 9L), events.single())
    }

    @Test
    fun `blank results are dropped`() {
        engine.callbacks.onText("   ", isFinal = true)

        assertTrue(events.isEmpty())
    }
}
