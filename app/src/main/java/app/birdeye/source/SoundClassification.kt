package app.birdeye.source

import app.birdeye.core.AppEvent
import app.birdeye.core.NoiseKind
import app.birdeye.core.SoundKind

/**
 * Turns one classifier result into events the rule engine debounces. Which vehicle it is stays
 * unsaid: the model's fine labels confuse an ambulance with a police car, and telling a driver the
 * wrong one is worse than saying "emergency vehicle".
 */
object SoundFrameMapper {
    private val SIREN_LABELS = setOf(
        "Siren",
        "Civil defense siren",
        "Emergency vehicle",
        "Police car (siren)",
        "Ambulance (siren)",
        "Fire engine, fire truck (siren)",
    )
    private val HORN_LABELS = setOf("Vehicle horn, car horn, honking", "Air horn, truck horn")

    private val NOISE_LABELS = mapOf(
        NoiseKind.RAIN to setOf("Rain", "Raindrop", "Rain on surface"),
        NoiseKind.THUNDER to setOf("Thunder", "Thunderstorm"),
        NoiseKind.WIND to setOf("Wind", "Wind noise (microphone)"),
    )

    /** Below this a weather label is just the model's background guess. */
    private const val NOISE_FLOOR = 0.1f

    val LISTENED_LABELS: Set<String> = SIREN_LABELS + HORN_LABELS + NOISE_LABELS.values.flatten()

    fun map(categories: List<Pair<String, Float>>, at: Long): List<AppEvent.Sound> = listOf(
        AppEvent.Sound(SoundKind.SIREN, direction = null, confidence = anyOf(categories, SIREN_LABELS), at = at),
        AppEvent.Sound(SoundKind.HORN, direction = null, confidence = anyOf(categories, HORN_LABELS), at = at),
    )

    fun mapNoise(categories: List<Pair<String, Float>>, at: Long): List<AppEvent.AmbientNoise> =
        NOISE_LABELS.mapNotNull { (kind, labels) ->
            anyOf(categories, labels).takeIf { it >= NOISE_FLOOR }?.let { AppEvent.AmbientNoise(kind, it, at) }
        }

    /** The chance at least one of these labels is right. */
    private fun anyOf(categories: List<Pair<String, Float>>, labels: Set<String>): Float =
        1f - categories.filter { it.first in labels }.fold(1f) { none, (_, score) -> none * (1f - score) }
}

/** Slices a continuous PCM stream into overlapping windows of normalized samples. */
class AudioWindow(private val windowSize: Int, private val hopSize: Int) {
    private val buffer = ShortArray(windowSize)
    private var filled = 0

    fun push(samples: ShortArray, count: Int = samples.size): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var index = 0
        while (index < count) {
            val copied = minOf(windowSize - filled, count - index)
            samples.copyInto(buffer, filled, index, index + copied)
            filled += copied
            index += copied
            if (filled == windowSize) {
                frames += FloatArray(windowSize) { buffer[it] / PCM_SCALE }
                buffer.copyInto(buffer, 0, hopSize, windowSize)
                filled = windowSize - hopSize
            }
        }
        return frames
    }

    private companion object {
        const val PCM_SCALE = 32768f
    }
}

/** 16-bit little endian PCM, the format the speech engine takes. */
fun pcmBytes(samples: ShortArray, count: Int): ByteArray {
    val bytes = ByteArray(count * 2)
    for (i in 0 until count) {
        val sample = samples[i].toInt()
        bytes[i * 2] = sample.toByte()
        bytes[i * 2 + 1] = (sample shr 8).toByte()
    }
    return bytes
}
