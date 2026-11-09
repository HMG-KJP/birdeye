package app.visiblesound.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** PoC starting values; tune them during rehearsal. */
data class RuleConfig(
    val soundThreshold: Float = 0.35f,
    val framesToAlert: Int = 3,
    val frameGapMs: Long = 1_500,
    val soundCooldownMs: Long = 10_000,
    val soundClearMs: Long = 4_000,
    val talkHoldMs: Long = 20_000,
    val speechStartGapMs: Long = 5_000,
    val echoGuardMs: Long = 500,
    val windowOpenPercent: Int = 20,
    val turnSignalStaleMs: Long = 20_000,
    val steeringStraightDeg: Float = 10f,
    val noticeHoldMs: Long = 8_000,
    val tripEndMs: Long = 120_000,
    val maxCaptions: Int = 20,
    val toastMs: Long = 2_500,
    val replyTimeoutMs: Long = 20_000,
)

data class EngineState(
    val ui: UiState = UiState(),
    internal val streaks: Map<SoundKind, Streak> = emptyMap(),
    internal val activeSound: ActiveSound? = null,
    internal val lastFiredAt: Map<SoundKind, Long> = emptyMap(),
    internal val ttsEndedAt: Long? = null,
    internal val talkUntil: Long? = null,
    internal val talkPinned: Boolean = false,
    internal val toastUntil: Long? = null,
    internal val replyUntil: Long? = null,
    internal val replyStarted: Boolean = false,
    internal val lastSpeechAt: Long? = null,
    internal val lastOutsideAt: Long? = null,
    internal val recapOpen: Boolean = false,
    internal val steeringDeg: Float = 0f,
    internal val signalSince: Long? = null,
    internal val signalNotified: Boolean = false,
    internal val openDoors: Set<DoorArea> = emptySet(),
    internal val doorNotified: Boolean = false,
    internal val lamps: Map<String, LampState> = emptyMap(),
    internal val noticeSource: NoticeSource? = null,
    internal val noticeExpiresAt: Long? = null,
    internal val parkedAt: Long? = null,
    internal val lastFix: Fix? = null,
    internal val place: String? = null,
) {
    data class Streak(val count: Int, val lastAt: Long)
    data class ActiveSound(
        val kind: SoundKind,
        val direction: Direction?,
        val vehicle: EmergencyVehicle?,
        val lastHeardAt: Long,
    )
    data class Fix(val lat: Double, val lng: Double)
    sealed interface NoticeSource {
        data object TurnSignal : NoticeSource
        data object Door : NoticeSource
        data class Lamp(val name: String) : NoticeSource
    }
}

data class Reduction(val state: EngineState, val effects: List<Effect> = emptyList())

/** All product rules as one pure function, so a scripted demo and the real car behave the same. */
class RuleEngine(private val config: RuleConfig = RuleConfig()) {

    fun reduce(state: EngineState, event: AppEvent): Reduction {
        val effects = mutableListOf<Effect>()
        var next = advanceTime(state, event.at, effects)
        next = applyEvent(next, event, effects)
        next = evaluateTurnSignal(next, event.at, effects)
        next = evaluateDoors(next, event.at, effects)
        next = resolveScreen(next)
        return Reduction(next, effects)
    }

    private fun advanceTime(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        var s = state

        val sound = s.activeSound
        if (sound != null && now - sound.lastHeardAt >= config.soundClearMs) {
            s = logActiveSound(s, effects).copy(activeSound = null).withUi { copy(alert = null) }
        }

        val expires = s.noticeExpiresAt
        if (expires != null && now >= expires) {
            s = s.clearNotice()
        }

        val talkUntil = s.talkUntil
        if (talkUntil != null && now >= talkUntil) {
            s = s.copy(talkUntil = null).withUi { copy(activeSeat = null, recentCaption = null) }
        }

        val toastUntil = s.toastUntil
        if (toastUntil != null && now >= toastUntil) {
            s = s.copy(toastUntil = null).withUi { copy(toast = null) }
        }

        val replyUntil = s.replyUntil
        if (replyUntil != null && now >= replyUntil) {
            s = failReply(s, now)
        }

        val parkedAt = s.parkedAt
        if (parkedAt != null && s.ui.trip.active && now - parkedAt >= config.tripEndMs) {
            s = logActiveSound(s, effects)
            effects += Effect.TripEnded(now, s.ui.trip.distanceMeters)
            s = s.copy(parkedAt = null).withUi { copy(trip = trip.copy(active = false, endedAt = now)) }
        }
        return s
    }

