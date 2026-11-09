package app.visiblesound.source.demo

import app.visiblesound.core.AppEvent
import app.visiblesound.core.Direction
import app.visiblesound.core.DoorArea
import app.visiblesound.core.EmergencyVehicle
import app.visiblesound.core.GearPos
import app.visiblesound.core.LampState
import app.visiblesound.core.Seat
import app.visiblesound.core.SignalState
import app.visiblesound.core.SoundKind
import app.visiblesound.core.WindowArea
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class TimedEvent(val t: Long, val event: AppEvent)

enum class DemoScene(val asset: String, val label: String) {
    ALONE("demo/solo_drive.json", "혼자 운전"),
    WITH_PASSENGER("demo/evening_commute.json", "동승자와 함께"),
}

object DemoScript {
    private const val FRAME_MS = 500L
    private const val DEFAULT_SOUND_MS = 5_000L

    fun parse(json: String, startAt: Long): List<TimedEvent> =
        JsonParser.parseString(json).asJsonArray
            .flatMap { parseItem(it.asJsonObject, startAt) }
            .sortedBy { it.t }

    private fun parseItem(item: JsonObject, startAt: Long): List<TimedEvent> {
        val t = item["t"].asLong
        val at = startAt + t
        fun string(key: String): String = item[key].asString
        fun stringOrNull(key: String): String? = item[key]?.takeUnless { it.isJsonNull }?.asString
        fun single(event: AppEvent) = listOf(TimedEvent(t, event))

        return when (val type = string("type")) {
            "sound" -> soundFrames(item, t, startAt)
            "gear" -> single(AppEvent.Gear(GearPos.valueOf(string("value")), at))
            "caption" -> single(
                AppEvent.Caption(
                    seat = stringOrNull("seat")?.let(Seat::valueOf),
                    text = string("text"),
                    isFinal = item["isFinal"]?.asBoolean ?: true,
                    at = at,
                ),
            )
            "outside" -> single(AppEvent.Outside(string("text"), item["isFinal"]?.asBoolean ?: true, at))
            "window" -> single(AppEvent.WindowPos(WindowArea.valueOf(string("area")), item["position"].asInt, at))
            "door" -> single(AppEvent.DoorOpen(DoorArea.valueOf(string("area")), item["open"].asBoolean, at))
            "seat" -> single(AppEvent.SeatOccupied(Seat.valueOf(string("seat")), item["occupied"].asBoolean, at))
            "telltale" -> single(AppEvent.Telltale(string("name"), LampState.valueOf(string("state")), at))
            "turnSignal" -> single(AppEvent.TurnSignal(SignalState.valueOf(string("state")), at))
            "turnSignalStale" -> single(AppEvent.TurnSignalStale(SignalState.valueOf(string("side")), at))
            "steering" -> single(AppEvent.Steering(item["angle"].asFloat, at))
            "location" -> single(
                AppEvent.Location(item["lat"].asDouble, item["lng"].asDouble, stringOrNull("label"), at),
            )
            "reply" -> single(AppEvent.Reply(string("text"), at))
            else -> throw IllegalArgumentException("Unknown demo script item type: $type")
        }
    }

    /** The rules debounce classifier frames, so one scripted sound becomes a run of frames. */
    private fun soundFrames(item: JsonObject, t: Long, startAt: Long): List<TimedEvent> {
        val duration = item["durationMs"]?.asLong ?: DEFAULT_SOUND_MS
        val kind = SoundKind.valueOf(item["kind"].asString)
        val direction = item["direction"]?.asString?.let(Direction::valueOf)
        val vehicle = item["vehicle"]?.asString?.let(EmergencyVehicle::valueOf)
        val confidence = item["confidence"].asFloat
        return (0 until duration step FRAME_MS).map { offset ->
            val frameT = t + offset
            TimedEvent(frameT, AppEvent.Sound(kind, direction, confidence, startAt + frameT, vehicle))
        }
    }
}

/** Replays a script in real time so the demo looks the same on every run. */
class DemoScriptPlayer(private val script: String, private val clock: () -> Long) {
    fun events(): Flow<AppEvent> = flow {
        var elapsed = 0L
        for (item in DemoScript.parse(script, clock())) {
            delay(item.t - elapsed)
            elapsed = item.t
            emit(item.event)
        }
    }
}
