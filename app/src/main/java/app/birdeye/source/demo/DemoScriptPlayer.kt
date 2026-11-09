package app.birdeye.source.demo

import app.birdeye.core.AppEvent
import app.birdeye.core.Direction
import app.birdeye.core.DoorArea
import app.birdeye.core.EmergencyVehicle
import app.birdeye.core.FogPosition
import app.birdeye.core.GearPos
import app.birdeye.core.LampState
import app.birdeye.core.NoiseKind
import app.birdeye.core.Seat
import app.birdeye.core.SignalState
import app.birdeye.core.SoundKind
import app.birdeye.core.TrafficKind
import app.birdeye.core.WindowArea
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
            "traffic" -> single(
                AppEvent.TrafficWarning(TrafficKind.valueOf(string("kind")), Direction.valueOf(string("side")), at),
            )
            "wiper" -> single(AppEvent.Wiper(item["on"].asBoolean, at))
            "fogLight" -> single(AppEvent.FogLight(FogPosition.valueOf(string("position")), item["on"].asBoolean, at))
            "noise" -> noiseFrames(item, t, startAt)
            else -> throw IllegalArgumentException("Unknown demo script item type: $type")
        }
    }

    private fun noiseFrames(item: JsonObject, t: Long, startAt: Long): List<TimedEvent> {
        val kind = NoiseKind.valueOf(item["kind"].asString)
        val confidence = item["confidence"].asFloat
        val duration = item["durationMs"]?.asLong ?: DEFAULT_SOUND_MS
        return (0 until duration step FRAME_MS).map { offset ->
            TimedEvent(t + offset, AppEvent.AmbientNoise(kind, confidence, startAt + t + offset))
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
