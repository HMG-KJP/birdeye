package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class F2F3TalkRulesTest {
    @Test
    fun `captions hold back while the driver is about to turn, then return`() {
        val h = EngineHarness().gear(GearPos.D).send(AppEvent.TurnSignal(SignalState.LEFT, at = 1000))
        assertEquals(true, h.ui.captionsHeld)

        h.send(AppEvent.TurnSignal(SignalState.OFF, at = 2000))
        assertEquals(false, h.ui.captionsHeld)
    }

    @Test
    fun `the note says what the car knows, a signal on, not that the driver is turning`() {
        assertEquals("방향지시등이 켜져 있어 자막을 잠시 줄였어요", Copy.CAPTIONS_HELD)
    }

    @Test
    fun `a signal forgotten on does not hide captions for the rest of the trip`() {
        val h = EngineHarness().gear(GearPos.D).send(AppEvent.TurnSignal(SignalState.LEFT, at = 1000))
        h.tick(21_000)

        assertEquals(false, h.ui.captionsHeld)
    }

    private fun driving() = EngineHarness().gear(GearPos.D)
    private fun caption(text: String, at: Long, seat: Seat? = Seat.PASSENGER, isFinal: Boolean = true) =
        AppEvent.Caption(seat, text, isFinal, at)

    @Test
    fun `a passenger sentence opens the talk screen and highlights the seat`() {
        val h = driving().send(caption("저기 편의점 앞에 세워 줘", at = 100))

        assertEquals(Screen.TALK, h.ui.screen)
        assertEquals(Seat.PASSENGER, h.ui.activeSeat)
        assertEquals("저기 편의점 앞에 세워 줘", h.ui.captions.last().text)
        assertEquals(Speaker.Passenger(Seat.PASSENGER), h.ui.captions.last().speaker)
    }

    @Test
    fun `an unknown seat shows no highlighted seat`() {
        val h = driving().send(caption("잠깐만", at = 100, seat = null))

        assertEquals(null, h.ui.activeSeat)
        assertEquals(Speaker.Passenger(null), h.ui.captions.last().speaker)
    }

    @Test
    fun `the driver seat is never captioned`() {
        val h = driving().send(caption("내가 한 말", at = 100, seat = Seat.DRIVER))

        assertTrue(h.ui.captions.isEmpty())
        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `partial results update one line until the final result commits it`() {
        val h = driving()
            .send(caption("저기", at = 100, isFinal = false))
            .send(caption("저기 편의점", at = 200, isFinal = false))
            .send(caption("저기 편의점 앞에", at = 300, isFinal = true))
            .send(caption("세워 줘", at = 400, isFinal = false))

        assertEquals(listOf("저기 편의점 앞에", "세워 줘"), h.ui.captions.map { it.text })
    }

    @Test
    fun `only committed sentences are written to the trip log`() {
        val h = driving()
            .send(caption("저기", at = 100, isFinal = false))
            .send(caption("저기 편의점", at = 200, isFinal = true))

        assertEquals(listOf(EntryKind.TALK), h.ui.trip.entries.map { it.kind })
        assertEquals("조수석: \"저기 편의점\"", h.ui.trip.entries.single().text)
    }

    @Test
    fun `a reply cut off by something else in the car is reported, not confirmed as sent`() {
        val h = driving()
            .send(AppEvent.Reply("응, 알겠어", at = 0))
            .send(AppEvent.Tts(playing = true, at = 100))
            .send(AppEvent.Tts(playing = false, at = 500, interrupted = true))

        assertEquals(Copy.REPLY_INTERRUPTED, h.ui.toast)
        assertEquals(null, h.ui.replying)
        assertTrue(h.ui.captions.isEmpty())
    }

    @Test
    fun `speech heard while our own voice reply plays is dropped`() {
        val h = driving()
            .send(AppEvent.Tts(playing = true, at = 100))
            .send(caption("응, 알겠어", at = 200))

        assertTrue(h.ui.captions.isEmpty())
    }

    @Test
    fun `speech right after the reply ends is dropped too but later speech is kept`() {
        val h = driving()
            .send(AppEvent.Tts(playing = true, at = 100))
            .send(AppEvent.Tts(playing = false, at = 1000))
            .send(caption("메아리", at = 1400))
            .send(caption("고마워", at = 1600))

        assertEquals(listOf("고마워"), h.ui.captions.map { it.text })
    }

    @Test
    fun `the latest sentence is cut to thirty characters while not parked`() {
        val longText = "가".repeat(31)
        val line = CaptionLine(Speaker.Passenger(Seat.PASSENGER), longText, at = 0, isFinal = true)

        assertEquals("가".repeat(30) + "…", line.displayText(parked = false))
    }

    @Test
    fun `the whole sentence is shown when parked`() {
        val longText = "가".repeat(31)
        val line = CaptionLine(Speaker.Passenger(Seat.PASSENGER), longText, at = 0, isFinal = true)

        assertEquals(longText, line.displayText(parked = true))
    }

    @Test
    fun `thirty characters are not cut`() {
        val text = "가".repeat(30)
        val line = CaptionLine(Speaker.Passenger(null), text, at = 0, isFinal = true)

        assertEquals(text, line.displayText(parked = false))
    }

    @Test
    fun `first words after silence pulse the light once but the rest of the talk does not`() {
        val h = driving()
            .send(caption("저기", at = 100, isFinal = false))
            .send(caption("저기 편의점", at = 300))
            .send(caption("앞에 세워 줘", at = 3000))

        assertEquals(listOf(LightRhythm.PASSENGER), h.lightEffects())
    }

    @Test
    fun `words after a long silence pulse the light again`() {
        val h = driving()
            .send(caption("저기", at = 100))
            .send(caption("있잖아", at = 100 + 5001))

        assertEquals(listOf(LightRhythm.PASSENGER, LightRhythm.PASSENGER), h.lightEffects())
    }

    @Test
    fun `a reply in the middle of a sentence does not cut the sentence off`() {
        val h = driving()
            .send(caption("저기 편의점", at = 1000, isFinal = false))
            .send(AppEvent.Reply("응, 알겠어", at = 1100))
            .send(caption("저기 편의점 앞에 세워 줘", at = 1200, isFinal = true))

        assertEquals(listOf("저기 편의점 앞에 세워 줘", "응, 알겠어"), h.ui.captions.map { it.text })
        assertEquals(listOf(EntryKind.TALK), h.ui.trip.entries.map { it.kind })
    }

    @Test
    fun `the end of a sentence started before our voice played still counts`() {
        val h = driving()
            .send(caption("저기 편의점", at = 1000, isFinal = false))
            .send(AppEvent.Reply("응, 알겠어", at = 1100))
            .send(AppEvent.Tts(playing = true, at = 1300))
            .send(caption("저기 편의점 앞에 세워 줘", at = 1500, isFinal = true))

        assertEquals("저기 편의점 앞에 세워 줘", h.ui.captions.first().text)
        assertEquals(listOf(EntryKind.TALK), h.ui.trip.entries.map { it.kind })
    }

    @Test
    fun `our own voice is still dropped when it does not continue a sentence`() {
        val h = driving()
            .send(caption("저기 편의점", at = 1000, isFinal = false))
            .send(AppEvent.Tts(playing = true, at = 1300))
            .send(caption("응, 알겠어", at = 1500, isFinal = true))

        assertEquals(listOf("저기 편의점"), h.ui.captions.map { it.text })
    }

    @Test
    fun `the main screen only shows a sentence while the conversation is fresh`() {
        val h = driving().send(caption("저기 세워 줘", at = 1000))
        assertEquals("저기 세워 줘", h.ui.recentCaption?.text)

        h.tick(1000 + 20_000)

        assertEquals(null, h.ui.recentCaption)
        assertEquals(listOf("저기 세워 줘"), h.ui.captions.map { it.text })
    }

    @Test
    fun `a new trip starts with an empty conversation`() {
        val h = driving().send(caption("저기 세워 줘", at = 1000)).gear(GearPos.P, at = 2000)
        h.tick(2000 + 120_000)

        h.gear(GearPos.D, at = 200_000)

        assertEquals(emptyList<CaptionLine>(), h.ui.captions)
    }

    @Test
    fun `talk screen falls back to main after the conversation goes quiet`() {
        val h = driving().send(caption("저기", at = 1000))

        assertEquals(Screen.TALK, h.tick(1000 + 19_999).ui.screen)
        assertEquals(Screen.MAIN, h.tick(1000 + 20_000).ui.screen)
    }

    @Test
    fun `a reply speaks through the car and joins the conversation as the driver`() {
        val h = driving()
            .send(caption("여기 세워 줘", at = 100))
            .send(AppEvent.Reply("응, 알겠어", at = 200))

        assertEquals(listOf("응, 알겠어"), h.spoken())
        val line = h.ui.captions.last()
        assertEquals(Speaker.DriverReply, line.speaker)
        assertEquals("응, 알겠어", line.text)
        assertEquals("운전자 (음성 답장)", line.speaker.label)
    }

    @Test
    fun `a reply keeps the talk screen open`() {
        val h = driving()
            .send(caption("여기 세워 줘", at = 1000))
            .send(AppEvent.Reply("응, 알겠어", at = 15_000))

        assertEquals(Screen.TALK, h.tick(30_000).ui.screen)
    }

    @Test
    fun `an alert covers the talk screen and ending it returns to the conversation`() {
        val h = driving().send(caption("저기", at = 0))
        h.frames(SoundKind.SIREN, from = 500) // fires at 1500, last heard at 1500

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
        assertEquals(Screen.TALK, h.ui.screen)

        h.tick(5500)

        assertEquals(null, h.ui.alert)
        assertEquals(Screen.TALK, h.ui.screen)
    }
}
