package app.birdeye.output

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps the microphone open while another app or the rear camera covers this one: without it the
 * system silences the recording and freezes the process within seconds, and sirens go unheard.
 */
class ListeningService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = AlertNotifier(this).listening()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(AlertNotifier.LISTENING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(AlertNotifier.LISTENING_ID, notification)
            }
        } catch (e: RuntimeException) {
            // Not allowed right now: the app still listens while it is on screen.
            Log.w(TAG, "could not keep listening in the background", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "Birdeye"
    }
}
