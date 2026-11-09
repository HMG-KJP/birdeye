package app.birdeye.source.demo

import app.birdeye.core.AppEvent
import app.birdeye.core.GearPos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoScriptPlayerTest {
    private val script = """[
        {"t": 0, "type": "gear", "value": "D"},
        {"t": 1000, "type": "turnSignal", "state": "LEFT"},
        {"t": 3000, "type": "gear", "value": "P"}
    ]"""

    @Test
    fun `events come out when the script says so`() = runTest {
        val seen = mutableListOf<AppEvent>()
        val player = DemoScriptPlayer(script) { testScheduler.currentTime }
        launch { player.events().collect { seen += it } }

        runCurrent()
        assertEquals(1, seen.size)

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, seen.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, seen.size)

        advanceUntilIdle()
        assertEquals(3, seen.size)
    }

    @Test
    fun `the same script always produces the same sequence`() = runTest {
        val first = DemoScriptPlayer(script) { 0L }.events().toList()
        val second = DemoScriptPlayer(script) { 0L }.events().toList()

        assertEquals(first, second)
        assertEquals(AppEvent.Gear(GearPos.D, at = 0), first.first())
    }

    @Test
    fun `event timestamps follow the clock at the moment playback starts`() = runTest {
        advanceTimeBy(5000)
        val events = DemoScriptPlayer(script) { testScheduler.currentTime }.events().toList()

        assertEquals(AppEvent.Gear(GearPos.P, at = 8000), events.last())
    }
}
