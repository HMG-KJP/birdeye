package app.birdeye.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

fun interface Outputs {
    fun handle(effect: Effect)
}

/** The single source of truth for the UI: events go in, one [UiState] comes out. */
class AppState(
    private val bus: EventBus,
    private val outputs: Outputs,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val engine: RuleEngine = RuleEngine(),
    private val onOutputError: (Throwable) -> Unit = {},
) {
    private var engineState = EngineState()
    private val mutableState = MutableStateFlow(engineState.ui)
    val state: StateFlow<UiState> = mutableState

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect(::apply)
        }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                bus.post(AppEvent.Tick(clock()))
            }
        }
    }

    private fun apply(event: AppEvent) {
        val result = engine.reduce(engineState, event)
        engineState = result.state
        mutableState.value = result.state.ui
        result.effects.forEach { effect ->
            try {
                outputs.handle(effect)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onOutputError(e)
            }
        }
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
