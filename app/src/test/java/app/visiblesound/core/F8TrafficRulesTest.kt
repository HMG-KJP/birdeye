package app.visiblesound.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F8TrafficRulesTest {
    private fun driving() = EngineHarness().gear(GearPos.D)
    private fun cutIn(at: Long, side: Direction = Direction.RIGHT) = AppEvent.TrafficWarning(TrafficKind.CUT_IN, side, at)
    private fun blindSpot(at: Long, side: Direction = Direction.LEFT) = AppEvent.TrafficWarning(TrafficKind.BLIND_SPOT, side, at)

    @Test
    fun `a cut-in raises an alert that says from which side, with its own light rhythm`() {
        val h = driving().send(cutIn(at = 100))

        val alert = h.ui.alert!!
        assertEquals(AlertKind.TRAFFIC, alert.kind)
        assertEquals("오른쪽에서 차가 끼어들어요", alert.headline)
        assertEquals(Direction.RIGHT, alert.direction)
        assertEquals(listOf(LightRhythm.TRAFFIC), h.lightEffects())
    }

    @Test
    fun `a car in the blind spot says what to hold off on`() {
        val alert = driving().send(blindSpot(at = 100)).ui.alert!!

        assertEquals("왼쪽 사각지대에 차가 있어요", alert.headline)
        assertEquals("차로 변경을 잠깐 미뤄 주세요", alert.guidance)
    }

    @Test
    fun `the alert clears by itself and stays in the record`() {
        val h = driving().send(cutIn(at = 100))

        assertEquals(AlertKind.TRAFFIC, h.tick(5_099).ui.alert?.kind)
        assertNull(h.tick(5_100).ui.alert)
        assertEquals(EntryKind.TRAFFIC, h.ui.trip.entries.single().kind)
    }

    @Test
    fun `the car repeating the same warning keeps the card up without flashing or logging again`() {
        val h = driving().send(cutIn(at = 100)).send(cutIn(at = 3_000))

        assertEquals(AlertKind.TRAFFIC, h.tick(7_999).ui.alert?.kind)
        assertNull(h.tick(8_000).ui.alert)
        assertEquals(1, h.lightEffects().size)
        assertEquals(1, h.ui.trip.entries.size)
    }

    @Test
    fun `a different warning takes over at once`() {
        val h = driving().send(blindSpot(at = 100)).send(cutIn(at = 200))

        assertEquals("오른쪽에서 차가 끼어들어요", h.ui.alert?.headline)
        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `a car beside you outranks a siren, and the siren is still recorded`() {
        val h = driving().frames(SoundKind.SIREN, from = 0).send(cutIn(at = 2_000))

        assertEquals(AlertKind.TRAFFIC, h.ui.alert?.kind)
        assertTrue(h.ui.trip.entries.any { it.kind == EntryKind.SIREN })
    }

    @Test
    fun `a sound heard during the warning waits for the warning to end`() {
        val h = driving().send(cutIn(at = 100)).frames(SoundKind.HORN, from = 200)
        assertEquals(AlertKind.TRAFFIC, h.ui.alert?.kind)

        h.frames(SoundKind.HORN, from = 5_200)
        assertEquals(AlertKind.HORN, h.ui.alert?.kind)
    }

    @Test
    fun `nothing is raised while parked`() {
        val h = EngineHarness().gear(GearPos.P).send(cutIn(at = 100))

        assertNull(h.ui.alert)
        assertTrue(h.lightEffects().isEmpty())
    }
}
