package app.birdeye.source

import ai.pleos.playground.vehicle.api.Door
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.SteeringWheel
import ai.pleos.playground.vehicle.api.TurnSignal
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.Wiper
import ai.pleos.playground.vehicle.api.model.area.DoorArea as SdkDoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea as SdkWindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import app.birdeye.core.AppEvent
import app.birdeye.core.DoorArea
import app.birdeye.core.FogPosition
import app.birdeye.core.GearPos
import app.birdeye.core.LampState
import app.birdeye.core.SignalState
import app.birdeye.core.WindowArea
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.birdeye.core.Seat as AppSeat

class RealVehicleSourceTest {
    private val gear = mockk<Gear>(relaxed = true)
    private val turnSignal = mockk<TurnSignal>(relaxed = true)
    private val steering = mockk<SteeringWheel>(relaxed = true)
    private val door = mockk<Door>(relaxed = true)
    private val window = mockk<Window>(relaxed = true)
    private val seat = mockk<Seat>(relaxed = true)
    private val safety = mockk<Safety>(relaxed = true)
    private val wiper = mockk<Wiper>(relaxed = true)
    private val light = mockk<Light>(relaxed = true)
    private val config = CapabilityConfig(AccessType.READ)

    private val events = mutableListOf<AppEvent>()
    private val source = RealVehicleSource(
        VehicleDomains(gear, turnSignal, steering, door, window, seat, safety, wiper, light),
        clock = { 5L },
        onError = { _, _ -> },
    )

    private val driverDoor = SdkDoorArea(DoorValue.ROW_1_LEFT)
    private val driverWindow = SdkWindowArea(WindowValue.ROW_1_LEFT)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val passengerSeat = SeatArea(SeatValue.ROW_1_RIGHT)

    private val gearListener = slot<GearSelectionListener>()
    private val signalListener = slot<TurnSignalStateListener>()
    private val steeringListener = slot<SteeringWheelAngleListener>()
    private val doorListener = slot<DoorPositionListener>()
    private val windowListener = slot<WindowPositionListener>()
    private val beltListener = slot<SeatBeltNotBuckledWarningListener>()
    private val washerListener = slot<LowWasherFluidWaringListener>()
    private val oilListener = slot<OilLevelStateListener>()
    private val brakeListener = slot<OilLevelStateListener>()
    private val seatListener = slot<SeatOccupancyListener>()
    private val wiperListener = slot<WiperStateListener>()
    private val frontFogListener = slot<LightStateListener>()
    private val rearFogListener = slot<LightStateListener>()
    private val windshield = SdkWindowArea(WindowValue.FRONT_WINDSHIELD)