    private fun applyEvent(state: EngineState, event: AppEvent, effects: MutableList<Effect>): EngineState =
        when (event) {
            is AppEvent.Sound -> onSound(state, event, effects)
            is AppEvent.Caption -> onCaption(state, event, effects)
            is AppEvent.Outside -> onOutside(state, event, effects)
            is AppEvent.Reply -> onReply(state, event, effects)
            is AppEvent.Gear -> onGear(state, event, effects)
            is AppEvent.TurnSignal -> onTurnSignal(state, event)
            is AppEvent.TurnSignalStale -> fireNotice(
                state.copy(signalNotified = true),
                EngineState.NoticeSource.TurnSignal,
                headline = Copy.TURN_SIGNAL,
                guidance = null,
                logText = Copy.TURN_SIGNAL_LOG,
                at = event.at,
                effects = effects,
            )
            is AppEvent.Steering -> state.copy(steeringDeg = event.angleDeg)
            is AppEvent.WindowPos ->
                if (event.area == WindowArea.DRIVER) state.withUi { copy(driverWindowPercent = event.percent) } else state
            is AppEvent.DoorOpen -> state.copy(
                openDoors = if (event.open) state.openDoors + event.area else state.openDoors - event.area,
            )
            is AppEvent.Telltale -> onLamp(state, event, effects)
            is AppEvent.SeatOccupied -> state.withUi {
                copy(occupiedSeats = if (event.occupied) occupiedSeats + event.seat else occupiedSeats - event.seat)
            }
            is AppEvent.Location -> onLocation(state, event)
            is AppEvent.Tts -> onTts(state, event)
            is AppEvent.UxRestricted -> state
                .copy(recapOpen = state.recapOpen && !event.active)
                .withUi { copy(uxRestricted = event.active) }
            is AppEvent.SelectScreen -> onSelectScreen(state, event)
            is AppEvent.SetReplies -> state.withUi { copy(replies = event.replies) }
            is AppEvent.RestoreTrip -> if (state.ui.trip.active) state else state.withUi { copy(trip = event.trip) }
            is AppEvent.ListeningChanged -> state.withUi { copy(listening = event.status) }
            is AppEvent.Reset -> EngineState(
                ui = UiState(
                    replies = state.ui.replies,
                    listening = state.ui.listening,
                    uxRestricted = state.ui.uxRestricted,
                ),
            )
            is AppEvent.Tick -> state
        }

    private fun onSelectScreen(state: EngineState, event: AppEvent.SelectScreen): EngineState =
        when (event.screen) {
            Screen.MAIN -> state
                .copy(recapOpen = false, talkPinned = false, talkUntil = null)
                .withUi { copy(activeSeat = null, recentCaption = null) }
            Screen.TALK -> state.copy(recapOpen = false, talkPinned = true)
            Screen.RECAP -> if (state.ui.parked) state.copy(recapOpen = true) else state
            Screen.WINDOW -> state
        }

    // F1 ------------------------------------------------------------------------------------------

    private fun onSound(state: EngineState, event: AppEvent.Sound, effects: MutableList<Effect>): EngineState {
        val strong = event.confidence >= config.soundThreshold
        val previous = state.streaks[event.kind]
        val contiguous = previous != null && event.at - previous.lastAt <= config.frameGapMs
        val count = when {
            !strong -> 0
            contiguous -> previous!!.count + 1
            else -> 1
        }
        val s = state.copy(streaks = state.streaks + (event.kind to EngineState.Streak(count, event.at)))
        if (!strong) return s

        val active = s.activeSound
        if (active?.kind == event.kind) {
            return s.copy(activeSound = active.copy(lastHeardAt = event.at))
        }
        if (count < config.framesToAlert) return s

        val lastFired = s.lastFiredAt[event.kind]
        if (lastFired != null && event.at - lastFired < config.soundCooldownMs) return s

        // A siren keeps the card, but the driver can still find the horn in the recap later.
        if (active?.kind == SoundKind.SIREN) {
            return log(
                s.copy(lastFiredAt = s.lastFiredAt + (event.kind to event.at)),
                EntryKind.HORN,
                event.at,
                Copy.HORN_DURING_SIREN,
                effects,
            )
        }

        return fireSound(logActiveSound(s, effects), event, effects)
    }

