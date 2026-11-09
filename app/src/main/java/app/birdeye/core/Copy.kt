package app.birdeye.core

/** All driver-facing wording in one place. */
object Copy {
    const val WINDOW_INTRO = "저는 청각장애가 있어요. 말씀하시면 화면에 글자로 보입니다. 천천히 말씀해 주세요."
    val DEFAULT_REPLIES = listOf("응, 알겠어", "조금 이따 세울게", "다시 한 번 말해 줘", "잠깐만 기다려 줘")
    val WINDOW_REPLIES = listOf("네", "한 번 더 말씀해 주세요", "어디에 세우면 될까요?")

    const val LISTENING_CHANNEL = "듣는 중"
    const val SOUNDS_CHANNEL = "사이렌과 경적"
    const val REPLY_SENT = "답장을 보냈어요"
    const val REPLY_FAILED = "답장을 말하지 못했어요"
    const val REPLY_INTERRUPTED = "답장이 중간에 끊겼어요"
    const val HORN_DURING_SIREN = "경적 · 사이렌과 함께 들렸어요"
    const val TURN_SIGNAL = "방향지시등이 켜져 있어요"
    const val TURN_SIGNAL_LOG = "방향지시등이 오래 켜져 있었어요"
    const val DOOR_OPEN = "문이 열린 채 주행 중이에요"
    const val DOOR_OPEN_LOG = "문이 열린 채 주행했어요"
    const val VEHICLE_EYEBROW = "차량 소리"
    const val SWERVE = "핸들을 급하게 꺾었어요"
    const val SWERVE_GUIDANCE = "주변 차량을 확인하세요"
    const val SWERVE_LOG = "핸들을 급하게 꺾었어요"
    const val CHECK_AROUND = "주변을 확인해 주세요"
    const val UNKNOWN_SIDE = "방향 모름"
    const val IDLE_HEADLINE = "사이렌과 경적은 들리지 않아요"
    const val IDLE_DETAIL = "소리가 들리면 바로 알려 드릴게요"
    const val MUFFLED_IDLE = "소리를 놓칠 수 있어요"
    const val MUFFLED_IDLE_DETAIL = "사이렌과 경적이 작게 들릴 수 있으니 주변을 더 살펴 주세요"
    const val CAPTIONS_HELD = "방향지시등이 켜져 있어 자막을 잠시 줄였어요"
    const val CAPTIONS_HELD_DETAIL = "사이렌과 경적은 그대로 바로 알려 드려요"

    private fun sideName(side: Direction) = when (side) {
        Direction.FRONT -> "앞쪽"
        Direction.REAR -> "뒤쪽"
        Direction.LEFT -> "왼쪽"
        Direction.RIGHT -> "오른쪽"
    }

    fun trafficEyebrow(side: Direction) = "주변 차량 경고 · ${sideName(side)}"

    fun trafficHeadline(kind: TrafficKind, side: Direction) = when (kind) {
        TrafficKind.CUT_IN -> "${sideName(side)}에서 차가 끼어들어요"
        TrafficKind.BLIND_SPOT -> "${sideName(side)} 사각지대에 차가 있어요"
    }

    fun trafficGuidance(kind: TrafficKind) = when (kind) {
        TrafficKind.CUT_IN -> "속도를 줄이고 앞차와 거리를 두세요"
        TrafficKind.BLIND_SPOT -> "차로 변경을 잠깐 미뤄 주세요"
    }

    fun trafficLog(kind: TrafficKind, side: Direction) = when (kind) {
        TrafficKind.CUT_IN -> "${sideName(side)}에서 끼어들기"
        TrafficKind.BLIND_SPOT -> "${sideName(side)} 사각지대에 차량"
    }

    fun conditionText(condition: DrivingCondition): String = when (condition) {
        DrivingCondition.THUNDER -> "천둥소리가 커서 소리를 놓칠 수 있어요"
        DrivingCondition.RAIN -> "빗소리가 커서 소리를 놓칠 수 있어요"
        DrivingCondition.WIPER -> "와이퍼 소리로 소리를 놓칠 수 있어요"
        DrivingCondition.FOG -> "안개등이 켜져 있어요. 시야가 좁을 수 있어요"
        DrivingCondition.WIND -> "바람 소리가 커서 소리를 놓칠 수 있어요"
    }

