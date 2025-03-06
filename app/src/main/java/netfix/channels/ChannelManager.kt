package netfix.channels

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import netfix.App
import netfix.app.BuildConfig
import netfix.app.R
import netfix.content.NetfixProvider.BOOK
import netfix.content.NetfixProvider.CONT
import netfix.content.NetfixProvider.HIST
import netfix.content.NetfixProvider.LIKE
import netfix.content.NetfixProvider.LOOK
import netfix.content.NetfixProvider.RECS
import netfix.content.NetfixProvider.SCHD
import netfix.content.NetfixProvider.THRW
import netfix.content.NetfixProvider.VIEW
import netfix.helpers.ChannelHelper
import netfix.helpers.Coroutines
import netfix.helpers.Helpers.buildPendingIntent
import netfix.helpers.Helpers.setLanguage
import netfix.helpers.Prefs.appLang
import netfix.helpers.data
import netfix.models.NetfixCard
import java.util.Locale

object ChannelManager {
    private const val TAG = "ChannelManager"
    private val lock = Any()

    fun getChannelDisplayName(name: String): String {
        App.context.setLanguage()
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
            else -> name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale(App.context.appLang)) else it.toString() }
        }
    }

    @SuppressLint("RestrictedApi")
    private val PREVIEW_PROGRAM_MAP_PROJECTION =
        arrayOf(
            TvContractCompat.BaseTvColumns._ID,
            TvContractCompat.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            TvContractCompat.PreviewPrograms.COLUMN_BROWSABLE
        )

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun update(name: String, list: List<NetfixCard>) {
        if (BuildConfig.DEBUG) Log.d(TAG, "update($name, size:${list.size})")
        removeLostChannels()
        synchronized(lock) {
            val displayName = getChannelDisplayName(name)
            var ch = ChannelHelper.get(name)
            if (ch == null)
                ChannelHelper.add(name, displayName)
            ch = ChannelHelper.get(name)
            if (ch == null)
                return@synchronized

            val channel = Channel.Builder()
            channel.setDisplayName(displayName)
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setAppLinkIntentUri(Uri.parse("netfix://${BuildConfig.APPLICATION_ID}/update_channel/$name"))
                .build()

            App.context.contentResolver.update(
                TvContractCompat.buildChannelUri(ch.id),
                channel.build().toContentValues(), null, null
            )

            if (!Coroutines.running("update_channel_$name")) { // fix duplicates
                Coroutines.launch("update_channel_$name") {
                    App.context.contentResolver.delete(
                        TvContractCompat.buildPreviewProgramsUriForChannel(ch.id),
                        null,
                        null
                    )
                    list.forEachIndexed { index, entity ->
                        val prg =
                            getProgram(ch.id, name, entity, list.size - index)
                                ?: return@forEachIndexed
                        if (exist(ch.id, prg)) {
                            if (BuildConfig.DEBUG)
                                Log.d(
                                    "*****",
                                    "channel ${ch.displayName} already have program for ${prg.internalProviderId}"
                                )
                            deleteFromChannel(ch.id, prg.internalProviderId)
                        }
                        App.context.contentResolver.insert(
                            Uri.parse("content://android.media.tv/preview_program"),
                            prg.toContentValues()
                        )
                    }
                }
            } else {
                if (BuildConfig.DEBUG) Log.d("*****", "scope update_channel_$name already active!")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun removeAll() {
        synchronized(lock) {
            ChannelHelper.list().forEach {
                ChannelHelper.rem(it)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun removeLostChannels() {
        synchronized(lock) {
            //remove channels with null data
            ChannelHelper.list().filter { it.internalProviderDataByteArray == null }.forEach {
                ChannelHelper.rem(it)
            }

            //remove duplicate channels
            val list = ChannelHelper.list()
            val del = mutableListOf<Channel>()
            for (i in list.indices) {
                for (j in list.size - 1 downTo i) {
                    if (i != j && list[i].data == list[j].data)
                        del.add(list[j])
                }
            }

            del.distinctBy { it.id }.forEach {
                ChannelHelper.rem(it)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun deleteFromChannel(channelId: Long, movieId: String) {
        movieId.let {
            val program = findProgramByMovieId(channelId = channelId, movieId = it)
            program?.let { prg ->
                removeProgram(previewProgramId = prg.id)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun findProgramByMovieId(channelId: Long, movieId: String): PreviewProgram? {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            PREVIEW_PROGRAM_MAP_PROJECTION,
            null,
            null,
            null
        )
        cursor?.let {
            if (it.moveToFirst())
                do {
                    val program = PreviewProgram.fromCursor(it)
                    if (movieId == program.internalProviderId) {
                        cursor.close()
                        return program
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return null
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun exist(channelId: Long, previewProgram: PreviewProgram?): Boolean {
        val movieId = previewProgram?.internalProviderId
        val cursor = App.context.contentResolver.query(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            PREVIEW_PROGRAM_MAP_PROJECTION,
            null,
            null
        )
        cursor?.let {
            if (it.moveToFirst())
                do {
                    val program = PreviewProgram.fromCursor(it)
                    if (movieId == program.internalProviderId) {
                        cursor.close()
                        return true
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return false
    }

    @SuppressLint("RestrictedApi")
    fun getInternalIdAndChanIdFromPreviewProgramId(previewProgramId: Long): Pair<String?, Long?> {
        val curWatchNextUri = TvContractCompat.buildPreviewProgramUri(previewProgramId)
        var previewProgram: PreviewProgram? = null
        App.context.contentResolver.query(
            curWatchNextUri, null, null, null, null
        ).use { cursor ->
            if (cursor != null && cursor.count != 0) {
                cursor.moveToFirst()
                previewProgram = PreviewProgram.fromCursor(cursor)
            }
        }
        return Pair(previewProgram?.internalProviderId, previewProgram?.channelId)
    }

    private fun removeProgram(previewProgramId: Long): Int {
        val rowsDeleted = App.context.contentResolver.delete(
            TvContractCompat.buildPreviewProgramUri(previewProgramId),
            null, null
        )
        if (rowsDeleted < 1) {
            Log.e(TAG, "Failed to delete program $previewProgramId")
        }
        return rowsDeleted
    }

    @SuppressLint("RestrictedApi")
    private fun getProgram(
        channelId: Long,
        provName: String,
        card: NetfixCard,
        weight: Int
    ): PreviewProgram? {
        val info = mutableListOf<String>()

        val title = if (!card.name.isNullOrEmpty()) card.name else card.title

        card.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }

        var type = TvContractCompat.PreviewPrograms.TYPE_MOVIE
        if (card.type == "tv") {
            type = TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            card.number_of_seasons?.let { info.add("S$it") } ?: info.add(App.context.getString(R.string.series))
        }

        val genreList = mutableListOf<String>()
        card.genres?.forEach { g ->
            if (g?.name?.isNotEmpty() == true)
                genreList.add(g.name.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString() })
        }
        if (genreList.isNotEmpty())
            info.add(genreList.joinToString(", "))

        var country = card.production_countries?.joinToString(", ") { it.iso_3166_1.toString() } ?: ""
        if (country.isEmpty())
            country = card.origin_country?.joinToString(", ") ?: ""
        if (country.isEmpty())
            country = card.original_language?.uppercase() ?: ""
        if (country.isNotEmpty())
            info.add(country)

        val preview = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(title)
            .setAvailability(TvContractCompat.PreviewProgramColumns.AVAILABILITY_AVAILABLE)
            .setDescription(card.overview)
            .setGenre(info.joinToString(" · "))
            .setIntent(buildPendingIntent(card, null)) // provName
            .setInternalProviderId(card.id.toString())
            .setWeight(weight)
            .setType(type)
            .setDurationMillis(card.runtime?.times(60000) ?: 0)
            .setSearchable(true)
            .setLive(false)

        val releaseYear = if (!card.release_year.isNullOrEmpty()) card.release_year
        else if (card.type == "tv" && !card.first_air_date.isNullOrEmpty())
            card.first_air_date.substringBefore("-", card.first_air_date)
        else if (!card.release_date.isNullOrEmpty())
            card.release_date.substringBefore("-", card.release_date)
        else ""

        if (releaseYear.isNotEmpty()) {
            preview.setReleaseDate(releaseYear)
        }

        card.vote_average?.let {
            preview.setReviewRating((it.div(2)).toString())
        }

        var usePoster = true // use backdrop for recs
        if (!card.background_image.isNullOrEmpty() && provName == RECS) {
            val poster = card.background_image
            preview.setPosterArtUri(Uri.parse(poster))
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            preview.setThumbnailUri(Uri.parse(poster))
                .setThumbnailAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            usePoster = false
        }
        if (usePoster) {
            if (card.img.isNullOrEmpty()) {
                val resourceId = R.drawable.empty_poster // in-app poster
                val emptyPoster = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(App.context.resources.getResourcePackageName(resourceId))
                    .appendPath(App.context.resources.getResourceTypeName(resourceId))
                    .appendPath(App.context.resources.getResourceEntryName(resourceId))
                    .build()
                preview.setPosterArtUri(emptyPoster)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            } else {
                val poster = card.img
                preview.setPosterArtUri(Uri.parse(poster))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            }
        }

        return preview.build()
    }

}