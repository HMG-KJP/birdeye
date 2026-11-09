package app.birdeye

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Rules from the Pleos Connect App Development Guide that a review would reject. */
class PlatformPolicyTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val activity = File("src/main/java/app/birdeye/MainActivity.kt").readText()

    @Test
    fun `an app without rotary controller support declares so`() {
        val declaration = Regex("""com\.hmg\.rotary\.ROTARY_SUPPORTED"\s+android:value="false"""")

        assertTrue(declaration.containsMatchIn(manifest))
    }

    @Test
    fun `the app is usable while driving because it declares distraction optimization`() {
        val declaration = Regex("""distractionOptimized"\s+android:value="true"""")

        assertTrue(declaration.containsMatchIn(manifest))
    }

    @Test
    fun `the back key hides the app instead of closing it`() {
        assertTrue(activity.contains("moveTaskToBack(true)"))
        assertFalse(activity.contains("finish()"))
        assertFalse(activity.contains("finishAffinity()"))
    }

    @Test
    fun `the app keeps listening while hidden, and tells the system why`() {
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(Regex("""<service[^>]*ListeningService[^>]*foregroundServiceType="microphone"""").containsMatchIn(manifest))
    }

    @Test
    fun `closing the app stops the listening`() {
        assertTrue(Regex("""<service[^>]*ListeningService[^>]*stopWithTask="true"""").containsMatchIn(manifest))
    }
}
