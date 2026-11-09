package app.visiblesound.output

import app.visiblesound.core.LightRhythm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [color] is a raw RGB value: what the driver had set may not be one of the app's presets. */
data class LightValue(val brightness: Int, val color: Int)

interface AmbientLightController {
    /** Returns null when this car has no readable ambient light. */
    suspend fun read(): LightValue?
    fun apply(value: LightValue)
}

/**
 * Plays rhythms on the ambient light and puts the driver's own setting back afterwards. A failing
 * light must never take the alert down with it. Call [start] and [play] from one thread, and
 * [scope] must run on that same thread.
 */
class AmbientLightPlayer(
    private val controller: AmbientLightController,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = {},
    private val clock: () -> Long,
) {
    private var original: LightValue? = null
    private var playing: LightRhythm? = null
    private var job: Job? = null
    private var restoredAt: Long? = null

    fun start() {
        scope.launch { refreshOriginal() }
    }

    fun play(rhythm: LightRhythm) {
        val current = playing
        if (current != null && rhythm.priority < current.priority) return

        job?.cancel()
        playing = rhythm
        val wasIdle = current == null
        job = scope.launch {
            try {
                if (wasIdle && hasSettled()) refreshOriginal()
                for (step in rhythm.steps()) {
                    if (original != null) controller.apply(LightValue(step.brightness, step.color.rgb))
                    delay(step.durationMs)
                }
                original?.let {
                    controller.apply(it)
                    restoredAt = clock()
                }
                playing = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                playing = null
            }
        }
    }

    /** A set request completes before the car applies it, so a read too soon can see a rhythm value. */
    private fun hasSettled(): Boolean {
        val restored = restoredAt ?: return true
        return clock() - restored >= SETTLE_MS
    }

    private suspend fun refreshOriginal() {
        try {
            controller.read()?.let { original = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    companion object {
        const val SETTLE_MS = 2_000L
    }
}
