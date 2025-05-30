package top.rootu.lampa.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.TvContractCompat
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.channels.ChannelManager
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.helpers.ChannelHelper
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import top.rootu.lampa.helpers.Prefs.isInWatchNext
import top.rootu.lampa.sched.Scheduler

private const val TAG: String = "HomeWatch"

@RequiresApi(Build.VERSION_CODES.O)
class HomeWatch() : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.action

        if (action == null || !(isTvContentProviderAvailable))
            return

        val watchNextId = intent.getLongExtra(TvContract.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
        val previewId = intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID, -1L)

        when (action) {

            TvContractCompat.ACTION_INITIALIZE_PROGRAMS -> {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "ACTION_INITIALIZE_PROGRAMS received")
                Scheduler.scheduleUpdate(true)
            }

            TvContractCompat.ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT -> {

                val movieId = WatchNext.getInternalIdFromWatchNextProgramId(watchNextId)
                if (BuildConfig.DEBUG)
                    Log.d(
                        TAG,
                        "ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT, preview $previewId, watch-next $watchNextId movieId $movieId"
                    )
                movieId?.let {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "$it isInWatchNext? ${App.context.isInWatchNext(it)} card ${
                            WatchNext.getCardFromWatchNextProgramId(watchNextId)
                        }"
                    )
                    if (!App.context.isInWatchNext(it)) {
                        val card = WatchNext.getCardFromWatchNextProgramId(watchNextId)
                        Helpers.manageFavorite("add", "wath", it, card)
                    }
                }
            }

            TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED -> {

                val movieId = WatchNext.getInternalIdFromWatchNextProgramId(watchNextId)
                if (BuildConfig.DEBUG)
                    Log.d(
                        TAG,
                        "ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED, watch-next $watchNextId movieId $movieId"
                    )
                movieId?.let {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "$it isInWatchNext? ${App.context.isInWatchNext(it)} card ${
                            WatchNext.getCardFromWatchNextProgramId(watchNextId)
                        }"
                    )
                    if (App.context.isInWatchNext(movieId)) {
                        Helpers.manageFavorite("rem", "wath", movieId)
                    }
                    try { // remove from contentPrivider
                        WatchNext.rem(movieId)
                    } catch (e: Exception) {
                        Log.e(TAG, "error delete $movieId from WatchNext: $e")
                    }
                }
            }

            TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED, preview $previewId")
                val movieIdAndChanId =
                    ChannelManager.getInternalIdAndChanIdFromPreviewProgramId(previewId)
                movieIdAndChanId.first?.let { movieId ->
                    val chan = movieIdAndChanId.second?.let { ChannelHelper.getChanByID(it) }
                    if (!chan.isNullOrEmpty())
                        Helpers.manageFavorite("rem", chan, movieId)
                    try { // remove from contentPrivider
                        movieIdAndChanId.second?.let { chid ->
                            ChannelManager.deleteFromChannel(
                                chid,
                                movieId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "error delete $movieId from channel $chan: $e")
                    }
                }
            }
        }
    }
}