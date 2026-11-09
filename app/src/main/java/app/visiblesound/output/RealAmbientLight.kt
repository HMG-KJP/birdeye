package app.visiblesound.output

import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The car's ambient light. The app only changes it when it can also read the value to restore. */
class RealAmbientLight(
    private val light: Light,
    private val onError: (Exception) -> Unit,
) : AmbientLightController {
    private var areas: List<SeatArea> = emptyList()
    private var lastColor: Int? = null

    override suspend fun read(): LightValue? = withTimeoutOrNull(READ_TIMEOUT_MS) {
        val writable = writableAreas()
        areas = writable
        val area = writable.firstOrNull() ?: return@withTimeoutOrNull null
        val brightness = readInt { done, failed -> light.getAmbientLightBrightness(area, done, failed) }
        val color = readInt { done, failed -> light.getAmbientLightBrightnessColor(area, done, failed) }
        lastColor = color
        if (brightness != null && color != null) LightValue(brightness, color) else null
    }

    override fun apply(value: LightValue) {
        areas.forEach { area ->
            light.setAmbientLightBrightness(area, value.brightness, { _, _ -> }, onError)
            if (value.color != lastColor) {
                light.setAmbientLightBrightnessColor(area, value.color, { _, _ -> }, onError)
            }
        }
        lastColor = value.color
    }

    private suspend fun writableAreas(): List<SeatArea> = suspendCancellableCoroutine { continuation ->
        light.checkAmbientLightBrightnessColorCapabilities(
            { config, areas ->
                val writable = config.accessType == AccessType.READ_WRITE
                continuation.resume(if (writable) areas else emptyList())
            },
            {
                onError(it)
                continuation.resume(emptyList())
            },
        )
    }

    private suspend fun readInt(
        request: (done: (Int?) -> Unit, failed: (Exception) -> Unit) -> Unit,
    ): Int? = suspendCancellableCoroutine { continuation ->
        request(
            { continuation.resume(it) },
            {
                onError(it)
                continuation.resume(null)
            },
        )
    }

    companion object {
        const val READ_TIMEOUT_MS = 2_000L
    }
}
