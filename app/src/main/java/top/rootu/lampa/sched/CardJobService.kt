package top.rootu.lampa.sched

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import top.rootu.lampa.BuildConfig
import kotlin.concurrent.thread

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CardJobService : JobService() {
    private var isJobComplete = false

    /**
     * Called when the job is started.
     *
     * @param params The parameters for the job.
     * @return True if the job is running on a separate thread, false otherwise.
     */
    override fun onStartJob(params: JobParameters?): Boolean {
        isJobComplete = false

        // Start a background thread to update cards
        thread {
            if (BuildConfig.DEBUG) Log.i("Scheduler", "CardJobService call updateCards(sync: true)")
            Scheduler.updateCards(true) // Update cards with sync enabled
            isJobComplete = true
            jobFinished(params, false) // Notify the system that the job is complete
        }

        // Return true to indicate that the job is running on a separate thread
        return true
    }

    /**
     * Called when the job is stopped.
     *
     * @param params The parameters for the job.
     * @return True if the job should be rescheduled, false otherwise.
     */
    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true to reschedule the job if it was stopped prematurely
        return !isJobComplete
    }
}