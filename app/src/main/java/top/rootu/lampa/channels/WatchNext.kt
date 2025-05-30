package top.rootu.lampa.channels

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
import androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST
import androidx.tvprovider.media.tv.TvContractCompat.buildWatchNextProgramUri
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.rootu.lampa.App
import top.rootu.lampa.PlayerStateManager
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.debugLog
import top.rootu.lampa.helpers.Helpers.getDefaultPosterUri
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.isInWatchNext
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.helpers.capitalizeFirstLetter
import top.rootu.lampa.models.LAMPA_CARD_KEY
import top.rootu.lampa.models.LampaCard


object WatchNext {
    private const val TAG = "WatchNext"
    // private const val RESUME_ID = "-1"

    @SuppressLint("RestrictedApi")
    private val WATCH_NEXT_MAP_PROJECTION = arrayOf(
        TvContractCompat.BaseTvColumns._ID,
        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE
    )

    @SuppressLint("RestrictedApi")
    fun add(card: LampaCard) {
        if (!isTvContentProviderAvailable) return
        card.id?.let { movieId ->
            val existingProgram = findProgramByMovieId(movieId)
            val removed = removeIfNotBrowsable(existingProgram)
            val shouldUpdateProgram = existingProgram != null && !removed
            if (shouldUpdateProgram) {
                val contentValues =
                    WatchNextProgram.Builder(existingProgram).build().toContentValues()
                val rowsUpdated = App.context.contentResolver.update(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    contentValues, null, null
                )
                if (rowsUpdated < 1) {
                    Log.e(TAG, "Failed to update Watch Next program ${existingProgram?.id}")
                }
            } else {
                val programUri = App.context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    createWatchNextProgram(card).toContentValues()
                )
                if (programUri == null || programUri == Uri.EMPTY) {
                    Log.e(TAG, "Failed to insert movie $movieId into the Watch Next")
                }
            }
        }
    }

    fun rem(movieId: String?) {
        if (!isTvContentProviderAvailable) return
        movieId?.let { deleteFromWatchNext(it) }
    }

    suspend fun updateWatchNext() {
        if (!isTvContentProviderAvailable) return
        val context = App.context
        val deleted = removeStale()
        debugLog(TAG, "updateWatchNext() WatchNext stale cards removed: $deleted")

        val lst = when { // reversed order
            // CUB
            context.syncEnabled -> context.CUB
                ?.filter { it.type == LampaProvider.LATE }
                ?.sortedBy { it.time }
                ?.mapNotNull { it.data?.also { data -> data.fixCard() } }
                .orEmpty()
            // FAV
            else -> context.FAV?.card
                ?.filter { context.FAV?.wath?.contains(it.id) == true }
                ?.sortedByDescending { context.FAV?.wath?.indexOf(it.id) }
                ?.onEach { it.fixCard() }
                .orEmpty()
        }

        val (excludePending, pending) = lst.partition {
            !context.wathToRemove.contains(it.id.toString())
        }
        debugLog(
            TAG,
            "updateWatchNext() WatchNext items: ${excludePending.size} ${excludePending.map { it.id }}"
        )
        debugLog(
            TAG,
            "updateWatchNext() WatchNext items pending to remove: ${pending.size} ${pending.map { it.id }}"
        )

        excludePending.forEach { card ->
            withContext(Dispatchers.Default) {
                try {
                    add(card)
                } catch (_: Exception) {
                    // printLog(TAG, "Error adding $card to WatchNext: $e")
                }
            }
        }
    }

    fun addLastPlayed(card: LampaCard, lampaActivity: String) {
        if (!isTvContentProviderAvailable) return

        card.id?.let { movieId ->
            // deleteFromWatchNext(RESUME_ID) // Clear any existing continue watch
            deleteFromWatchNext(movieId)

            val programUri = App.context.contentResolver.insert(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                createWatchNextProgram(card, true, lampaActivity).toContentValues()
            )

            if (programUri == null) {
                Log.e(TAG, "Failed to insert continue watch for $movieId")
            }
        }
    }

    fun removeContinueWatch(card: LampaCard) {
        if (!isTvContentProviderAvailable) return
        // deleteFromWatchNext(RESUME_ID)
        card.id?.let { movieId -> deleteFromWatchNext(movieId) }
    }

    @SuppressLint("RestrictedApi")
    fun getInternalIdFromWatchNextProgramId(watchNextId: Long): String? {
        return App.context.contentResolver.query(
            buildWatchNextProgramUri(watchNextId), null, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val watchNextProgram = WatchNextProgram.fromCursor(cursor)
                watchNextProgram?.internalProviderId
            } else null
        }
    }

    @SuppressLint("RestrictedApi")
    fun getCardFromWatchNextProgramId(watchNextId: Long): LampaCard? {
        return App.context.contentResolver.query(
            buildWatchNextProgramUri(watchNextId), null, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val watchNextProgram = WatchNextProgram.fromCursor(cursor)
                val json = watchNextProgram?.intent?.getStringExtra(LAMPA_CARD_KEY)
                getJson(json, LampaCard::class.java)
            } else null
        }
    }

    @SuppressLint("RestrictedApi")
    private fun deleteFromWatchNext(movieId: String) {
        val program = findProgramByMovieId(movieId)
        program?.let {
            debugLog(TAG, "deleteFromWatchNext($movieId) removeProgram(${it.id})")
            removeProgram(it.id)
        }
    }

    // Find the movie by our app's internal id.
    @SuppressLint("RestrictedApi")
    private fun findProgramByMovieId(movieId: String): WatchNextProgram? {
        App.context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WATCH_NEXT_MAP_PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val program = WatchNextProgram.fromCursor(cursor)
                    if (movieId == program.internalProviderId) {
                        return program
                    }
                } while (cursor.moveToNext())
            }
        }
        return null
    }

    // Remove items not in Lampa Watch Later
    @SuppressLint("RestrictedApi")
    private fun removeStale(): Int {
        var count = 0
        App.context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WATCH_NEXT_MAP_PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val program = WatchNextProgram.fromCursor(cursor)
                    if (!App.context.isInWatchNext(program.internalProviderId) /* && program.internalProviderId != RESUME_ID */) {
                        count++
                        removeProgram(program.id)
                    }
                } while (cursor.moveToNext())
            }
        }
        return count
    }

    // Check is a program has been removed from the UI by the user. If so, then
    // remove the program from the content provider.
    @SuppressLint("RestrictedApi")
    private fun removeIfNotBrowsable(program: WatchNextProgram?): Boolean {
        if (program?.isBrowsable == false) {
            removeProgram(program.id)
            return true
        }
        return false
    }

    private fun removeProgram(watchNextProgramId: Long): Int {
        return App.context.contentResolver.delete(
            buildWatchNextProgramUri(watchNextProgramId),
            null, null
        ).also {
            if (it < 1) Log.e(TAG, "Failed to delete program $watchNextProgramId from Watch Next")
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createWatchNextProgram(
        card: LampaCard,
        resume: Boolean = false,
        activityJson: String? = null
    ): WatchNextProgram {
        val info = mutableListOf<String>()
        val programId = /* if (resume) RESUME_ID else */ card.id

        // Add vote average if present and > 0
        card.vote_average?.takeIf { it > 0.0 }?.let {
            info.add("%.1f".format(it))
        }

        var title = card.title
        var type = TvContractCompat.WatchNextPrograms.TYPE_MOVIE

        if (card.type == "tv") {
            if (!card.name.isNullOrEmpty()) title = card.name
            type = if (resume) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
            else TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES
            card.number_of_seasons?.takeIf { it > 0 }?.let {
                info.add("S$it")
            }
        }
        // Add genres if present
        card.genres?.mapNotNull {
            it?.name?.capitalizeFirstLetter()?.takeIf { genre -> genre.isNotBlank() }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let {
            info.add(it)
        }
        // https://developer.android.com/codelabs/watchnext-for-movie-tv-episodes#3
        val watchType = if (resume) WATCH_NEXT_TYPE_CONTINUE else WATCH_NEXT_TYPE_WATCHLIST

        val builder = WatchNextProgram.Builder()
            .setType(type)
            .setWatchNextType(watchType)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .setTitle(title)
            .setDescription(card.overview)
            .setGenre(info.joinToString(" · "))
            .setReviewRating((card.vote_average?.div(2) ?: 0).toString())
            .setIntent(Helpers.buildPendingIntent(card, resume, activityJson))
            .setInternalProviderId(programId) // Our internal ID
            .setDurationMillis(card.runtime?.times(60000) ?: 0)
            .setReleaseDate(card.release_year)
            .setSearchable(true)
            .setLive(false)
//        if (type == TYPE_TV_EPISODE) {
//            builder.setEpisodeNumber(video.episodeNumber.toInt())
//            builder.setSeasonNumber(video.seasonNumber.toInt())
//            // Use TV series name and season number to generate a fake season name.
//            builder.setSeasonTitle(context.getString(R.string.season, video.category, video.seasonNumber))
//            // Use the name of the video as the episode name.
//            builder.setEpisodeTitle(video.name)
//            // Use TV series name as the tile, in this sample,
//            // we use category as a fake TV series.
//            builder.setTitle(video.category)
//        }
        if (resume) {
            val playerStateManager = PlayerStateManager(App.context)
            // Get the most relevant playback state
            val state = playerStateManager.findStateByCard(card) // Find the state for this card
            // val state = playerStateManager.findMatchingStates(activityJson.toString()).firstOrNull()
            // Calculate watch position and duration if valid state exists
            state?.let {
                val (positionMs, durationMs) = it.run {
                    val timeline = currentItem?.timeline
                    val position = timeline?.time?.times(1000)?.toLong()
                        ?: currentPosition // Convert seconds to ms
                    val duration =
                        timeline?.duration?.times(1000)?.toLong() ?: 0L // Convert seconds to ms
                    position to duration
                }
                // Only set if we have valid position and duration
                if (positionMs > 0 && durationMs > 0) {
                    builder.setLastPlaybackPositionMillis(positionMs.toInt())
                        .setDurationMillis(durationMs.toInt())
                }
            }
        }

        val posterUri = card.img?.toUri() ?: getDefaultPosterUri()
        builder.setPosterArtUri(posterUri)
            .setPosterArtAspectRatio(ASPECT_RATIO_2_3)

        if (!card.background_image.isNullOrEmpty()) {
            builder.setThumbnailUri(card.background_image!!.toUri())
                .setThumbnailAspectRatio(ASPECT_RATIO_16_9)
        }

        return builder.build()
    }
}