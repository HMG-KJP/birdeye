package app.birdeye.core

enum class Screen { MAIN, TALK, WINDOW, RECAP }
enum class AlertKind { SIREN, HORN, VEHICLE, TRAFFIC }
/** What may be hiding sounds or the road right now. It informs; it never alerts. */
enum class DrivingCondition(val muffles: Boolean) {
    THUNDER(muffles = true),
    RAIN(muffles = true),
    WIPER(muffles = true),
    FOG(muffles = false),
    WIND(muffles = true),
}

enum class EntryKind { SIREN, HORN, TALK, VEHICLE, OUTSIDE, TRAFFIC }

/** Whether sounds are really being watched: a deaf driver has no other way to tell. */
enum class Listening { NOT_STARTED, ACTIVE, NO_PERMISSION, MIC_ERROR, DEMO }

data class Alert(
    val kind: AlertKind,
    val eyebrow: String,
    val headline: String,
    val guidance: String?,
    val rhythm: LightRhythm,
    val direction: Direction?,
    val firedAt: Long,
)

sealed interface Speaker {
    val label: String

    data class Passenger(val seat: Seat?) : Speaker {
        override val label: String get() = seat?.label ?: "승객"
    }

    data object DriverReply : Speaker {
        override val label = "운전자 (음성 답장)"
    }

    data object Outside : Speaker {
        override val label = "차 밖 · 운전석 쪽"
    }
}

data class CaptionLine(val speaker: Speaker, val text: String, val at: Long, val isFinal: Boolean) {
    /** While the car can move only a short line is readable; the full sentence waits for park. */
    fun displayText(parked: Boolean): String =
        if (parked || text.length <= MAX_DRIVING_CHARS) text else text.take(MAX_DRIVING_CHARS) + "…"

    private companion object {
        const val MAX_DRIVING_CHARS = 30
    }
}

data class TripEntry(val kind: EntryKind, val at: Long, val text: String, val place: String?)

data class TripSummary(
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val active: Boolean = false,
    val distanceMeters: Double = 0.0,
    val entries: List<TripEntry> = emptyList(),
) {
    fun counts(): Map<EntryKind, Int> {
        val grouped = entries.groupingBy { it.kind }.eachCount()
        return EntryKind.entries.associateWith { grouped[it] ?: 0 }
    }
}

data class UiState(
    val screen: Screen = Screen.MAIN,
    /** Null until the car has reported it; an unknown gear never counts as parked. */
    val gear: GearPos? = null,
    val listening: Listening = Listening.NOT_STARTED,
    val alert: Alert? = null,
    val notice: Alert? = null,
    /** A horn heard during a siren: the siren keeps the card, and this stays beside it for a moment. */
    val alsoHeard: Alert? = null,
    val captions: List<CaptionLine> = emptyList(),
    /** The sentence still fresh enough to call "just now"; older ones stay only in [captions]. */
    val recentCaption: CaptionLine? = null,
    val outside: CaptionLine? = null,
    val activeSeat: Seat? = null,
    val occupiedSeats: Set<Seat> = emptySet(),
    val driverWindowPercent: Int = 0,
    val ttsPlaying: Boolean = false,
    val replying: String? = null,
    val toast: String? = null,
    val uxRestricted: Boolean = false,
    val replies: List<String> = Copy.DEFAULT_REPLIES,
    val trip: TripSummary = TripSummary(),
    val turnSignal: SignalState = SignalState.OFF,
    val condition: DrivingCondition? = null,
    /** Read from the door sensors. Which door is open, in any gear, since it can't be heard. */
    val openDoors: Set<DoorArea> = emptySet(),
    /** Stands in for the sound of a door shutting, for a few seconds. */
    val doorJustClosed: DoorArea? = null,
    /** About to turn: reading can wait, while sirens and horns still come through. */
    val captionsHeld: Boolean = false,
) {
    val parked: Boolean get() = gear == GearPos.P && !uxRestricted

    /** The system opens its blind spot camera over the app with either signal, in any gear. */
    val systemCameraUp: Boolean get() = turnSignal != SignalState.OFF
}

sealed interface Effect {
    data class Light(val rhythm: LightRhythm) : Effect
    data class Speak(val text: String) : Effect
    data class Log(val entry: TripEntry) : Effect
    data class TripStarted(val at: Long) : Effect
    data class TripEnded(val at: Long, val distanceMeters: Double = 0.0) : Effect
}
