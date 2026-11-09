package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F5VehicleSoundRulesTest {
    private fun driving() = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.D)
    private fun signal(state: SignalState, at: Long) = AppEvent.TurnSignal(state, at)
    private fun steering(angle: Float, at: Long) = AppEvent.Steering(angle, at)
    private fun door(open: Boolean, at: Long, area: DoorArea = DoorArea.DRIVER) = AppEvent.DoorOpen(area, open, at)
    private fun lamp(name: String, state: LampState, at: Long) = AppEvent.Telltale(name, state, at)

    @Test
    fun `turn signal left on with a straight wheel for twenty seconds raises a notice`() {
        val h = driving().send(signal(SignalState.LEFT, at = 1000))

        assertNull(h.tick(20_999).ui.notice)

        h.tick(21_000)

        assertEquals("방향지시등이 켜져 있어요", h.ui.notice?.headline)
        assertEquals(listOf(LightRhythm.VEHICLE), h.lightEffects())
        assertEquals(EntryKind.VEHICLE, h.ui.trip.entries.single().kind)
    }

    @Test
    fun `the screen learns of a turn signal in any gear, because the system camera opens with it`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(signal(SignalState.RIGHT, at = 0))

        assertEquals(SignalState.RIGHT, h.ui.turnSignal)
        assertTrue(h.ui.systemCameraUp)

        h.send(signal(SignalState.OFF, at = 1))

        assertFalse(h.ui.systemCameraUp)
    }

    @Test
    fun `a sudden swing of the wheel raises a notice once the wheel has settled`() {
        val h = driving().send(steering(0f, at = 0)).send(steering(-120f, at = 1000))

        assertNull(h.tick(1699).ui.notice)
        assertEquals(Copy.SWERVE, h.tick(1700).ui.notice?.headline)
        assertEquals(listOf(LightRhythm.VEHICLE), h.lightEffects())
        assertEquals(EntryKind.VEHICLE, h.ui.trip.entries.single().kind)
    }

    @Test
    fun `the swing notice waits until the driver has finished correcting`() {
        val h = driving()
            .send(steering(0f, at = 0))
            .send(steering(-120f, at = 1000))
            .send(steering(-60f, at = 1500))
            .send(steering(0f, at = 2000))

        assertNull(h.tick(2699).ui.notice)
        assertEquals(Copy.SWERVE, h.tick(2700).ui.notice?.headline)
    }

    @Test
    fun `a quick swing made of many small readings still counts`() {
        val h = driving().send(steering(0f, at = 0))
        (1..3).forEach { h.send(steering(it * 40f, at = 1000L + it * 100)) }

        assertEquals(Copy.SWERVE, h.tick(2100).ui.notice?.headline)
    }

    @Test
    fun `ordinary steering raises nothing`() {
        val h = driving().send(steering(0f, at = 0))
        (1..30).forEach { h.send(steering(it * 10f, at = it * 100L)) }

        assertNull(h.tick(10_000).ui.notice)
    }

    @Test
    fun `the first reading of the wheel is not a swing`() {
        val h = driving().send(steering(-200f, at = 0))

        assertNull(h.tick(5_000).ui.notice)
    }

    @Test
    fun `a swing only counts in drive`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).gear(GearPos.R).send(steering(0f, at = 0)).send(steering(300f, at = 100))

        assertNull(h.tick(5_000).ui.notice)
    }

    @Test
    fun `a second swing soon after stays quiet, and a later one is reported`() {
        val h = driving().send(steering(0f, at = 0)).send(steering(120f, at = 100)).tick(800)
        assertEquals(1, h.lightEffects().size)

        h.send(steering(0f, at = 5_000)).tick(6_000)
        assertEquals(1, h.lightEffects().size)

        h.send(steering(120f, at = 11_000)).tick(11_700)
        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `turning the wheel restarts the turn signal timer`() {
        val h = driving()
            .send(signal(SignalState.RIGHT, at = 0))
            .send(steering(-30f, at = 10_000))
            .send(steering(5f, at = 15_000))

        assertNull(h.tick(34_999).ui.notice)
        assertEquals("방향지시등이 켜져 있어요", h.tick(35_000).ui.notice?.headline)
    }

    @Test
    fun `a wheel angle of exactly ten degrees counts as turning`() {
        val h = driving().send(signal(SignalState.LEFT, at = 0)).send(steering(10f, at = 1))

        assertNull(h.tick(60_000).ui.notice)
    }

    @Test
    fun `turning the signal off clears the notice`() {
        val h = driving().send(signal(SignalState.LEFT, at = 0)).tick(20_000)

        h.send(signal(SignalState.OFF, at = 21_000))

        assertNull(h.ui.notice)
    }

    @Test
    fun `after the wheel turns, a new stale period alerts again`() {
        val h = driving().send(signal(SignalState.LEFT, at = 0)).tick(20_000)
        assertEquals(1, h.lightEffects().size)

        h.send(steering(30f, at = 40_000)).send(steering(0f, at = 45_000))
        h.tick(65_000)

        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `switching to the other signal starts the timer over`() {
        val h = driving().send(signal(SignalState.LEFT, at = 0)).tick(20_000)
        h.send(signal(SignalState.RIGHT, at = 21_000))

        h.tick(40_999)
        assertEquals(1, h.lightEffects().size)

        h.tick(41_000)
        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `when a lamp notice ends the door notice comes back without pulsing the light again`() {
        val h = driving()
            .send(door(open = true, at = 0))
            .send(lamp("안전벨트", LampState.ON, at = 1000))
        val lights = h.lightEffects().size

        h.tick(9000)

        assertEquals("문이 열린 채 주행 중이에요", h.ui.notice?.headline)
        assertEquals(lights, h.lightEffects().size)
    }

    @Test
    fun `turn signal outside of drive does not count`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(signal(SignalState.LEFT, at = 0))

        assertNull(h.tick(60_000).ui.notice)
    }

    @Test
    fun `the scripted stale signal shortcut raises the notice directly`() {
        val h = driving().send(AppEvent.TurnSignalStale(SignalState.LEFT, at = 5))

        assertEquals("방향지시등이 켜져 있어요", h.ui.notice?.headline)
        assertEquals(listOf(LightRhythm.VEHICLE), h.lightEffects())
    }

    @Test
    fun `driving with a door open raises a lasting notice until it closes`() {
        val h = driving().send(door(open = true, at = 5))

        assertEquals("문이 열린 채 주행 중이에요", h.ui.notice?.headline)
        assertEquals("문이 열린 채 주행 중이에요", h.tick(60_000).ui.notice?.headline)

        h.send(door(open = false, at = 61_000))

        assertNull(h.ui.notice)
    }

    @Test
    fun `an open door in park is fine until the car goes into drive`() {
        val h = EngineHarness(RuleConfig(vehicleAlerts = true)).send(door(open = true, at = 5))
        assertNull(h.ui.notice)

        h.gear(GearPos.D, at = 10)

        assertEquals("문이 열린 채 주행 중이에요", h.ui.notice?.headline)
    }

    @Test
    fun `the door notice stays until every open door is closed`() {
        val h = driving()
            .send(door(open = true, at = 5, area = DoorArea.DRIVER))
            .send(door(open = true, at = 6, area = DoorArea.REAR_LEFT))
            .send(door(open = false, at = 7, area = DoorArea.DRIVER))

        assertEquals("문이 열린 채 주행 중이에요", h.ui.notice?.headline)
        assertEquals(1, h.lightEffects().size)
    }

    @Test
    fun `a warning lamp coming on shows its name and one line of explanation`() {
        val h = driving().send(lamp("안전벨트", LampState.ON, at = 5))

        assertEquals("안전벨트", h.ui.notice?.headline)
        assertEquals("안전벨트를 매 주세요", h.ui.notice?.guidance)
    }

    @Test
    fun `a blinking lamp counts as on`() {
        val h = driving().send(lamp("워셔액", LampState.BLINK, at = 5))

        assertEquals("워셔액이 부족해요", h.ui.notice?.guidance)
    }

    @Test
    fun `a lamp that stays on does not alert again but it can after going off`() {
        val h = driving()
            .send(lamp("안전벨트", LampState.ON, at = 5))
            .send(lamp("안전벨트", LampState.BLINK, at = 6))
        assertEquals(1, h.lightEffects().size)

        h.send(lamp("안전벨트", LampState.OFF, at = 7)).send(lamp("안전벨트", LampState.ON, at = 8))

        assertEquals(2, h.lightEffects().size)
    }

    @Test
    fun `lamp notices go away by themselves after eight seconds`() {
        val h = driving().send(lamp("안전벨트", LampState.ON, at = 1000))

        assertEquals("안전벨트", h.tick(8999).ui.notice?.headline)
        assertNull(h.tick(9000).ui.notice)
    }

    @Test
    fun `a vehicle notice does not take over the talk screen`() {
        val h = driving()
            .send(AppEvent.Caption(Seat.PASSENGER, "저기", true, at = 1))
            .send(lamp("안전벨트", LampState.ON, at = 2))

        assertEquals(Screen.TALK, h.ui.screen)
    }

    @Test
    fun `what the car's own screens already show stays out of the app unless switched on`() {
        val h = EngineHarness().gear(GearPos.D)
            .send(door(true, at = 1))
            .send(lamp("안전벨트", LampState.ON, at = 2))
            .send(steering(0f, at = 3))
            .send(steering(120f, at = 100))
        h.tick(2_000)

        assertNull(h.ui.notice)
        assertTrue(h.lightEffects().isEmpty())
        assertTrue(h.ui.trip.entries.isEmpty())
    }

    @Test
    fun `a signal left on still gets its notice, because only its click would have told the driver`() {
        val h = EngineHarness().gear(GearPos.D).send(signal(SignalState.LEFT, at = 1000))
        h.tick(21_000)

        assertEquals("방향지시등이 켜져 있어요", h.ui.notice?.headline)
    }

    private fun anyGear(gear: GearPos) = EngineHarness().gear(gear)

    @Test
    fun `an open door is known to the screen in any gear, because a deaf driver can't hear it`() {
        listOf(GearPos.P, GearPos.D).forEach { gear ->
            val h = anyGear(gear).send(door(true, at = 1, area = DoorArea.REAR_RIGHT))

            assertEquals(setOf(DoorArea.REAR_RIGHT), h.ui.openDoors)
            assertNull(h.ui.notice)
        }
    }

    @Test
    fun `a door closing stands in for the sound of it, then goes away`() {
        val h = anyGear(GearPos.P)
            .send(door(true, at = 1, area = DoorArea.PASSENGER))
            .send(door(false, at = 1_000, area = DoorArea.PASSENGER))

        assertEquals(emptySet<DoorArea>(), h.ui.openDoors)
        assertEquals(DoorArea.PASSENGER, h.ui.doorJustClosed)

        h.tick(1_000 + 5_000)
        assertNull(h.ui.doorJustClosed)
    }

    @Test
    fun `a door opening again replaces its closed message`() {
        val h = anyGear(GearPos.P)
            .send(door(true, at = 1, area = DoorArea.PASSENGER))
            .send(door(false, at = 1_000, area = DoorArea.PASSENGER))
            .send(door(true, at = 2_000, area = DoorArea.PASSENGER))

        assertNull(h.ui.doorJustClosed)
    }

    @Test
    fun `a report that a closed door is closed announces nothing`() {
        val h = anyGear(GearPos.P).send(door(false, at = 1, area = DoorArea.PASSENGER))

        assertNull(h.ui.doorJustClosed)
    }

    @Test
    fun `a door closing during a trip can be found in the recap`() {
        val h = anyGear(GearPos.D)
            .send(door(true, at = 1, area = DoorArea.REAR_LEFT))
            .send(door(false, at = 1_000, area = DoorArea.REAR_LEFT))

        assertEquals("왼쪽 뒷문이 닫혔어요", h.ui.trip.entries.single().text)
    }

    @Test
    fun `door wording names which door`() {
        assertEquals("운전석 문이 열려 있어요", Copy.doorOpen(DoorArea.DRIVER))
        assertEquals("조수석 문이 열려 있어요", Copy.doorOpen(DoorArea.PASSENGER))
        assertEquals("왼쪽 뒷문이 닫혔어요", Copy.doorClosed(DoorArea.REAR_LEFT))
        assertEquals("오른쪽 뒷문이 열려 있어요", Copy.doorOpen(DoorArea.REAR_RIGHT))
    }
}
