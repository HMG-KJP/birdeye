package app.visiblesound.source

import app.visiblesound.core.AppEvent

/**
 * Cut-in and blind spot warnings from the car's own sensors. The car has this data (for example
 * BLIND_SPOT_WARNING_STATE and FORWARD_COLLISION_WARNING_STATE), but the Pleos Platform SDK that
 * carries it is only for approved privileged apps. Until it is opened to this app, only the demo
 * script raises these warnings; this class is where the real connection goes.
 */
class UnavailableTrafficSource : EventSource {
    override fun start(sink: (AppEvent) -> Unit) = Unit

    override fun stop() = Unit
}
