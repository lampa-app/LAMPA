package netfix.channels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import netfix.app.BuildConfig
import netfix.content.NetfixProvider
import netfix.helpers.Helpers
import netfix.models.NetfixCard
import kotlin.concurrent.thread

object NetfixChannels {
    private const val TAG = "NetfixChannels"
    private val lock = Any()
    private const val MAX_RECS_CAP = 30 // For recs only

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun update(sync: Boolean = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return

        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "update(sync: $sync)")

            var recs = emptyList<NetfixCard>()
            var like = emptyList<NetfixCard>()
            var book = emptyList<NetfixCard>()
            var hist = emptyList<NetfixCard>()
            var look = emptyList<NetfixCard>()
            var view = emptyList<NetfixCard>()
            var schd = emptyList<NetfixCard>()
            var cont = emptyList<NetfixCard>()
            var thrw = emptyList<NetfixCard>()

            if (!sync) {
                val thRE = thread {
                    recs = NetfixProvider.get(NetfixProvider.RECS, true)?.items.orEmpty()
                        .take(MAX_RECS_CAP)
                }
                val thLI = thread {
                    like = NetfixProvider.get(NetfixProvider.LIKE, false)?.items.orEmpty()
                }
                val thFB = thread {
                    book = NetfixProvider.get(NetfixProvider.BOOK, false)?.items.orEmpty()
                }
                val thHI = thread {
                    hist = NetfixProvider.get(NetfixProvider.HIST, false)?.items.orEmpty()
                }
                val thLO = thread {
                    look = NetfixProvider.get(NetfixProvider.LOOK, false)?.items.orEmpty()
                }
                val thVI = thread {
                    view = NetfixProvider.get(NetfixProvider.VIEW, false)?.items.orEmpty()
                }
                val thSC = thread {
                    schd = NetfixProvider.get(NetfixProvider.SCHD, false)?.items.orEmpty()
                }
                val thCO = thread {
                    cont = NetfixProvider.get(NetfixProvider.CONT, false)?.items.orEmpty()
                }
                val thTH = thread {
                    thrw = NetfixProvider.get(NetfixProvider.THRW, false)?.items.orEmpty()
                }
                thRE.join()
                ChannelManager.update(NetfixProvider.RECS, recs)
                thLI.join()
                ChannelManager.update(NetfixProvider.LIKE, like)
                thFB.join()
                ChannelManager.update(NetfixProvider.BOOK, book)
                thHI.join()
                ChannelManager.update(NetfixProvider.HIST, hist)
                thLO.join()
                ChannelManager.update(NetfixProvider.LOOK, look)
                thVI.join()
                ChannelManager.update(NetfixProvider.VIEW, view)
                thSC.join()
                ChannelManager.update(NetfixProvider.SCHD, schd)
                thCO.join()
                ChannelManager.update(NetfixProvider.CONT, cont)
                thTH.join()
                ChannelManager.update(NetfixProvider.THRW, thrw)
                CoroutineScope(Dispatchers.IO).launch {
                    WatchNext.updateWatchNext()
                }
            } else {
                recs = NetfixProvider.get(NetfixProvider.RECS, true)?.items.orEmpty()
                    .take(MAX_RECS_CAP)
                ChannelManager.update(NetfixProvider.RECS, recs)
                like = NetfixProvider.get(NetfixProvider.LIKE, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.LIKE, like)
                book = NetfixProvider.get(NetfixProvider.BOOK, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.BOOK, book)
                hist = NetfixProvider.get(NetfixProvider.HIST, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.HIST, hist)
                look = NetfixProvider.get(NetfixProvider.LOOK, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.LOOK, look)
                view = NetfixProvider.get(NetfixProvider.VIEW, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.VIEW, view)
                schd = NetfixProvider.get(NetfixProvider.SCHD, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.SCHD, schd)
                cont = NetfixProvider.get(NetfixProvider.CONT, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.CONT, cont)
                thrw = NetfixProvider.get(NetfixProvider.THRW, false)?.items.orEmpty()
                ChannelManager.update(NetfixProvider.THRW, thrw)
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
                NetfixProvider.get(NetfixProvider.RECS, true)?.items.orEmpty().take(MAX_RECS_CAP)
            ChannelManager.update(NetfixProvider.RECS, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateChanByName(name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateChanByName($name)")
            val list = NetfixProvider.get(name, false)?.items.orEmpty()
            ChannelManager.update(name, list)
        }
    }
}