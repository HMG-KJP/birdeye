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
import ai.pleos.playground.vehicle.api.model.area.SeatArea
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
import ai.pleos.playground.vehicle.api.model.area.DoorArea as SdkDoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea as SdkWindowArea
import app.birdeye.core.Seat as AppSeat

/** Anything that turns the outside world into events: the real car, or a demo script. */
interface EventSource {
    fun start(sink: (AppEvent) -> Unit)
    fun stop()
}

/** Only the public domains the app needs; `Vehicle` supplies them in production. */
class VehicleDomains(
    val gear: Gear,
    val turnSignal: TurnSignal,
    val steering: SteeringWheel,
    val door: Door,
    val window: Window,
    val seat: Seat,
    val safety: Safety,
    val wiper: Wiper,
    val light: Light,
)

/** Maps a raw position to 0..100 using the range this car reports. */
internal fun percentOf(value: Int, bounds: AreaBounds<Int>): Int {
    val min = bounds.minValue
    val range = bounds.maxValue - min
    if (range <= 0) return 0
    return ((value - min) * 100 / range).coerceIn(0, 100)
}

/**
 * Follows the SDK's order for every property: check the capability, read the current value, then
 * subscribe. The SDK queues requests until its service connects, so this can run right after
 * `Vehicle.initialize()`. Warning lamps come from public Safety APIs, not the private Telltales.
 */
