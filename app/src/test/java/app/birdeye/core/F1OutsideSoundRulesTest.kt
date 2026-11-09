package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class F1OutsideSoundRulesTest {
    private fun driving() = EngineHarness().gear(GearPos.D)

    @Test
    fun `fewer than three strong frames do not alert`() {
        val h = driving().frames(SoundKind.SIREN, from = 0, count = 2)

        assertNull(h.ui.alert)
    }

    @Test
    fun `three consecutive frames at the threshold raise the alert and play the siren rhythm`() {
        val h = driving().frames(SoundKind.SIREN, from = 0, count = 3, confidence = 0.35f)

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
        assertEquals(listOf(LightRhythm.SIREN), h.lightEffects())
    }

    @Test
    fun `a weak frame breaks the streak`() {
        val h = driving()
            .frames(SoundKind.SIREN, from = 0, count = 2)
            .frames(SoundKind.SIREN, from = 1000, count = 1, confidence = 0.34f)
            .frames(SoundKind.SIREN, from = 1500, count = 2)

        assertNull(h.ui.alert)
    }

    @Test
    fun `frames separated by a long gap are not consecutive`() {
        val h = driving()
            .frames(SoundKind.SIREN, from = 0, count = 2)
            .frames(SoundKind.SIREN, from = 5000, count = 2)

        assertNull(h.ui.alert)
    }

    @Test
    fun `a siren is told as what was heard, without naming the vehicle or giving orders`() {
        val alert = driving().frames(SoundKind.SIREN, from = 0, direction = Direction.REAR).ui.alert

        assertEquals("사이렌 감지 · 뒤쪽", alert?.eyebrow)
        assertEquals("뒤쪽에서 사이렌이 들려요", alert?.headline)
        assertEquals("주변을 확인해 주세요", alert?.guidance)
    }

    @Test
    fun `an unknown direction is said to be unknown, never guessed`() {
        val alert = driving().frames(SoundKind.SIREN, from = 0, direction = null).ui.alert

        assertEquals("사이렌 감지 · 방향 모름", alert?.eyebrow)
        assertEquals("사이렌이 들려요", alert?.headline)
        assertEquals("어느 쪽인지는 알 수 없어요. 주변을 확인해 주세요", alert?.guidance)
    }

    @Test
    fun `a weak match is told as a likeness, not as a fact`() {
        val siren = driving().frames(SoundKind.SIREN, from = 0, direction = Direction.REAR, confidence = 0.4f).ui.alert
        val horn = driving().frames(SoundKind.HORN, from = 0, direction = null, confidence = 0.4f).ui.alert

        assertEquals("뒤쪽에서 사이렌 같은 소리가 들려요", siren?.headline)
        assertEquals("경적 같은 소리가 들렸어요", horn?.headline)
    }

    @Test
    fun `when rain may hide sounds, fainter ones are told too`() {
        val clear = driving().frames(SoundKind.SIREN, from = 0, confidence = 0.28f)
        assertNull(clear.ui.alert)

        val rainy = driving().send(AppEvent.Wiper(on = true, at = 0)).frames(SoundKind.SIREN, from = 0, confidence = 0.28f)
        assertEquals("뒤쪽에서 사이렌 같은 소리가 들려요", rainy.ui.alert?.headline)
    }

    @Test
    fun `a horn during a siren stays on screen beside it for a moment`() {
        val h = driving()
            .frames(SoundKind.SIREN, from = 0, count = 12, direction = Direction.REAR)
            .frames(SoundKind.HORN, from = 2000, direction = Direction.LEFT)

        assertEquals("뒤쪽에서 사이렌이 들려요", h.ui.alert?.headline)
        assertEquals("왼쪽에서 경적이 울렸어요", h.ui.alsoHeard?.headline)
        assertEquals(Direction.LEFT, h.ui.alsoHeard?.direction)

        h.tick(3_000 + 8_000)
        assertNull(h.ui.alsoHeard)
    }

    @Test
    fun `horn tells the driver to check surrounding cars with the single strong rhythm`() {
        val h = driving().frames(SoundKind.HORN, from = 0, direction = Direction.LEFT)

        assertEquals(AlertKind.HORN, h.ui.alert?.kind)
        assertEquals("주변을 확인해 주세요", h.ui.alert?.guidance)
        assertEquals(listOf(LightRhythm.HORN), h.lightEffects())
    }

    @Test
    fun `the alert goes away after four seconds of silence`() {
        val h = driving().frames(SoundKind.SIREN, from = 0) // last heard at 1000

        assertNotNull(h.tick(4999).ui.alert)
        assertNull(h.tick(5000).ui.alert)
    }

    @Test
    fun `continued sound keeps the alert up without replaying the light`() {
        val h = driving().frames(SoundKind.SIREN, from = 0, count = 20) // heard until 9500

        assertNotNull(h.tick(13000).ui.alert)
        assertEquals(1, h.lightEffects().size)
    }

    @Test
    fun `same kind does not alert again within ten seconds of firing`() {
        val h = driving().frames(SoundKind.SIREN, from = 0) // fires at 1000
        h.tick(5000) // cleared

        h.frames(SoundKind.SIREN, from = 7000) // would fire at 8000

        assertNull(h.ui.alert)
        assertEquals(1, h.lightEffects().size)
    }

    @Test
    fun `same kind can alert again once ten seconds have passed`() {
        val h = driving().frames(SoundKind.SIREN, from = 0) // fires at 1000
        h.tick(5000)

        h.frames(SoundKind.SIREN, from = 10000) // fires at 11000

        assertNotNull(h.ui.alert)
        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `a different kind is not held back by the cooldown`() {
        val h = driving().frames(SoundKind.HORN, from = 0)
        h.tick(5000)

        h.frames(SoundKind.SIREN, from = 6000)

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
    }

    @Test
    fun `siren replaces an active horn alert`() {
        val h = driving().frames(SoundKind.HORN, from = 0).frames(SoundKind.SIREN, from = 500)

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
    }

    @Test
    fun `horn does not replace an active siren alert`() {
        val h = driving().frames(SoundKind.SIREN, from = 0).frames(SoundKind.HORN, from = 500)

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
    }

    @Test
    fun `a replaced horn alert is still kept in the trip log`() {
        val h = driving().frames(SoundKind.HORN, from = 0).frames(SoundKind.SIREN, from = 500)

        assertEquals(listOf(EntryKind.HORN), h.ui.trip.entries.map { it.kind })
    }

    @Test
    fun `a cleared alert is logged with how long it lasted`() {
        val h = driving().frames(
            SoundKind.SIREN,
            from = 0,
            count = 40,
            direction = Direction.REAR,
        ) // fires at 1000, last heard at 19500
        h.tick(23500)

        val entry = h.ui.trip.entries.single()
        assertEquals(EntryKind.SIREN, entry.kind)
        assertEquals(1000L, entry.at)
        assertEquals("뒤쪽에서 사이렌 · 약 19초", entry.text)
    }
}
