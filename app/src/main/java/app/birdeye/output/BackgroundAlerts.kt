package app.birdeye.output

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.birdeye.MainActivity
import app.birdeye.R
import app.birdeye.core.Alert
import app.birdeye.core.Copy
import app.birdeye.core.Listening

/**
 * Navigation fills the screen by default, so the app is often hidden when a sound comes. Such a sound
 * has to reach the driver another way: the cabin light always fires, and this adds the words.
 */
object BackgroundAlerts {
    sealed interface Decision
    data class Show(val title: String, val text: String?) : Decision
    data object Clear : Decision

    fun decide(previous: Alert?, current: Alert?, appVisible: Boolean): Decision? = when {
        appVisible -> Clear
        current == null -> if (previous != null) Clear else null
        previous?.firedAt == current.firedAt -> null
        else -> Show(current.headline, current.guidance)
    }
}

/** The system's own notification, so the alert looks like every other one in the car. */
class AlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java).also { manager ->
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, Copy.LISTENING_CHANNEL, NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SOUNDS, Copy.SOUNDS_CHANNEL, NotificationManager.IMPORTANCE_HIGH).apply {
                // A chime would reach the microphone and means nothing to the driver.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private val openApp: PendingIntent
        get() = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    fun listening(): Notification = Notification.Builder(context, CHANNEL_LISTENING)
        .setSmallIcon(R.drawable.ic_stat_birdeye)
        .setContentTitle(Copy.listeningSubtitle(Listening.ACTIVE))
        .setContentIntent(openApp)
        .setOngoing(true)
        .build()

    fun show(title: String, text: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(
            SOUND_ID,
            Notification.Builder(context, CHANNEL_SOUNDS)
                .setSmallIcon(R.drawable.ic_stat_birdeye)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun clear() = manager.cancel(SOUND_ID)

    companion object {
        const val LISTENING_ID = 1
        private const val SOUND_ID = 2
        private const val CHANNEL_LISTENING = "listening"
        private const val CHANNEL_SOUNDS = "sounds"
    }
}
