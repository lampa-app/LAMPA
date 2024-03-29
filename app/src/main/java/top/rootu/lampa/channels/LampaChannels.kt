package top.rootu.lampa.channels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.models.LampaCard
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

            if (!sync) {
                val thR = thread {
                    recs = LampaProvider.get(LampaProvider.Recs, true)?.items.orEmpty().take(MAX_RECS_CAP)
                }
                val thL = thread {
                    like = LampaProvider.get(LampaProvider.Like, false)?.items.orEmpty()
                }
                val thF = thread {
                    book = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
                }
                val thH = thread {
                    hist = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
                }
                thR.join()
                ChannelManager.update(LampaProvider.Recs, recs)
                thL.join()
                ChannelManager.update(LampaProvider.Like, like)
                thF.join()
                ChannelManager.update(LampaProvider.Book, book)
                thH.join()
                ChannelManager.update(LampaProvider.Hist, hist)
                CoroutineScope(Dispatchers.IO).launch {
                    WatchNext.updateWatchNext()
                }
            } else {
                recs = LampaProvider.get(LampaProvider.Recs, true)?.items.orEmpty().take(MAX_RECS_CAP)
                ChannelManager.update(LampaProvider.Recs, recs)
                like = LampaProvider.get(LampaProvider.Like, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.Like, like)
                book = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.Book, book)
                hist = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.Hist, hist)
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
            val list = LampaProvider.get(LampaProvider.Recs, true)?.items.orEmpty().take(MAX_RECS_CAP)
            ChannelManager.update(LampaProvider.Recs, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateLikeChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateLikeChannel")
            val list = LampaProvider.get(LampaProvider.Like, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Like, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateHistChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateHistChannel")
            val list = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Hist, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateBookChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Helpers.isAndroidTV)
            return
        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "updateBookChannel")
            val list = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Book, list)
        }
    }
}