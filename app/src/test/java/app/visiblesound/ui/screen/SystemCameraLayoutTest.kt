package app.visiblesound.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.visiblesound.core.CaptionLine
import app.visiblesound.core.DrivingCondition
import app.visiblesound.core.GearPos
import app.visiblesound.core.Screen
import app.visiblesound.core.SignalState
import app.visiblesound.core.Speaker
import app.visiblesound.core.TripEntry
import app.visiblesound.core.TripSummary
import app.visiblesound.core.EntryKind
import app.visiblesound.core.UiState
import app.visiblesound.ui.theme.FormFactor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Measured on the default display: the camera opens at the window's top left, 754 by 424. */
private val CAMERA_WIDTH = 754.dp
private val CAMERA_BOTTOM = 424.dp

/** The shortest line any of these labels has across the display sizes. */
private val READABLE_LINE = 24.dp

private val signalOn = UiState(gear = GearPos.D, turnSignal = SignalState.LEFT)

/** A turn signal brings up the system's blind spot camera, which must not hide what the app is saying. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-mdpi")
class SystemCameraLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: UiState, formFactor: FormFactor = FormFactor.S) {
        compose.setContent { AppRoot(state = state, actions = UiActions(), formFactor = formFactor) }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text).getBoundsInRoot()

    private fun assertBelowCamera(text: String) =
        assertTrue("$text starts at ${bounds(text).top}", bounds(text).top >= CAMERA_BOTTOM)

    /** A column squeezes what no longer fits, so the bottom edge alone would never fail. */
    private fun assertInsideWindow(text: String, height: Dp) {
        val box = bounds(text)
        assertTrue("$text ends at ${box.bottom}", box.bottom <= height)
        assertTrue("$text was squeezed to ${box.height}", box.height >= READABLE_LINE)
    }

    @Test
    fun `with no signal an alert stays at the top of the window`() {
        show(UiState(gear = GearPos.D, alert = sirenAlert))

        assertTrue(bounds("구급차가 다가와요").top < CAMERA_BOTTOM)
        compose.onNodeWithText("소리 방향").assertIsDisplayed()
    }

    @Test
    fun `an alert moves below the camera and the side pane under the camera gives way`() {
        val latest = line("구급차 지나가면 천천히 가자")
        show(signalOn.copy(alert = sirenAlert, recentCaption = latest, captions = listOf(latest)))

        assertBelowCamera("구급차가 다가와요")
        assertBelowCamera("오른쪽 차로로 비켜 주세요")
        assertBelowCamera("구급차 지나가면 천천히 가자")
        compose.onNodeWithText("소리 방향").assertDoesNotExist()
    }

    @Test
    fun `the tabs stay reachable beside the camera`() {
        show(signalOn)

        assertTrue(bounds("주행").left >= CAMERA_WIDTH)
        assertTrue(bounds("주행").top < CAMERA_BOTTOM)
    }

    @Test
    fun `the conversation moves below the camera and keeps its replies on screen`() {
        show(signalOn.copy(screen = Screen.TALK, captions = listOf(line("강남역 쪽으로 가 줘"), line("저기 세워 줘"))))

        assertBelowCamera("저기 세워 줘")
        compose.onNodeWithText("뒷좌석 왼쪽").assertDoesNotExist()
        assertInsideWindow("조금 이따 세울게", 1184.dp)
    }

    @Test
    fun `the window conversation moves below the camera and keeps its button on screen`() {
        show(
            signalOn.copy(
                gear = GearPos.P,
                screen = Screen.WINDOW,
                driverWindowPercent = 60,
                outside = CaptionLine(Speaker.Outside, "음주 단속 중입니다", 0, true),
            ),
        )

        assertBelowCamera("음주 단속 중입니다")
        assertInsideWindow("안내 문장 말하기", 1184.dp)
    }

    @Test
    fun `the recap list moves below the camera`() {
        val trip = TripSummary(entries = listOf(TripEntry(EntryKind.HORN, 0, "경적 · 약 2초", null)))
        show(signalOn.copy(gear = GearPos.P, screen = Screen.RECAP, trip = trip))

        assertBelowCamera("경적 · 약 2초")
    }

    @Test
    @Config(qualifiers = "w1640dp-h810dp-land-mdpi")
    fun `the alert and the caption still fit in the shortest wide window`() {
        val latest = line("구급차 지나가면 천천히 가자")
        show(signalOn.copy(alert = sirenAlert, recentCaption = latest, captions = listOf(latest)), FormFactor.W)

        assertInsideWindow("오른쪽 차로로 비켜 주세요", 810.dp)
        assertInsideWindow("구급차 지나가면 천천히 가자", 810.dp)
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `the alert and the caption still fit with the largest tokens`() {
        val latest = line("구급차 지나가면 천천히 가자")
        show(signalOn.copy(alert = sirenAlert, recentCaption = latest, captions = listOf(latest)), FormFactor.WF)

        assertInsideWindow("오른쪽 차로로 비켜 주세요", 800.dp)
        assertInsideWindow("구급차 지나가면 천천히 가자", 800.dp)
    }

    private val atTheWindow = signalOn.copy(
        gear = GearPos.P,
        screen = Screen.WINDOW,
        driverWindowPercent = 60,
        outside = CaptionLine(Speaker.Outside, "음주 단속 중입니다", 0, true),
    )

    @Test
    @Config(qualifiers = "w1640dp-h810dp-land-mdpi")
    fun `the window conversation keeps its buttons in the shortest wide window`() {
        show(atTheWindow, FormFactor.W)

        assertInsideWindow("음주 단속 중입니다", 810.dp)
        assertInsideWindow("안내 문장 말하기", 810.dp)
        assertInsideWindow("어디에 세우면 될까요?", 810.dp)
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `the window conversation keeps its buttons with the largest tokens`() {
        show(atTheWindow, FormFactor.WF)

        assertInsideWindow("음주 단속 중입니다", 800.dp)
        assertInsideWindow("안내 문장 말하기", 800.dp)
        assertInsideWindow("어디에 세우면 될까요?", 800.dp)
    }

    @Test
    @Config(qualifiers = "w1640dp-h810dp-land-mdpi")
    fun `the conversation keeps its replies in the shortest wide window`() {
        show(signalOn.copy(screen = Screen.TALK, captions = listOf(line("강남역 쪽으로 가 줘"), line("저기 세워 줘"))), FormFactor.W)

        assertInsideWindow("저기 세워 줘", 810.dp)
        assertInsideWindow("조금 이따 세울게", 810.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1184dp-port-mdpi")
class PortraitSystemCameraLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `everything moves below the camera, which nearly fills a narrow window's width`() {
        val latest = line("구급차 지나가면 천천히 가자")
        compose.setContent {
            AppRoot(signalOn.copy(alert = sirenAlert, recentCaption = latest, captions = listOf(latest)), UiActions())
        }

        val headline = compose.onNodeWithText("구급차가 다가와요").getBoundsInRoot()
        assertTrue("alert starts at ${headline.top}", headline.top >= CAMERA_BOTTOM)
        compose.onNodeWithText("구급차 지나가면 천천히 가자").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w628dp-h855dp-port-mdpi")
    fun `the alert and the caption still fit in the smallest narrow window`() {
        val latest = line("구급차 지나가면 천천히 가자")
        compose.setContent {
            AppRoot(
                signalOn.copy(alert = sirenAlert, recentCaption = latest, captions = listOf(latest), condition = DrivingCondition.RAIN),
                UiActions(),
                FormFactor.L,
            )
        }

        listOf("오른쪽 차로로 비켜 주세요", "구급차 지나가면 천천히 가자").forEach {
            val box = compose.onNodeWithText(it).getBoundsInRoot()
            assertTrue("$it ends at ${box.bottom}", box.bottom <= 855.dp)
            assertTrue("$it was squeezed to ${box.height}", box.height >= READABLE_LINE)
        }
    }

    @Test
    fun `parked, the tabs move below the camera too`() {
        compose.setContent { AppRoot(signalOn.copy(gear = GearPos.P), UiActions()) }

        val tab = compose.onNodeWithText("기록").getBoundsInRoot()
        assertTrue("tab starts at ${tab.top}", tab.top >= CAMERA_BOTTOM)
    }
}
