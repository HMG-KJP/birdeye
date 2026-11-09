package app.birdeye.source

import app.birdeye.core.AppEvent
import app.birdeye.core.NoiseKind
import app.birdeye.core.SoundKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class SoundFrameMapperTest {
    private fun sound(events: List<AppEvent.Sound>, kind: SoundKind) = events.single { it.kind == kind }

    @Test
    fun `each frame reports a score for both sounds, so a quiet frame can break a streak`() {
        val events = SoundFrameMapper.map(listOf("Speech" to 0.9f), at = 7)

        assertEquals(setOf(SoundKind.SIREN, SoundKind.HORN), events.map { it.kind }.toSet())
        events.forEach {
            assertEquals(0f, it.confidence, 0f)
            assertEquals(7L, it.at)
        }
    }

    @Test
    fun `related siren labels back each other up instead of only the strongest counting`() {
        val events = SoundFrameMapper.map(listOf("Siren" to 0.2f, "Ambulance (siren)" to 0.2f, "Music" to 0.9f), at = 0)

        // 1 - (1 - 0.2)(1 - 0.2): either label being right means a siren is there.
        assertEquals(0.36f, sound(events, SoundKind.SIREN).confidence, 0.0001f)
        assertEquals(0f, sound(events, SoundKind.HORN).confidence, 0f)
    }

    @Test
    fun `one strong siren label is enough`() {
        val events = SoundFrameMapper.map(listOf("Civil defense siren" to 0.6f), at = 0)

        assertEquals(0.6f, sound(events, SoundKind.SIREN).confidence, 0.0001f)
    }

    @Test
    fun `the horn label sets the horn score`() {
        val events = SoundFrameMapper.map(listOf("Vehicle horn, car horn, honking" to 0.7f), at = 0)

        assertEquals(0.7f, sound(events, SoundKind.HORN).confidence, 0.0001f)
    }

    @Test
    fun `direction is unknown because one microphone cannot locate a sound`() {
        SoundFrameMapper.map(listOf("Siren" to 0.9f), at = 0).forEach { assertNull(it.direction) }
    }

    @Test
    fun `the classifier never names the vehicle, because its fine labels are not reliable`() {
        val ambulance = SoundFrameMapper.map(listOf("Ambulance (siren)" to 0.9f), at = 0)
        val police = SoundFrameMapper.map(listOf("Police car (siren)" to 0.9f), at = 0)

        assertNull(sound(ambulance, SoundKind.SIREN).vehicle)
        assertNull(sound(police, SoundKind.SIREN).vehicle)
    }

    @Test
    fun `rain labels combine into one rain score`() {
        val noise = SoundFrameMapper.mapNoise(listOf("Rain" to 0.5f, "Raindrop" to 0.5f), at = 7)

        assertEquals(listOf(AppEvent.AmbientNoise(NoiseKind.RAIN, 0.75f, at = 7)), noise)
    }

    @Test
    fun `thunder and wind are told apart`() {
        val noise = SoundFrameMapper.mapNoise(listOf("Thunder" to 0.6f, "Wind noise (microphone)" to 0.4f), at = 7)

        assertEquals(setOf(NoiseKind.THUNDER, NoiseKind.WIND), noise.map { it.kind }.toSet())
    }

    @Test
    fun `barely there weather sounds make no event, to keep the stream small`() {
        assertTrue(SoundFrameMapper.mapNoise(listOf("Rain" to 0.05f, "Siren" to 0.9f), at = 7).isEmpty())
    }

    @Test
    fun `the shipped label list covers the model and every sound the app listens for`() {
        val labels = File("src/main/assets/yamnet_labels.txt").readLines().map { it.trim() }

        assertEquals(521, labels.size)
        SoundFrameMapper.LISTENED_LABELS.forEach { assertTrue("no label: $it", it in labels) }
    }

    @Test
    fun `the shipped label list is the one inside the model`() {
        val model = ZipFile(File("src/main/assets/yamnet.tflite")).use { zip ->
            val entry = zip.entries().asSequence().first { it.name.contains("label", ignoreCase = true) }
            zip.getInputStream(entry).bufferedReader().readLines()
        }

        assertEquals(model, File("src/main/assets/yamnet_labels.txt").readLines())
    }
}

class AudioWindowTest {
    private fun samples(vararg values: Int) = ShortArray(values.size) { values[it].toShort() }

    @Test
    fun `no frame comes out until the window has filled`() {
        val window = AudioWindow(windowSize = 4, hopSize = 2)

        assertEquals(0, window.push(samples(1, 2, 3)).size)
    }

    @Test
    fun `a full window comes out with the oldest sample first`() {
        val window = AudioWindow(windowSize = 4, hopSize = 2)

        val frames = window.push(samples(1, 2, 3, 4))

        assertEquals(1, frames.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f).map { it / 32768f }.toFloatArray(), frames[0], 0.00001f)
    }

    @Test
    fun `frames overlap by the hop`() {
        val window = AudioWindow(windowSize = 4, hopSize = 2)
        window.push(samples(1, 2, 3, 4))

        val frames = window.push(samples(5, 6))

        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f).map { it / 32768f }.toFloatArray(), frames.single(), 0.00001f)
    }

    @Test
    fun `a large chunk yields every frame it contains`() {
        val window = AudioWindow(windowSize = 4, hopSize = 2)

        val frames = window.push(samples(1, 2, 3, 4, 5, 6, 7, 8))

        assertEquals(3, frames.size)
    }

    @Test
    fun `bytes for the speech engine are little endian sixteen bit`() {
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0xFF.toByte()), pcmBytes(shortArrayOf(0x1234, -1), 2))
    }
}