    private fun fireSound(state: EngineState, event: AppEvent.Sound, effects: MutableList<Effect>): EngineState {
        val rhythm = when (event.kind) {
            SoundKind.SIREN -> LightRhythm.SIREN
            SoundKind.HORN -> LightRhythm.HORN
        }
        effects += Effect.Light(rhythm)
        val alert = Alert(
            kind = when (event.kind) {
                SoundKind.SIREN -> AlertKind.SIREN
                SoundKind.HORN -> AlertKind.HORN
            },
            eyebrow = Copy.soundEyebrow(event.kind, event.direction),
            headline = Copy.soundHeadline(event.kind, event.vehicle),
            guidance = Copy.soundGuidance(event.kind, event.direction),
            rhythm = rhythm,
            direction = event.direction,
            firedAt = event.at,
        )
        return state
            .copy(
                activeSound = EngineState.ActiveSound(event.kind, event.direction, event.vehicle, event.at),
                lastFiredAt = state.lastFiredAt + (event.kind to event.at),
            )
            .withUi { copy(alert = alert) }
    }

    private fun logActiveSound(state: EngineState, effects: MutableList<Effect>): EngineState {
        val sound = state.activeSound ?: return state
        val alert = state.ui.alert ?: return state
        val seconds = ((sound.lastHeardAt - alert.firedAt) / 1000.0).roundToLong().coerceAtLeast(1)
        val kind = when (sound.kind) {
            SoundKind.SIREN -> EntryKind.SIREN
            SoundKind.HORN -> EntryKind.HORN
        }
        val text = Copy.soundLog(sound.kind, sound.direction, sound.vehicle, seconds)
        return log(state, kind, alert.firedAt, text, effects)
    }

    // F2, F3 --------------------------------------------------------------------------------------

    private fun hearsOwnVoice(state: EngineState, at: Long): Boolean {
        if (state.ui.ttsPlaying) return true
        val ended = state.ttsEndedAt ?: return false
        return at - ended < config.echoGuardMs
    }

    private fun onCaption(state: EngineState, event: AppEvent.Caption, effects: MutableList<Effect>): EngineState {
        if (event.seat == Seat.DRIVER || event.text.isBlank()) return state
        val open = state.ui.captions.indexOfLast { !it.isFinal }
        // The end of a sentence begun before our voice started is theirs, not an echo of ours.
        val continuesSentence = open >= 0 && event.text.startsWith(state.ui.captions[open].text)
        if (hearsOwnVoice(state, event.at) && !continuesSentence) return state

        val speaker = Speaker.Passenger(event.seat)
        val startsTalking = state.lastSpeechAt == null || event.at - state.lastSpeechAt >= config.speechStartGapMs
        if (startsTalking) effects += Effect.Light(LightRhythm.PASSENGER)

        val line = CaptionLine(speaker, event.text, event.at, event.isFinal)
        val updated = state.ui.captions.let { lines ->
            if (open >= 0) lines.toMutableList().also { it[open] = line } else lines + line
        }
        var s = state
            .copy(lastSpeechAt = event.at, talkUntil = event.at + config.talkHoldMs)
            .withUi {
                copy(
                    captions = updated.takeLast(config.maxCaptions),
                    recentCaption = line,
                    activeSeat = event.seat,
                )
            }
        if (event.isFinal) {
            s = log(s, EntryKind.TALK, event.at, "${speaker.label}: \"${event.text}\"", effects)
        }
        return s
    }

    private fun onReply(state: EngineState, event: AppEvent.Reply, effects: MutableList<Effect>): EngineState {
        effects += Effect.Speak(event.text)
        val line = CaptionLine(Speaker.DriverReply, event.text, event.at, isFinal = true)
        return state
            .copy(
                talkUntil = if (windowMode(state)) state.talkUntil else event.at + config.talkHoldMs,
                replyUntil = event.at + config.replyTimeoutMs,
                replyStarted = false,
            )
            .withUi {
                copy(
                    captions = (captions + line).takeLast(config.maxCaptions),
                    replying = event.text,
                )
            }
    }

