package app.birdeye

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.birdeye.core.AppEvent
import app.birdeye.settings.SettingsScreen
import app.birdeye.source.demo.DemoScene
import app.birdeye.ui.screen.AppRoot
import app.birdeye.ui.screen.UiActions
import app.birdeye.ui.screen.settingsVisible
import app.birdeye.ui.theme.FormFactor
import app.birdeye.ui.theme.BirdeyeTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as BirdeyeApp).container

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.onPermissionsChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = container
        container.start()
        askForMissingPermissions()
        handleDebugIntent(intent)
        // The platform's rule for the top level of an app: hide, never terminate.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        val bus = container.bus
        val clock = container.clock
        val formFactor = displayFormFactor()
        setContent {
            val state by container.appState.state.collectAsStateWithLifecycle()
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            var demoMode by remember { mutableStateOf(container.settings.demoMode) }
            // Leaving park closes settings, so they don't reopen by themselves at the next stop.
            LaunchedEffect(state.parked) {
                if (!state.parked) settingsOpen = false
            }

            if (settingsVisible(state, settingsOpen)) {
                BackHandler { settingsOpen = false }
                BirdeyeTheme(formFactor) {
                    SettingsScreen(
                        demoMode = demoMode,
                        replies = state.replies,
                        onDemoMode = {
                            container.setDemoMode(it)
                            demoMode = it
                        },
                        onPlayDemo = {
                            settingsOpen = false
                            container.playDemo(it)
                        },
                        onSaveReplies = {
                            container.settings.replies = it
                            bus.post(AppEvent.SetReplies(it, clock()))
                        },
                        onClose = { settingsOpen = false },
                    )
                }
            } else {
                AppRoot(
                    state = state,
                    formFactor = formFactor,
                    actions = remember {
                        UiActions(
                            onReply = { bus.post(AppEvent.Reply(it, clock())) },
                            onSelectScreen = { bus.post(AppEvent.SelectScreen(it, clock())) },
                            onOpenSettings = { settingsOpen = true },
                            now = clock,
                        )
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.onAppVisible(true)
    }

    override fun onStop() {
        container.onAppVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    private fun askForMissingPermissions() {
        val missing = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) askPermissions.launch(missing.toTypedArray())
    }

    /** Debug builds only: lets end-to-end tests start the demo or feed the microphone stream. */
    private fun handleDebugIntent(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) {
            container.setDemoMode(true)
            container.playDemo(
                intent.getStringExtra(EXTRA_DEMO_SCENE)?.let(DemoScene::valueOf) ?: DemoScene.WITH_PASSENGER,
            )
        }
        intent.getStringExtra(EXTRA_MIC_FILE)?.let { container.debugFeedMic(it) }
    }

    /** The token column comes from the whole display, which stays the same when the window is split. */
    @Suppress("DEPRECATION")
    private fun displayFormFactor(): FormFactor {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return FormFactor.ofDisplay(widthDp = (metrics.widthPixels / metrics.density).toInt())
    }

    private companion object {
        const val EXTRA_DEMO = "demo"
        const val EXTRA_DEMO_SCENE = "demoScene"
        const val EXTRA_MIC_FILE = "micFile"
        val RUNTIME_PERMISSIONS = listOfNotNull(
            Manifest.permission.RECORD_AUDIO,
            "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
        )
    }
}
