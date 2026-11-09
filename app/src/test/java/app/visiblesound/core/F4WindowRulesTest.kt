package app.visiblesound.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class F4WindowRulesTest {
    private fun driverWindow(percent: Int, at: Long = 10) =
        AppEvent.WindowPos(WindowArea.DRIVER, percent, at)

    @Test
    fun `opening the driver window twenty percent in park opens the window screen`() {
        val h = EngineHarness().send(driverWindow(20))

        assertEquals(Screen.WINDOW, h.ui.screen)
    }

    @Test
    fun `a window opened less than twenty percent does nothing`() {
        val h = EngineHarness().send(driverWindow(19))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `other windows do nothing`() {
        val h = EngineHarness().send(AppEvent.WindowPos(WindowArea.PASSENGER, 80, 10))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `an open window while driving does nothing`() {
        val h = EngineHarness().gear(GearPos.D).send(driverWindow(60))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `shifting into park with the window already open opens the window screen`() {
        val h = EngineHarness().gear(GearPos.D).send(driverWindow(60)).gear(GearPos.P, at = 20)

        assertEquals(Screen.WINDOW, h.ui.screen)
    }

    @Test
    fun `closing the window goes back to the normal screen and forgets the outside sentence`() {
        val h = EngineHarness()
            .send(driverWindow(60))
            .send(AppEvent.Outside("면허증 보여 주세요", isFinal = true, at = 20))
            .send(driverWindow(0, at = 30))

        assertEquals(Screen.MAIN, h.ui.screen)
        assertNull(h.ui.outside)
    }

    @Test
    fun `driving off goes back to the normal screen`() {
        val h = EngineHarness().send(driverWindow(60)).gear(GearPos.D, at = 20)

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `outside speech appears on the window screen and pulses the blue rhythm once`() {
        val h = EngineHarness()
            .send(driverWindow(60))
            .send(AppEvent.Outside("음주 단속", isFinal = false, at = 20))
            .send(AppEvent.Outside("음주 단속 중입니다", isFinal = true, at = 30))

        assertEquals("음주 단속 중입니다", h.ui.outside?.text)
        assertEquals(Speaker.Outside, h.ui.outside?.speaker)
        assertEquals("차 밖 · 운전석 쪽", Speaker.Outside.label)
        assertEquals(listOf(LightRhythm.OUTSIDE), h.lightEffects())
    }

    @Test
    fun `outside speech with no open window is ignored`() {
        val h = EngineHarness().send(AppEvent.Outside("안녕하세요", isFinal = true, at = 20))

        assertNull(h.ui.outside)
    }

    @Test
    fun `window screen wins over the talk screen`() {
        val h = EngineHarness()
            .send(AppEvent.Caption(Seat.PASSENGER, "저기", true, at = 5))
            .send(driverWindow(60, at = 6))

        assertEquals(Screen.WINDOW, h.ui.screen)
    }

    @Test
    fun `a reply through the window does not leave the talk screen open afterwards`() {
        val h = EngineHarness()
            .send(driverWindow(60))
            .send(AppEvent.Reply("네", at = 20))
            .send(driverWindow(0, at = 30))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `the introduction sentence is spoken when asked for`() {
        val h = EngineHarness().send(driverWindow(60)).send(AppEvent.Reply(Copy.WINDOW_INTRO, at = 20))

        assertEquals(
            listOf("저는 청각장애가 있어요. 말씀하시면 화면에 글자로 보입니다. 천천히 말씀해 주세요."),
            h.spoken(),
        )
    }
}
