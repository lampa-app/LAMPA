package top.rootu.lampa.channels

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider.Book
import top.rootu.lampa.content.LampaProvider.Hist
import top.rootu.lampa.content.LampaProvider.Like
import top.rootu.lampa.content.LampaProvider.Recs
import top.rootu.lampa.helpers.ChannelHelper
import top.rootu.lampa.helpers.Coroutines
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.setLanguage
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.data
import top.rootu.lampa.models.TmdbID
import top.rootu.lampa.models.getEntity
import java.util.*

object ChannelManager {
    private val TAG = if (BuildConfig.DEBUG) "***** ChannelManager" else "ChannelManager"
    private val lock = Any()

    fun getChannelDisplayName(name: String): String {
        App.context.setLanguage()
        return when (name) {
            Recs -> App.context.getString(R.string.ch_recs)
            Like -> App.context.getString(R.string.ch_liked)
            Book -> App.context.getString(R.string.ch_bookmarks)
            Hist -> App.context.getString(R.string.ch_history)
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
    fun update(name: String, list: List<TmdbID>) {
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
                .setAppLinkIntentUri(Uri.parse("lampa://${BuildConfig.APPLICATION_ID}/update_channel/$name"))
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
                        if (!exist(ch.id, prg)) {
                            App.context.contentResolver.insert(
                                Uri.parse("content://android.media.tv/preview_program"),
                                prg.toContentValues()
                            )
                        } else {
                            if (BuildConfig.DEBUG)
                                Log.d(
                                    "*****",
                                    "channel ${ch.displayName} already have program ${prg.internalProviderId}"
                                )
                        }
                    }
                }
            } else {
                if (BuildConfig.DEBUG) Log.d("*****", "scope update_channel_$name already active!")
            }
        }
    }


    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    fun exist(channelId: Long, pp: PreviewProgram?): Boolean {
        val movieId = pp?.internalProviderId
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
                        return true // program
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun removeAll() {
        synchronized(lock) {
            ChannelHelper.list().forEach {
                ChannelHelper.rem(it)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
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
    private fun getProgram(
        channelId: Long,
        provName: String,
        id: TmdbID,
        weight: Int
    ): PreviewProgram? {
        val info = mutableListOf<String>()

        val ent = id.getEntity() ?: return null

        ent.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }

        if (ent.media_type == "tv")
            ent.number_of_seasons?.let { info.add("S$it") }

        ent.genres?.joinToString(", ") { g ->
            g?.name?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }.toString()
        }?.let { info.add(it) }

        var country = ent.production_countries?.joinToString(", ") { it.iso_3166_1 } ?: ""
        if (country.isEmpty())
            country = ent.origin_country?.joinToString(", ") ?: ""
        if (country.isNotEmpty())
            info.add(country)

        ent.certification?.let {
            if (it.isNotBlank())
                info.add(it)
        }

        val preview = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(ent.title)
            .setAvailability(TvContractCompat.PreviewProgramColumns.AVAILABILITY_AVAILABLE)
            .setDescription(ent.overview)
            .setGenre(info.joinToString(" · "))
            .setIntent(buildPendingIntent(ent.toTmdbID(), provName))
            .setInternalProviderId(ent.id.toString())
            .setWeight(weight)
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setDurationMillis(ent.runtime?.times(60000) ?: 0)
            .setSearchable(true)
            .setLive(false)

        ent.year?.let {
            preview.setReleaseDate(it)
        }

        ent.vote_average?.let {
            preview.setReviewRating((it.div(2)).toString())
        }

        var usePoster = true // use backdrop for recs
        if (!ent.backdrop_path.isNullOrEmpty() && provName == Recs) {
            val poster = ent.backdrop_path
            preview.setPosterArtUri(Uri.parse(poster))
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            preview.setThumbnailUri(Uri.parse(poster))
                .setThumbnailAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            usePoster = false
        }
        if (usePoster) {
            if (ent.poster_path.isNullOrEmpty()) {
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
                val poster = ent.poster_path
                preview.setPosterArtUri(Uri.parse(poster))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
            }
        }

        return preview.build()
    }

}