package top.rootu.lampa.sched

import android.app.IntentService
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import top.rootu.lampa.recs.RecsService

@Suppress("DEPRECATION")
@Deprecated("IntentService is deprecated.")
class ContentAlarmManager : IntentService("ContentAlarmManager") {

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    @Suppress("DEPRECATION")
    @Deprecated("Migrate to WorkManager's doWork()")
    override fun onHandleIntent(intent: Intent?) {
        RecsService.updateRecs()
    }
}