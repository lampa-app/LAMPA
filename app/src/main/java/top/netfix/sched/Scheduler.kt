package top.netfix.sched

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import top.netfix.App
import top.netfix.BuildConfig
import top.netfix.channels.LampaChannels
import top.netfix.helpers.Helpers.isAndroidTV
import top.netfix.recs.RecsService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Scheduler {
    private const val JOBID = 0
    private val lock = Any()
    private var isUpdate = false

    fun scheduleUpdate(sync: Boolean) {
        if (!isAndroidTV)
            return

        if (BuildConfig.DEBUG)
            Log.i("*****", "Scheduler: scheduleUpdate(sync: $sync)")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            jobScheduler(sync)
        else
            alarmScheduler()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun jobScheduler(sync: Boolean) {
        val context = App.context
        thread {
            updateCards(sync)
        }
        val builder = JobInfo.Builder(JOBID, ComponentName(context, CardJobService::class.java))
        builder.setPeriodic(TimeUnit.MINUTES.toMillis(15))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)

        context.getSystemService(JobScheduler::class.java)?.schedule(builder.build())
    }

    private fun alarmScheduler() {
        val context = App.context
        val pendingIntent = Intent(context, CardAlarmManager::class.java)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = PendingIntent.getService(context, JOBID, pendingIntent, FLAG_IMMUTABLE)

        alarmManager.cancel(alarmIntent)
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),
            AlarmManager.INTERVAL_HOUR,
            alarmIntent
        )
    }

    fun updateCards(sync: Boolean) {
        synchronized(lock) {
            if (isUpdate)
                return
            isUpdate = true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            RecsService.updateRecs()
        else
            LampaChannels.update(sync)
        isUpdate = false
    }

}