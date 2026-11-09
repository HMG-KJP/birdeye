package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingTest {
    private val noon = 12
    private val night = 22
    private val time: (Long) -> String = { "21:58" }

    private fun lines(state: UiState, hour: Int = noon) = Briefing.lines(state, hour, time)

    private fun alert(kind: AlertKind, headline: String) = Alert(kind, "", headline, null, LightRhythm.SIREN, null, 0)

    @Test
    fun `on a quiet road it says sounds are being heard and when the last one was`() {
        val state = UiState(
            listening = Listening.ACTIVE,
            trip = TripSummary(entries = listOf(TripEntry(EntryKind.HORN, 1, "왼쪽에서 경적 · 약 1초", null))),
        )

        assertEquals(listOf("소리는 잘 듣고 있어요", "마지막 소리는 21:58, 왼쪽에서 경적 · 약 1초"), lines(state))
    }

    @Test
    fun `before a trip starts nothing is logged, so it does not claim nothing was heard`() {
        val state = UiState(listening = Listening.ACTIVE, trip = TripSummary(active = false))

        assertEquals(listOf("소리는 잘 듣고 있어요"), lines(state))
    }

    @Test
    fun `while a sound is going it never says none has been heard`() {
        val state = UiState(
            listening = Listening.ACTIVE,
            alert = alert(AlertKind.HORN, "왼쪽에서 경적이 울렸어요"),
            trip = TripSummary(active = true),
        )

        assertTrue(lines(state).none { it.contains("없어요") })
    }

    @Test
    fun `talk entries are not called sounds heard outside`() {
        val state = UiState(
            listening = Listening.ACTIVE,
            trip = TripSummary(active = true, entries = listOf(TripEntry(EntryKind.TALK, 1, "승객: 안녕", null))),
        )

        assertEquals(listOf("소리는 잘 듣고 있어요", "이번 운행에서 들린 사이렌과 경적은 없어요"), lines(state))
    }

    @Test
    fun `during a siren it explains what others may be reacting to, without claiming they are`() {
        val state = UiState(listening = Listening.ACTIVE, alert = alert(AlertKind.SIREN, "뒤쪽에서 사이렌이 들려요"))

        assertEquals(listOf("사이렌이 이어지고 있어요", "주변 차가 움직이면 이 소리 때문일 수 있어요"), lines(state))
    }

    @Test
    fun `rain at night is told as weaker sight and hearing together`() {
        val state = UiState(listening = Listening.ACTIVE, condition = DrivingCondition.RAIN)

        assertEquals("비 오는 밤이에요. 시야와 소리가 함께 약해요", lines(state, night).first())
        assertEquals("빗소리가 커서 소리를 놓칠 수 있어요", lines(state, noon).first())
    }

    @Test
    fun `at night with nothing else it only says what it can vouch for`() {
        assertEquals("어두운 시간이에요. 소리는 잘 듣고 있어요", lines(UiState(listening = Listening.ACTIVE), night).first())
    }

    @Test
    fun `a stopped microphone is never reported as listening`() {
        assertTrue(lines(UiState(listening = Listening.MIC_ERROR)).none { it.contains("듣고 있어요") })
    }

    @Test
    fun `a signal left on leads the briefing`() {
        val state = UiState(listening = Listening.ACTIVE, notice = alert(AlertKind.VEHICLE, "방향지시등이 켜져 있어요"))

        assertEquals("방향지시등이 켜져 있어요", lines(state).first())
    }

    @Test
    fun `it never runs past three lines`() {
        val state = UiState(
            listening = Listening.ACTIVE,
            notice = alert(AlertKind.VEHICLE, "방향지시등이 켜져 있어요"),
            alert = alert(AlertKind.SIREN, "뒤쪽에서 사이렌이 들려요"),
        )

        assertEquals(3, lines(state).size)
    }

    @Test
    fun `hearing status names what is in the way`() {
        assertEquals("좋음", Copy.hearingStatus(Listening.ACTIVE, null))
        assertEquals("좋음", Copy.hearingStatus(Listening.ACTIVE, DrivingCondition.FOG))
        assertEquals("약함 · 빗소리", Copy.hearingStatus(Listening.ACTIVE, DrivingCondition.RAIN))
        assertEquals("꺼짐", Copy.hearingStatus(Listening.MIC_ERROR, null))
    }
}
