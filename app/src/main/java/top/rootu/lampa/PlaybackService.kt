package top.rootu.lampa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import top.rootu.lampa.helpers.Helpers.debugLog

/**
 * Foreground service that keeps the app process at foreground importance while an external
 * video player is in front. Without it, aggressive OEM/Android background freezers
 * (e.g. OPPO/OnePlus HANS / Osense, Android Cached Apps Freezer) suspend the whole process
 * — including the WebView renderer thread — which stops the RCH socket heartbeat and drops
 * the connection during playback.
 *
 * Started/stopped by [MainActivity] around external playback, gated by the user setting.
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        debugLog(TAG, "started")
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.keep_connection_notification))
            .setSmallIcon(R.drawable.lampa_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.keep_connection_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                nm.createNotificationChannel(channel)
            }
        }
        return CHANNEL_ID
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "playback_keepalive"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            try {
                val intent = Intent(context, PlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(intent)
                else
                    context.startService(intent)
            } catch (e: Exception) {
                debugLog(TAG, "start failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                debugLog(TAG, "stop failed: ${e.message}")
            }
        }
    }
}
