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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScriptTest {
    private fun one(json: String, startAt: Long = 0L): TimedEvent =
        DemoScript.parse("[$json]", startAt).single()

    @Test
    fun `gear item becomes a gear event offset from the start time`() {
        val item = one("""{"t": 500, "type": "gear", "value": "D"}""", startAt = 1000)

        assertEquals(500L, item.t)
        assertEquals(AppEvent.Gear(GearPos.D, at = 1500), item.event)
    }

    @Test
    fun `caption item keeps seat and text and is final by default`() {
        val item = one("""{"t": 0, "type": "caption", "seat": "REAR_RIGHT", "text": "저기"}""")

        assertEquals(AppEvent.Caption(Seat.REAR_RIGHT, "저기", isFinal = true, at = 0), item.event)
    }

    @Test
    fun `caption item without a seat has an unknown speaker`() {
        val item = one("""{"t": 0, "type": "caption", "text": "저기", "isFinal": false}""")

        assertEquals(AppEvent.Caption(null, "저기", isFinal = false, at = 0), item.event)
    }

    @Test
    fun `sound item expands into repeating classifier frames so the debounce rule applies`() {
        val items = DemoScript.parse(
            """[{"t": 3000, "type": "sound", "kind": "SIREN", "vehicle": "AMBULANCE",
                 "direction": "REAR", "confidence": 0.82, "durationMs": 1500}]""",
            startAt = 0,
        )

        assertEquals(listOf(3000L, 3500L, 4000L), items.map { it.t })
        items.forEach {
            val sound = it.event as AppEvent.Sound
            assertEquals(SoundKind.SIREN, sound.kind)
            assertEquals(Direction.REAR, sound.direction)
            assertEquals(EmergencyVehicle.AMBULANCE, sound.vehicle)
            assertEquals(0.82f, sound.confidence, 0.0001f)
            assertEquals(it.t, sound.at)
        }
    }

    @Test
    fun `sound item without a duration still produces enough frames to alert`() {
        val items = DemoScript.parse("""[{"t": 0, "type": "sound", "kind": "HORN", "confidence": 0.9}]""", 0)

        assertTrue(items.size >= 3)
    }

    @Test
    fun `window item uses position as percent`() {
        val item = one("""{"t": 1, "type": "window", "area": "DRIVER", "position": 60}""")

        assertEquals(AppEvent.WindowPos(WindowArea.DRIVER, 60, at = 1), item.event)
    }

    @Test
    fun `outside item`() {
        val item = one("""{"t": 1, "type": "outside", "text": "음주 단속 중입니다"}""")

        assertEquals(AppEvent.Outside("음주 단속 중입니다", isFinal = true, at = 1), item.event)
    }

    @Test
    fun `turn signal stale item`() {
        val item = one("""{"t": 1, "type": "turnSignalStale", "side": "LEFT"}""")

        assertEquals(AppEvent.TurnSignalStale(SignalState.LEFT, at = 1), item.event)
    }

    @Test
    fun `remaining vehicle items`() {
        assertEquals(
            AppEvent.SeatOccupied(Seat.PASSENGER, true, at = 1),
            one("""{"t":1,"type":"seat","seat":"PASSENGER","occupied":true}""").event,
        )
        assertEquals(
            AppEvent.Location(37.5, 127.0, "강남대로", at = 1),
            one("""{"t":1,"type":"location","lat":37.5,"lng":127.0,"label":"강남대로"}""").event,
        )
        assertEquals(AppEvent.Reply("응, 알겠어", at = 1), one("""{"t":1,"type":"reply","text":"응, 알겠어"}""").event)
        assertEquals(
            AppEvent.DoorOpen(DoorArea.DRIVER, true, at = 1),
            one("""{"t":1,"type":"door","area":"DRIVER","open":true}""").event,
        )
        assertEquals(
            AppEvent.Telltale("안전벨트", LampState.ON, at = 1),
            one("""{"t":1,"type":"telltale","name":"안전벨트","state":"ON"}""").event,
        )
        assertEquals(AppEvent.TurnSignal(SignalState.RIGHT, at = 1), one("""{"t":1,"type":"turnSignal","state":"RIGHT"}""").event)
        assertEquals(AppEvent.Steering(3.5f, at = 1), one("""{"t":1,"type":"steering","angle":3.5}""").event)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown type fails loudly instead of silently skipping part of the show`() {
        one("""{"t": 1, "type": "teleport"}""")
    }

    @Test
    fun `items come out in time order even when the frames of a long sound overlap later items`() {
        val items = DemoScript.parse(
            """[{"t": 0, "type": "sound", "kind": "HORN", "confidence": 0.9, "durationMs": 2000},
                {"t": 700, "type": "gear", "value": "D"}]""",
            0,
        )

        assertEquals(items.map { it.t }.sorted(), items.map { it.t })
    }
}
