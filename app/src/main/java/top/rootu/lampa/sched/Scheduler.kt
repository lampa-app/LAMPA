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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.channels.LampaChannels
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.recs.RecsService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object Scheduler {
    private const val CARDS_JOB_ID = 0
    private val isUpdate = AtomicBoolean(false)

    private val schedulerScope = CoroutineScope(Dispatchers.IO)

    /**
     * Schedules periodic updates for Android TV content.
     *
     * @param sched Whether to schedule updates or perform a one-shot update.
     */
    fun scheduleUpdate(sched: Boolean) {
        if (!isAndroidTV) return

        if (BuildConfig.DEBUG) Log.d("Scheduler", "scheduleUpdate(sched: $sched)")

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
                ComponentName(context, ContentJobService::class.java)
            ).apply {
                setPeriodic(TimeUnit.MINUTES.toMillis(15)) // Schedule every 15 minutes
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require any network
                setRequiresDeviceIdle(false) // Don't require device to be idle
                setRequiresCharging(false) // Don't require device to be charging
            }.build()
            if (BuildConfig.DEBUG) Log.d(
                "Scheduler",
                "jobScheduler schedule periodic updates with ContentJobService"
            )
            jobScheduler?.schedule(jobInfo)
        } else { // Perform a one-shot update in a background thread
            schedulerScope.launch {
                if (BuildConfig.DEBUG) Log.d(
                    "Scheduler",
                    "jobScheduler call updateContent(sync = $sched)"
                )
                updateContent(sync = sched)
            }
        }
    }

    /**
     * Uses AlarmManager to schedule periodic updates (for older Android versions).
     */
    private fun alarmScheduler() {
        val context = App.context
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        @Suppress("DEPRECATION")
        // Create a PendingIntent for the alarm
        val pendingIntent = Intent(context, ContentAlarmManager::class.java).let { intent ->
            PendingIntent.getService(context, CARDS_JOB_ID, intent, 0)
        }

        // Cancel any existing alarms
        alarmManager.cancel(pendingIntent)

        // Schedule a repeating alarm
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (BuildConfig.DEBUG) Log.d(
                "Scheduler",
                "alarmScheduler schedule AlarmManager for 1H update Recs."
            )
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, // Wake up the device
                SystemClock.elapsedRealtime(), // Start time
                AlarmManager.INTERVAL_HOUR, // Repeat every hour
                pendingIntent // PendingIntent to trigger
            )
        }
    }

    /**
     * Updates the Android TV Home content.
     *
     * @param sync Whether to update TV channels sequentially or in parallel.
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun updateContent(sync: Boolean) {
        if (!isUpdate.compareAndSet(false, true))
            return // Early return if update is already running
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                if (BuildConfig.DEBUG) Log.d(
                    "Scheduler",
                    "updateContent call RecsService.updateRecs()"
                )
                RecsService.updateRecs() // Update recommendations for older versions
            } else {
                if (BuildConfig.DEBUG) Log.d(
                    "Scheduler",
                    "updateContent call LampaChannels.update($sync)"
                )
                LampaChannels.update(sync) // Update channels for newer versions
            }
        } finally {
            isUpdate.set(false)
        }
    }
}