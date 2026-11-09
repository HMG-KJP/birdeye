package app.birdeye.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import app.birdeye.core.AlertKind
import app.birdeye.core.Copy
import app.birdeye.core.Direction
import app.birdeye.core.DoorArea
import app.birdeye.core.EntryKind
import app.birdeye.core.GearPos
import app.birdeye.core.Listening
import app.birdeye.core.SignalState
import app.birdeye.core.TripEntry
import app.birdeye.core.TripSummary
import app.birdeye.core.UiState
import app.birdeye.ui.component.SITUATION_CARD
import app.birdeye.ui.component.TitledRows
import app.birdeye.ui.component.needsAttention
import app.birdeye.ui.theme.FormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEADLINE = "뒤쪽에서 사이렌이 들려요"
private const val GUIDANCE = "주변을 확인해 주세요"
private const val BRIEFING_TITLE = "지금 브리핑"
private const val BRIEFING_FIRST = "사이렌이 이어지고 있어요"
private const val BRIEFING_SECOND = "주변 차가 움직이면 이 소리 때문일 수 있어요"
private const val RECENT_TITLE = "최근 소리"
private const val CAPTION = "구급차 지나가면 천천히 가자"
private val RECENT_ROWS = listOf(
    "왼쪽에서 경적 · 약 1초",
    "뒤쪽에서 사이렌 · 약 4초",
    "오른쪽에서 경적 · 약 1초",
    "앞쪽에서 사이렌 · 약 6초",
)

private val siren = UiState(gear = GearPos.D, listening = Listening.ACTIVE, alert = sirenAlert)
private val pastTrip = TripSummary(entries = RECENT_ROWS.map { TripEntry(EntryKind.HORN, 0, it, null) })
private val withCaption = line(CAPTION).let { siren.copy(recentCaption = it, captions = listOf(it)) }

