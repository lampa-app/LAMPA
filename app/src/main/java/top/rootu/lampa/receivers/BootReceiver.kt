package top.rootu.lampa.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.sched.Scheduler

// https://stackoverflow.com/questions/46445265/android-8-0-java-lang-illegalstateexception-not-allowed-to-start-service-inten/49846410#49846410
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (Intent.ACTION_BOOT_COMPLETED == intent!!.action) {
            if (BuildConfig.DEBUG)
                Log.d("*****", "onReceive: BOOT_COMPLETED")
            Scheduler.scheduleUpdate(true)
        }
    }
}