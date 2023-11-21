package top.rootu.lampa.channels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.models.TmdbID
import kotlin.concurrent.thread

object LampaChannels {

    private val lock = Any()
    const val MAX_CHANNEL_CAP = 30 // For recs only

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun update(sync: Boolean = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        synchronized(lock) {
            if (BuildConfig.DEBUG)
                Log.i("*****", "LampaChannels: update(sync: $sync)")

            var recs = emptyList<TmdbID>()
            var book = emptyList<TmdbID>()
            var hist = emptyList<TmdbID>()

            if (!sync) {
                val thR = thread {
                    recs = LampaProvider.get(LampaProvider.Recs, true)?.items.orEmpty().take(MAX_CHANNEL_CAP)
                }
                val thF = thread {
                    book = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
                }
                val thH = thread {
                    hist = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
                }
                thR.join()
                ChannelManager.update(LampaProvider.Recs, recs)
                thF.join()
                ChannelManager.update(LampaProvider.Book, book)
                thH.join()
                ChannelManager.update(LampaProvider.Hist, hist)
            } else {
                recs = LampaProvider.get(LampaProvider.Recs, true)?.items.orEmpty().take(MAX_CHANNEL_CAP)
                ChannelManager.update(LampaProvider.Recs, recs)
                book = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.Book, book)
                hist = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
                ChannelManager.update(LampaProvider.Hist, hist)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateRecsChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        synchronized(lock) {
            val list = LampaProvider.get(LampaProvider.Recs, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Recs, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateHistChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        synchronized(lock) {
            val list = LampaProvider.get(LampaProvider.Hist, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Hist, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateBookChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        synchronized(lock) {
            val list = LampaProvider.get(LampaProvider.Book, false)?.items.orEmpty()
            ChannelManager.update(LampaProvider.Book, list)
        }
    }
}