/** The sound and what it means read as one card; what was heard earlier stays small beneath it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-mdpi")
class SituationLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: UiState, formFactor: FormFactor = FormFactor.S) {
        compose.setContent { AppRoot(state = state, actions = UiActions(), formFactor = formFactor) }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text).getBoundsInRoot()

    private fun card() = compose.onNodeWithTag(SITUATION_CARD).getBoundsInRoot()

    private fun assertInCard(text: String, card: DpRect = card()) {
        val box = bounds(text)
        assertTrue("$text spans ${box.top}..${box.bottom}, card ${card.top}..${card.bottom}", box.top >= card.top && box.bottom <= card.bottom)
    }

    /** The card's share of what lies under the page header, down to the window's bottom padding. */
    private fun cardShare(windowHeight: Float, bottomPadding: Float = 24f): Float {
        val card = card()
        return card.height.value / (windowHeight - bottomPadding - card.top.value)
    }

    @Test
    fun `the sound and the briefing share one card about two thirds of the height`() {
        show(siren)

        val share = cardShare(1184f)
        assertTrue("the card takes $share", share in 0.64f..0.70f)
        listOf(HEADLINE, GUIDANCE, BRIEFING_TITLE, BRIEFING_FIRST, BRIEFING_SECOND).forEach { assertInCard(it) }
    }

    @Test
    fun `with no sound the same card holds the quiet words and the briefing`() {
        show(UiState(gear = GearPos.D, listening = Listening.ACTIVE))

        val share = cardShare(1184f)
        assertTrue("the card takes $share", share in 0.64f..0.70f)
        assertInCard(Copy.IDLE_HEADLINE)
        assertInCard(BRIEFING_TITLE)
    }

    @Test
    fun `recent sounds sit under the card in smaller type than the briefing`() {
        show(siren.copy(trip = pastTrip))

        val card = card()
        assertTrue("recent starts at ${bounds(RECENT_TITLE).top}", bounds(RECENT_TITLE).top >= card.bottom)
        assertTrue("title is ${bounds(RECENT_TITLE).height} tall", bounds(RECENT_TITLE).height <= 30.dp)
        RECENT_ROWS.forEach {
            assertTrue("$it ends at ${bounds(it).bottom}", bounds(it).bottom <= 1184.dp)
            assertTrue("$it is ${bounds(it).height} tall", bounds(it).height < bounds(BRIEFING_FIRST).height)
        }
    }

    @Test
    fun `a card with more to say grows rather than clips, and the caption stays below it`() {
        val horn = sirenAlert.copy(kind = AlertKind.HORN, headline = "왼쪽에서 경적이 울렸어요", direction = Direction.LEFT)
        show(withCaption.copy(alsoHeard = horn, openDoors = setOf(DoorArea.PASSENGER, DoorArea.REAR_RIGHT), trip = pastTrip))

        val card = card()
        listOf(HEADLINE, "왼쪽에서 경적이 울렸어요", "조수석 문이 열려 있어요", "오른쪽 뒷문이 열려 있어요", BRIEFING_TITLE, BRIEFING_SECOND)
            .forEach { assertInCard(it, card) }
        assertTrue("caption starts at ${bounds(CAPTION).top}", bounds(CAPTION).top >= card.bottom)
        assertTrue("caption ends at ${bounds(CAPTION).bottom}", bounds(CAPTION).bottom <= 1184.dp)
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `with no room the briefing leaves the card rather than push out the caption`() {
        show(withCaption, FormFactor.WF)

        compose.onNodeWithText(BRIEFING_TITLE).assertIsNotDisplayed()
        assertInCard(GUIDANCE)
        assertTrue("caption starts at ${bounds(CAPTION).top}", bounds(CAPTION).top >= card().bottom)
        assertTrue("caption ends at ${bounds(CAPTION).bottom}", bounds(CAPTION).bottom <= 800.dp)
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `a recent sound that would not fit whole is left out`() {
        show(siren.copy(trip = pastTrip), FormFactor.WF)

        assertInCard(BRIEFING_TITLE)
        RECENT_ROWS.forEach { text ->
            compose.onAllNodesWithText(text).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }.forEach {
                assertTrue("$text ends at ${it.boundsInRoot.bottom}", it.boundsInRoot.bottom <= 800f)
            }
        }
    }

    @Test
    fun `when a caption takes room, recent sounds drop row by row and the newest stay`() {
        show(withCaption.copy(trip = pastTrip))

        val placed = RECENT_ROWS.filter { text ->
            compose.onAllNodesWithText(text).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
        }
        assertTrue("placed $placed", placed.size in 1..3)
        assertEquals(RECENT_ROWS.takeLast(placed.size), placed)
        compose.onNodeWithText(RECENT_TITLE).assertIsDisplayed()
        placed.forEach { assertTrue("$it ends at ${bounds(it).bottom}", bounds(it).bottom <= 1160.dp) }
    }

    @Test
    fun `a title never stands alone, and a row is shown only if it fits whole`() {
        fun rows(height: Int) = @Composable {
            Box(Modifier.width(400.dp).height(height.dp)) {
                TitledRows(gapSize = 0.dp, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag("title"))
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag("first"))
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag("second"))
                }
            }
        }
        var height by mutableStateOf(30)
        compose.setContent { rows(height)() }
        compose.onNodeWithTag("title").assertIsNotDisplayed()
        compose.onNodeWithTag("first").assertIsNotDisplayed()

        height = 50
        compose.waitForIdle()
        compose.onNodeWithTag("title").assertIsDisplayed()
        compose.onNodeWithTag("first").assertIsDisplayed()
        compose.onNodeWithTag("second").assertIsNotDisplayed()
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `two sounds and a caption still fit the smallest wide window`() {
        val horn = sirenAlert.copy(kind = AlertKind.HORN, headline = "왼쪽에서 경적이 울렸어요", direction = Direction.LEFT)
        show(withCaption.copy(alsoHeard = horn), FormFactor.WF)

        assertInCard(GUIDANCE)
        assertInCard("왼쪽에서 경적이 울렸어요")
        assertTrue("caption ends at ${bounds(CAPTION).bottom}", bounds(CAPTION).bottom <= 800.dp)
    }

    @Test
    @Config(qualifiers = "w1256dp-h800dp-land-mdpi")
    fun `two sounds, an open door and a caption still fit the smallest wide window`() {
        val horn = sirenAlert.copy(kind = AlertKind.HORN, headline = "왼쪽에서 경적이 울렸어요", direction = Direction.LEFT)
        show(withCaption.copy(alsoHeard = horn, openDoors = setOf(DoorArea.PASSENGER)), FormFactor.WF)

        assertInCard("조수석 문이 열려 있어요")
        assertTrue("caption ends at ${bounds(CAPTION).bottom}", bounds(CAPTION).bottom <= 800.dp)
    }

    @Test
    fun `a microphone that stopped says so in the card, with nothing to brief`() {
        show(UiState(gear = GearPos.D, listening = Listening.MIC_ERROR))

        assertInCard("소리를 듣지 못하고 있어요")
        compose.onNodeWithText(BRIEFING_TITLE).assertDoesNotExist()
    }

    @Test
    fun `with the camera up only the card and the caption remain`() {
        show(withCaption.copy(turnSignal = SignalState.LEFT))

        assertInCard(GUIDANCE)
        compose.onNodeWithText(BRIEFING_TITLE).assertDoesNotExist()
        compose.onNodeWithText(RECENT_TITLE).assertDoesNotExist()
        compose.onNodeWithText(CAPTION).assertIsDisplayed()
    }

    @Test
    fun `only a sound to notice or a stopped microphone makes the card stand out`() {
        assertTrue(needsAttention(sirenAlert, Listening.ACTIVE))
        assertTrue(needsAttention(sirenAlert.copy(kind = AlertKind.HORN), Listening.ACTIVE))
        assertTrue(needsAttention(sirenAlert.copy(kind = AlertKind.TRAFFIC), Listening.ACTIVE))
        assertFalse(needsAttention(sirenAlert.copy(kind = AlertKind.VEHICLE), Listening.ACTIVE))
        assertFalse(needsAttention(null, Listening.ACTIVE))
        assertFalse(needsAttention(null, Listening.DEMO))
        assertFalse(needsAttention(null, Listening.NOT_STARTED))
        assertTrue(needsAttention(null, Listening.MIC_ERROR))
        assertTrue(needsAttention(null, Listening.NO_PERMISSION))
    }
}

/** A narrow window keeps the same card, with one briefing line and the map below instead of a list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1184dp-port-mdpi")
class PortraitSituationLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bounds(text: String) = compose.onNodeWithText(text).getBoundsInRoot()

    @Test
    fun `the first briefing line sits in the card, above the caption and the map`() {
        compose.setContent { AppRoot(withCaption, UiActions()) }

        val card = compose.onNodeWithTag(SITUATION_CARD).getBoundsInRoot()
        listOf(HEADLINE, BRIEFING_TITLE, BRIEFING_FIRST).forEach {
            assertTrue("$it spans ${bounds(it).top}..${bounds(it).bottom}", bounds(it).top >= card.top && bounds(it).bottom <= card.bottom)
        }
        compose.onNodeWithText(BRIEFING_SECOND).assertDoesNotExist()
        compose.onNodeWithText(RECENT_TITLE).assertDoesNotExist()
        assertTrue("caption starts at ${bounds(CAPTION).top}", bounds(CAPTION).top >= card.bottom)
    }
}
