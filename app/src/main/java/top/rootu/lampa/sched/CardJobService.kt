package top.rootu.lampa.sched

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.rootu.lampa.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CardJobService : JobService() {
    private val isJobComplete = AtomicBoolean(false)
    private val jobScope = CoroutineScope(Dispatchers.Default)

    override fun onStartJob(params: JobParameters?): Boolean {
        isJobComplete.set(false)

        jobScope.launch {
            try {
                if (BuildConfig.DEBUG) Log.i(
                    "CardJobService",
                    "CardJobService call updateCards(sync: true)"
                )
                Scheduler.updateContent(true) // Update cards with sync enabled
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("CardJobService", "Error updating cards", e)
            } finally {
                isJobComplete.set(true)
                jobFinished(params, false) // Notify the system that the job is complete
            }
        }

        // Return true to indicate that the job is running on a separate thread
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Return true to reschedule the job if it was stopped prematurely
        return !isJobComplete.get()
    }

    override fun onDestroy() {
        super.onDestroy()
        jobScope.cancel() // Cancel the coroutine scope when the service is destroyed
    }
}