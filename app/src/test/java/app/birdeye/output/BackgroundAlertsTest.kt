package app.birdeye.output

import app.birdeye.core.Alert
import app.birdeye.core.AlertKind
import app.birdeye.core.Direction
import app.birdeye.core.LightRhythm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundAlertsTest {
    private fun siren(firedAt: Long = 1L) = Alert(
        kind = AlertKind.SIREN,
        eyebrow = "사이렌 감지",
        headline = "뒤쪽에서 사이렌이 들려요",
        guidance = "주변을 확인해 주세요",
        rhythm = LightRhythm.SIREN,
        direction = Direction.REAR,
        firedAt = firedAt,
    )

    @Test
    fun `a new sound is shown over whatever the driver is looking at when the app is hidden`() {
        assertEquals(
            BackgroundAlerts.Show("뒤쪽에서 사이렌이 들려요", "주변을 확인해 주세요"),
            BackgroundAlerts.decide(previous = null, current = siren(), appVisible = false),
        )
    }

    @Test
    fun `the same sound continuing is not announced again`() {
        assertNull(BackgroundAlerts.decide(previous = siren(), current = siren(), appVisible = false))
    }

    @Test
    fun `a later sound is announced even right after another one`() {
        assertEquals(
            BackgroundAlerts.Show("뒤쪽에서 사이렌이 들려요", "주변을 확인해 주세요"),
            BackgroundAlerts.decide(previous = siren(firedAt = 1), current = siren(firedAt = 2), appVisible = false),
        )
    }

    @Test
    fun `an app on screen already shows the sound, so nothing covers it`() {
        assertEquals(BackgroundAlerts.Clear, BackgroundAlerts.decide(previous = null, current = siren(), appVisible = true))
    }

    @Test
    fun `the notice goes away when the sound does`() {
        assertEquals(BackgroundAlerts.Clear, BackgroundAlerts.decide(previous = siren(), current = null, appVisible = false))
    }

    @Test
    fun `nothing happens while nothing is heard`() {
        assertNull(BackgroundAlerts.decide(previous = null, current = null, appVisible = false))
    }
}
