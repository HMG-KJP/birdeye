package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {
    @Test
    fun `starts on the main screen with nothing to show and the gear not yet known`() {
        val state = UiState()

        assertEquals(Screen.MAIN, state.screen)
        assertNull(state.gear)
        assertNull(state.alert)
        assertNull(state.notice)
        assertNull(state.toast)
        assertNull(state.replying)
        assertTrue(state.captions.isEmpty())
    }

    @Test
    fun `an unknown gear never counts as parked`() {
        assertEquals(false, UiState(gear = null).parked)
        assertEquals(true, UiState(gear = GearPos.P).parked)
    }

    @Test
    fun `a restricted screen never counts as parked`() {
        assertEquals(false, UiState(gear = GearPos.P, uxRestricted = true).parked)
    }

    @Test
    fun `starts with the four default replies from the spec`() {
        assertEquals(
            listOf("응, 알겠어", "조금 이따 세울게", "다시 한 번 말해 줘", "잠깐만 기다려 줘"),
            UiState().replies,
        )
    }
}
