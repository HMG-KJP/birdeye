package app.visiblesound.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.visiblesound.core.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1700dp-h1184dp-land-mdpi")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the demo switch reports its new value`() {
        var value: Boolean? = null
        compose.setContent {
            SettingsScreen(demoMode = false, replies = Copy.DEFAULT_REPLIES, onDemoMode = { value = it })
        }

        compose.onNodeWithText("데모 모드").performClick()

        assertEquals(true, value)
    }

    @Test
    fun `the script can be started only in demo mode`() {
        var played = false
        compose.setContent {
            SettingsScreen(demoMode = true, replies = Copy.DEFAULT_REPLIES, onPlayDemo = { played = true })
        }

        compose.onNodeWithText("대본 재생").performClick()

        assertTrue(played)
    }

    @Test
    fun `the play button is hidden outside demo mode`() {
        compose.setContent { SettingsScreen(demoMode = false, replies = Copy.DEFAULT_REPLIES) }

        compose.onNodeWithText("대본 재생").assertDoesNotExist()
    }

    @Test
    fun `replies can be edited and saved`() {
        var saved: List<String>? = null
        compose.setContent {
            SettingsScreen(demoMode = false, replies = Copy.DEFAULT_REPLIES, onSaveReplies = { saved = it })
        }

        compose.onNodeWithContentDescription("답장 1").performTextReplacement("지금 세울게")
        compose.onNodeWithText("답장 저장").performScrollTo().performClick()

        assertEquals(listOf("지금 세울게") + Copy.DEFAULT_REPLIES.drop(1), saved)
    }

    @Test
    fun `an empty reply is not saved over the old one`() {
        var saved: List<String>? = null
        compose.setContent {
            SettingsScreen(demoMode = false, replies = Copy.DEFAULT_REPLIES, onSaveReplies = { saved = it })
        }

        compose.onNodeWithContentDescription("답장 2").performTextReplacement("   ")
        compose.onNodeWithText("답장 저장").performScrollTo().performClick()

        assertEquals(Copy.DEFAULT_REPLIES, saved)
    }

    @Test
    fun `settings can be closed`() {
        var closed = false
        compose.setContent {
            SettingsScreen(demoMode = false, replies = Copy.DEFAULT_REPLIES, onClose = { closed = true })
        }

        compose.onNodeWithText("닫기").assertIsDisplayed().performClick()

        assertTrue(closed)
    }
}
