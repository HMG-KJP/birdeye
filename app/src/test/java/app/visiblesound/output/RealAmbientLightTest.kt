package app.visiblesound.output

import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import app.visiblesound.core.AmbientColor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealAmbientLightTest {
    private val light = mockk<Light>(relaxed = true)
    private val front = SeatArea(SeatValue.ROW_1_LEFT)
    private val rear = SeatArea(SeatValue.ROW_2_LEFT)
    private val errors = mutableListOf<Exception>()
    private val ambient = RealAmbientLight(light) { errors += it }

    private fun capabilities(access: AccessType, areas: List<SeatArea>) {
        every {
            light.checkAmbientLightBrightnessColorCapabilities(any(), any())
        } answers {
            firstArg<(CapabilityConfig, List<SeatArea>) -> Unit>()(CapabilityConfig(access), areas)
        }
    }

    @Before
    fun setUp() {
        capabilities(AccessType.READ_WRITE, listOf(front, rear))
        every { light.getAmbientLightBrightness(front, any(), any()) } answers { secondArg<(Int) -> Unit>()(70) }
        every { light.getAmbientLightBrightnessColor(front, any(), any()) } answers { secondArg<(Int) -> Unit>()(0x2979FF) }
    }

    @Test
    fun `the four cabin colors have the car's values`() {
        assertEquals(0x00E5FF, AmbientColor.CYAN.rgb)
        assertEquals(0x2979FF, AmbientColor.BLUE.rgb)
        assertEquals(0xAA00FF, AmbientColor.PURPLE.rgb)
        assertEquals(0xFFFFFF, AmbientColor.WHITE.rgb)
    }

    @Test
    fun `reading goes through the capability check and returns the current light`() = runTest {
        assertEquals(LightValue(70, 0x2979FF), ambient.read())
    }

    @Test
    fun `a car with no ambient light reads as nothing`() = runTest {
        capabilities(AccessType.READ_WRITE, emptyList())

        assertNull(ambient.read())
    }

    @Test
    fun `read only access reads as nothing because the app could not restore what it changes`() = runTest {
        capabilities(AccessType.READ, listOf(front))

        assertNull(ambient.read())
    }

    @Test
    fun `an unsupported property is reported and reads as nothing`() = runTest {
        every { light.checkAmbientLightBrightnessColorCapabilities(any(), any()) } answers {
            secondArg<(Exception) -> Unit>()(IllegalStateException("unsupported"))
        }

        assertNull(ambient.read())
        assertEquals(1, errors.size)
    }

    @Test
    fun `a car service that never answers does not hang the read`() = runTest {
        every { light.getAmbientLightBrightness(any(), any(), any()) } returns Unit

        assertNull(ambient.read())
        assertTrue(testScheduler.currentTime >= RealAmbientLight.READ_TIMEOUT_MS)
    }

    @Test
    fun `applying sets brightness and color on every area`() = runTest {
        ambient.read()

        ambient.apply(LightValue(100, AmbientColor.WHITE.rgb))

        verify { light.setAmbientLightBrightness(front, 100, any(), any()) }
        verify { light.setAmbientLightBrightness(rear, 100, any(), any()) }
        verify { light.setAmbientLightBrightnessColor(front, 0xFFFFFF, any(), any()) }
        verify { light.setAmbientLightBrightnessColor(rear, 0xFFFFFF, any(), any()) }
    }

    @Test
    fun `an unchanged color is not sent again so fast rhythms stay light on the car bus`() = runTest {
        ambient.read()

        ambient.apply(LightValue(100, AmbientColor.WHITE.rgb))
        ambient.apply(LightValue(0, AmbientColor.WHITE.rgb))

        verify(exactly = 1) { light.setAmbientLightBrightnessColor(front, 0xFFFFFF, any(), any()) }
        verify(exactly = 1) { light.setAmbientLightBrightness(front, 0, any(), any()) }
    }

    @Test
    fun `the color the driver had comes back on restore even though the app never set it`() = runTest {
        ambient.read()

        ambient.apply(LightValue(100, AmbientColor.WHITE.rgb))
        ambient.apply(LightValue(70, 0x2979FF))

        verify { light.setAmbientLightBrightnessColor(front, 0x2979FF, any(), any()) }
    }

    @Test
    fun `a failing set is reported`() = runTest {
        every { light.setAmbientLightBrightness(any(), any(), any(), any()) } answers {
            arg<(Exception) -> Unit>(3)(IllegalStateException("no permission"))
        }
        ambient.read()

        ambient.apply(LightValue(100, AmbientColor.WHITE.rgb))

        assertTrue(errors.isNotEmpty())
    }
}
