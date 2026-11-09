package app.birdeye.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class F7ConditionRulesTest {
    private fun driving() = EngineHarness().gear(GearPos.D)

    private fun EngineHarness.heard(kind: NoiseKind, from: Long, frames: Int = 4, confidence: Float = 0.6f) = apply {
        repeat(frames) { send(AppEvent.AmbientNoise(kind, confidence, at = from + it * 250L)) }
    }

    @Test
    fun `running wipers mean sounds may be muffled until they stop`() {
        val h = driving().send(AppEvent.Wiper(on = true, at = 1))
        assertEquals(DrivingCondition.WIPER, h.ui.condition)

        h.send(AppEvent.Wiper(on = false, at = 2))
        assertNull(h.ui.condition)
    }

    @Test
    fun `fog lights count while either one is on`() {
        val h = driving()
            .send(AppEvent.FogLight(FogPosition.FRONT, on = true, at = 1))
            .send(AppEvent.FogLight(FogPosition.REAR, on = true, at = 2))
        assertEquals(DrivingCondition.FOG, h.ui.condition)

        h.send(AppEvent.FogLight(FogPosition.FRONT, on = false, at = 3))
        assertEquals(DrivingCondition.FOG, h.ui.condition)

        h.send(AppEvent.FogLight(FogPosition.REAR, on = false, at = 4))
        assertNull(h.ui.condition)
    }

    @Test
    fun `rain has to be heard for a moment before it counts`() {
        val h = driving().heard(NoiseKind.RAIN, from = 0, frames = 3)
        assertNull(h.ui.condition)

        h.send(AppEvent.AmbientNoise(NoiseKind.RAIN, 0.6f, at = 750))
        assertEquals(DrivingCondition.RAIN, h.ui.condition)
    }

    @Test
    fun `a faint rain score does not count`() {
        assertNull(driving().heard(NoiseKind.RAIN, from = 0, frames = 20, confidence = 0.1f).ui.condition)
    }

    @Test
    fun `broken up frames do not add up`() {
        val h = driving()
            .heard(NoiseKind.RAIN, from = 0, frames = 3)
            .heard(NoiseKind.RAIN, from = 10_000, frames = 3)

        assertNull(h.ui.condition)
    }

    @Test
    fun `the condition stays for a while after the rain was last heard, then clears`() {
        val h = driving().heard(NoiseKind.RAIN, from = 0)

        assertEquals(DrivingCondition.RAIN, h.tick(30_000).ui.condition)
        assertNull(h.tick(30_750).ui.condition)
    }

    @Test
    fun `thunder comes first, then rain, wipers, fog and wind`() {
        val h = driving()
            .heard(NoiseKind.WIND, from = 0)
        assertEquals(DrivingCondition.WIND, h.ui.condition)

        h.send(AppEvent.FogLight(FogPosition.FRONT, on = true, at = 1_000))
        assertEquals(DrivingCondition.FOG, h.ui.condition)

        h.send(AppEvent.Wiper(on = true, at = 1_100))
        assertEquals(DrivingCondition.WIPER, h.ui.condition)

        h.heard(NoiseKind.RAIN, from = 1_200)
        assertEquals(DrivingCondition.RAIN, h.ui.condition)

        h.heard(NoiseKind.THUNDER, from = 2_500)
        assertEquals(DrivingCondition.THUNDER, h.ui.condition)
    }

    @Test
    fun `a condition stays quiet, with no light and no record`() {
        val h = driving().heard(NoiseKind.RAIN, from = 0).send(AppEvent.Wiper(on = true, at = 1_000))

        assertTrue(h.lightEffects().isEmpty())
        assertTrue(h.ui.trip.entries.isEmpty())
    }

    @Test
    fun `fog does not muffle sound, unlike the others`() {
        assertTrue(DrivingCondition.RAIN.muffles)
        assertTrue(DrivingCondition.WIPER.muffles)
        assertTrue(DrivingCondition.THUNDER.muffles)
        assertTrue(DrivingCondition.WIND.muffles)
        assertEquals(false, DrivingCondition.FOG.muffles)
    }

    @Test
    fun `starting over forgets the weather`() {
        val h = driving().send(AppEvent.Wiper(on = true, at = 1)).send(AppEvent.Reset(at = 2))

        assertNull(h.ui.condition)
    }
}