    private fun onTts(state: EngineState, event: AppEvent.Tts): EngineState {
        if (event.playing) {
            return state.copy(replyStarted = true).withUi { copy(ttsPlaying = true) }
        }
        val s = state.copy(ttsEndedAt = event.at).withUi { copy(ttsPlaying = false) }
        return when {
            s.ui.replying == null -> s
            s.replyStarted -> s
                .copy(replyUntil = null, toastUntil = event.at + config.toastMs)
                .withUi { copy(replying = null, toast = Copy.REPLY_SENT) }
            else -> failReply(s, event.at)
        }
    }

    /** The car never spoke, so the driver must not believe the other person heard the reply. */
    private fun failReply(state: EngineState, now: Long): EngineState {
        val text = state.ui.replying
        return state
            .copy(replyUntil = null, toastUntil = now + config.toastMs)
            .withUi {
                val unsaid = captions.indexOfLast { it.speaker == Speaker.DriverReply && it.text == text }
                copy(
                    captions = if (unsaid >= 0) captions.filterIndexed { i, _ -> i != unsaid } else captions,
                    replying = null,
                    toast = Copy.REPLY_FAILED,
                )
            }
    }

    // F4 ------------------------------------------------------------------------------------------

    private fun windowMode(state: EngineState) =
        state.ui.gear == GearPos.P && state.ui.driverWindowPercent >= config.windowOpenPercent

    private fun onOutside(state: EngineState, event: AppEvent.Outside, effects: MutableList<Effect>): EngineState {
        if (!windowMode(state) || event.text.isBlank() || hearsOwnVoice(state, event.at)) return state

        val startsTalking = state.lastOutsideAt == null || event.at - state.lastOutsideAt >= config.speechStartGapMs
        if (startsTalking) effects += Effect.Light(LightRhythm.OUTSIDE)

        var s = state
            .copy(lastOutsideAt = event.at)
            .withUi { copy(outside = CaptionLine(Speaker.Outside, event.text, event.at, event.isFinal)) }
        if (event.isFinal) {
            s = log(s, EntryKind.OUTSIDE, event.at, "차 밖: \"${event.text}\"", effects)
        }
        return s
    }

    // F5 ------------------------------------------------------------------------------------------

    private fun onTurnSignal(state: EngineState, event: AppEvent.TurnSignal): EngineState {
        val changed = event.state != state.ui.turnSignal
        val s = state.copy(
            signalSince = if (changed) null else state.signalSince,
            signalNotified = if (changed) false else state.signalNotified,
        ).withUi { copy(turnSignal = event.state) }
        return if (event.state == SignalState.OFF) s.clearNoticeFrom(EngineState.NoticeSource.TurnSignal) else s
    }

    private fun evaluateTurnSignal(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        val staleCondition = state.ui.gear == GearPos.D &&
            state.ui.turnSignal != SignalState.OFF &&
            abs(state.steeringDeg) < config.steeringStraightDeg
        if (!staleCondition) {
            return state.copy(signalSince = null, signalNotified = false)
        }
        val since = state.signalSince ?: now
        val s = state.copy(signalSince = since)
        if (s.signalNotified || now - since < config.turnSignalStaleMs) return s
        return fireNotice(
            s.copy(signalNotified = true),
            EngineState.NoticeSource.TurnSignal,
            headline = Copy.TURN_SIGNAL,
            guidance = null,
            logText = Copy.TURN_SIGNAL_LOG,
            at = now,
            effects = effects,
        )
    }

    private fun evaluateDoors(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        val drivingWithDoorOpen = state.ui.gear == GearPos.D && state.openDoors.isNotEmpty()
        return when {
            !drivingWithDoorOpen ->
                state.copy(doorNotified = false).clearNoticeFrom(EngineState.NoticeSource.Door)
            !state.doorNotified -> fireNotice(
                state.copy(doorNotified = true),
                EngineState.NoticeSource.Door,
                headline = Copy.DOOR_OPEN,
                guidance = null,
                logText = Copy.DOOR_OPEN_LOG,
                at = now,
                effects = effects,
                hold = false,
            )
            // A shorter lamp notice took the slot and has expired; the door is still open.
            state.ui.notice == null -> state
                .copy(noticeSource = EngineState.NoticeSource.Door, noticeExpiresAt = null)
                .withUi { copy(notice = vehicleAlert(Copy.DOOR_OPEN, null, now)) }
            else -> state
        }
    }

