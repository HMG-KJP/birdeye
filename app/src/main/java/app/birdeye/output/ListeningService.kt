package app.birdeye.output

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.birdeye.core.Listening

/**
 * Keeps the microphone open while another app or the rear camera covers this one: without it the
 * system silences the recording and freezes the process within seconds, and sirens go unheard.
 */
class ListeningService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = AlertNotifier(this).listening(Listening.ACTIVE)
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
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "Birdeye"
        private const val ACTION_STOP = "app.birdeye.STOP_LISTENING"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java))
        }

        /**
         * Goes through the service rather than stopService: a stop that overtakes the start would leave
         * the system waiting for a foreground call that never comes, and it ends the app for that.
         */
        fun stop(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ACTION_STOP))
        }
    }
}
