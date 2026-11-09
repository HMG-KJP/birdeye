package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F6TripRulesTest {
    private fun lamp(at: Long) = AppEvent.Telltale("안전벨트", LampState.ON, at)

    @Test
    fun `a trip starts when the car leaves park for drive`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 100)

        assertEquals(100L, h.ui.trip.startedAt)
        assertTrue(h.ui.trip.active)
        assertTrue(h.effects.contains(Effect.TripStarted(100L)))
    }

    @Test
    fun `backing out of a parking space and then driving also starts a trip`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.R, at = 100)
        assertNull(h.ui.trip.startedAt)

        h.gear(GearPos.D, at = 200)

        assertEquals(200L, h.ui.trip.startedAt)
    }

    @Test
    fun `the trip ends two minutes after parking`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0).gear(GearPos.P, at = 1000)

        assertTrue(h.tick(1000 + 119_999).ui.trip.active)

        h.tick(1000 + 120_000)

        assertFalse(h.ui.trip.active)
        assertEquals(121_000L, h.ui.trip.endedAt)
        assertTrue(h.effects.contains(Effect.TripEnded(121_000L)))
    }

    @Test
    fun `driving again within two minutes continues the same trip`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .gear(GearPos.D, at = 0)
            .send(lamp(10))
            .gear(GearPos.P, at = 1000)
            .gear(GearPos.D, at = 60_000)

        h.tick(500_000)

        assertTrue(h.ui.trip.active)
        assertEquals(0L, h.ui.trip.startedAt)
        assertEquals(1, h.ui.trip.entries.size)
    }

    @Test
    fun `a new trip forgets the previous one`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0).send(lamp(10)).gear(GearPos.P, at = 1000)
        h.tick(200_000)

        h.gear(GearPos.D, at = 300_000)

        assertEquals(300_000L, h.ui.trip.startedAt)
        assertTrue(h.ui.trip.entries.isEmpty())
    }

    @Test
    fun `nothing is logged outside of a trip`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(AppEvent.Caption(Seat.PASSENGER, "저기", true, at = 5))

        assertTrue(h.ui.trip.entries.isEmpty())
    }

    @Test
    fun `every logged entry is also handed to the persistence layer with the place`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .gear(GearPos.D, at = 0)
            .send(AppEvent.Location(37.5, 127.0, "강남대로", at = 1))
            .send(lamp(10))

        val saved = h.effects.filterIsInstance<Effect.Log>().single().entry
        assertEquals(h.ui.trip.entries.single(), saved)
        assertEquals("강남대로", saved.place)
    }

    @Test
    fun `an entry without a place label falls back to coordinates`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .gear(GearPos.D, at = 0)
            .send(AppEvent.Location(37.50123, 127.03987, null, at = 1))
            .send(lamp(10))

        assertEquals("37.5012, 127.0399", h.ui.trip.entries.single().place)
    }

    @Test
    fun `counts are grouped by kind for the recap`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .gear(GearPos.D, at = 0)
            .send(lamp(1))
            .send(AppEvent.Caption(Seat.PASSENGER, "하나", true, at = 2))
            .send(AppEvent.Caption(Seat.PASSENGER, "둘", true, at = 3))

        val counts = h.ui.trip.counts()

        assertEquals(2, counts[EntryKind.TALK])
        assertEquals(1, counts[EntryKind.VEHICLE])
        assertEquals(0, counts[EntryKind.SIREN])
        assertEquals(0, counts[EntryKind.HORN])
    }

    @Test
    fun `gps movement adds up to the trip distance only while on a trip`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .send(AppEvent.Location(37.0, 127.0, null, at = 1))
            .send(AppEvent.Location(37.5, 127.0, null, at = 2))
        assertEquals(0.0, h.ui.trip.distanceMeters, 0.001)

        h.gear(GearPos.D, at = 3)
            .send(AppEvent.Location(37.5, 127.0, null, at = 4))
            .send(AppEvent.Location(37.51, 127.0, null, at = 5))

        assertEquals(1112.0, h.ui.trip.distanceMeters, 5.0)
    }

    @Test
    fun `the recap reads in the order things happened even when a sound is logged late`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0)
        h.frames(SoundKind.SIREN, from = 1000, count = 3) // fires at 2000, heard until 2000
        h.send(AppEvent.Caption(Seat.PASSENGER, "저기", true, at = 3000))
        h.tick(6000) // the siren is logged now, after the caption

        assertEquals(listOf(EntryKind.SIREN, EntryKind.TALK), h.ui.trip.entries.map { it.kind })
    }

    @Test
    fun `a sound still going when the trip ends is not lost`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0).gear(GearPos.P, at = 1000)
        h.frames(SoundKind.SIREN, from = 119_000, count = 6) // fires at 120000, heard until 121500

        h.tick(121_000) // the trip ends here

        assertEquals(false, h.ui.trip.active)
        assertEquals(listOf(EntryKind.SIREN), h.ui.trip.entries.map { it.kind })
    }

    @Test
    fun `a horn that sounds during a siren alert still reaches the recap`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0)
        h.frames(SoundKind.SIREN, from = 1000, count = 10)
        h.frames(SoundKind.HORN, from = 1500, count = 3) // reaches three frames at 2500

        assertEquals(AlertKind.SIREN, h.ui.alert?.kind)
        assertEquals(1, h.ui.trip.counts()[EntryKind.HORN])
    }

    @Test
    fun `resetting forgets the trip but keeps the driver's replies`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .send(AppEvent.SetReplies(listOf("가", "나"), at = 1))
            .gear(GearPos.D, at = 2)
            .send(lamp(3))

        h.send(AppEvent.Reset(at = 4))

        assertTrue(h.ui.trip.entries.isEmpty())
        assertFalse(h.ui.trip.active)
        assertEquals(listOf("가", "나"), h.ui.replies)
    }

    @Test
    fun `the end of a trip hands over its distance for saving`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .gear(GearPos.D, at = 0)
            .send(AppEvent.Location(37.5, 127.0, null, at = 1))
            .send(AppEvent.Location(37.51, 127.0, null, at = 2))
            .gear(GearPos.P, at = 3)
        h.tick(3 + 120_000)

        val ended = h.effects.filterIsInstance<Effect.TripEnded>().single()
        assertEquals(1112.0, ended.distanceMeters, 5.0)
    }

    @Test
    fun `a saved trip comes back into the recap after a restart`() {
        val saved = TripSummary(startedAt = 10, endedAt = 20, entries = listOf(TripEntry(EntryKind.TALK, 15, "저기", null)))

        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(AppEvent.RestoreTrip(saved, at = 30))

        assertEquals(saved, h.ui.trip)
    }

    @Test
    fun `a saved trip never overwrites a trip already under way`() {
        val saved = TripSummary(startedAt = 10, endedAt = 20)

        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 25).send(AppEvent.RestoreTrip(saved, at = 30))

        assertEquals(25L, h.ui.trip.startedAt)
    }

    @Test
    fun `recap opens only in park`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D, at = 0).send(AppEvent.SelectScreen(Screen.RECAP, at = 1))
        assertEquals(Screen.MAIN, h.ui.screen)

        h.gear(GearPos.P, at = 2).send(AppEvent.SelectScreen(Screen.RECAP, at = 3))
        assertEquals(Screen.RECAP, h.ui.screen)
    }

    @Test
    fun `recap stays closed while the system restricts the screen`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true))
            .send(AppEvent.UxRestricted(active = true, at = 1))
            .send(AppEvent.SelectScreen(Screen.RECAP, at = 2))

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `leaving park closes the recap`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(AppEvent.SelectScreen(Screen.RECAP, at = 1)).gear(GearPos.D, at = 2)

        assertEquals(Screen.MAIN, h.ui.screen)
    }

    @Test
    fun `the close button leaves the recap`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(AppEvent.SelectScreen(Screen.RECAP, at = 1)).send(AppEvent.SelectScreen(Screen.MAIN, at = 2))

        assertEquals(Screen.MAIN, h.ui.screen)
    }
}
