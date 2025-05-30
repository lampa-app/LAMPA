package top.rootu.lampa.channels

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider.BOOK
import top.rootu.lampa.content.LampaProvider.CONT
import top.rootu.lampa.content.LampaProvider.HIST
import top.rootu.lampa.content.LampaProvider.LIKE
import top.rootu.lampa.content.LampaProvider.LOOK
import top.rootu.lampa.content.LampaProvider.RECS
import top.rootu.lampa.content.LampaProvider.SCHD
import top.rootu.lampa.content.LampaProvider.THRW
import top.rootu.lampa.content.LampaProvider.VIEW
import top.rootu.lampa.helpers.ChannelHelper
import top.rootu.lampa.helpers.Coroutines
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.getDefaultPosterUri
import top.rootu.lampa.helpers.capitalizeFirstLetter
import top.rootu.lampa.helpers.data
import top.rootu.lampa.models.LampaCard

object ChannelManager {
    private const val TAG = "ChannelManager"
    private val lock = Any()

    @SuppressLint("RestrictedApi")
    private val PREVIEW_PROGRAM_MAP_PROJECTION = arrayOf(
        TvContractCompat.BaseTvColumns._ID,
        TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.PreviewPrograms.COLUMN_BROWSABLE
    )

    /**
     * Gets the localized display name for a channel.
     */
    fun getChannelDisplayName(name: String): String {
        return when (name) {
            RECS -> App.context.getString(R.string.ch_recs)
            LIKE -> App.context.getString(R.string.ch_liked)
            BOOK -> App.context.getString(R.string.ch_bookmarks)
            HIST -> App.context.getString(R.string.ch_history)
            LOOK -> App.context.getString(R.string.ch_look)
            VIEW -> App.context.getString(R.string.ch_viewed)
            SCHD -> App.context.getString(R.string.ch_scheduled)
            CONT -> App.context.getString(R.string.ch_continued)
            THRW -> App.context.getString(R.string.ch_thrown)
            else -> name.capitalizeFirstLetter()
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun update(name: String, list: List<LampaCard>) {
        if (BuildConfig.DEBUG) Log.d(TAG, "update($name, ${list.size} items)")
        removeLostChannels()

        synchronized(lock) {
            val displayName = getChannelDisplayName(name)
            val channel = ChannelHelper.get(name) ?: run {
                ChannelHelper.add(name, displayName)
                ChannelHelper.get(name)
            } ?: return@synchronized

            // Update channel metadata
            updateChannelMetadata(channel, displayName)

            // Add programs to the channel
            if (!Coroutines.running("update_channel_$name")) { // fix duplicates
                Coroutines.launch("update_channel_$name") {
                    clearChannelPrograms(channel.id)
                    addProgramsToChannel(channel.id, name, list)
                }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "scope update_channel_$name already active!")
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteFromChannel(channelId: Long, movieId: String) {
        findProgramByMovieId(channelId, movieId)?.let { program ->
            removeProgram(program.id)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun removeAll() {
        synchronized(lock) {
            ChannelHelper.list().forEach { ChannelHelper.rem(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateChannelMetadata(channel: Channel, displayName: String) {
        val channelValues = Channel.Builder()
            .setDisplayName(displayName)
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setAppLinkIntentUri("lampa://${BuildConfig.APPLICATION_ID}/update_channel/${channel.data}".toUri()) // channel.internalProviderId is null
            .build()
            .toContentValues()

        App.context.contentResolver.update(
            TvContractCompat.buildChannelUri(channel.id),
            channelValues, null, null
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun clearChannelPrograms(channelId: Long) {
        App.context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null, null
        )
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun addProgramsToChannel(channelId: Long, provName: String, list: List<LampaCard>) {
        list.forEachIndexed { index, card ->
            val program = createPreviewProgram(channelId, provName, card, list.size - index)
            program?.let {
                if (existsInChannel(channelId, it.internalProviderId)) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "Program ${it.internalProviderId} already exists in channel $channelId, removing..."
                    )
                    deleteFromChannel(channelId, it.internalProviderId)
                }
                App.context.contentResolver.insert(
                    TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                    it.toContentValues()
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun removeLostChannels() {
        synchronized(lock) {
            // Remove channels with null data
            ChannelHelper.list().filter { it.internalProviderDataByteArray == null }.forEach {
                ChannelHelper.rem(it)
            }
            // Remove duplicate channels
            val channels = ChannelHelper.list()
            val duplicates = channels.groupBy { it.data }.values.filter { it.size > 1 }
            duplicates.flatten().distinctBy { it.id }.forEach {
                ChannelHelper.rem(it)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun getInternalIdAndChanIdFromPreviewProgramId(previewProgramId: Long): Pair<String?, Long?> {
        return App.context.contentResolver.query(
            TvContractCompat.buildPreviewProgramUri(previewProgramId), null, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val program = PreviewProgram.fromCursor(cursor)
                Pair(program.internalProviderId, program.channelId)
            } else {
                Pair(null, null)
            }
        } ?: Pair(null, null)
    }

    @SuppressLint("RestrictedApi")
    private fun findProgramByMovieId(channelId: Long, movieId: String): PreviewProgram? {
        App.context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            PREVIEW_PROGRAM_MAP_PROJECTION,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                do {
                    val program = PreviewProgram.fromCursor(cursor)
                    if (movieId == program.internalProviderId) {
                        return program
                    }
                } while (cursor.moveToNext())
            }
        }
        return null
    }

    @SuppressLint("RestrictedApi")
    private fun existsInChannel(channelId: Long, movieId: String): Boolean {
        return findProgramByMovieId(channelId, movieId) != null
    }

    private fun removeProgram(previewProgramId: Long): Int {
        return App.context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramUri(previewProgramId),
            null, null
        ).also {
            if (it < 1) Log.e(TAG, "Failed to delete program $previewProgramId")
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createPreviewProgram(
        channelId: Long,
        provName: String,
        card: LampaCard,
        weight: Int
    ): PreviewProgram? {
        val title = when {
            !card.name.isNullOrEmpty() -> card.name
            !card.title.isNullOrEmpty() -> card.title
            else -> return null // or provide a default value like "Unknown"
        }
        val info = mutableListOf<String>()

        // Add vote average if present and > 0
        card.vote_average?.takeIf { it > 0.0 }?.let {
            info.add("%.1f".format(it))
        }

        val type = if (card.type == "tv") {
            card.number_of_seasons?.takeIf { it > 0 }?.let {
                info.add("S$it")
            } ?: info.add(App.context.getString(R.string.series))
            TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
        } else {
            TvContractCompat.PreviewPrograms.TYPE_MOVIE
        }
        // Add genres if present
        card.genres?.mapNotNull {
            it?.name?.capitalizeFirstLetter()?.takeIf { genre -> genre.isNotBlank() }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let {
            info.add(it)
        }

        val country = card.production_countries?.mapNotNull { country ->
            when {
                !country.iso_3166_1.isNullOrEmpty() -> country.iso_3166_1
                country.name.isNotEmpty() -> country.name.trim()
                else -> null
            }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: card.origin_country?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: card.original_language?.uppercase()
            ?: ""

        if (country.isNotEmpty()) info.add(country)

        val releaseYear = card.release_year ?: when {
            card.type == "tv" && !card.first_air_date.isNullOrEmpty() -> card.first_air_date.substringBefore(
                "-"
            )

            !card.release_date.isNullOrEmpty() -> card.release_date.substringBefore("-")
            else -> ""
        }

        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(title)
            .setAvailability(TvContractCompat.PreviewProgramColumns.AVAILABILITY_AVAILABLE)
            .setDescription(card.overview)
            .setGenre(info.joinToString(" · "))
            .setIntent(buildPendingIntent(card, null, null)) // provName
            .setInternalProviderId(card.id.toString())
            .setWeight(weight)
            .setType(type)
            .setDurationMillis(card.runtime?.times(60000) ?: 0)
            .setSearchable(true)
            .setLive(false)
            .apply {
                if (releaseYear.isNotEmpty()) setReleaseDate(releaseYear)
                card.vote_average?.let { setReviewRating((it / 2).toString()) }
                if (provName == RECS && !card.background_image.isNullOrEmpty()) {
                    setPosterArtUri(card.background_image?.toUri())
                        .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                    setThumbnailUri(card.background_image?.toUri())
                        .setThumbnailAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                } else {
                    val posterUri = card.img?.toUri() ?: getDefaultPosterUri()
                    setPosterArtUri(posterUri)
                        .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
                }
            }
            .build()
    }
}