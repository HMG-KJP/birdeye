package app.visiblesound.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.visiblesound.core.Screen
import app.visiblesound.core.UiState
import app.visiblesound.ui.component.BasicButton
import app.visiblesound.ui.component.LineIcons
import app.visiblesound.ui.component.LocalSystemCameraHeight
import app.visiblesound.ui.component.TabItem
import app.visiblesound.ui.component.Toast
import app.visiblesound.ui.theme.FormFactor
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.VisibleSoundTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the driver can do; the UI only reports intents and never touches an SDK. */
class UiActions(
    val onReply: (String) -> Unit = {},
    val onSelectScreen: (Screen) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val now: () -> Long = System::currentTimeMillis,
    val timeText: (Long) -> String = { SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(it)) },
)

internal fun mainTabs(state: UiState) = listOf(
    TabItem(Screen.MAIN, "주행"),
    TabItem(Screen.TALK, "대화"),
    TabItem(Screen.RECAP, "기록", enabled = state.parked),
)

@Composable
internal fun SettingsEntry(state: UiState, actions: UiActions) {
    if (state.parked) {
        BasicButton("설정", actions.onOpenSettings, size = Tokens.buttonSmall, icon = LineIcons.settings)
    }
}

/** Settings opens only while parked and always gives way to an alert or a person at the window. */
fun settingsVisible(state: UiState, requested: Boolean) =
    requested && state.parked && state.alert == null && state.screen != Screen.WINDOW

/**
 * The app window's shape picks the layout, since the system can resize or split it at any time:
 * taller than wide means one pane. The display picks the token column.
 */
@Composable
fun AppRoot(state: UiState, actions: UiActions, formFactor: FormFactor = FormFactor.S) {
    VisibleSoundTheme(formFactor) {
        // Held here so a half-typed reply survives an alert or a change of window shape.
        val draft = rememberSaveable { mutableStateOf<String?>(null) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val onePane = maxHeight > maxWidth
            val camera = if (state.systemCameraUp) maxHeight * Tokens.SYSTEM_CAMERA_HEIGHT_RATIO else 0.dp
            CompositionLocalProvider(LocalSystemCameraHeight provides camera) {
                when {
                    state.alert != null -> MainScreen(state, onePane, actions)
                    state.screen == Screen.TALK -> TalkScreen(state, onePane, draft, actions)
                    state.screen == Screen.WINDOW -> WindowScreen(state, onePane, actions)
                    state.screen == Screen.RECAP -> RecapScreen(state, onePane, actions)
                    else -> MainScreen(state, onePane, actions)
                }
            }
            state.toast?.let { Toast(it) }
        }
    }
}
