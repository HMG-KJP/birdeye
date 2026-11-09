package app.visiblesound.output

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.visiblesound.core.AppEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

interface TtsCallbacks {
    fun onInitialized(success: Boolean)
    fun onStarted()
    fun onFinished()
}

interface TtsEngine {
    fun initialize(callbacks: TtsCallbacks)
    fun speak(text: String)
    fun stop()
    fun release()
}

interface TtsAudioFocus {
    /** False when the car refuses, as it may during a call or an emergency. */
    fun request(onLost: () -> Unit): Boolean
    fun abandon()
}

/**
 * Says replies through the car and tells the app when it is talking, so the microphone does not
 * caption the car's own voice. Every path ends in a "finished" event, because a stuck "playing"
 * state would mute the captions and lock the reply buttons.
 */
class SpeechOutput(
    private val engine: TtsEngine,
    private val focus: TtsAudioFocus,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val sink: (AppEvent.Tts) -> Unit,
    private val onError: (Throwable) -> Unit = {},
) {
    private var ready: Boolean? = null
    private var pending: String? = null
    private var watchdog: Job? = null
    private var active = false

    private val callbacks = object : TtsCallbacks {
        override fun onInitialized(success: Boolean) {
            ready = success
            val text = pending
            pending = null
            when {
                text == null -> Unit
                success -> play(text)
                else -> sink(AppEvent.Tts(playing = false, clock()))
            }
        }

        override fun onStarted() {
            // Callbacks can cross: a start reported after the end must not mark the car as talking.
            if (active) sink(AppEvent.Tts(playing = true, clock()))
        }

        override fun onFinished() {
            // Stopping the engine after a focus loss reports here too, but the app already knows.
            if (active) finish()
        }
    }

    fun start() {
        try {
            engine.initialize(callbacks)
        } catch (e: Exception) {
            onError(e)
            ready = false
        }
    }

    fun speak(text: String) {
        when (ready) {
            true -> play(text)
            false -> sink(AppEvent.Tts(playing = false, clock()))
            null -> pending = text
        }
    }

    fun stop() {
        watchdog?.cancel()
        focus.abandon()
        engine.release()
    }

    private fun play(text: String) {
        if (!focus.request(::onFocusLost)) {
            sink(AppEvent.Tts(playing = false, clock()))
            return
        }
        active = true
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(MAX_PLAYBACK_MS)
            finish()
        }
        try {
            engine.speak(text)
        } catch (e: Exception) {
            onError(e)
            finish()
        }
    }

    /** A call or a voice command took the car's audio: the rest of the sentence would not be heard. */
    private fun onFocusLost() {
        if (!active) return
        try {
            engine.stop()
        } catch (e: Exception) {
            onError(e)
        }
        finish(interrupted = true)
    }

    private fun finish(interrupted: Boolean = false) {
        active = false
        watchdog?.cancel()
        watchdog = null
        focus.abandon()
        sink(AppEvent.Tts(playing = false, clock(), interrupted))
    }

    companion object {
        const val MAX_PLAYBACK_MS = 30_000L
    }
}

/** Delivers SDK callbacks on the main thread, because [SpeechOutput] keeps its state there. */
class PleosTtsEngine(context: Context) : TtsEngine {
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val main = Handler(Looper.getMainLooper())

    /** The SDK throws if a listener is added before initialization has succeeded. */
    override fun initialize(callbacks: TtsCallbacks) {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.addEventListener(object : EventListener {
                    override fun onReady() = Unit
                    override fun onStart() {
                        main.post { callbacks.onStarted() }
                    }

                    override fun onDone() {
                        main.post { callbacks.onFinished() }
                    }

                    override fun onStop() {
                        main.post { callbacks.onFinished() }
                    }

                    override fun onError(errorType: ErrorType) {
                        main.post { callbacks.onFinished() }
                    }

                    override fun onUpdatedRms(rms: Double) = Unit
                })
                tts.setLocale(Locale.KOREA)
                main.post { callbacks.onInitialized(true) }
            }

            override fun onFailure(errorType: ErrorType) {
                main.post { callbacks.onInitialized(false) }
            }
        })
    }

    override fun speak(text: String) = tts.speak(text, true)

    override fun stop() = tts.stop()

    override fun release() = tts.release()
}

/** Lowers other audio instead of stopping it while a reply plays. */
class DuckingAudioFocus(context: Context) : TtsAudioFocus {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var onLost: () -> Unit = {}
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener({ change ->
            Log.i(TAG, "audio focus changed: $change")
            // Others lower their volume for a duck; only these two mean the reply can't be heard.
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLost()
        }, Handler(Looper.getMainLooper()))
        .build()

    override fun request(onLost: () -> Unit): Boolean {
        this.onLost = onLost
        val result = audio.requestAudioFocus(request)
        Log.i(TAG, "audio focus requested: usage=ASSISTANT gain=TRANSIENT_MAY_DUCK result=$result")
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        audio.abandonAudioFocusRequest(request)
    }

    private companion object {
        const val TAG = "VisibleSound"
    }
}
