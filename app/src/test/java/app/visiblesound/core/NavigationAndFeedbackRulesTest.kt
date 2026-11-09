package app.visiblesound.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndFeedbackRulesTest {
    private fun caption(text: String, at: Long) = AppEvent.Caption(Seat.PASSENGER, text, true, at)

    @Test
    fun `the talk tab can be opened by hand and stays open`() {
        val h = EngineHarness().gear(GearPos.D).send(AppEvent.SelectScreen(Screen.TALK, at = 10))

        assertEquals(Screen.TALK, h.ui.screen)
        assertEquals(Screen.TALK, h.tick(10 * 60_000).ui.screen)
    }

    @Test
    fun `the drive tab closes the conversation until someone speaks again`() {
        val h = EngineHarness().gear(GearPos.D).send(caption("저기", at = 10))

        h.send(AppEvent.SelectScreen(Screen.MAIN, at = 20))
        assertEquals(Screen.MAIN, h.ui.screen)

        h.send(caption("있잖아", at = 30))
        assertEquals(Screen.TALK, h.ui.screen)
    }

    @Test
    fun `a pinned talk tab is released by leaving it`() {
        val h = EngineHarness().gear(GearPos.D)
            .send(AppEvent.SelectScreen(Screen.TALK, at = 10))
            .send(AppEvent.SelectScreen(Screen.MAIN, at = 20))
            .send(caption("저기", at = 30))

        assertEquals(Screen.TALK, h.ui.screen)
        assertEquals(Screen.MAIN, h.tick(30 + 20_000).ui.screen)
    }

    @Test
    fun `the record tab does nothing while driving`() {
        val h = EngineHarness().gear(GearPos.D).send(AppEvent.SelectScreen(Screen.RECAP, at = 10))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `selecting the window screen by hand is not possible`() {
        val h = EngineHarness().send(AppEvent.SelectScreen(Screen.WINDOW, at = 10))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `leaving the record tab for the talk tab closes the record`() {
        val h = EngineHarness()
            .send(AppEvent.SelectScreen(Screen.RECAP, at = 10))
            .send(AppEvent.SelectScreen(Screen.TALK, at = 20))

        assertEquals(Screen.TALK, h.ui.screen)
    }

    @Test
    fun `the confirmation appears when the car has actually spoken and then goes away`() {
        val h = EngineHarness()
            .send(AppEvent.Reply("응, 알겠어", at = 1000))
            .send(AppEvent.Tts(playing = true, at = 1100))
        assertNull(h.ui.toast)

        h.send(AppEvent.Tts(playing = false, at = 2000))

        assertEquals("답장을 보냈어요", h.ui.toast)
        assertEquals("답장을 보냈어요", h.tick(4499).ui.toast)
        assertNull(h.tick(4500).ui.toast)
    }

    @Test
    fun `a reply the car could not speak says so and leaves the conversation`() {
        val h = EngineHarness()
            .send(AppEvent.Reply("응, 알겠어", at = 1000))
            .send(AppEvent.Tts(playing = false, at = 1050))

        assertEquals("답장을 말하지 못했어요", h.ui.toast)
        assertTrue(h.ui.captions.isEmpty())
        assertNull(h.ui.replying)
    }

    @Test
    fun `a reply that times out says so too`() {
        val h = EngineHarness().send(AppEvent.Reply("응, 알겠어", at = 1000))

        h.tick(21_000)

        assertEquals("답장을 말하지 못했어요", h.ui.toast)
    }

    @Test
    fun `the listening state reaches the screen`() {
        val h = EngineHarness()
        assertEquals(Listening.NOT_STARTED, h.ui.listening)

        h.send(AppEvent.ListeningChanged(Listening.ACTIVE, at = 1))
        assertEquals(Listening.ACTIVE, h.ui.listening)

        h.send(AppEvent.ListeningChanged(Listening.NO_PERMISSION, at = 2))
        assertEquals(Listening.NO_PERMISSION, h.ui.listening)
    }

    @Test
    fun `the pressed reply is marked as in progress at once, before the car starts speaking`() {
        val h = EngineHarness().send(AppEvent.Reply("응, 알겠어", at = 1000))

        assertEquals("응, 알겠어", h.ui.replying)
    }

    @Test
    fun `the reply is done when the car finishes speaking`() {
        val h = EngineHarness()
            .send(AppEvent.Reply("응, 알겠어", at = 1000))
            .send(AppEvent.Tts(playing = true, at = 1100))

        assertEquals("응, 알겠어", h.ui.replying)

        h.send(AppEvent.Tts(playing = false, at = 2000))

        assertNull(h.ui.replying)
    }

    @Test
    fun `a reply that never finishes does not lock the buttons forever`() {
        val h = EngineHarness().send(AppEvent.Reply("응, 알겠어", at = 1000))

        assertEquals("응, 알겠어", h.tick(20_999).ui.replying)
        assertNull(h.tick(21_000).ui.replying)
    }
}
