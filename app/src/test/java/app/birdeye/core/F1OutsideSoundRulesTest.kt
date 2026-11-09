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
    fun `siren from behind asks the driver to move right`() {
        val alert = driving()
            .frames(SoundKind.SIREN, from = 0, direction = Direction.REAR, vehicle = EmergencyVehicle.AMBULANCE)
            .ui.alert

        assertEquals("사이렌 감지 · 뒤쪽에서 접근 중", alert?.eyebrow)
        assertEquals("구급차가 다가와요", alert?.headline)
        assertEquals("오른쪽 차로로 비켜 주세요", alert?.guidance)
    }

    @Test
    fun `siren from the front asks the driver to slow down`() {
        val alert = driving().frames(SoundKind.SIREN, from = 0, direction = Direction.FRONT).ui.alert

        assertEquals("속도를 줄이고 주변을 확인하세요", alert?.guidance)
    }

    @Test
    fun `siren of unknown type and direction still gives safe guidance`() {
        val alert = driving().frames(SoundKind.SIREN, from = 0, direction = null).ui.alert

        assertEquals("사이렌 감지", alert?.eyebrow)
        assertEquals("긴급차량이 가까이 있어요", alert?.headline)
        assertEquals("속도를 줄이고 주변을 확인하세요", alert?.guidance)
    }

    @Test
    fun `horn tells the driver to check surrounding cars with the single strong rhythm`() {
        val h = driving().frames(SoundKind.HORN, from = 0, direction = Direction.LEFT)

        assertEquals(AlertKind.HORN, h.ui.alert?.kind)
        assertEquals("주변 차량을 확인하세요", h.ui.alert?.guidance)
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
            vehicle = EmergencyVehicle.AMBULANCE,
        ) // fires at 1000, last heard at 19500
        h.tick(23500)

        val entry = h.ui.trip.entries.single()
        assertEquals(EntryKind.SIREN, entry.kind)
        assertEquals(1000L, entry.at)
        assertEquals("뒤쪽에서 구급차 사이렌 · 약 19초", entry.text)
    }
}
