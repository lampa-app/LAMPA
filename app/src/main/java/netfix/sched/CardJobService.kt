package netfix.sched

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.concurrent.thread

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class CardJobService : JobService() {
    private var complete = false
    override fun onStartJob(params: JobParameters?): Boolean {
        complete = false
        thread {
            Scheduler.updateCards(true)
            complete = true
        }.join(500)
        return !complete
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return !complete
    }
}