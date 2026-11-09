package app.birdeye.core

/**
 * The short summary beside the map. It repeats what was heard or read from the car and says so when it
 * cannot hear. About other drivers it offers only a possible reason, because deaf drivers report taking
 * other cars' movement for an emergency vehicle; it never states what they intend.
 */
object Briefing {
    private const val MAX_LINES = 3
    private const val NIGHT_FROM = 19
    private const val NIGHT_UNTIL = 6

    fun lines(state: UiState, hour: Int, timeText: (Long) -> String): List<String> {
        val lines = mutableListOf<String>()
        state.notice?.let { lines += it.headline }
        if (state.alert?.kind == AlertKind.SIREN) {
            lines += "사이렌이 이어지고 있어요"
            lines += "주변 차가 움직이면 이 소리 때문일 수 있어요"
        } else {
            surroundings(state, hour)?.let { lines += it }
            lastSound(state, timeText)?.let { lines += it }
        }
        return lines.take(MAX_LINES)
    }

    private fun surroundings(state: UiState, hour: Int): String? {
        val hearing = state.listening == Listening.ACTIVE || state.listening == Listening.DEMO
        val night = hour >= NIGHT_FROM || hour < NIGHT_UNTIL
        val condition = state.condition
        return when {
            !hearing -> null
            condition == DrivingCondition.RAIN && night -> "비 오는 밤이에요. 시야와 소리가 함께 약해요"
            condition != null -> Copy.conditionText(condition)
            night -> "어두운 시간이에요. 소리는 잘 듣고 있어요"
            else -> "소리는 잘 듣고 있어요"
        }
    }

    /** Sounds are only recorded during a trip, so before one starts nothing can be said about them. */
    private fun lastSound(state: UiState, timeText: (Long) -> String): String? {
        val last = state.trip.entries.lastOrNull { it.kind == EntryKind.SIREN || it.kind == EntryKind.HORN }
        return when {
            last != null -> "마지막 소리는 ${timeText(last.at)}, ${last.text}"
            state.trip.active -> "이번 운행에서 들린 사이렌과 경적은 없어요"
            else -> null
        }
    }
}