class RealVehicleSource(
    private val domains: VehicleDomains,
    private val clock: () -> Long,
    private val onError: (String, Exception) -> Unit,
) : EventSource {
    private val lock = Any()
    private val unsubscribers = mutableListOf<() -> Unit>()
    private var stopped = false

    override fun start(sink: (AppEvent) -> Unit) {
        val guardedSink: (AppEvent) -> Unit = { event -> if (!isStopped()) sink(event) }
        startAll(guardedSink)
    }

    private fun isStopped() = synchronized(lock) { stopped }

    private fun startAll(sink: (AppEvent) -> Unit) {
        guarded("gear") { startGear(sink) }
        guarded("turn signal") { startTurnSignal(sink) }
        guarded("steering") { startSteering(sink) }
        guarded("doors") { startDoors(sink) }
        guarded("windows") { startWindows(sink) }
        guarded("seats") { startSeats(sink) }
        guarded("seat belt") { startSeatBelt(sink) }
        guarded("washer fluid") { startWasherFluid(sink) }
        guarded("engine oil") { startFluidLevel(sink, ENGINE_OIL, FluidLevel.ENGINE_OIL) }
        guarded("brake fluid") { startFluidLevel(sink, BRAKE_FLUID, FluidLevel.BRAKE_FLUID) }
        guarded("wiper") { startWiper(sink) }
        guarded("front fog light") { startFogLight(sink, FogPosition.FRONT) }
        guarded("rear fog light") { startFogLight(sink, FogPosition.REAR) }
    }

    override fun stop() {
        val toRun = synchronized(lock) {
            stopped = true
            unsubscribers.toList().also { unsubscribers.clear() }
        }
        toRun.forEach { unsubscribe ->
            try {
                unsubscribe()
            } catch (e: Exception) {
                onError("unsubscribe", e)
            }
        }
    }

    /**
     * Answers arrive on SDK threads, possibly after [stop], and an exception there would be fatal,
     * so the step that follows a capability answer runs through here.
     */
    private inline fun whenRunning(what: String, block: () -> Unit) {
        if (isStopped()) return
        guarded(what, block)
    }

    /** Holding the lock means [stop] cannot slip in between the check and the registration. */
    private fun subscribe(subscribe: () -> Unit, unsubscribe: () -> Unit) {
        synchronized(lock) {
            if (stopped) return
            subscribe()
            unsubscribers += unsubscribe
        }
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            onError(what, e)
        }
    }

    private fun failure(what: String): (Exception) -> Unit = { onError(what, it) }

    private fun CapabilityConfig.readable() =
        accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun startGear(sink: (AppEvent) -> Unit) {
        val emit = { gear: VehicleGear? -> gear?.toGearPos()?.let { sink(AppEvent.Gear(it, clock())) } }
        domains.gear.getCurrentGearSelectionState({ emit(it) }, failure("gear"))
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                emit(gear)
            }

            override fun onFailed(exception: Exception) = onError("gear", exception)
        }
        subscribe(
            { domains.gear.registerGearSelection(listener) },
            { domains.gear.unregisterGearSelection(listener) },
        )
    }

    private fun startTurnSignal(sink: (AppEvent) -> Unit) {
        val emit = { value: TurnSignalValue? -> value?.let { sink(AppEvent.TurnSignal(it.toSignalState(), clock())) } }
        domains.turnSignal.checkTurnSignalStateCapabilities({ config ->
            if (config.readable()) whenRunning("turn signal") {
                domains.turnSignal.getCurrentTurnSignalState({ emit(it) }, failure("turn signal"))
                val listener = object : TurnSignalStateListener {
                    override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                        emit(value)
                    }

                    override fun onFailed(exception: Exception) = onError("turn signal", exception)
                }
                subscribe(
                    { domains.turnSignal.registerTurnSignalState(listener) },
                    { domains.turnSignal.unregisterTurnSignalState(listener) },
                )
            }
        }, failure("turn signal"))
    }

    private fun startSteering(sink: (AppEvent) -> Unit) {
        val emit = { angle: Float? -> angle?.let { sink(AppEvent.Steering(it, clock())) } }
        domains.steering.checkSteeringWheelAngleCapabilities({ config, _ ->
            if (config.readable()) whenRunning("steering") {
                domains.steering.getCurrentSteeringWheelAngle({ emit(it) }, failure("steering"))
                val listener = object : SteeringWheelAngleListener {
                    override fun onSteeringWheelAngleUpdated(angle: Float?) {
                        emit(angle)
                    }

                    override fun onFailed(exception: Exception) = onError("steering", exception)
                }
                subscribe(
                    { domains.steering.registerSteeringWheelAngle(listener) },
                    { domains.steering.unregisterSteeringWheelAngle(listener) },
                )
            }
        }, failure("steering"))
    }

    private fun startDoors(sink: (AppEvent) -> Unit) {
        domains.door.checkDoorPositionCapabilities({ config: CapabilityConfig, areas: Map<SdkDoorArea, AreaBounds<Int>> ->
            if (config.readable()) areas.forEach { (area, bounds) -> whenRunning("doors") { startDoor(area, bounds, sink) } }
        }, failure("doors"))
    }

    private fun startDoor(area: SdkDoorArea, bounds: AreaBounds<Int>, sink: (AppEvent) -> Unit) {
        val door = area.values.firstNotNullOfOrNull { it.toDoorArea() } ?: return
        val emit = { position: Int? ->
            position?.let { sink(AppEvent.DoorOpen(door, it > bounds.minValue, clock())) }
        }
        domains.door.getCurrentDoorPosition(area, { emit(it) }, failure("doors"))
        val listener = object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) {
                emit(pos)
            }

            override fun onFailed(exception: Exception) = onError("doors", exception)
        }
        subscribe(
            { domains.door.registerDoorPosition(area, listener) },
            { domains.door.unregisterDoorPosition(area, listener) },
        )
    }

    private fun startWindows(sink: (AppEvent) -> Unit) {
        domains.window.checkWindowPositionCapabilities({ config: CapabilityConfig, areas: Map<SdkWindowArea, AreaBounds<Int>> ->
            if (config.readable()) areas.forEach { (area, bounds) -> whenRunning("windows") { startWindow(area, bounds, sink) } }
        }, failure("windows"))
    }

    private fun startWindow(area: SdkWindowArea, bounds: AreaBounds<Int>, sink: (AppEvent) -> Unit) {
        val window = area.values.firstNotNullOfOrNull { it.toWindowArea() } ?: return
        val emit = { position: Int? ->
            position?.let { sink(AppEvent.WindowPos(window, percentOf(it, bounds), clock())) }
        }
        domains.window.getCurrentWindowPosition(area, { emit(it) }, failure("windows"))
        val listener = object : WindowPositionListener {
            override fun onWindowPositionUpdated(position: Int?) {
                emit(position)
            }

            override fun onFailed(exception: Exception) = onError("windows", exception)
        }
        subscribe(
            { domains.window.registerWindowPosition(area, listener) },
            { domains.window.unregisterWindowPosition(area, listener) },
        )
    }

    private fun startSeats(sink: (AppEvent) -> Unit) {
        domains.seat.checkSeatOccupancyCapabilities({ config: CapabilityConfig, areas: List<SeatArea> ->
            if (config.readable()) areas.forEach { whenRunning("seats") { startSeat(it, sink) } }
        }, failure("seats"))
    }

    private fun startSeat(area: SeatArea, sink: (AppEvent) -> Unit) {
        val seat = area.values.firstNotNullOfOrNull { it.toAppSeat() } ?: return
        // The SDK compares the car's raw value with 1, which means VACANT, so true is an empty seat.
        val emit = { vacant: Boolean? ->
            vacant?.let { sink(AppEvent.SeatOccupied(seat, !it, clock())) }
        }
        domains.seat.getCurrentSeatOccupancy(area, { emit(it) }, failure("seats"))
        val listener = object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(vacant: Boolean?) {
                emit(vacant)
            }

            override fun onFailed(exception: Exception) = onError("seats", exception)
        }
        subscribe(
            { domains.seat.registerSeatOccupancy(area, listener) },
            { domains.seat.unregisterSeatOccupancy(area, listener) },
        )
    }

    private fun lamp(name: String, on: Boolean) = AppEvent.Telltale(name, if (on) LampState.ON else LampState.OFF, clock())

    private fun startSeatBelt(sink: (AppEvent) -> Unit) {
        domains.safety.checkSeatNotBuckledWarningCapabilities({ config, areas ->
            val driver = areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values }
            if (config.readable() && driver != null) whenRunning("seat belt") {
                val emit = { warning: Boolean? -> warning?.let { sink(lamp(SEAT_BELT, it)) } }
                domains.safety.hasSeatBeltNotBuckledWarning(driver, { emit(it) }, failure("seat belt"))
                val listener = object : SeatBeltNotBuckledWarningListener {
                    override fun onSeatBeltNotBuckledWarningUpdated(warning: Boolean?) {
                        emit(warning)
                    }

                    override fun onFailed(exception: Exception) = onError("seat belt", exception)
                }
                subscribe(
                    { domains.safety.registerSeatBeltNotBuckledWarning(driver, listener) },
                    { domains.safety.unregisterSeatBeltNotBuckledWarning(driver, listener) },
                )
            }
        }, failure("seat belt"))
    }

    private fun startWasherFluid(sink: (AppEvent) -> Unit) {
        domains.safety.checkLowWasherFluidWarningStateCapabilities({ config ->
            if (config.readable()) whenRunning("washer fluid") {
                val emit = { low: Boolean? -> low?.let { sink(lamp(WASHER_FLUID, it)) } }
                domains.safety.hasLowWasherFluidWarning({ emit(it) }, failure("washer fluid"))
                val listener = object : LowWasherFluidWaringListener {
                    override fun onLowWasherFluidWaringUpdated(low: Boolean?) {
                        emit(low)
                    }

                    override fun onFailed(exception: Exception) = onError("washer fluid", exception)
                }
                subscribe(
                    { domains.safety.registerLowWasherFluidWaringState(listener) },
                    { domains.safety.unregisterLowWasherFluidWaringState(listener) },
                )
            }
        }, failure("washer fluid"))
    }

    private fun startWiper(sink: (AppEvent) -> Unit) {
        domains.wiper.checkWiperStateCapabilities({ config: CapabilityConfig, areas: Map<SdkWindowArea, List<WiperState>> ->
            val windshield = areas.keys.firstOrNull { WindowValue.FRONT_WINDSHIELD in it.values }
            if (config.readable() && windshield != null) whenRunning("wiper") {
                val emit = { state: WiperState? -> state?.let { sink(AppEvent.Wiper(it == WiperState.ON, clock())) } }
                domains.wiper.getCurrentWiperState(windshield, { emit(it) }, failure("wiper"))
                val listener = object : WiperStateListener {
                    override fun onWiperStateUpdated(state: WiperState?) {
                        emit(state)
                    }

                    override fun onFailed(exception: Exception) = onError("wiper", exception)
                }
                subscribe(
                    { domains.wiper.registerWiperState(windshield, listener) },
                    { domains.wiper.unregisterWiperState(windshield, listener) },
                )
            }
        }, failure("wiper"))
    }

    private fun startFogLight(sink: (AppEvent) -> Unit, position: FogPosition) {
        val what = "fog light"
        val emit = { state: LightState? -> state?.let { sink(AppEvent.FogLight(position, it == LightState.ON, clock())) } }
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                emit(state)
            }

            override fun onFailed(exception: Exception) = onError(what, exception)
        }
        val onChecked = { config: CapabilityConfig, _: List<LightState> ->
            if (config.readable()) whenRunning(what) {
                when (position) {
                    FogPosition.FRONT -> {
                        domains.light.getCurrentFrontFogLightState({ emit(it) }, failure(what))
                        subscribe(
                            { domains.light.registerFrontFogLightState(listener) },
                            { domains.light.unregisterFrontFogLightState(listener) },
                        )
                    }
                    FogPosition.REAR -> {
                        domains.light.getCurrentRearFogLightState({ emit(it) }, failure(what))
                        subscribe(
                            { domains.light.registerRearFogLightState(listener) },
                            { domains.light.unregisterRearFogLightState(listener) },
                        )
                    }
                }
            }
        }
        when (position) {
            FogPosition.FRONT -> domains.light.checkFrontFogLightStateCapabilities(onChecked, failure(what))
            FogPosition.REAR -> domains.light.checkRearFogLightStateCapabilities(onChecked, failure(what))
        }
    }

    private enum class FluidLevel { ENGINE_OIL, BRAKE_FLUID }

    private fun startFluidLevel(sink: (AppEvent) -> Unit, name: String, fluid: FluidLevel) {
        val emit = { level: OilLevelState? -> level?.let { sink(lamp(name, it.needsAttention())) } }
        val listener = object : OilLevelStateListener {
            override fun onOilLevelUpdated(level: OilLevelState?) {
                emit(level)
            }

            override fun onFailed(exception: Exception) = onError(name, exception)
        }
        val onChecked = { config: CapabilityConfig, _: List<OilLevelState> ->
            if (config.readable()) whenRunning(name) {
                when (fluid) {
                    FluidLevel.ENGINE_OIL -> {
                        domains.safety.getEngineOilLevelState({ emit(it) }, failure(name))
                        subscribe(
                            { domains.safety.registerEngineOilLevelState(listener) },
                            { domains.safety.unregisterEngineOilLevelState(listener) },
                        )
                    }
                    FluidLevel.BRAKE_FLUID -> {
                        domains.safety.getBrakeFluidLevelState({ emit(it) }, failure(name))
                        subscribe(
                            { domains.safety.registerBrakeFluidLevelState(listener) },
                            { domains.safety.unregisterBrakeFluidLevelState(listener) },
                        )
                    }
                }
            }
        }
        when (fluid) {
            FluidLevel.ENGINE_OIL -> domains.safety.checkEngineOilLevelStateCapabilities(onChecked, failure(name))
            FluidLevel.BRAKE_FLUID -> domains.safety.checkBrakeFluidLevelStateCapabilities(onChecked, failure(name))
        }
    }

    private companion object {
        const val SEAT_BELT = "안전벨트"
        const val WASHER_FLUID = "워셔액"
        const val ENGINE_OIL = "엔진오일"
        const val BRAKE_FLUID = "브레이크액"
    }
}

