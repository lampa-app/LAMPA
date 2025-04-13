package top.rootu.lampa.channels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable

object LampaChannels {
    private const val TAG = "LampaChannels"
    private val lock = Any()
    private const val MAX_RECS_CAP = 30

    @RequiresApi(Build.VERSION_CODES.O)
    fun update(sync: Boolean = true) {
        if (!isTvContentProviderAvailable) return

        synchronized(lock) {
            if (BuildConfig.DEBUG) Log.d(TAG, "update(sync: $sync)")

            // List of channel names and their corresponding update functions
            val channels = listOf(
                LampaProvider.RECS to {
                    LampaProvider.get(LampaProvider.RECS, true)?.items.orEmpty().take(MAX_RECS_CAP)
                },
                LampaProvider.LIKE to {
                    LampaProvider.get(
                        LampaProvider.LIKE,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.BOOK to {
                    LampaProvider.get(
                        LampaProvider.BOOK,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.HIST to {
                    LampaProvider.get(
                        LampaProvider.HIST,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.LOOK to {
                    LampaProvider.get(
                        LampaProvider.LOOK,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.VIEW to {
                    LampaProvider.get(
                        LampaProvider.VIEW,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.SCHD to {
                    LampaProvider.get(
                        LampaProvider.SCHD,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.CONT to {
                    LampaProvider.get(
                        LampaProvider.CONT,
                        false
                    )?.items.orEmpty()
                },
                LampaProvider.THRW to {
                    LampaProvider.get(
                        LampaProvider.THRW,
                        false
                    )?.items.orEmpty()
                }
            )

            if (!sync) {
                // Use coroutines to update data concurrently
                CoroutineScope(Dispatchers.Default).launch {
                    val deferredResults = channels.map { (name, fetchFunction) ->
                        async { name to fetchFunction() }
                    }
                    deferredResults.forEach { deferred ->
                        val (name, items) = deferred.await()
                        ChannelManager.update(name, items)
                    }
                    // Update WatchNext after all channels are updated
                    WatchNext.updateWatchNext()
                }
            } else {
                // Fetch data sequentially
                channels.forEach { (name, fetchFunction) ->
                    val items = fetchFunction()
                    ChannelManager.update(name, items)
                }
                // Update WatchNext after all channels are updated
                CoroutineScope(Dispatchers.Default).launch {
                    WatchNext.updateWatchNext()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateRecsChannel() {
        if (!isTvContentProviderAvailable) return
        synchronized(lock) {
            if (BuildConfig.DEBUG) Log.d(TAG, "updateRecsChannel()")
            val list =
                LampaProvider.get(LampaProvider.RECS, true)?.items.orEmpty().take(MAX_RECS_CAP)
            ChannelManager.update(LampaProvider.RECS, list)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateChanByName(name: String) {
        if (!isTvContentProviderAvailable) return
        synchronized(lock) {
            if (BuildConfig.DEBUG) Log.d(TAG, "updateChanByName($name)")
            val list = LampaProvider.get(name, false)?.items.orEmpty()
            ChannelManager.update(name, list)
        }
    }
}