    private fun onLamp(state: EngineState, event: AppEvent.Telltale, effects: MutableList<Effect>): EngineState {
        val previous = state.lamps[event.name] ?: LampState.OFF
        val s = state.copy(lamps = state.lamps + (event.name to event.state))
        if (event.state == LampState.OFF) {
            return s.clearNoticeFrom(EngineState.NoticeSource.Lamp(event.name))
        }
        if (previous != LampState.OFF) return s
        val description = Copy.lampDescription(event.name)
        return fireNotice(
            s,
            EngineState.NoticeSource.Lamp(event.name),
            headline = event.name,
            guidance = description,
            logText = listOfNotNull(event.name, description).joinToString(" · "),
            at = event.at,
            effects = effects,
        )
    }

    private fun vehicleAlert(headline: String, guidance: String?, at: Long) = Alert(
        kind = AlertKind.VEHICLE,
        eyebrow = Copy.VEHICLE_EYEBROW,
        headline = headline,
        guidance = guidance,
        rhythm = LightRhythm.VEHICLE,
        direction = null,
        firedAt = at,
    )

    private fun fireNotice(
        state: EngineState,
        source: EngineState.NoticeSource,
        headline: String,
        guidance: String?,
        logText: String,
        at: Long,
        effects: MutableList<Effect>,
        hold: Boolean = true,
    ): EngineState {
        effects += Effect.Light(LightRhythm.VEHICLE)
        val s = state
            .copy(noticeSource = source, noticeExpiresAt = if (hold) at + config.noticeHoldMs else null)
            .withUi { copy(notice = vehicleAlert(headline, guidance, at)) }
        return log(s, EntryKind.VEHICLE, at, logText, effects)
    }

    private fun EngineState.clearNotice() =
        copy(noticeSource = null, noticeExpiresAt = null).withUi { copy(notice = null) }

    private fun EngineState.clearNoticeFrom(source: EngineState.NoticeSource) =
        if (noticeSource == source) clearNotice() else this

    // F6 ------------------------------------------------------------------------------------------

    private fun onGear(state: EngineState, event: AppEvent.Gear, effects: MutableList<Effect>): EngineState {
        val before = state.ui.gear
        var s = state.withUi { copy(gear = event.value) }
        if (event.value != GearPos.P) s = s.copy(recapOpen = false, parkedAt = null)
        if (event.value == GearPos.P && before != GearPos.P) s = s.copy(parkedAt = event.at)
        if (event.value == GearPos.D && !s.ui.trip.active) {
            effects += Effect.TripStarted(event.at)
            s = s.withUi {
                copy(
                    trip = TripSummary(startedAt = event.at, active = true),
                    captions = emptyList(),
                    recentCaption = null,
                    activeSeat = null,
                )
            }
        }
        return s
    }

    private fun onLocation(state: EngineState, event: AppEvent.Location): EngineState {
        val fix = EngineState.Fix(event.lat, event.lng)
        val place = event.label ?: String.format(Locale.US, "%.4f, %.4f", event.lat, event.lng)
        val previous = state.lastFix
        val s = state.copy(lastFix = fix, place = place)
        if (previous == null || !s.ui.trip.active) return s
        val moved = GeoMath.distanceMeters(previous.lat, previous.lng, fix.lat, fix.lng)
        return s.withUi { copy(trip = trip.copy(distanceMeters = trip.distanceMeters + moved)) }
    }

    private fun log(
        state: EngineState,
        kind: EntryKind,
        at: Long,
        text: String,
        effects: MutableList<Effect>,
    ): EngineState {
        if (!state.ui.trip.active) return state
        val entry = TripEntry(kind, at, text, state.place)
        effects += Effect.Log(entry)
        // Sounds are logged when they end, so an entry can belong before ones already written.
        return state.withUi { copy(trip = trip.copy(entries = (trip.entries + entry).sortedBy { it.at })) }
    }

    // Screen --------------------------------------------------------------------------------------

    private fun resolveScreen(state: EngineState): EngineState {
        val window = windowMode(state)
        val screen = when {
            window -> Screen.WINDOW
            state.recapOpen -> Screen.RECAP
            state.talkPinned || state.talkUntil != null -> Screen.TALK
            else -> Screen.MAIN
        }
        return state.withUi {
            copy(screen = screen, outside = if (window) outside else null)
        }
    }

    private inline fun EngineState.withUi(change: UiState.() -> UiState) = copy(ui = ui.change())
}
