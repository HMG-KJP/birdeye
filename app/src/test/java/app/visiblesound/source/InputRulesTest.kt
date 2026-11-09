package app.visiblesound.source

import app.visiblesound.core.AppEvent
import app.visiblesound.core.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerLocatorTest {
    @Test
    fun `one occupied passenger seat is taken to be the speaker`() {
        val event = SpeakerLocator.attribute("저기", true, setOf(Seat.DRIVER, Seat.REAR_RIGHT), outsideMode = false, at = 5)

        assertEquals(AppEvent.Caption(Seat.REAR_RIGHT, "저기", true, 5), event)
    }

    @Test
    fun `several passengers cannot be told apart so no seat is claimed`() {
        val event = SpeakerLocator.attribute("저기", true, setOf(Seat.DRIVER, Seat.PASSENGER, Seat.REAR_LEFT), false, 5)

        assertEquals(AppEvent.Caption(null, "저기", true, 5), event)
    }

    @Test
    fun `alone in the car any speech must be the driver`() {
        val event = SpeakerLocator.attribute("저기", true, setOf(Seat.DRIVER), false, 5)

        assertEquals(AppEvent.Caption(Seat.DRIVER, "저기", true, 5), event)
    }

    @Test
    fun `outside mode attributes speech to the person at the window`() {
        val event = SpeakerLocator.attribute("면허증 주세요", false, setOf(Seat.DRIVER, Seat.PASSENGER), outsideMode = true, at = 5)

        assertEquals(AppEvent.Outside("면허증 주세요", false, 5), event)
    }
}

class MicArbiterTest {
    @Test
    fun `speech recognition listens when there is someone to listen to`() {
        assertTrue(MicArbiter.speechOpen(hasPassenger = true, outsideMode = false, ttsPlaying = false))
        assertTrue(MicArbiter.speechOpen(hasPassenger = false, outsideMode = true, ttsPlaying = false))
    }

    @Test
    fun `speech recognition stays off when the driver is alone`() {
        assertFalse(MicArbiter.speechOpen(hasPassenger = false, outsideMode = false, ttsPlaying = false))
    }

    @Test
    fun `speech recognition stops while our own voice reply plays`() {
        assertFalse(MicArbiter.speechOpen(hasPassenger = true, outsideMode = true, ttsPlaying = true))
    }
}
