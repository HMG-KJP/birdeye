package app.visiblesound.source

import app.visiblesound.core.AppEvent
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficWarningSourceTest {
    @Test
    fun `until the car's data is opened to this app, the real source reports nothing`() {
        val events = mutableListOf<AppEvent>()
        val source = UnavailableTrafficSource()

        source.start { events += it }
        source.stop()

        assertTrue(events.isEmpty())
    }
}
