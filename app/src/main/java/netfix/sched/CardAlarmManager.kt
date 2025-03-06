package netfix.sched

import android.app.IntentService
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import netfix.recs.RecsService

class CardAlarmManager : IntentService("CardAlarmManager") {
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onHandleIntent(intent: Intent?) {
        RecsService.updateRecs()
    }
}