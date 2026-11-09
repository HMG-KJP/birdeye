package app.birdeye

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.vehicle.Vehicle
import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import app.birdeye.core.AppEvent
import app.birdeye.core.AppState
import app.birdeye.core.Effect
import app.birdeye.core.EventBus
import app.birdeye.core.Listening
import app.birdeye.core.Screen
import app.birdeye.core.Seat
import app.birdeye.core.Alert
import app.birdeye.output.AlertNotifier
import app.birdeye.output.AmbientLightPlayer
import app.birdeye.output.BackgroundAlerts
import app.birdeye.output.DuckingAudioFocus
import app.birdeye.output.ListeningService
import app.birdeye.output.PleosTtsEngine
import app.birdeye.output.RealAmbientLight
import app.birdeye.output.SpeechOutput
import app.birdeye.output.TripDatabase
import app.birdeye.output.TripLogger
import app.birdeye.settings.SettingsStore
import app.birdeye.source.AudioRecordReader
import app.birdeye.source.EventSource
import app.birdeye.source.MicArbiter
import app.birdeye.source.MicPump
import app.birdeye.source.UnavailableTrafficSource
import app.birdeye.source.demo.DemoScene
import app.birdeye.source.PcmReader
import app.birdeye.source.PleosSttEngine
import app.birdeye.source.RealLocationSource
import app.birdeye.source.RealVehicleSource
import app.birdeye.source.SoundFrameMapper
import app.birdeye.source.SpeechSession
import app.birdeye.source.UxRestrictionSource
import app.birdeye.source.VehicleDomains
import app.birdeye.source.YamnetSoundSource
import app.birdeye.source.demo.DemoScriptPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class BirdeyeApp : Application() {
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
    private val trafficSource = UnavailableTrafficSource()
    private var micJob: Job? = null
    private var demoJob: Job? = null
    private var started = false
    private val appVisible = MutableStateFlow(false)
    private val notifier by lazy { AlertNotifier(context) }

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
        watchForHiddenAlerts()
        if (BuildConfig.DEBUG) {
            logScreenChanges()
            listenForDebugMicFeed()
        }
        if (settings.demoMode) {
            bus.post(AppEvent.ListeningChanged(Listening.DEMO, clock()))
        } else {
            startRealSources()
        }
    }

    fun setDemoMode(on: Boolean) {
        if (on == settings.demoMode) return
        settings.demoMode = on
        demoJob?.cancel()
        if (on) stopRealSources()
        // What one mode knew about the car means nothing in the other, such as a signal left on.
        bus.post(AppEvent.Reset(clock()))
        if (on) {
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

    /** Follows the activity: the service can only be started while the app is on screen. */
    fun onAppVisible(visible: Boolean) {
        appVisible.value = visible
        if (visible) startListeningService()
    }

    private fun startListeningService() {
        if (settings.demoMode || !appVisible.value || !granted(Manifest.permission.RECORD_AUDIO)) return
        try {
            context.startForegroundService(Intent(context, ListeningService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "could not keep listening in the background", e)
        }
    }

    private fun watchForHiddenAlerts() {
        scope.launch {
            var shown: Alert? = null
            combine(appState.state.map { it.alert }.distinctUntilChanged(), appVisible) { alert, visible -> alert to visible }
                .collect { (alert, visible) ->
                    when (val decision = BackgroundAlerts.decide(shown, alert, visible)) {
                        is BackgroundAlerts.Show -> notifier.show(decision.title, decision.text)
                        BackgroundAlerts.Clear -> notifier.clear()
                        null -> Unit
                    }
                    shown = alert
                }
        }
    }

    /** Debug builds only: feeds the microphone without opening the app, to test it while hidden. */
    private fun listenForDebugMicFeed() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                intent.getStringExtra("path")?.let(::debugFeedMic)
            }
        }
        val filter = IntentFilter("app.birdeye.debug.FEED_MIC")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
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
        startListeningService()
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
                    wiper = vehicle.getWiper(),
                    light = vehicle.getLight(),
                ),
                clock = clock,
                onError = { what, error -> Log.i(TAG, "vehicle: $what: $error") },
            ).also { it.start(bus::post) }
        }
        trafficSource.start(bus::post)
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
        context.stopService(Intent(context, ListeningService::class.java))
        trafficSource.stop()
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
        const val TAG = "Birdeye"
        const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val CHUNK_MS = 100L
        const val DEBUG_SCORE_FLOOR = 0.05f
        const val MIC_RETRY_MS = 5_000L
    }
}
