package app.visiblesound

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.vehicle.Vehicle
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.room.Room
import app.visiblesound.core.AppEvent
import app.visiblesound.core.AppState
import app.visiblesound.core.Effect
import app.visiblesound.core.EventBus
import app.visiblesound.core.Listening
import app.visiblesound.core.Screen
import app.visiblesound.core.Seat
import app.visiblesound.output.AmbientLightPlayer
import app.visiblesound.output.DuckingAudioFocus
import app.visiblesound.output.PleosTtsEngine
import app.visiblesound.output.RealAmbientLight
import app.visiblesound.output.SpeechOutput
import app.visiblesound.output.TripDatabase
import app.visiblesound.output.TripLogger
import app.visiblesound.settings.SettingsStore
import app.visiblesound.source.AudioRecordReader
import app.visiblesound.source.EventSource
import app.visiblesound.source.MicArbiter
import app.visiblesound.source.MicPump
import app.visiblesound.source.demo.DemoScene
import app.visiblesound.source.PcmReader
import app.visiblesound.source.PleosSttEngine
import app.visiblesound.source.RealLocationSource
import app.visiblesound.source.RealVehicleSource
import app.visiblesound.source.SoundFrameMapper
import app.visiblesound.source.SpeechSession
import app.visiblesound.source.UxRestrictionSource
import app.visiblesound.source.VehicleDomains
import app.visiblesound.source.YamnetSoundSource
import app.visiblesound.source.demo.DemoScriptPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class VisibleSoundApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Wires inputs to outputs. Real sources and the demo script are interchangeable here. */
class AppContainer(private val context: Context) {
    val clock: () -> Long = System::currentTimeMillis
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val bus = EventBus()
    val settings = SettingsStore(context)
    private val vehicle by lazy { Vehicle(context).also { it.initialize() } }
    val light by lazy {
        AmbientLightPlayer(
            RealAmbientLight(vehicle.getLight()) { Log.i(TAG, "ambient light: $it") },
            scope,
            onError = { Log.w(TAG, "ambient light failed", it) },
            clock = clock,
        )
    }

    private val speechOutput = SpeechOutput(
        engine = PleosTtsEngine(context),
        focus = DuckingAudioFocus(context),
        scope = scope,
        clock = clock,
        sink = bus::post,
        onError = { Log.w(TAG, "speech output failed", it) },
    )

    private val tripLogger = TripLogger(
        Room.databaseBuilder(context, TripDatabase::class.java, "trips.db").build().tripDao(),
    )

    /** Saves run one at a time, so entries reach the database in the order they happened. */
    private val saves = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val appState = AppState(
        bus = bus,
        outputs = { effect ->
            when (effect) {
                is Effect.Light -> light.play(effect.rhythm)
                is Effect.Speak -> speechOutput.speak(effect.text)
                is Effect.Log -> saves.trySend { tripLogger.log(effect.entry) }
                is Effect.TripStarted -> saves.trySend { tripLogger.tripStarted(effect.at) }
                is Effect.TripEnded -> saves.trySend { tripLogger.tripEnded(effect.at, effect.distanceMeters) }
            }
        },
        clock = clock,
        scope = scope,
        onOutputError = { Log.w(TAG, "output failed", it) },
    )

    private var vehicleSource: EventSource? = null
    private var locationSource: EventSource? = null
    private val uxSource = UxRestrictionSource(context, clock) { Log.i(TAG, "no driving restriction service: $it") }
    private var micJob: Job? = null
    private var demoJob: Job? = null
    private var started = false

    @Volatile
    private var debugMicFile: FileInputStream? = null

    fun start() {
        if (started) return
        started = true
        appState.start()
        light.start()
        speechOutput.start()
        scope.launch(Dispatchers.IO) {
            for (save in saves) {
                try {
                    save()
                } catch (e: Exception) {
                    Log.w(TAG, "could not save the trip", e)
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            tripLogger.restoreLastTrip()?.let { bus.post(AppEvent.RestoreTrip(it, clock())) }
        }
        bus.post(AppEvent.SetReplies(settings.replies, clock()))
        uxSource.start(bus::post)
        if (BuildConfig.DEBUG) logScreenChanges()
        if (settings.demoMode) {
            bus.post(AppEvent.ListeningChanged(Listening.DEMO, clock()))
        } else {
            startRealSources()
        }
    }

    fun setDemoMode(on: Boolean) {
        settings.demoMode = on
        if (on) {
            stopRealSources()
            bus.post(AppEvent.ListeningChanged(Listening.DEMO, clock()))
        } else {
            startRealSources()
        }
    }

    fun playDemo(scene: DemoScene) {
        stopRealSources()
        demoJob?.cancel()
        bus.post(AppEvent.Reset(clock()))
        val script = context.assets.open(scene.asset).bufferedReader().use { it.readText() }
        demoJob = scope.launch {
            DemoScriptPlayer(script, clock).events().collect(bus::post)
        }
    }

    /** Called again once the driver answers the permission prompt. */
    fun onPermissionsChanged() {
        if (!settings.demoMode) startRealSources()
    }

    /** Debug builds only: play a raw PCM file as if the microphone had heard it. */
    fun debugFeedMic(path: String) {
        if (BuildConfig.DEBUG) {
            debugMicFile = FileInputStream(File(path))
            Log.i(TAG, "mic feed start")
        }
    }

    /** Debug builds only, since it contains what people said: lets end-to-end checks time reactions. */
    private fun logScreenChanges() {
        scope.launch {
            appState.state
                .map {
                    "screen=${it.screen} gear=${it.gear} window=${it.driverWindowPercent} " +
                        "alert=${it.alert?.headline} notice=${it.notice?.headline} seats=${it.occupiedSeats} " +
                        "caption=${it.captions.lastOrNull()?.text} outside=${it.outside?.text} tts=${it.ttsPlaying}"
                }
                .distinctUntilChanged()
                .collect { Log.i(TAG, it) }
        }
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun startRealSources() {
        demoJob?.cancel()
        if (vehicleSource == null) {
            vehicleSource = RealVehicleSource(
                VehicleDomains(
                    gear = vehicle.getGear(),
                    turnSignal = vehicle.getTurnSignal(),
                    steering = vehicle.getSteeringWheel(),
                    door = vehicle.getDoor(),
                    window = vehicle.getWindow(),
                    seat = vehicle.getSeat(),
                    safety = vehicle.getSafety(),
                ),
                clock = clock,
                onError = { what, error -> Log.i(TAG, "vehicle: $what: $error") },
            ).also { it.start(bus::post) }
        }
        if (locationSource == null && granted(LOCATION_PERMISSION)) {
            locationSource = RealLocationSource(FusedLocation(context), clock) { Log.i(TAG, "location: $it") }
                .also { it.start(bus::post) }
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            bus.post(AppEvent.ListeningChanged(Listening.NO_PERMISSION, clock()))
        } else if (micJob == null) {
            micJob = scope.launch(Dispatchers.IO) { keepMicrophoneRunning() }
        }
    }

    private fun stopRealSources() {
        vehicleSource?.stop()
        vehicleSource = null
        locationSource?.stop()
        locationSource = null
        micJob?.cancel()
        micJob = null
    }

    /** The microphone can fail at any time; the driver is told, and the app keeps trying. */
    private suspend fun keepMicrophoneRunning() {
        while (true) {
            try {
                runMicrophone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "microphone failed", e)
            }
            bus.post(AppEvent.ListeningChanged(Listening.MIC_ERROR, clock()))
            delay(MIC_RETRY_MS)
        }
    }

    private suspend fun runMicrophone() {
        val speech = SpeechSession(
            engine = PleosSttEngine(context),
            occupied = { appState.state.value.occupiedSeats },
            outsideMode = { appState.state.value.screen == Screen.WINDOW },
            clock = clock,
            sink = bus::post,
            onFailed = { Log.w(TAG, "speech recognition: $it") },
        )
        val classifier = try {
            YamnetSoundSource(context)
        } catch (e: Exception) {
            Log.w(TAG, "sound classifier unavailable", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "sound classifier unavailable", e)
            null
        }
        val microphone = AudioRecordReader()
        try {
            val canHear = classifier != null && (microphone.isReady || BuildConfig.DEBUG)
            bus.post(AppEvent.ListeningChanged(if (canHear) Listening.ACTIVE else Listening.MIC_ERROR, clock()))
            speech.start()
            MicPump(
                reader = withDebugFeed(microphone),
                classify = { window ->
                    classifier?.classify(window).orEmpty().also { scores ->
                        if (BuildConfig.DEBUG) {
                            val siren = SoundFrameMapper.map(scores, 0L).first().confidence
                            if (siren > DEBUG_SCORE_FLOOR) Log.d(TAG, "frame siren ${"%.2f".format(siren)}")
                        }
                    }
                },
                speech = speech,
                speechOpen = {
                    val state = appState.state.value
                    MicArbiter.speechOpen(
                        hasPassenger = (state.occupiedSeats - Seat.DRIVER).isNotEmpty(),
                        outsideMode = state.screen == Screen.WINDOW,
                        ttsPlaying = state.ttsPlaying,
                    )
                },
                clock = clock,
                sink = bus::post,
                onError = { Log.w(TAG, "sound classification failed", it) },
            ).run()
        } finally {
            microphone.close()
            classifier?.close()
            speech.stop()
        }
    }

    private fun withDebugFeed(microphone: PcmReader): PcmReader {
        if (!BuildConfig.DEBUG) return microphone
        val bytes = ByteArray(MicPump.CHUNK_SAMPLES * 2)
        return PcmReader { buffer ->
            val feed = debugMicFile
            val read = feed?.read(bytes) ?: -1
            if (read <= 0) {
                debugMicFile = null
                microphone.read(buffer).also { if (it <= 0) Thread.sleep(CHUNK_MS) }.coerceAtLeast(0)
            } else {
                for (i in 0 until read / 2) {
                    buffer[i] = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
                }
                Thread.sleep(CHUNK_MS)
                read / 2
            }
        }
    }

    private companion object {
        const val TAG = "VisibleSound"
        const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val CHUNK_MS = 100L
        const val DEBUG_SCORE_FLOOR = 0.05f
        const val MIC_RETRY_MS = 5_000L
    }
}
