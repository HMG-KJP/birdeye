package app.visiblesound.core

/** The cabin's preset colors, with the RGB values the car uses for them. */
enum class AmbientColor(val rgb: Int) {
    CYAN(0x00E5FF),
    BLUE(0x2979FF),
    PURPLE(0xAA00FF),
    WHITE(0xFFFFFF),
}

data class LightStep(val brightness: Int, val color: AmbientColor, val durationMs: Long)

/** The light language: ambient colors are limited, so the rhythm is what tells sounds apart. */
enum class LightRhythm(val priority: Int) {
    SIREN(priority = 4),
    HORN(priority = 3),
    OUTSIDE(priority = 2),
    PASSENGER(priority = 1),
    VEHICLE(priority = 0);

    fun steps(): List<LightStep> = when (this) {
        SIREN -> sirenSet() + LightStep(OFF, AmbientColor.WHITE, SET_GAP_MS) + sirenSet()
        HORN -> listOf(LightStep(FULL, AmbientColor.WHITE, 400))
        PASSENGER -> softFade(AmbientColor.CYAN)
        OUTSIDE -> softFade(AmbientColor.BLUE) + softFade(AmbientColor.BLUE)
        VEHICLE -> List(2) {
            listOf(LightStep(FULL, AmbientColor.PURPLE, 500), LightStep(OFF, AmbientColor.PURPLE, 500))
        }.flatten()
    }

    private fun sirenSet() = List(3) {
        listOf(LightStep(FULL, AmbientColor.WHITE, 150), LightStep(OFF, AmbientColor.WHITE, 150))
    }.flatten()

    private fun softFade(color: AmbientColor) =
        listOf(30, 60, 90, 90, 60, 30).map { LightStep(it, color, 100) }

    private companion object {
        const val FULL = 100
        const val OFF = 0
        const val SET_GAP_MS = 300L
    }
}
