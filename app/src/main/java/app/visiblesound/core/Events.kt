package app.visiblesound.core

/** Every input to the app, real or scripted, becomes one of these. */
sealed interface AppEvent {
    val at: Long

    data class Sound(
        val kind: SoundKind,
        val direction: Direction?,
        val confidence: Float,
        override val at: Long,
        val vehicle: EmergencyVehicle? = null,
    ) : AppEvent

    /** [seat] is null when the speaker cannot be located. */
    data class Caption(val seat: Seat?, val text: String, val isFinal: Boolean, override val at: Long) : AppEvent
    data class Outside(val text: String, val isFinal: Boolean, override val at: Long) : AppEvent
    data class Gear(val value: GearPos, override val at: Long) : AppEvent
    data class TurnSignal(val state: SignalState, override val at: Long) : AppEvent
    data class Steering(val angleDeg: Float, override val at: Long) : AppEvent
    data class WindowPos(val area: WindowArea, val percent: Int, override val at: Long) : AppEvent
    data class DoorOpen(val area: DoorArea, val open: Boolean, override val at: Long) : AppEvent
    data class Telltale(val name: String, val state: LampState, override val at: Long) : AppEvent
    data class SeatOccupied(val seat: Seat, val occupied: Boolean, override val at: Long) : AppEvent
    data class Reply(val text: String, override val at: Long) : AppEvent

    data class Wiper(val on: Boolean, override val at: Long) : AppEvent
    data class FogLight(val position: FogPosition, val on: Boolean, override val at: Long) : AppEvent

    /** Weather heard through the microphone, which also makes other sounds harder to hear. */
    data class AmbientNoise(val kind: NoiseKind, val confidence: Float, override val at: Long) : AppEvent

    /** The car saw another vehicle. Only the demo script produces this until the data is opened. */
    data class TrafficWarning(val kind: TrafficKind, val side: Direction, override val at: Long) : AppEvent

    data class Location(val lat: Double, val lng: Double, val label: String?, override val at: Long) : AppEvent
    data class TurnSignalStale(val side: SignalState, override val at: Long) : AppEvent
    /** [interrupted]: something took the car's audio away before the sentence was finished. */
    data class Tts(val playing: Boolean, override val at: Long, val interrupted: Boolean = false) : AppEvent
    data class UxRestricted(val active: Boolean, override val at: Long) : AppEvent

    /** The driver picked a tab. The window screen is entered only by opening the window. */
    data class SelectScreen(val screen: Screen, override val at: Long) : AppEvent
    data class SetReplies(val replies: List<String>, override val at: Long) : AppEvent
    data class RestoreTrip(val trip: TripSummary, override val at: Long) : AppEvent
    data class ListeningChanged(val status: Listening, override val at: Long) : AppEvent

    /** Starts over, keeping the driver's settings, so a rerun of the demo looks the same. */
    data class Reset(override val at: Long) : AppEvent
    data class Tick(override val at: Long) : AppEvent
}

enum class SoundKind { SIREN, HORN }
enum class NoiseKind { RAIN, THUNDER, WIND }
enum class TrafficKind { CUT_IN, BLIND_SPOT }
enum class FogPosition { FRONT, REAR }
enum class EmergencyVehicle { AMBULANCE, POLICE, FIRE }
enum class Direction { FRONT, REAR, LEFT, RIGHT }
enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }
enum class GearPos { P, R, N, D }
enum class SignalState { OFF, LEFT, RIGHT }
enum class LampState { OFF, ON, BLINK }
enum class WindowArea { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }
enum class DoorArea { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }
