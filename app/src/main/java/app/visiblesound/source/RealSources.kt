package app.visiblesound.source

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import app.visiblesound.core.AppEvent

/** Position from the Pleos SDK, used for the recap's places and trip distance. */
class RealLocationSource(
    private val location: FusedLocation,
    private val clock: () -> Long,
    private val onError: (Exception) -> Unit,
) : EventSource {
    private var listener: FusedLocationListener? = null

    override fun start(sink: (AppEvent) -> Unit) {
        location.initialize()
        if (!location.isSupported()) return
        val active = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                location ?: return
                sink(AppEvent.Location(location.latitude, location.longitude, null, clock()))
            }

            override fun onError(exception: Exception) = this@RealLocationSource.onError(exception)
        }
        listener = active
        location.registerFusedLocationCallback(active)
    }

    override fun stop() {
        listener?.let { location.unregisterFusedLocationCallback(it) }
        listener = null
        location.release()
    }
}

/** The car's own driving restrictions; the Pleos SDK has no equivalent. */
class UxRestrictionSource(
    private val context: Context,
    private val clock: () -> Long,
    private val onError: (Throwable) -> Unit,
) : EventSource {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    override fun start(sink: (AppEvent) -> Unit) {
        try {
            val connected = Car.createCar(context) ?: return
            val restrictions = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            car = connected
            manager = restrictions
            sink(AppEvent.UxRestricted(restrictions.currentCarUxRestrictions.isRequiresDistractionOptimization, clock()))
            restrictions.registerListener { sink(AppEvent.UxRestricted(it.isRequiresDistractionOptimization, clock())) }
        } catch (e: LinkageError) {
            onError(e)
        } catch (e: Exception) {
            onError(e)
        }
    }

    override fun stop() {
        manager?.unregisterListener()
        car?.disconnect()
        manager = null
        car = null
    }
}
