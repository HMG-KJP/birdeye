package app.visiblesound.source

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import app.visiblesound.core.AppEvent
import app.visiblesound.core.Seat
import java.util.Locale

interface SttCallbacks {
    fun onText(text: String, isFinal: Boolean)
    fun onFailed(reason: String) = Unit
}

interface SttEngine : SpeechFeed {
    fun initialize(callbacks: SttCallbacks)
    fun release()
}

/** Turns recognized text into caption events, deciding who said it. */
class SpeechSession(
    private val engine: SttEngine,
    private val occupied: () -> Set<Seat>,
    private val outsideMode: () -> Boolean,
    private val clock: () -> Long,
    private val sink: (AppEvent) -> Unit,
    private val onFailed: (String) -> Unit = {},
) : SpeechFeed {
    fun start() {
        engine.initialize(object : SttCallbacks {
            override fun onText(text: String, isFinal: Boolean) {
                if (text.isBlank()) return
                sink(SpeakerLocator.attribute(text, isFinal, occupied(), outsideMode(), clock()))
            }

            override fun onFailed(reason: String) = this@SpeechSession.onFailed(reason)
        })
    }

    override fun send(pcm: ByteArray) = engine.send(pcm)

    override fun complete() = engine.complete()

    fun stop() = engine.release()
}

/**
 * Pleos speech recognition fed with the app's own audio. Audio injection only exists in
 * ON_DEVICE mode, which is also the mode that needs no app registration.
 */
class PleosSttEngine(context: Context) : SttEngine {
    private val stt = SpeechToText(context.applicationContext, Mode.ON_DEVICE)

    @Volatile
    private var ready = false

    override fun initialize(callbacks: SttCallbacks) {
        stt.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                // The SDK throws if a listener is added before initialization has succeeded.
                stt.addListener(object : ResultListener {
                    override fun onUpdated(stt: String, completed: Boolean) = callbacks.onText(stt, completed)
                    override fun onUpdatedEpdData(on: Long, off: Long) = Unit
                    override fun onStartedRecognition() = Unit
                    override fun onEndedRecognition() = Unit
                    override fun onError(errorType: ErrorType) = callbacks.onFailed(errorType.name)
                    override fun onReady() = Unit
                    override fun onUpdatedRms(rms: Double) = Unit
                })
                stt.setLocale(Locale.KOREA) // the engine needs language and country: "ko" alone is rejected
                ready = true
            }

            override fun onFailure(errorType: ErrorType) = callbacks.onFailed(errorType.name)
        })
    }

    override fun send(pcm: ByteArray) {
        if (ready) stt.sendAudio(pcm)
    }

    override fun complete() {
        if (ready) stt.completeAudioSend()
    }

    override fun release() = stt.release()
}
