package top.rootu.lampa.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import top.rootu.lampa.MainActivity

// Just+ Player's result_callback: a progress report each time the viewer leaves the player (Home
// included), which setResult alone never delivers.
class PlayerReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MainActivity.onPlayerReport(context, intent)
    }
}
