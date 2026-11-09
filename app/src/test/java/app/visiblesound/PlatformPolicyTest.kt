package app.visiblesound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Rules from the Pleos Connect App Development Guide that a review would reject. */
class PlatformPolicyTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val activity = File("src/main/java/app/visiblesound/MainActivity.kt").readText()

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
}
