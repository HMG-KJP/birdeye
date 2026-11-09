package app.birdeye.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** PoC starting values; tune them during rehearsal. */
data class RuleConfig(
    val soundThreshold: Float = 0.35f,
    /** When noise may hide sounds a fainter match is worth telling, in words that admit the doubt. */
    val soundThresholdMuffled: Float = 0.25f,
    val soundSureConfidence: Float = 0.6f,
    /** The car's own screens show doors and warning lamps, so the app repeats them only if asked to. */
    val vehicleAlerts: Boolean = false,
    val framesToAlert: Int = 3,
    val frameGapMs: Long = 1_500,
    val soundCooldownMs: Long = 10_000,
    val soundClearMs: Long = 4_000,
    val trafficHoldMs: Long = 5_000,
    val noiseThreshold: Float = 0.3f,
    val noiseFrames: Int = 4,
    val noiseHoldMs: Long = 30_000,
    val talkHoldMs: Long = 20_000,
    val speechStartGapMs: Long = 5_000,
    val echoGuardMs: Long = 500,
    val windowOpenPercent: Int = 20,
    val turnSignalStaleMs: Long = 20_000,
    val steeringStraightDeg: Float = 10f,
    val swerveDeg: Float = 90f,
    val swerveWindowMs: Long = 500,
    val swerveSettleMs: Long = 700,
    val swerveCooldownMs: Long = 10_000,
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
    internal val alsoHeardUntil: Long? = null,
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
    internal val lastSteerAt: Long? = null,
    internal val swerveFromDeg: Float = 0f,
    internal val swerveFromAt: Long = 0,
    internal val swervePending: Boolean = false,
    internal val lastSwerveAt: Long? = null,
    internal val signalSince: Long? = null,
    internal val signalNotified: Boolean = false,
    internal val openDoors: Set<DoorArea> = emptySet(),
    internal val doorNotified: Boolean = false,
    internal val lamps: Map<String, LampState> = emptyMap(),
    internal val noticeSource: NoticeSource? = null,
    internal val noticeExpiresAt: Long? = null,
    internal val parkedAt: Long? = null,
    internal val activeTraffic: Pair<TrafficKind, Direction>? = null,
    internal val trafficUntil: Long? = null,
    internal val wiperOn: Boolean = false,
    internal val fogLights: Set<FogPosition> = emptySet(),
    internal val noiseStreaks: Map<NoiseKind, Streak> = emptyMap(),
    internal val noiseUntil: Map<NoiseKind, Long> = emptyMap(),
    internal val lastFix: Fix? = null,
    internal val place: String? = null,
) {
    data class Streak(val count: Int, val lastAt: Long)
    data class ActiveSound(
        val kind: SoundKind,
        val direction: Direction?,
        val lastHeardAt: Long,
    )
    data class Fix(val lat: Double, val lng: Double)
    sealed interface NoticeSource {
        data object TurnSignal : NoticeSource
        data object Door : NoticeSource
        data object Swerve : NoticeSource
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
        next = evaluateSwerve(next, event.at, effects)
        next = resolveCondition(next, event.at)
        next = resolveScreen(next)
        return Reduction(next, effects)
    }

    private fun advanceTime(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        var s = state

        val sound = s.activeSound
        if (sound != null && now - sound.lastHeardAt >= config.soundClearMs) {
            s = logActiveSound(s, effects)
                .copy(activeSound = null, alsoHeardUntil = null)
                .withUi { copy(alert = null, alsoHeard = null) }
        }

        val alsoHeardUntil = s.alsoHeardUntil
        if (alsoHeardUntil != null && now >= alsoHeardUntil) {
            s = s.copy(alsoHeardUntil = null).withUi { copy(alsoHeard = null) }
        }

        val trafficUntil = s.trafficUntil
        if (trafficUntil != null && now >= trafficUntil) {
            s = s.copy(activeTraffic = null, trafficUntil = null).withUi { copy(alert = null) }
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
            is AppEvent.Steering -> onSteering(state, event)
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
            is AppEvent.TrafficWarning -> onTraffic(state, event, effects)
            is AppEvent.Wiper -> state.copy(wiperOn = event.on)
            is AppEvent.FogLight -> state.copy(
                fogLights = if (event.on) state.fogLights + event.position else state.fogLights - event.position,
            )
            is AppEvent.AmbientNoise -> onNoise(state, event)
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
        val threshold = if (state.ui.condition?.muffles == true) config.soundThresholdMuffled else config.soundThreshold
        val strong = event.confidence >= threshold
        val previous = state.streaks[event.kind]
        val contiguous = previous != null && event.at - previous.lastAt <= config.frameGapMs
        val count = when {
            !strong -> 0
            contiguous -> previous!!.count + 1
            else -> 1
        }
        val s = state.copy(streaks = state.streaks + (event.kind to EngineState.Streak(count, event.at)))
        if (!strong || s.activeTraffic != null) return s

        val active = s.activeSound
        if (active?.kind == event.kind) {
            return s.copy(activeSound = active.copy(lastHeardAt = event.at))
        }
        if (count < config.framesToAlert) return s

        val lastFired = s.lastFiredAt[event.kind]
        if (lastFired != null && event.at - lastFired < config.soundCooldownMs) return s

        // A siren keeps the card, but the driver can still find the horn in the recap later.
        if (active?.kind == SoundKind.SIREN) {
            val beside = soundAlert(event)
            return log(
                s.copy(lastFiredAt = s.lastFiredAt + (event.kind to event.at), alsoHeardUntil = event.at + config.noticeHoldMs)
                    .withUi { copy(alsoHeard = beside) },
                EntryKind.HORN,
                event.at,
                Copy.HORN_DURING_SIREN,
                effects,
            )
        }

        return fireSound(logActiveSound(s, effects), event, effects)
    }

    private fun soundAlert(event: AppEvent.Sound) = Alert(
        kind = when (event.kind) {
            SoundKind.SIREN -> AlertKind.SIREN
            SoundKind.HORN -> AlertKind.HORN
        },
        eyebrow = Copy.soundEyebrow(event.kind, event.direction),
        headline = Copy.soundHeadline(event.kind, event.direction, sure = event.confidence >= config.soundSureConfidence),
        guidance = Copy.soundGuidance(event.direction),
        rhythm = when (event.kind) {
            SoundKind.SIREN -> LightRhythm.SIREN
            SoundKind.HORN -> LightRhythm.HORN
        },
        direction = event.direction,
        firedAt = event.at,
    )

    private fun fireSound(state: EngineState, event: AppEvent.Sound, effects: MutableList<Effect>): EngineState {
        val alert = soundAlert(event)
        effects += Effect.Light(alert.rhythm)
        return state
            .copy(
                activeSound = EngineState.ActiveSound(event.kind, event.direction, event.at),
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
        val text = Copy.soundLog(sound.kind, sound.direction, seconds)
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
            event.interrupted -> failReply(s, event.at, Copy.REPLY_INTERRUPTED)
            s.replyStarted -> s
                .copy(replyUntil = null, toastUntil = event.at + config.toastMs)
                .withUi { copy(replying = null, toast = Copy.REPLY_SENT) }
            else -> failReply(s, event.at)
        }
    }

    /** The car did not say it all, and the driver cannot hear that: they must not believe it was heard. */
    private fun failReply(state: EngineState, now: Long, message: String = Copy.REPLY_FAILED): EngineState {
        val text = state.ui.replying
        return state
            .copy(replyUntil = null, toastUntil = now + config.toastMs)
            .withUi {
                val unsaid = captions.indexOfLast { it.speaker == Speaker.DriverReply && it.text == text }
                copy(
                    captions = if (unsaid >= 0) captions.filterIndexed { i, _ -> i != unsaid } else captions,
                    replying = null,
                    toast = message,
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

    /** The wheel reports only when it moves, so before this reading it was still at the last one. */
    private fun onSteering(state: EngineState, event: AppEvent.Steering): EngineState {
        val moved = state.copy(steeringDeg = event.angleDeg, lastSteerAt = event.at)
        if (state.lastSteerAt == null) {
            return moved.copy(swerveFromDeg = event.angleDeg, swerveFromAt = event.at)
        }
        val windowOver = event.at - state.swerveFromAt > config.swerveWindowMs
        val fromDeg = if (windowOver) state.steeringDeg else state.swerveFromDeg
        val fromAt = if (windowOver) event.at else state.swerveFromAt
        val swung = abs(event.angleDeg - fromDeg) >= config.swerveDeg
        val quietPeriodOver = state.lastSwerveAt == null || event.at - state.lastSwerveAt >= config.swerveCooldownMs
        return moved.copy(
            swerveFromDeg = if (swung) event.angleDeg else fromDeg,
            swerveFromAt = if (swung) event.at else fromAt,
            swervePending = state.swervePending || (swung && quietPeriodOver && state.ui.gear == GearPos.D),
        )
    }

    /** Lighting up the cabin in the middle of a swerve would only distract, so this waits for it to end. */
    private fun evaluateSwerve(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        if (!config.vehicleAlerts) return state
        val lastSteerAt = state.lastSteerAt
        if (!state.swervePending || lastSteerAt == null || now - lastSteerAt < config.swerveSettleMs) return state
        val settled = state.copy(swervePending = false)
        if (settled.ui.gear != GearPos.D) return settled
        return fireNotice(
            settled.copy(lastSwerveAt = now),
            EngineState.NoticeSource.Swerve,
            headline = Copy.SWERVE,
            guidance = Copy.SWERVE_GUIDANCE,
            logText = Copy.SWERVE_LOG,
            at = now,
            effects = effects,
        )
    }

    private fun evaluateDoors(state: EngineState, now: Long, effects: MutableList<Effect>): EngineState {
        if (!config.vehicleAlerts) return state
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
        if (!config.vehicleAlerts) return s
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

    // F8 ------------------------------------------------------------------------------------------

    private fun onTraffic(state: EngineState, event: AppEvent.TrafficWarning, effects: MutableList<Effect>): EngineState {
        if (state.ui.gear != GearPos.D) return state
        val warning = event.kind to event.side
        val until = event.at + config.trafficHoldMs
        if (state.activeTraffic == warning) return state.copy(trafficUntil = until)

        effects += Effect.Light(LightRhythm.TRAFFIC)
        val alert = Alert(
            kind = AlertKind.TRAFFIC,
            eyebrow = Copy.trafficEyebrow(event.side),
            headline = Copy.trafficHeadline(event.kind, event.side),
            guidance = Copy.trafficGuidance(event.kind),
            rhythm = LightRhythm.TRAFFIC,
            direction = event.side,
            firedAt = event.at,
        )
        val s = logActiveSound(state, effects)
            .copy(activeSound = null, activeTraffic = warning, trafficUntil = until)
            .withUi { copy(alert = alert) }
        return log(s, EntryKind.TRAFFIC, event.at, Copy.trafficLog(event.kind, event.side), effects)
    }

    // F7 ------------------------------------------------------------------------------------------

    private fun onNoise(state: EngineState, event: AppEvent.AmbientNoise): EngineState {
        val strong = event.confidence >= config.noiseThreshold
        val previous = state.noiseStreaks[event.kind]
        val contiguous = previous != null && event.at - previous.lastAt <= config.frameGapMs
        val count = when {
            !strong -> 0
            contiguous -> previous!!.count + 1
            else -> 1
        }
        val s = state.copy(noiseStreaks = state.noiseStreaks + (event.kind to EngineState.Streak(count, event.at)))
        if (count < config.noiseFrames) return s
        return s.copy(noiseUntil = s.noiseUntil + (event.kind to event.at + config.noiseHoldMs))
    }

    /** Most likely to hide a sound comes first, and fog, which hides only the road, comes after. */
    private fun resolveCondition(state: EngineState, now: Long): EngineState {
        fun heard(kind: NoiseKind) = (state.noiseUntil[kind] ?: 0L) > now
        val condition = when {
            heard(NoiseKind.THUNDER) -> DrivingCondition.THUNDER
            heard(NoiseKind.RAIN) -> DrivingCondition.RAIN
            state.wiperOn -> DrivingCondition.WIPER
            state.fogLights.isNotEmpty() -> DrivingCondition.FOG
            heard(NoiseKind.WIND) -> DrivingCondition.WIND
            else -> null
        }
        return state.withUi { copy(condition = condition) }
    }

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
        val aboutToTurn = state.ui.gear == GearPos.D && state.ui.turnSignal != SignalState.OFF && !state.signalNotified
        return state.withUi {
            copy(screen = screen, outside = if (window) outside else null, captionsHeld = aboutToTurn)
        }
    }

    private inline fun EngineState.withUi(change: UiState.() -> UiState) = copy(ui = ui.change())
}
