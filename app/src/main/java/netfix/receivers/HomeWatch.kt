package netfix.receivers

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import netfix.App
import netfix.app.BuildConfig
import netfix.channels.ChannelManager
import netfix.channels.WatchNext
import netfix.helpers.ChannelHelper
import netfix.helpers.Helpers
import netfix.helpers.Helpers.isAndroidTV
import netfix.helpers.Prefs.isInWatchNext
import netfix.sched.Scheduler

@TargetApi(Build.VERSION_CODES.O)
class HomeWatch : BroadcastReceiver() {
    private val TAG = "HomeWatch"

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.action

        if (action == null || !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAndroidTV))
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
                    if (BuildConfig.DEBUG) Log.d("*****", "$it isInWatchNext? ${App.context.isInWatchNext(it)} card ${WatchNext.getCardFromWatchNextProgramId(watchNextId)}")
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
                    if (BuildConfig.DEBUG) Log.d("*****", "$it isInWatchNext? ${App.context.isInWatchNext(it)} card ${WatchNext.getCardFromWatchNextProgramId(watchNextId)}")
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
                        movieIdAndChanId.second?.let { chid -> ChannelManager.deleteFromChannel(chid, movieId) }
                    } catch (e: Exception) {
                        Log.e(TAG, "error delete $movieId from channel $chan: $e")
                    }
                }
            }
        }
    }
}