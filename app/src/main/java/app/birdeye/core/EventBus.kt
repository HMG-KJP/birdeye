package app.birdeye.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class EventBus {
    private val flow = MutableSharedFlow<AppEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AppEvent> = flow

    /** Safe to call from any thread, including SDK binder callbacks. */
    fun post(event: AppEvent) {
        flow.tryEmit(event)
    }
}