private fun VehicleGear.toGearPos(): GearPos? = when (this) {
    VehicleGear.GEAR_PARK -> GearPos.P
    VehicleGear.GEAR_REVERSE -> GearPos.R
    VehicleGear.GEAR_NEUTRAL -> GearPos.N
    VehicleGear.GEAR_UNKNOWN -> null
    else -> GearPos.D
}

private fun TurnSignalValue.toSignalState(): SignalState = when (this) {
    TurnSignalValue.OFF -> SignalState.OFF
    TurnSignalValue.LEFT -> SignalState.LEFT
    TurnSignalValue.RIGHT -> SignalState.RIGHT
}

private fun DoorValue.toDoorArea(): DoorArea? = when (this) {
    DoorValue.ROW_1_LEFT -> DoorArea.DRIVER
    DoorValue.ROW_1_RIGHT -> DoorArea.PASSENGER
    DoorValue.ROW_2_LEFT -> DoorArea.REAR_LEFT
    DoorValue.ROW_2_RIGHT -> DoorArea.REAR_RIGHT
    else -> null
}

private fun WindowValue.toWindowArea(): WindowArea? = when (this) {
    WindowValue.ROW_1_LEFT -> WindowArea.DRIVER
    WindowValue.ROW_1_RIGHT -> WindowArea.PASSENGER
    WindowValue.ROW_2_LEFT -> WindowArea.REAR_LEFT
    WindowValue.ROW_2_RIGHT -> WindowArea.REAR_RIGHT
    else -> null
}

private fun SeatValue.toAppSeat(): AppSeat? = when (this) {
    SeatValue.ROW_1_LEFT -> AppSeat.DRIVER
    SeatValue.ROW_1_RIGHT -> AppSeat.PASSENGER
    SeatValue.ROW_2_LEFT -> AppSeat.REAR_LEFT
    SeatValue.ROW_2_RIGHT -> AppSeat.REAR_RIGHT
    else -> null
}

private fun OilLevelState.needsAttention() = when (this) {
    OilLevelState.LOW, OilLevelState.CRITICALLY_LOW, OilLevelState.ERROR -> true
    OilLevelState.NORMAL, OilLevelState.HIGH -> false
}
