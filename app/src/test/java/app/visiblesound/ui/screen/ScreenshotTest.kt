package app.visiblesound.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.visiblesound.core.CaptionLine
import app.visiblesound.core.EntryKind
import app.visiblesound.core.GearPos
import app.visiblesound.core.Listening
import app.visiblesound.core.Screen
import app.visiblesound.core.Seat
import app.visiblesound.core.Speaker
import app.visiblesound.core.TripEntry
import app.visiblesound.core.TripSummary
import app.visiblesound.core.UiState
import app.visiblesound.ui.theme.FormFactor
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val sirenCaption = CaptionLine(Speaker.Passenger(Seat.PASSENGER), "구급차 지나가면 천천히 가자", 0, true)
private val sirenState = UiState(
    gear = GearPos.D,
    listening = Listening.ACTIVE,
    alert = sirenAlert,
    captions = listOf(sirenCaption),
    recentCaption = sirenCaption,
)
private val talkState = UiState(
    screen = Screen.TALK,
    gear = GearPos.D,
    activeSeat = Seat.PASSENGER,
    occupiedSeats = setOf(Seat.DRIVER, Seat.PASSENGER),
    captions = listOf(
        CaptionLine(Speaker.Passenger(Seat.PASSENGER), "강남역 쪽으로 가 줘", 0, true),
        CaptionLine(Speaker.DriverReply, "응, 알겠어", 0, true),
        CaptionLine(Speaker.Passenger(Seat.PASSENGER), "저기 편의점 앞에 세워 줘", 0, true),
    ),
    recentCaption = CaptionLine(Speaker.Passenger(Seat.PASSENGER), "저기 편의점 앞에 세워 줘", 0, true),
)
private val windowState = UiState(
    screen = Screen.WINDOW,
    gear = GearPos.P,
    driverWindowPercent = 60,
    outside = CaptionLine(Speaker.Outside, "음주 단속 중입니다. 기계에 길게 불어 주세요", 0, true),
)
private val recapState = UiState(
    screen = Screen.RECAP,
    gear = GearPos.P,
    trip = TripSummary(
        startedAt = 0,
        endedAt = 48 * 60_000L,
        distanceMeters = 21_000.0,
        entries = listOf(
            TripEntry(EntryKind.SIREN, 0, "뒤쪽에서 구급차 사이렌 · 약 20초", "테헤란로"),
            TripEntry(EntryKind.HORN, 0, "왼쪽에서 경적 · 약 2초", "역삼역 사거리"),
            TripEntry(EntryKind.VEHICLE, 0, "방향지시등이 오래 켜져 있었어요", "강남대로"),
            TripEntry(EntryKind.TALK, 0, "조수석: \"휴대폰 두고 내린 것 같아\"", "강남대로"),
        ),
    ),
)

/** Renders the mockup boards so the result can be compared with the mockup by eye. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase {
    @get:Rule
    val compose = createComposeRule()

    protected abstract val prefix: String
    protected open val formFactor = FormFactor.S

    protected fun capture(name: String, state: UiState) {
        compose.setContent { AppRoot(state, actions = UiActions(timeText = { "08:24" }), formFactor = formFactor) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_$name.png")
    }

    @Test fun idle() = capture("0_idle", UiState(gear = GearPos.D, listening = Listening.ACTIVE))
    @Test fun noMic() = capture("0_nomic", UiState(gear = GearPos.P, listening = Listening.NO_PERMISSION))
    @Test fun siren() = capture("1_siren", sirenState)
    @Test fun talk() = capture("2_talk", talkState)
    @Test fun window() = capture("3_window", windowState)
    @Test fun recap() = capture("4_recap", recapState)
}

@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-night-mdpi")
class S17LandscapeDarkScreenshotTest : ScreenshotBase() {
    override val prefix = "s17_land_dark"
}

@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-notnight-mdpi")
class S17LandscapeLightScreenshotTest : ScreenshotBase() {
    override val prefix = "s17_land_light"
}

@Config(sdk = [34], qualifiers = "w840dp-h1184dp-port-night-mdpi")
class S17PortraitDarkScreenshotTest : ScreenshotBase() {
    override val prefix = "s17_port_dark"
}

@Config(sdk = [34], qualifiers = "w1640dp-h810dp-land-night-mdpi")
class WideLandscapeDarkScreenshotTest : ScreenshotBase() {
    override val prefix = "w_land_dark"
    override val formFactor = FormFactor.W
}

@Config(sdk = [34], qualifiers = "w800dp-h810dp-port-night-mdpi")
class WidePortraitDarkScreenshotTest : ScreenshotBase() {
    override val prefix = "w_port_dark"
    override val formFactor = FormFactor.W
}

@Config(sdk = [34], qualifiers = "w1274dp-h855dp-land-night-mdpi")
class L129LandscapeDarkScreenshotTest : ScreenshotBase() {
    override val prefix = "l_land_dark"
    override val formFactor = FormFactor.L
}
