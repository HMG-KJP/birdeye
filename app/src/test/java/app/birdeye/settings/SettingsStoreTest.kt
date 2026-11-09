package app.birdeye.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.birdeye.core.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `demo mode is off and the replies are the defaults on first run`() {
        val store = SettingsStore(context)

        assertFalse(store.demoMode)
        assertEquals(Copy.DEFAULT_REPLIES, store.replies)
    }

    @Test
    fun `settings survive being reopened`() {
        SettingsStore(context).apply {
            demoMode = true
            replies = listOf("가", "나, 다", "라", "마")
        }

        val reopened = SettingsStore(context)

        assertTrue(reopened.demoMode)
        assertEquals(listOf("가", "나, 다", "라", "마"), reopened.replies)
    }
}
