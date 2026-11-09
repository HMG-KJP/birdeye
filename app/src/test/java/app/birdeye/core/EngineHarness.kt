package app.birdeye.core

/** Feeds events into a [RuleEngine] and records everything it produces. */
class EngineHarness(config: RuleConfig = RuleConfig()) {
    private val engine = RuleEngine(config)
    var state = EngineState()
        private set
    val effects = mutableListOf<Effect>()

    init {
        // Tests start from a parked car; a car whose gear is unknown has its own tests.
        send(AppEvent.Gear(GearPos.P, at = 0L))
    }

    val ui: UiState get() = state.ui

    fun send(event: AppEvent): EngineHarness {
        val result = engine.reduce(state, event)
        state = result.state
        effects += result.effects
        return this
    }

    fun tick(at: Long) = send(AppEvent.Tick(at))

    fun gear(value: GearPos, at: Long = 0L) = send(AppEvent.Gear(value, at))

    /** Sends [count] classifier frames, [stepMs] apart, starting at [from]. */
    fun frames(
        kind: SoundKind,
        from: Long,
        count: Int = 3,
        confidence: Float = 0.8f,
        direction: Direction? = Direction.REAR,
        stepMs: Long = 500L,
    ) = apply {
        repeat(count) { i ->
            send(AppEvent.Sound(kind, direction, confidence, at = from + i * stepMs))
        }
    }

    fun lightEffects(): List<LightRhythm> = effects.filterIsInstance<Effect.Light>().map { it.rhythm }

    fun spoken(): List<String> = effects.filterIsInstance<Effect.Speak>().map { it.text }
}
