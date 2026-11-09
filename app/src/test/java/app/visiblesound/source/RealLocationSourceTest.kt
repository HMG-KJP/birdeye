package app.visiblesound.source

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import app.visiblesound.core.AppEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLocationSourceTest {
    private val location = mockk<FusedLocation>(relaxed = true)
    private val listener = slot<FusedLocationListener>()
    private val events = mutableListOf<AppEvent>()
    private val errors = mutableListOf<Exception>()
    private val source = RealLocationSource(location, clock = { 5L }, onError = { errors += it })

    private fun data(lat: Double, lng: Double) = LocationData(lat, lng, 0.0, 0f, 1f, 1L)

    @Test
    fun `position updates become location events without a place name`() {
        every { location.isSupported() } returns true
        every { location.registerFusedLocationCallback(capture(listener)) } returns Unit

        source.start { events += it }
        listener.captured.onChanged(data(37.5, 127.0))

        assertEquals(AppEvent.Location(37.5, 127.0, null, at = 5), events.single())
    }

    @Test
    fun `a car without location support is left alone`() {
        every { location.isSupported() } returns false

        source.start { events += it }

        verify(exactly = 0) { location.registerFusedLocationCallback(any()) }
    }

    @Test
    fun `errors are reported`() {
        every { location.isSupported() } returns true
        every { location.registerFusedLocationCallback(capture(listener)) } returns Unit

        source.start { events += it }
        listener.captured.onError(IllegalStateException("no fix"))

        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `stop unregisters the same listener and releases the service`() {
        every { location.isSupported() } returns true
        every { location.registerFusedLocationCallback(capture(listener)) } returns Unit
        source.start { events += it }

        source.stop()

        verify { location.unregisterFusedLocationCallback(listener.captured) }
        verify { location.release() }
    }
}
