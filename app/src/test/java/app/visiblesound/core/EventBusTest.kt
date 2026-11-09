package app.visiblesound.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBusTest {
    @Test
    fun `delivers a posted event to a subscriber`() = runTest(UnconfinedTestDispatcher()) {
        val bus = EventBus()
        var received: AppEvent? = null
        val job = launch { received = bus.events.first() }

        bus.post(AppEvent.Gear(GearPos.D, at = 1L))
        job.join()

        assertNotNull(received)
        assertEquals(AppEvent.Gear(GearPos.D, at = 1L), received)
    }
}
