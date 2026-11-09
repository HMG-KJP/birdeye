package app.visiblesound.ui.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.visiblesound.core.Alert
import app.visiblesound.core.AlertKind
import app.visiblesound.core.CaptionLine
import app.visiblesound.core.Copy
import app.visiblesound.core.Direction
import app.visiblesound.core.DrivingCondition
import app.visiblesound.core.EntryKind
import app.visiblesound.core.GearPos
import app.visiblesound.core.LightRhythm
import app.visiblesound.core.Listening
import app.visiblesound.core.Screen
import app.visiblesound.core.Seat
import app.visiblesound.core.Speaker
import app.visiblesound.core.TripEntry
import app.visiblesound.core.TripSummary
import app.visiblesound.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal val sirenAlert = Alert(
    kind = AlertKind.SIREN,
    eyebrow = "사이렌 감지 · 뒤쪽에서 접근 중",
    headline = "구급차가 다가와요",
    guidance = "오른쪽 차로로 비켜 주세요",
    rhythm = LightRhythm.SIREN,
    direction = Direction.REAR,
    firedAt = 0,
)

internal fun line(text: String, seat: Seat? = Seat.PASSENGER, at: Long = 0) =
    CaptionLine(Speaker.Passenger(seat), text, at, isFinal = true)

/** The two-pane landscape window: everything is available. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-mdpi")
class ScreenSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: UiState, actions: UiActions = UiActions()) {
        compose.setContent { AppRoot(state = state, actions = actions) }
    }

    @Test
    fun `main screen shows the app name and says it is listening only when it really is`() {
        show(UiState(listening = Listening.ACTIVE))

        compose.onNodeWithText("보이는 소리").assertIsDisplayed()
        compose.onNodeWithText("주변 소리를 듣고 있어요").assertIsDisplayed()
        compose.onNodeWithText("조용해요").assertIsDisplayed()
    }

    @Test
    fun `without the microphone the driver is told plainly that sounds are not being watched`() {
        show(UiState(listening = Listening.NO_PERMISSION))

        compose.onNodeWithText("주변 소리를 듣고 있어요").assertDoesNotExist()
        compose.onNodeWithText("조용해요").assertDoesNotExist()
        compose.onNodeWithText("소리를 듣지 못하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("마이크 권한을 허용해 주세요").assertIsDisplayed()
    }

    @Test
    fun `a microphone failure is shown the same way`() {
        show(UiState(listening = Listening.MIC_ERROR))

        compose.onNodeWithText("소리를 듣지 못하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("마이크를 사용할 수 없어요").assertIsDisplayed()
    }

    @Test
    fun `demo mode says it is a demonstration rather than claiming to listen`() {
        show(UiState(listening = Listening.DEMO))

        compose.onNodeWithText("주변 소리를 듣고 있어요").assertDoesNotExist()
        compose.onNodeWithText("시연 중이에요").assertIsDisplayed()
    }

    @Test
    fun `before any script has played, demo mode says it is waiting for one`() {
        show(UiState(listening = Listening.DEMO, gear = null))

        compose.onNodeWithText("시연을 기다리고 있어요").assertIsDisplayed()
    }

    @Test
    fun `once a script is playing, demo mode no longer says it is waiting for one`() {
        show(UiState(listening = Listening.DEMO, gear = GearPos.D))

        compose.onNodeWithText("시연을 기다리고 있어요").assertDoesNotExist()
        compose.onNodeWithText("조용해요").assertIsDisplayed()
        compose.onNodeWithText("사이렌과 경적이 들리면 바로 알려 드릴게요").assertIsDisplayed()
    }

    @Test
    fun `a condition that muffles sounds is shown, and the idle card stops calling the road quiet`() {
        show(UiState(gear = GearPos.D, listening = Listening.ACTIVE, condition = DrivingCondition.RAIN))

        compose.onNodeWithText(Copy.conditionText(DrivingCondition.RAIN)).assertIsDisplayed()
        compose.onNodeWithText("조용해요").assertDoesNotExist()
        compose.onNodeWithText(Copy.MUFFLED_IDLE).assertIsDisplayed()
    }

    @Test
    fun `fog is shown but the road can still be called quiet, since fog does not muffle sound`() {
        show(UiState(gear = GearPos.D, listening = Listening.ACTIVE, condition = DrivingCondition.FOG))

        compose.onNodeWithText(Copy.conditionText(DrivingCondition.FOG)).assertIsDisplayed()
        compose.onNodeWithText("조용해요").assertIsDisplayed()
    }

    @Test
    fun `the condition stays visible beside an alert and on the talk screen`() {
        show(UiState(gear = GearPos.D, alert = sirenAlert, condition = DrivingCondition.WIPER))
        compose.onNodeWithText(Copy.conditionText(DrivingCondition.WIPER)).assertIsDisplayed()
    }

    @Test
    fun `a traffic warning shows its message and the side on the car diagram`() {
        val warning = sirenAlert.copy(
            kind = AlertKind.TRAFFIC,
            eyebrow = "주변 차량 경고 · 오른쪽",
            headline = "오른쪽에서 차가 끼어들어요",
            guidance = "속도를 줄이고 앞차와 거리를 두세요",
            rhythm = LightRhythm.TRAFFIC,
            direction = Direction.RIGHT,
        )
        show(UiState(gear = GearPos.D, alert = warning))

        compose.onNodeWithText("오른쪽에서 차가 끼어들어요").assertIsDisplayed()
        compose.onNodeWithText(Copy.rhythmLegend(LightRhythm.TRAFFIC)).assertIsDisplayed()
    }

    @Test
    fun `the recap counts traffic warnings`() {
        val trip = TripSummary(entries = listOf(TripEntry(EntryKind.TRAFFIC, 0, "오른쪽에서 끼어들기", null)))
        show(UiState(gear = GearPos.P, screen = Screen.RECAP, trip = trip))

        compose.onNodeWithText("주변 차량 경고").assertIsDisplayed()
        compose.onNodeWithText("오른쪽에서 끼어들기").assertIsDisplayed()
    }

    @Test
    fun `the condition is on the talk screen too`() {
        show(UiState(gear = GearPos.D, screen = Screen.TALK, captions = listOf(line("저기")), condition = DrivingCondition.THUNDER))

        compose.onNodeWithText(Copy.conditionText(DrivingCondition.THUNDER)).assertIsDisplayed()
        compose.onNodeWithText("저기").assertIsDisplayed()
    }

    @Test
    fun `a vehicle notice stays visible on the talk screen`() {
        val notice = sirenAlert.copy(
            kind = AlertKind.VEHICLE,
            eyebrow = Copy.VEHICLE_EYEBROW,
            headline = Copy.TURN_SIGNAL,
            guidance = null,
            rhythm = LightRhythm.VEHICLE,
            direction = null,
        )
        show(UiState(gear = GearPos.D, screen = Screen.TALK, notice = notice, captions = listOf(line("저기"))))

        compose.onNodeWithText(Copy.TURN_SIGNAL).assertIsDisplayed()
        compose.onNodeWithText("저기").assertIsDisplayed()
    }

    @Test
    fun `a vehicle notice stays visible on the window and record screens`() {
        val notice = sirenAlert.copy(
            kind = AlertKind.VEHICLE,
            eyebrow = Copy.VEHICLE_EYEBROW,
            headline = "안전벨트",
            guidance = "안전벨트를 매 주세요",
            rhythm = LightRhythm.VEHICLE,
            direction = null,
        )
        show(UiState(gear = GearPos.P, screen = Screen.WINDOW, notice = notice))

        compose.onNodeWithText("안전벨트 · 안전벨트를 매 주세요").assertIsDisplayed()
    }

    @Test
    fun `the latest sentence is marked as just now only while it is fresh`() {
        val latest = line("저기")
        show(UiState(gear = GearPos.D, screen = Screen.TALK, captions = listOf(latest), recentCaption = null))

        compose.onNodeWithText("조수석 · 방금").assertDoesNotExist()
        compose.onAllNodesWithText("조수석").assertCountEquals(2) // the card's label and the seat map
    }

    @Test
    fun `an old sentence does not linger on the main screen`() {
        show(UiState(gear = GearPos.D, screen = Screen.MAIN, captions = listOf(line("어제 한 말")), recentCaption = null))

        compose.onNodeWithText("어제 한 말").assertDoesNotExist()
    }

    @Test
    fun `speed and gear belong to the system driving view and are not drawn`() {
        show(UiState(gear = GearPos.D))

        compose.onNodeWithText("km/h").assertDoesNotExist()
        compose.onNodeWithText("PRND").assertDoesNotExist()
        compose.onNodeWithText("D").assertDoesNotExist()
    }

    @Test
    fun `an alert shows its big message, guidance, rhythm legend and the latest caption`() {
        val latest = line("구급차 지나가면 천천히 가자")
        show(UiState(gear = GearPos.D, alert = sirenAlert, captions = listOf(latest), recentCaption = latest))

        compose.onNodeWithText("사이렌 감지 · 뒤쪽에서 접근 중").assertIsDisplayed()
        compose.onNodeWithText("구급차가 다가와요").assertIsDisplayed()
        compose.onNodeWithText("오른쪽 차로로 비켜 주세요").assertIsDisplayed()
        compose.onNodeWithText("빠른 3회 점멸 = 긴급차량").assertIsDisplayed()
        compose.onNodeWithText("구급차 지나가면 천천히 가자").assertIsDisplayed()
    }

    @Test
    fun `an alert covers other screens`() {
        show(UiState(gear = GearPos.D, screen = Screen.TALK, alert = sirenAlert, captions = listOf(line("저기"))))

        compose.onNodeWithText("구급차가 다가와요").assertIsDisplayed()
        compose.onNodeWithText("대화 자막").assertDoesNotExist()
    }

    @Test
    fun `a vehicle notice is shown on the main screen`() {
        val notice = sirenAlert.copy(
            kind = AlertKind.VEHICLE,
            eyebrow = Copy.VEHICLE_EYEBROW,
            headline = Copy.TURN_SIGNAL,
            guidance = null,
            rhythm = LightRhythm.VEHICLE,
            direction = null,
        )
        show(UiState(gear = GearPos.D, notice = notice))

        compose.onNodeWithText(Copy.TURN_SIGNAL).assertIsDisplayed()
    }

    @Test
    fun `tabs switch between drive, talk and record`() {
        val picked = mutableListOf<Screen>()
        show(UiState(gear = GearPos.P), UiActions(onSelectScreen = { picked += it }))

        compose.onNodeWithText("주행").assertIsSelected()
        compose.onNodeWithText("대화").performClick()
        compose.onNodeWithText("기록").performClick()

        assertEquals(listOf(Screen.TALK, Screen.RECAP), picked)
    }

    @Test
    fun `the record tab is disabled while the car can move`() {
        show(UiState(gear = GearPos.D))

        compose.onNodeWithText("기록").assertIsNotEnabled()
        compose.onNodeWithText("대화").assertIsEnabled()
    }

    @Test
    fun `settings can be opened only while parked and carries a text label`() {
        var opened = 0
        show(UiState(gear = GearPos.P), UiActions(onOpenSettings = { opened++ }))

        compose.onNodeWithText("설정").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `settings is gone while driving`() {
        show(UiState(gear = GearPos.D))

        compose.onNodeWithText("설정").assertDoesNotExist()
    }

    @Test
    fun `talk screen shows the seat, two earlier lines and the latest sentence big`() {
        show(
            UiState(
                gear = GearPos.P,
                screen = Screen.TALK,
                activeSeat = Seat.REAR_RIGHT,
                occupiedSeats = setOf(Seat.DRIVER, Seat.REAR_RIGHT),
                captions = listOf(
                    line("너무 오래된 문장", Seat.REAR_RIGHT),
                    line("강남역 쪽으로 가 줘", Seat.REAR_RIGHT),
                    CaptionLine(Speaker.DriverReply, "응, 알겠어", 0, true),
                    line("저기 편의점 앞에 세워 줘", Seat.REAR_RIGHT),
                ),
                recentCaption = line("저기 편의점 앞에 세워 줘", Seat.REAR_RIGHT),
            ),
        )

        compose.onNodeWithText("승객 1명 · 듣는 중").assertIsDisplayed()
        compose.onNodeWithText("저기 편의점 앞에 세워 줘").assertIsDisplayed()
        compose.onNodeWithText("강남역 쪽으로 가 줘").assertIsDisplayed()
        compose.onAllNodesWithText("응, 알겠어").assertCountEquals(2) // the history line and its reply button
        compose.onNodeWithText("너무 오래된 문장").assertDoesNotExist()
        compose.onNodeWithText("뒷좌석 오른쪽 · 방금").assertIsDisplayed()
        compose.onNodeWithText("운전자 (음성 답장)").assertIsDisplayed()
    }

    @Test
    fun `seat map labels all four seats`() {
        show(UiState(screen = Screen.TALK, captions = listOf(line("저기", seat = null))))

        listOf("운전석", "조수석", "뒷좌석 왼쪽", "뒷좌석 오른쪽").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun `an unlocated speaker shows up as a passenger`() {
        val latest = line("저기", seat = null)
        show(UiState(screen = Screen.TALK, captions = listOf(latest), recentCaption = latest))

        compose.onNodeWithText("승객 · 방금").assertIsDisplayed()
    }

    @Test
    fun `reply buttons speak the reply`() {
        val spoken = mutableListOf<String>()
        show(
            UiState(screen = Screen.TALK, gear = GearPos.D, captions = listOf(line("세워 줘"))),
            UiActions(onReply = { spoken += it }),
        )

        compose.onNodeWithText("조금 이따 세울게").performClick()

        assertEquals(listOf("조금 이따 세울게"), spoken)
    }

    @Test
    fun `buttons are unresponsive while a reply is being spoken`() {
        val spoken = mutableListOf<String>()
        show(
            UiState(screen = Screen.TALK, replying = "응, 알겠어", captions = listOf(line("세워 줘"))),
            UiActions(onReply = { spoken += it }),
        )

        compose.onNodeWithText("조금 이따 세울게").assertIsNotEnabled()
        compose.onNodeWithContentDescription("말하는 중").assertIsDisplayed()
    }

    @Test
    fun `a short confirmation appears after a reply`() {
        show(UiState(screen = Screen.TALK, toast = "답장을 보냈어요", captions = listOf(line("세워 줘"))))

        compose.onNodeWithText("답장을 보냈어요").assertIsDisplayed()
    }

    @Test
    fun `the free text reply is only offered while parked`() {
        show(UiState(screen = Screen.TALK, gear = GearPos.D, captions = listOf(line("세워 줘"))))

        compose.onNodeWithText("직접 입력").assertDoesNotExist()
    }

    @Test
    fun `the free text reply opens while parked`() {
        show(UiState(screen = Screen.TALK, gear = GearPos.P, captions = listOf(line("세워 줘"))))

        compose.onNodeWithText("직접 입력").performClick()

        compose.onNodeWithContentDescription("답장 입력창").assertIsDisplayed()
    }

    @Test
    fun `a long sentence is cut while driving`() {
        val long = "가".repeat(31)
        show(UiState(screen = Screen.TALK, gear = GearPos.D, captions = listOf(line(long))))

        compose.onNodeWithText("가".repeat(30) + "…").assertIsDisplayed()
    }

    @Test
    fun `a long sentence is shown whole while parked`() {
        val long = "가".repeat(31)
        show(UiState(screen = Screen.TALK, gear = GearPos.P, captions = listOf(line(long))))

        compose.onNodeWithText(long).assertIsDisplayed()
    }

    @Test
    fun `window screen shows the outside sentence, the introduction button and quick replies`() {
        val spoken = mutableListOf<String>()
        show(
            UiState(
                screen = Screen.WINDOW,
                driverWindowPercent = 60,
                outside = CaptionLine(Speaker.Outside, "음주 단속 중입니다", 0, true),
            ),
            UiActions(onReply = { spoken += it }),
        )

        compose.onNodeWithText("창문 너머 대화").assertIsDisplayed()
        compose.onNodeWithText("운전석 창문 열림").assertIsDisplayed()
        compose.onNodeWithText("차 밖 · 운전석 쪽").assertIsDisplayed()
        compose.onNodeWithText("음주 단속 중입니다").assertIsDisplayed()
        compose.onNodeWithText("\"${Copy.WINDOW_INTRO}\"").assertIsDisplayed()

        compose.onNodeWithText("안내 문장 말하기").performClick()
        compose.onNodeWithText("한 번 더 말씀해 주세요").performClick()

        assertEquals(listOf(Copy.WINDOW_INTRO, "한 번 더 말씀해 주세요"), spoken)
    }

    @Test
    fun `window screen invites the person outside before anyone has spoken`() {
        show(UiState(screen = Screen.WINDOW, gear = GearPos.P, driverWindowPercent = 60))

        compose.onNodeWithText("말씀을 기다리고 있어요").assertIsDisplayed()
    }

    @Test
    fun `recap lists the trip summary and every entry`() {
        val trip = TripSummary(
            startedAt = 0,
            endedAt = 48 * 60_000L,
            distanceMeters = 21_000.0,
            entries = listOf(
                TripEntry(EntryKind.SIREN, 0, "뒤쪽에서 구급차 사이렌 · 약 20초", "테헤란로"),
                TripEntry(EntryKind.HORN, 60_000, "경적 · 약 2초", "역삼역 사거리"),
                TripEntry(EntryKind.TALK, 120_000, "조수석: \"저기\"", null),
                TripEntry(EntryKind.TALK, 180_000, "조수석: \"고마워\"", null),
            ),
        )
        show(UiState(screen = Screen.RECAP, trip = trip), UiActions(timeText = { "08:24" }))

        compose.onNodeWithText("놓친 소리 다시보기").assertIsDisplayed()
        compose.onNodeWithText("정차 중에만 열려요").assertIsDisplayed()
        compose.onNodeWithText("48분 · 21km").assertIsDisplayed()
        compose.onNodeWithText("긴급차량 사이렌").assertIsDisplayed()
        compose.onNodeWithText("2문장").assertIsDisplayed()
        compose.onNodeWithText("뒤쪽에서 구급차 사이렌 · 약 20초").assertIsDisplayed()
        compose.onNodeWithText("테헤란로").assertIsDisplayed()
    }

    @Test
    fun `settings give way to an alert and to a person at the window`() {
        val parked = UiState(gear = GearPos.P)

        assertTrue(settingsVisible(parked, requested = true))
        assertFalse(settingsVisible(parked, requested = false))
        assertFalse(settingsVisible(parked.copy(alert = sirenAlert), requested = true))
        assertFalse(settingsVisible(parked.copy(screen = Screen.WINDOW), requested = true))
        assertFalse(settingsVisible(UiState(gear = GearPos.D), requested = true))
        assertFalse(settingsVisible(UiState(gear = null), requested = true))
    }

    @Test
    fun `recap says so when nothing has been missed`() {
        show(UiState(screen = Screen.RECAP))

        compose.onNodeWithText("이번 운행에 놓친 소리가 없어요").assertIsDisplayed()
    }
}

/** The one-pane portrait window: only the alert card and the latest caption while driving. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1184dp-port-mdpi")
class PortraitScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: UiState, actions: UiActions = UiActions()) {
        compose.setContent { AppRoot(state = state, actions = actions) }
    }

    @Test
    fun `while driving only the alert card and the latest caption are shown`() {
        val latest = line("구급차 지나가면 천천히 가자")
        show(UiState(gear = GearPos.D, alert = sirenAlert, captions = listOf(latest), recentCaption = latest))

        compose.onNodeWithText("구급차가 다가와요").assertIsDisplayed()
        compose.onNodeWithText("구급차 지나가면 천천히 가자").assertIsDisplayed()
        compose.onNodeWithText("빠른 3회 점멸 = 긴급차량").assertDoesNotExist()
        compose.onNodeWithText("주행").assertDoesNotExist()
    }

    @Test
    fun `talking while driving leaves out the seat map, the history and the replies`() {
        show(
            UiState(
                gear = GearPos.D,
                screen = Screen.TALK,
                captions = listOf(line("강남역 쪽으로 가 줘"), line("저기 세워 줘")),
            ),
        )

        compose.onNodeWithText("저기 세워 줘").assertIsDisplayed()
        compose.onNodeWithText("강남역 쪽으로 가 줘").assertDoesNotExist()
        compose.onNodeWithText("뒷좌석 왼쪽").assertDoesNotExist()
        compose.onNodeWithText("응, 알겠어").assertDoesNotExist()
    }

    @Test
    fun `an idle drive still tells the driver the app is listening`() {
        show(UiState(gear = GearPos.D, listening = Listening.ACTIVE))

        compose.onNodeWithText("조용해요").assertIsDisplayed()
    }

    @Test
    fun `tabs and settings come back once parked`() {
        show(UiState(gear = GearPos.P))

        compose.onNodeWithText("기록").assertIsDisplayed()
        compose.onNodeWithText("설정").assertIsDisplayed()
    }

    @Test
    fun `at the window the introduction button is still there because the car is parked`() {
        val spoken = mutableListOf<String>()
        show(
            UiState(screen = Screen.WINDOW, driverWindowPercent = 60),
            UiActions(onReply = { spoken += it }),
        )

        compose.onNodeWithText("안내 문장 말하기").performClick()

        assertEquals(listOf(Copy.WINDOW_INTRO), spoken)
    }
}
