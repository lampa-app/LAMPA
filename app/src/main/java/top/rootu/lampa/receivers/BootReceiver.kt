package top.rootu.lampa.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.helpers.Helpers.openLampa
import top.rootu.lampa.helpers.Prefs.autostart
import top.rootu.lampa.sched.Scheduler


// https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten/49846410#49846410
class BootReceiver : BroadcastReceiver() {
    private var screenOff = false
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: App.context
        if (Intent.ACTION_BOOT_COMPLETED == intent!!.action) {
            val autostart = (ctx.autostart?.toIntOrNull() ?: 0) > 0
            if (BuildConfig.DEBUG)
                Log.d("*****", "onReceive: BOOT_COMPLETED. Autostart: $autostart")
            Scheduler.scheduleUpdate(true)
            if (autostart) {
                // https://developer.android.com/guide/components/activities/background-starts
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                    openLampa()
                // https://developer.android.com/develop/ui/views/notifications/time-sensitive
                else {
                    val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    try {
                        ctx.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e("*****", "Launch on boot failed: $e")
                    }
                }
            }
        } else if (Intent.ACTION_SCREEN_OFF == intent.action) {
            screenOff = true
            if (BuildConfig.DEBUG)
                Log.d("*****", "onReceive SCREEN_OFF")
        } else if (Intent.ACTION_SCREEN_ON == intent.action) {
            screenOff = false
            if (BuildConfig.DEBUG)
                Log.d("*****", "onReceive SCREEN_ON")
            if ((ctx.autostart?.toIntOrNull() ?: 0) == 2) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                    openLampa()
            }
        }
    }
}