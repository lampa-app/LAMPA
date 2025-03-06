package netfix.channels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import netfix.app.BuildConfig
import netfix.content.LampaProvider
import netfix.helpers.Helpers
import netfix.models.LampaCard
import kotlin.concurrent.thread

object LampaChannels {
    private const val TAG = "LampaChannels"
    private val lock = Any()
    private const val MAX_RECS_CAP = 30 // For recs only

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun update(sync: Boolean = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return

        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "update(sync: $sync)")

            var recs = emptyList<LampaCard>()
            var like = emptyList<LampaCard>()
            var book = emptyList<LampaCard>()
            var hist = emptyList<LampaCard>()
            var look = emptyList<LampaCard>()
            var view = emptyList<LampaCard>()
            var schd = emptyList<LampaCard>()
            var cont = emptyList<LampaCard>()
            var thrw = emptyList<LampaCard>()

            if (!sync) {
                val thRE = thread {
                    recs = LampaProvider.get(LampaProvider.RECS, true)?.items.orEmpty()
                        .take(MAX_RECS_CAP)
                }
                val thLI = thread {
                    like = LampaProvider.get(LampaProvider.LIKE, false)?.items.orEmpty()
                }
                val thFB = thread {
                    book = LampaProvider.get(LampaProvider.BOOK, false)?.items.orEmpty()
                }
                val thHI = thread {
                    hist = LampaProvider.get(LampaProvider.HIST, false)?.items.orEmpty()
                }
                val thLO = thread {
                    look = LampaProvider.get(LampaProvider.LOOK, false)?.items.orEmpty()
                }
                val thVI = thread {
                    view = LampaProvider.get(LampaProvider.VIEW, false)?.items.orEmpty()
                }
                val thSC = thread {
                    schd = LampaProvider.get(LampaProvider.SCHD, false)?.items.orEmpty()
                }
                val thCO = thread {
                    cont = LampaProvider.get(LampaProvider.CONT, false)?.items.orEmpty()
                }
                val thTH = thread {
                    thrw = LampaProvider.get(LampaProvider.THRW, false)?.items.orEmpty()
                }
                thRE.join()
                ChannelManager.update(LampaProvider.RECS, recs)
                thLI.join()
                ChannelManager.update(LampaProvider.LIKE, like)
                thFB.join()
                ChannelManager.update(LampaProvider.BOOK, book)
                thHI.join()
                ChannelManager.update(LampaProvider.HIST, hist)
                thLO.join()
                ChannelManager.update(LampaProvider.LOOK, look)
                thVI.join()
                ChannelManager.update(LampaProvider.VIEW, view)
                thSC.join()
                ChannelManager.update(LampaProvider.SCHD, schd)
                thCO.join()
                ChannelManager.update(LampaProvider.CONT, cont)
                thTH.join()
                ChannelManager.update(LampaProvider.THRW, thrw)
                CoroutineScope(Dispatchers.IO).launch {
                    WatchNext.updateWatchNext()
                }
            } else {
                recs = LampaProvider.get(LampaProvider.RECS, true)?.items.orEmpty()
                    .take(MAX_RECS_CAP)
                ChannelManager.update(LampaProvider.RECS, recs)
                like = LampaProvider.get(LampaProvider.LIKE, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.LIKE, like)
                book = LampaProvider.get(LampaProvider.BOOK, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.BOOK, book)
                hist = LampaProvider.get(LampaProvider.HIST, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.HIST, hist)
                look = LampaProvider.get(LampaProvider.LOOK, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.LOOK, look)
                view = LampaProvider.get(LampaProvider.VIEW, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.VIEW, view)
                schd = LampaProvider.get(LampaProvider.SCHD, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.SCHD, schd)
                cont = LampaProvider.get(LampaProvider.CONT, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.CONT, cont)
                thrw = LampaProvider.get(LampaProvider.THRW, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.THRW, thrw)
                CoroutineScope(Dispatchers.IO).launch {
                    WatchNext.updateWatchNext()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateRecsChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateRecsChannel")
            val list =
                LampaProvider.get(LampaProvider.RECS, true)?.items.orEmpty().take(MAX_RECS_CAP)
            ChannelManager.update(LampaProvider.RECS, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateChanByName(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateChanByName($name)")
            val list = LampaProvider.get(name, false)?.items.orEmpty()
            ChannelManager.update(name, list)
        }
    }
}