    @Before
    fun setUp() {
        every {
            wiper.checkWiperStateCapabilities(any<(CapabilityConfig, Map<SdkWindowArea, List<WiperState>>) -> Unit>(), any())
        } answers {
            firstArg<(CapabilityConfig, Map<SdkWindowArea, List<WiperState>>) -> Unit>()(
                config,
                mapOf(windshield to listOf(WiperState.OFF, WiperState.ON)),
            )
        }
        every { wiper.getCurrentWiperState(any(), any(), any()) } answers {
            secondArg<(WiperState) -> Unit>()(WiperState.ON)
        }
        every { wiper.registerWiperState(any(), capture(wiperListener)) } returns Unit

        every { light.checkFrontFogLightStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, List<LightState>) -> Unit>()(config, listOf(LightState.OFF, LightState.ON))
        }
        every { light.getCurrentFrontFogLightState(any(), any()) } answers {
            firstArg<(LightState) -> Unit>()(LightState.OFF)
        }
        every { light.registerFrontFogLightState(capture(frontFogListener)) } returns Unit
        every { light.checkRearFogLightStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, List<LightState>) -> Unit>()(config, listOf(LightState.OFF, LightState.ON))
        }
        every { light.getCurrentRearFogLightState(any(), any()) } answers {
            firstArg<(LightState) -> Unit>()(LightState.OFF)
        }
        every { light.registerRearFogLightState(capture(rearFogListener)) } returns Unit

        every { gear.getCurrentGearSelectionState(any(), any()) } answers {
            firstArg<(VehicleGear) -> Unit>()(VehicleGear.GEAR_PARK)
        }
        every { gear.registerGearSelection(capture(gearListener)) } returns Unit

        every { turnSignal.checkTurnSignalStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig) -> Unit>()(config)
        }
        every { turnSignal.getCurrentTurnSignalState(any(), any()) } answers {
            firstArg<(TurnSignalValue) -> Unit>()(TurnSignalValue.OFF)
        }
        every { turnSignal.registerTurnSignalState(capture(signalListener)) } returns Unit

        every { steering.checkSteeringWheelAngleCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, AreaBounds<Float>) -> Unit>()(config, AreaBounds(-540f, 540f))
        }
        every { steering.getCurrentSteeringWheelAngle(any(), any()) } answers {
            firstArg<(Float) -> Unit>()(0f)
        }
        every { steering.registerSteeringWheelAngle(capture(steeringListener)) } returns Unit

        every {
            door.checkDoorPositionCapabilities(any<(CapabilityConfig, Map<SdkDoorArea, AreaBounds<Int>>) -> Unit>(), any())
        } answers {
            firstArg<(CapabilityConfig, Map<SdkDoorArea, AreaBounds<Int>>) -> Unit>()(
                config,
                mapOf(driverDoor to AreaBounds(0, 100)),
            )
        }
        every { door.getCurrentDoorPosition(any(), any(), any()) } answers {
            secondArg<(Int) -> Unit>()(0)
        }
        every { door.registerDoorPosition(any(), capture(doorListener)) } returns Unit

        every {
            window.checkWindowPositionCapabilities(any<(CapabilityConfig, Map<SdkWindowArea, AreaBounds<Int>>) -> Unit>(), any())
        } answers {
            firstArg<(CapabilityConfig, Map<SdkWindowArea, AreaBounds<Int>>) -> Unit>()(
                config,
                mapOf(driverWindow to AreaBounds(0, 10)),
            )
        }
        every { window.getCurrentWindowPosition(any(), any(), any()) } answers {
            secondArg<(Int) -> Unit>()(0)
        }
        every { window.registerWindowPosition(any(), capture(windowListener)) } returns Unit

        every {
            seat.checkSeatOccupancyCapabilities(any<(CapabilityConfig, List<SeatArea>) -> Unit>(), any())
        } answers {
            firstArg<(CapabilityConfig, List<SeatArea>) -> Unit>()(config, listOf(driverSeat, passengerSeat))
        }
        // The SDK's boolean is true for a vacant seat: it compares the car's raw value with 1, VACANT.
        every { seat.getCurrentSeatOccupancy(driverSeat, any(), any()) } answers { secondArg<(Boolean) -> Unit>()(false) }
        every { seat.getCurrentSeatOccupancy(passengerSeat, any(), any()) } answers { secondArg<(Boolean) -> Unit>()(true) }
        every { seat.registerSeatOccupancy(passengerSeat, capture(seatListener)) } returns Unit

        every { safety.checkSeatNotBuckledWarningCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, List<SeatArea>) -> Unit>()(config, listOf(driverSeat, passengerSeat))
        }
        every { safety.checkLowWasherFluidWarningStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig) -> Unit>()(config)
        }
        every { safety.checkEngineOilLevelStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, List<OilLevelState>) -> Unit>()(config, OilLevelState.entries)
        }
        every { safety.checkBrakeFluidLevelStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig, List<OilLevelState>) -> Unit>()(config, OilLevelState.entries)
        }
        every { safety.registerBrakeFluidLevelState(capture(brakeListener)) } returns Unit
        every { safety.hasSeatBeltNotBuckledWarning(any(), any(), any()) } answers { secondArg<(Boolean) -> Unit>()(false) }
        every { safety.registerSeatBeltNotBuckledWarning(any(), capture(beltListener)) } returns Unit
        every { safety.hasLowWasherFluidWarning(any(), any()) } answers { firstArg<(Boolean) -> Unit>()(false) }
        every { safety.registerLowWasherFluidWaringState(capture(washerListener)) } returns Unit
        every { safety.registerEngineOilLevelState(capture(oilListener)) } returns Unit
    }

    private fun started(): RealVehicleSource {
        source.start { events += it }
        return source
    }

    private inline fun <reified T : AppEvent> last(): T = events.filterIsInstance<T>().last()

    @Test
    fun `turn signal goes through check, then current value, then subscription`() {
        started()

        verifyOrder {
            turnSignal.checkTurnSignalStateCapabilities(any(), any())
            turnSignal.getCurrentTurnSignalState(any(), any())
            turnSignal.registerTurnSignalState(any())
        }
    }

    @Test
    fun `the current gear is read at start and mapped`() {
        started()

        assertEquals(AppEvent.Gear(GearPos.P, at = 5), events.filterIsInstance<AppEvent.Gear>().single())
    }

    @Test
    fun `gear changes are mapped, numbered gears count as drive, unknown is ignored`() {
        started()

        gearListener.captured.onSelected(VehicleGear.GEAR_DRIVE)
        assertEquals(GearPos.D, last<AppEvent.Gear>().value)
        gearListener.captured.onSelected(VehicleGear.GEAR_REVERSE)
        assertEquals(GearPos.R, last<AppEvent.Gear>().value)
        gearListener.captured.onSelected(VehicleGear.GEAR_NEUTRAL)
        assertEquals(GearPos.N, last<AppEvent.Gear>().value)
        gearListener.captured.onSelected(VehicleGear.GEAR_3)
        assertEquals(GearPos.D, last<AppEvent.Gear>().value)

        val before = events.size
        gearListener.captured.onSelected(VehicleGear.GEAR_UNKNOWN)
        assertEquals(before, events.size)
    }

    @Test
    fun `turn signal states are mapped`() {
        started()

        signalListener.captured.onTurnSignalStateUpdated(TurnSignalValue.LEFT)
        assertEquals(SignalState.LEFT, last<AppEvent.TurnSignal>().state)
        signalListener.captured.onTurnSignalStateUpdated(TurnSignalValue.RIGHT)
        assertEquals(SignalState.RIGHT, last<AppEvent.TurnSignal>().state)
        signalListener.captured.onTurnSignalStateUpdated(TurnSignalValue.OFF)
        assertEquals(SignalState.OFF, last<AppEvent.TurnSignal>().state)
    }

    @Test
    fun `steering angle is passed on`() {
        started()

        steeringListener.captured.onSteeringWheelAngleUpdated(-12.5f)

        assertEquals(-12.5f, last<AppEvent.Steering>().angleDeg, 0.001f)
    }

    @Test
    fun `window position is scaled to percent using the car's reported range`() {
        started()

        windowListener.captured.onWindowPositionUpdated(6)

        assertEquals(AppEvent.WindowPos(WindowArea.DRIVER, 60, at = 5), last<AppEvent.WindowPos>())
    }

    @Test
    fun `a door counts as open when it is past its closed position`() {
        started()

        doorListener.captured.onPositionUpdated(1)
        assertEquals(AppEvent.DoorOpen(DoorArea.DRIVER, true, at = 5), last<AppEvent.DoorOpen>())

        doorListener.captured.onPositionUpdated(0)
        assertEquals(AppEvent.DoorOpen(DoorArea.DRIVER, false, at = 5), last<AppEvent.DoorOpen>())
    }

    @Test
    fun `seat occupancy is read and subscribed per supported seat`() {
        started()

        assertTrue(events.contains(AppEvent.SeatOccupied(AppSeat.DRIVER, true, at = 5)))
        assertTrue(events.contains(AppEvent.SeatOccupied(AppSeat.PASSENGER, false, at = 5)))
        verify { seat.registerSeatOccupancy(passengerSeat, any()) }
    }

    @Test
    fun `seat updates use the same inverted meaning`() {
        started()

        seatListener.captured.onSeatOccupancyUpdated(false)
        assertEquals(AppEvent.SeatOccupied(AppSeat.PASSENGER, true, at = 5), last<AppEvent.SeatOccupied>())

        seatListener.captured.onSeatOccupancyUpdated(true)
        assertEquals(AppEvent.SeatOccupied(AppSeat.PASSENGER, false, at = 5), last<AppEvent.SeatOccupied>())
    }

    @Test
    fun `an unbuckled belt becomes the named warning lamp`() {
        started()

        beltListener.captured.onSeatBeltNotBuckledWarningUpdated(true)
        assertEquals(AppEvent.Telltale("안전벨트", LampState.ON, at = 5), last<AppEvent.Telltale>())

        beltListener.captured.onSeatBeltNotBuckledWarningUpdated(false)
        assertEquals(LampState.OFF, last<AppEvent.Telltale>().state)
    }

    @Test
    fun `low washer fluid becomes a warning lamp`() {
        started()

        washerListener.captured.onLowWasherFluidWaringUpdated(true)

        assertEquals(AppEvent.Telltale("워셔액", LampState.ON, at = 5), last<AppEvent.Telltale>())
    }

    @Test
    fun `low engine oil is a warning and normal oil clears it`() {
        started()

        oilListener.captured.onOilLevelUpdated(OilLevelState.CRITICALLY_LOW)
        assertEquals(AppEvent.Telltale("엔진오일", LampState.ON, at = 5), last<AppEvent.Telltale>())

        oilListener.captured.onOilLevelUpdated(OilLevelState.NORMAL)
        assertEquals(LampState.OFF, last<AppEvent.Telltale>().state)
    }

    @Test
    fun `low brake fluid is a warning`() {
        started()

        brakeListener.captured.onOilLevelUpdated(OilLevelState.LOW)

        assertEquals(AppEvent.Telltale("브레이크액", LampState.ON, at = 5), last<AppEvent.Telltale>())
    }

    @Test
    fun `the belt warning watches only the driver`() {
        started()

        verify { safety.registerSeatBeltNotBuckledWarning(driverSeat, any()) }
        verify(exactly = 0) { safety.registerSeatBeltNotBuckledWarning(passengerSeat, any()) }
    }

    @Test
    fun `the private telltale api is never called`() {
        started()

        verify(exactly = 0) { safety.registerTelltales(any()) }
        verify(exactly = 0) { safety.getTelltalesInfo(any(), any()) }
    }

    @Test
    fun `stop unregisters the same listeners with the same areas`() {
        started()

        source.stop()

        verify { gear.unregisterGearSelection(gearListener.captured) }
        verify { turnSignal.unregisterTurnSignalState(signalListener.captured) }
        verify { steering.unregisterSteeringWheelAngle(steeringListener.captured) }
        verify { door.unregisterDoorPosition(driverDoor, doorListener.captured) }
        verify { window.unregisterWindowPosition(driverWindow, windowListener.captured) }
        verify { seat.unregisterSeatOccupancy(passengerSeat, any()) }
        verify { safety.unregisterSeatBeltNotBuckledWarning(any(), beltListener.captured) }
    }

    @Test
    fun `a failing domain is reported and does not stop the others`() {
        val errors = mutableListOf<String>()
        every { turnSignal.checkTurnSignalStateCapabilities(any(), any()) } answers {
            secondArg<(Exception) -> Unit>()(IllegalStateException("no permission"))
        }
        val failing = RealVehicleSource(
            VehicleDomains(gear, turnSignal, steering, door, window, seat, safety, wiper, light),
            clock = { 5L },
            onError = { what, _ -> errors += what },
        )

        failing.start { events += it }

        assertTrue(errors.isNotEmpty())
        verify(exactly = 0) { turnSignal.registerTurnSignalState(any()) }
        verify { gear.registerGearSelection(any()) }
    }

    @Test
    fun `a capability answer that arrives after stop does not subscribe`() {
        val check = slot<(CapabilityConfig) -> Unit>()
        every { turnSignal.checkTurnSignalStateCapabilities(capture(check), any()) } returns Unit
        started()

        source.stop()
        check.captured(config)

        verify(exactly = 0) { turnSignal.registerTurnSignalState(any()) }
    }

    @Test
    fun `events that arrive after stop are dropped`() {
        started()
        source.stop()
        val before = events.size

        gearListener.captured.onSelected(VehicleGear.GEAR_DRIVE)

        assertEquals(before, events.size)
    }

    @Test
    fun `a capability answer after stop does not read the current value either`() {
        val check = slot<(CapabilityConfig) -> Unit>()
        every { turnSignal.checkTurnSignalStateCapabilities(capture(check), any()) } returns Unit
        started()

        source.stop()
        check.captured(config)

        verify(exactly = 0) { turnSignal.getCurrentTurnSignalState(any(), any()) }
    }

    @Test
    fun `a subscription that throws inside a capability answer is reported, not fatal`() {
        val errors = mutableListOf<String>()
        every { turnSignal.registerTurnSignalState(any()) } throws IllegalStateException("service died")
        val failing = RealVehicleSource(
            VehicleDomains(gear, turnSignal, steering, door, window, seat, safety, wiper, light),
            clock = { 5L },
            onError = { what, _ -> errors += what },
        )

        failing.start { events += it }

        assertTrue(errors.isNotEmpty())
        verify { steering.registerSteeringWheelAngle(any()) }
    }

    @Test
    fun `a capability with no read access is not subscribed`() {
        every { turnSignal.checkTurnSignalStateCapabilities(any(), any()) } answers {
            firstArg<(CapabilityConfig) -> Unit>()(CapabilityConfig(AccessType.NONE))
        }

        started()

        verify(exactly = 0) { turnSignal.registerTurnSignalState(any()) }
    }

    @Test
    fun `the windshield wiper is checked, read, then followed`() {
        source.start { events += it }

        verifyOrder {
            wiper.checkWiperStateCapabilities(any<(CapabilityConfig, Map<SdkWindowArea, List<WiperState>>) -> Unit>(), any())
            wiper.getCurrentWiperState(windshield, any(), any())
            wiper.registerWiperState(windshield, any())
        }
        assertTrue(AppEvent.Wiper(on = true, at = 5L) in events)

        wiperListener.captured.onWiperStateUpdated(WiperState.OFF)

        assertEquals(AppEvent.Wiper(on = false, at = 5L), events.last())
    }

    @Test
    fun `only the windshield wiper counts, not one a car may have on a rear window`() {
        every {
            wiper.checkWiperStateCapabilities(any<(CapabilityConfig, Map<SdkWindowArea, List<WiperState>>) -> Unit>(), any())
        } answers {
            firstArg<(CapabilityConfig, Map<SdkWindowArea, List<WiperState>>) -> Unit>()(
                config,
                mapOf(SdkWindowArea(WindowValue.REAR_WINDSHIELD) to listOf(WiperState.ON)),
            )
        }

        source.start { events += it }

        assertTrue(events.none { it is AppEvent.Wiper })
    }

    @Test
    fun `both fog lights are followed and reported`() {
        source.start { events += it }

        frontFogListener.captured.onLightStateUpdated(LightState.ON)
        assertEquals(AppEvent.FogLight(FogPosition.FRONT, on = true, at = 5L), events.last())

        rearFogListener.captured.onLightStateUpdated(LightState.ON)
        assertEquals(AppEvent.FogLight(FogPosition.REAR, on = true, at = 5L), events.last())

        rearFogListener.captured.onLightStateUpdated(LightState.OFF)
        assertEquals(AppEvent.FogLight(FogPosition.REAR, on = false, at = 5L), events.last())
    }

    @Test
    fun `stopping releases the wiper and fog light listeners`() {
        source.start { events += it }

        source.stop()

        verify { wiper.unregisterWiperState(windshield, wiperListener.captured) }
        verify { light.unregisterFrontFogLightState(frontFogListener.captured) }
        verify { light.unregisterRearFogLightState(rearFogListener.captured) }
    }
}

class VehicleScaleTest {
    @Test
    fun `position maps across the reported range`() {
        assertEquals(0, percentOf(0, AreaBounds(0, 10)))
        assertEquals(20, percentOf(2, AreaBounds(0, 10)))
        assertEquals(100, percentOf(10, AreaBounds(0, 10)))
        assertEquals(50, percentOf(50, AreaBounds(0, 100)))
    }

    @Test
    fun `an odd range is handled without dividing by zero and stays in bounds`() {
        assertEquals(0, percentOf(5, AreaBounds(5, 5)))
        assertEquals(100, percentOf(15, AreaBounds(0, 10)))
        assertEquals(0, percentOf(-3, AreaBounds(0, 10)))
    }
}