    /** Warning lamps come from public Safety and Seat APIs and are named here. */
    fun lampDescription(name: String): String? = when (name) {
        "안전벨트" -> "안전벨트를 매 주세요"
        "워셔액" -> "워셔액이 부족해요"
        "엔진오일" -> "엔진오일을 확인하세요"
        "브레이크액" -> "브레이크액을 확인하세요"
        else -> null
    }

    /** Says what was heard and asks only for a look around: naming a vehicle or a maneuver could mislead. */
    fun soundEyebrow(kind: SoundKind, direction: Direction?): String {
        val base = when (kind) {
            SoundKind.SIREN -> "사이렌 감지"
            SoundKind.HORN -> "경적 감지"
        }
        return "$base · ${direction?.let(::sideName) ?: UNKNOWN_SIDE}"
    }

    fun soundHeadline(kind: SoundKind, direction: Direction?, sure: Boolean = true): String {
        val where = direction?.let { "${sideName(it)}에서 " }.orEmpty()
        return where + when (kind) {
            SoundKind.SIREN -> if (sure) "사이렌이 들려요" else "사이렌 같은 소리가 들려요"
            SoundKind.HORN -> if (sure) "경적이 울렸어요" else "경적 같은 소리가 들렸어요"
        }
    }

    fun soundGuidance(direction: Direction?): String =
        if (direction == null) "어느 쪽인지는 알 수 없어요. $CHECK_AROUND" else CHECK_AROUND

    fun soundLog(kind: SoundKind, direction: Direction?, seconds: Long): String {
        val where = direction?.let { "${sideName(it)}에서 " }.orEmpty()
        val what = when (kind) {
            SoundKind.HORN -> "경적"
            SoundKind.SIREN -> "사이렌"
        }
        return "$where$what · 약 ${seconds}초"
    }

    /** How well the app can hear right now, in the words the driver sees on the map pane. */
    fun hearingStatus(listening: Listening, condition: DrivingCondition?): String = when (listening) {
        Listening.NO_PERMISSION, Listening.MIC_ERROR -> "꺼짐"
        Listening.NOT_STARTED -> "준비 중"
        Listening.ACTIVE, Listening.DEMO ->
            if (condition?.muffles == true) "약함 · ${noiseName(condition)}" else "좋음"
    }

    private fun noiseName(condition: DrivingCondition) = when (condition) {
        DrivingCondition.THUNDER -> "천둥소리"
        DrivingCondition.RAIN -> "빗소리"
        DrivingCondition.WIPER -> "와이퍼 소리"
        DrivingCondition.WIND -> "바람 소리"
        DrivingCondition.FOG -> "안개"
    }

    fun listeningSubtitle(listening: Listening): String = when (listening) {
        Listening.ACTIVE -> "주변 소리를 듣고 있어요"
        Listening.NOT_STARTED -> "준비하고 있어요"
        Listening.NO_PERMISSION, Listening.MIC_ERROR -> "마이크 꺼짐"
        Listening.DEMO -> "시연 중이에요"
    }

    /** One line explaining a rhythm, shown next to its dots on the main screen. */
    fun rhythmLegend(rhythm: LightRhythm): String = when (rhythm) {
        LightRhythm.SIREN -> "빠른 3회 점멸 = 사이렌"
        LightRhythm.TRAFFIC -> "빠른 2회 점멸 = 주변 차량"
        LightRhythm.HORN -> "강한 1회 = 경적"
        LightRhythm.PASSENGER -> "부드러운 1회 = 승객의 말"
        LightRhythm.OUTSIDE -> "부드러운 2회 = 바깥 사람 말"
        LightRhythm.VEHICLE -> "느린 2회 = 차량 소리"
    }

    fun rhythmDots(rhythm: LightRhythm): Int = when (rhythm) {
        LightRhythm.SIREN -> 3
        LightRhythm.HORN, LightRhythm.PASSENGER -> 1
        LightRhythm.OUTSIDE, LightRhythm.VEHICLE, LightRhythm.TRAFFIC -> 2
    }
}

val Seat.label: String
    get() = when (this) {
        Seat.DRIVER -> "운전석"
        Seat.PASSENGER -> "조수석"
        Seat.REAR_LEFT -> "뒷좌석 왼쪽"
        Seat.REAR_RIGHT -> "뒷좌석 오른쪽"
    }
