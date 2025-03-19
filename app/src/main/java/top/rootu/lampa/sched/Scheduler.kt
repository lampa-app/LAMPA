package top.rootu.lampa.sched

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.channels.LampaChannels
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.recs.RecsService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Scheduler {
    private const val CARDS_JOB_ID = 0
    private val lock = Any()
    private var isUpdate = false

    /**
     * Schedules periodic updates for cards.
     *
     * @param sched Whether to schedule updates or perform a one-shot update.
     */
    fun scheduleUpdate(sched: Boolean) {
        if (!isAndroidTV) return

        if (BuildConfig.DEBUG) Log.i("Scheduler", "scheduleUpdate(sched: $sched)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            jobScheduler(sched)
        } else {
            alarmScheduler()
        }
    }

    /**
     * Uses JobScheduler to schedule periodic updates (for Android M and above).
     *
     * @param sched Whether to schedule updates or perform a one-shot update.
     */
    @TargetApi(Build.VERSION_CODES.M)
    private fun jobScheduler(sched: Boolean) {
        val context = App.context

        if (sched) {
            // Configure the JobScheduler for periodic updates
            val jobScheduler = context.getSystemService(JobScheduler::class.java)
            val jobInfo = JobInfo.Builder(
                CARDS_JOB_ID,
                ComponentName(context, CardJobService::class.java)
            ).apply {
                setPeriodic(TimeUnit.MINUTES.toMillis(15)) // Schedule every 15 minutes
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require any network
                setRequiresDeviceIdle(false) // Don't require device to be idle
                setRequiresCharging(false) // Don't require device to be charging
            }.build()
            if (BuildConfig.DEBUG) Log.i(
                "Scheduler",
                "jobScheduler schedule periodic updates with CardJobService"
            )
            jobScheduler?.schedule(jobInfo)
        } else { // Perform a one-shot update in a background thread
            thread {
                if (BuildConfig.DEBUG) Log.i(
                    "Scheduler",
                    "jobScheduler call updateCards(sync: $sched)"
                )
                updateCards(sched)
            }
        }
    }

    /**
     * Uses AlarmManager to schedule periodic updates (for older Android versions).
     */
    private fun alarmScheduler() {
        val context = App.context
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Create a PendingIntent for the alarm
        val pendingIntent = Intent(context, CardAlarmManager::class.java).let { intent ->
            PendingIntent.getService(context, CARDS_JOB_ID, intent, 0)
        }

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

        // Schedule a repeating alarm
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, // Wake up the device
            SystemClock.elapsedRealtime(), // Start time
            AlarmManager.INTERVAL_HOUR, // Repeat every hour
            pendingIntent // PendingIntent to trigger
        )
    }

    /**
     * Updates the cards data.
     *
     * @param sync Whether to sync data with the server.
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun updateCards(sync: Boolean) {
        synchronized(lock) {
            if (BuildConfig.DEBUG) Log.i(
                "Scheduler",
                "updateCards sync: $sync isUpdate: $isUpdate."
            )
            if (isUpdate) {
                if (BuildConfig.DEBUG) Log.i("Scheduler", "updateCards isUpdate = true, cancel.")
                return // Prevent multiple updates at the same time
            }
            isUpdate = true
        }

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (BuildConfig.DEBUG) Log.i(
                    "Scheduler",
                    "updateCards call RecsService.updateRecs()."
                )
                RecsService.updateRecs() // Update recommendations for older versions
            } else {
                if (BuildConfig.DEBUG) Log.i(
                    "Scheduler",
                    "updateCards call LampaChannels.update($sync)."
                )
                LampaChannels.update(sync) // Update channels for newer versions
            }
        } finally {
            isUpdate = false // Reset the update flag
        }
    }
}