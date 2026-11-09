package app.visiblesound.output

import app.visiblesound.core.AmbientColor
import app.visiblesound.core.LightRhythm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmbientLightPlayerTest {
    private class FakeController(var current: LightValue? = LightValue(40, AmbientColor.BLUE.rgb)) : AmbientLightController {
        val applied = mutableListOf<LightValue>()
        var reads = 0

        override suspend fun read(): LightValue? {
            reads++
            return current
        }

        override fun apply(value: LightValue) {
            applied += value
            current = value
        }
    }

    private fun TestScope.player(controller: AmbientLightController) =
        AmbientLightPlayer(controller, this) { testScheduler.currentTime }

    @Test
    fun `siren is three fast flashes in two sets of white`() {
        val on = LightRhythm.SIREN.steps().filter { it.brightness > 0 }

        assertEquals(6, on.size)
        on.forEach {
            assertEquals(150L, it.durationMs)
            assertEquals(AmbientColor.WHITE, it.color)
        }
    }

    @Test
    fun `horn is one strong four hundred millisecond flash`() {
        val steps = LightRhythm.HORN.steps()

        assertEquals(400L, steps.single().durationMs)
        assertEquals(100, steps.single().brightness)
        assertEquals(AmbientColor.WHITE, steps.single().color)
    }

    @Test
    fun `passenger speech is one gentle cyan fade lasting six hundred milliseconds`() {
        val steps = LightRhythm.PASSENGER.steps()

        assertEquals(600L, steps.sumOf { it.durationMs })
        assertEquals(steps.map { it.brightness }.max(), steps[steps.size / 2].brightness)
        steps.forEach { assertEquals(AmbientColor.CYAN, it.color) }
    }

    @Test
    fun `outside speech is the gentle fade twice in blue`() {
        val steps = LightRhythm.OUTSIDE.steps()

        assertEquals(1200L, steps.sumOf { it.durationMs })
        steps.forEach { assertEquals(AmbientColor.BLUE, it.color) }
    }

    @Test
    fun `vehicle sound is two slow purple pulses`() {
        val steps = LightRhythm.VEHICLE.steps()

        assertEquals(listOf(500L, 500L, 500L, 500L), steps.map { it.durationMs })
        assertEquals(2, steps.count { it.brightness > 0 })
        steps.forEach { assertEquals(AmbientColor.PURPLE, it.color) }
    }

    @Test
    fun `plays every step then restores the original light`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()

        player.play(LightRhythm.VEHICLE)
        advanceUntilIdle()

        val expected = LightRhythm.VEHICLE.steps().map { LightValue(it.brightness, it.color.rgb) }
        assertEquals(expected, controller.applied.dropLast(1))
        assertEquals(LightValue(40, AmbientColor.BLUE.rgb), controller.applied.last())
    }

    @Test
    fun `steps are held for their full duration`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()

        player.play(LightRhythm.VEHICLE)
        runCurrent()
        assertEquals(1, controller.applied.size)

        advanceTimeBy(499)
        runCurrent()
        assertEquals(1, controller.applied.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, controller.applied.size)
    }

    @Test
    fun `a higher priority rhythm cuts in and the light still restores to the original`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()

        player.play(LightRhythm.VEHICLE)
        advanceTimeBy(100)
        runCurrent()
        player.play(LightRhythm.SIREN)
        advanceUntilIdle()

        assertEquals(LightValue(40, AmbientColor.BLUE.rgb), controller.current)
        val restores = controller.applied.count { it == LightValue(40, AmbientColor.BLUE.rgb) }
        assertEquals(1, restores)
    }

    @Test
    fun `a lower priority rhythm waits its turn by being dropped`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()

        player.play(LightRhythm.SIREN)
        advanceTimeBy(100)
        runCurrent()
        player.play(LightRhythm.VEHICLE)
        advanceUntilIdle()

        val colors = controller.applied.dropLast(1).map { it.color }.toSet()
        assertEquals(setOf(AmbientColor.WHITE.rgb), colors)
    }

    @Test
    fun `a car with no readable light is left untouched`() = runTest {
        val controller = FakeController(current = null)
        val player = player(controller)
        player.start()
        runCurrent()

        player.play(LightRhythm.HORN)
        advanceUntilIdle()

        assertEquals(emptyList<LightValue>(), controller.applied)
    }

    @Test
    fun `a light service failure neither crashes nor blocks later rhythms`() = runTest {
        val errors = mutableListOf<Throwable>()
        var failing = true
        val controller = object : AmbientLightController {
            val applied = mutableListOf<LightValue>()
            override suspend fun read() = LightValue(40, AmbientColor.BLUE.rgb)
            override fun apply(value: LightValue) {
                if (failing) error("car service died")
                applied += value
            }
        }
        val player = AmbientLightPlayer(controller, this, onError = { errors += it }) { testScheduler.currentTime }
        player.start()
        runCurrent()

        player.play(LightRhythm.SIREN)
        advanceUntilIdle()
        assertEquals(1, errors.size)

        failing = false
        player.play(LightRhythm.VEHICLE)
        advanceUntilIdle()

        assertEquals(LightValue(40, AmbientColor.BLUE.rgb), controller.applied.last())
    }

    @Test
    fun `a failing read counts as having no light to change`() = runTest {
        val controller = object : AmbientLightController {
            var applied = 0
            override suspend fun read(): LightValue? = error("car service died")
            override fun apply(value: LightValue) {
                applied++
            }
        }
        val player = AmbientLightPlayer(controller, this, onError = {}) { testScheduler.currentTime }
        player.start()
        runCurrent()

        player.play(LightRhythm.HORN)
        advanceUntilIdle()

        assertEquals(0, controller.applied)
    }

    @Test
    fun `the driver's new light setting becomes the value to restore`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()
        player.play(LightRhythm.HORN)
        advanceUntilIdle()

        advanceTimeBy(AmbientLightPlayer.SETTLE_MS + 1)
        controller.current = LightValue(80, AmbientColor.PURPLE.rgb)
        player.play(LightRhythm.HORN)
        advanceUntilIdle()

        assertEquals(LightValue(80, AmbientColor.PURPLE.rgb), controller.current)
    }

    @Test
    fun `reading right after a restore does not mistake a rhythm value for the driver's setting`() = runTest {
        val controller = FakeController()
        val player = player(controller)
        player.start()
        runCurrent()
        player.play(LightRhythm.HORN)
        advanceUntilIdle()
        val readsAfterFirst = controller.reads

        controller.current = LightValue(100, AmbientColor.WHITE.rgb) // the restore has not landed yet
        player.play(LightRhythm.HORN)
        advanceUntilIdle()

        assertEquals(readsAfterFirst, controller.reads)
        assertEquals(LightValue(40, AmbientColor.BLUE.rgb), controller.current)
    }
}
