package netfix.search

import android.app.SearchManager
import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.media.Rating
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import netfix.App
import netfix.app.R
import netfix.tmdb.TMDB
import netfix.tmdb.models.entity.Entity
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

object SearchDatabase {
    // https://developer.android.com/training/tv/discovery/searchable
    var KEY_NAME = SearchManager.SUGGEST_COLUMN_TEXT_1 // (required)
    var KEY_DESCRIPTION = SearchManager.SUGGEST_COLUMN_TEXT_2
    var KEY_ICON = SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE
    var KEY_DATA_TYPE = SearchManager.SUGGEST_COLUMN_CONTENT_TYPE
    var KEY_IS_LIVE = SearchManager.SUGGEST_COLUMN_IS_LIVE
    var KEY_VIDEO_WIDTH = SearchManager.SUGGEST_COLUMN_VIDEO_WIDTH
    var KEY_VIDEO_HEIGHT = SearchManager.SUGGEST_COLUMN_VIDEO_HEIGHT
    var KEY_AUDIO_CHANNEL_CONFIG = SearchManager.SUGGEST_COLUMN_AUDIO_CHANNEL_CONFIG
    var KEY_PURCHASE_PRICE = SearchManager.SUGGEST_COLUMN_PURCHASE_PRICE
    var KEY_RENTAL_PRICE = SearchManager.SUGGEST_COLUMN_RENTAL_PRICE
    var KEY_RATING_STYLE = SearchManager.SUGGEST_COLUMN_RATING_STYLE
    var KEY_RATING_SCORE = SearchManager.SUGGEST_COLUMN_RATING_SCORE
    var KEY_PRODUCTION_YEAR = SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR // (required)
    var KEY_COLUMN_DURATION = SearchManager.SUGGEST_COLUMN_DURATION // (required)
    var KEY_ACTION = SearchManager.SUGGEST_COLUMN_INTENT_ACTION

    fun search(query: String): List<Entity> {
        val ents = mutableListOf<Entity>()
        val th1 = thread {
            val lst = searchTMDB(query, 1, true)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th2 = thread {
            val lst = searchTMDB(query, 2, true)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th3 = thread {
            val lst = searchTMDB(query, 1, false)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th4 = thread {
            val lst = searchTMDB(query, 2, false)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }

        th1.join()
        th2.join()
        th3.join()
        th4.join()

        ents.sortWith(compareBy({ Utils.getDistance(it, query) },
            { -(it.year?.toIntOrNull() ?: 9999) })
        )
        return ents
    }

    private fun searchTMDB(query: String, page: Int, movie: Boolean): List<Entity> {
        val params = mutableMapOf<String, String>()
        params["query"] = query
        params["page"] = page.toString()

        val srchType = if (movie) "movie" else "tv"

        var ents = TMDB.videos("search/$srchType", params)
        if (ents != null && ents.results.isEmpty() && query.lowercase(Locale.getDefault())
                .contains('ё')
        ) {
            params["query"] = query.replace('ё', 'е', true)
            ents = TMDB.videos("search/$srchType", params)
        }
        return ents?.results ?: emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getMatrix(list: List<Entity>): Cursor {
        val matrixCursor = MatrixCursor(SearchProvider.queryProjection)
        try {
            for (movie in list) matrixCursor.addRow(convertMovieIntoRow(movie))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return matrixCursor
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun convertMovieIntoRow(ent: Entity): Array<Any> {
        val info = mutableListOf<String>()
        // year
        ent.year?.let {
            val year = if (App.context.getString(R.string.shortyear)
                    .isNotBlank()
            ) it + " " + App.context.getString(R.string.shortyear) else it
            info.add(year)
        }
        //ent.original_title?.let { if (it.isNotBlank() && it != ent.title) info.add(it) }
        if (ent.media_type == "tv") {
            info.add(App.context.getString(R.string.series))
            ent.number_of_seasons?.let { info.add("S$it") }
        }
        // genres
        ent.genres?.joinToString(", ") { g ->
            g?.name?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }.toString()
        }?.let { if (it.isNotBlank()) info.add(it) }
        // rating
        ent.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }
        // poster
        val resourceId = R.drawable.empty_poster // in-app poster
        val emptyPoster = Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(App.context.resources.getResourcePackageName(resourceId))
            .appendPath(App.context.resources.getResourceTypeName(resourceId))
            .appendPath(App.context.resources.getResourceEntryName(resourceId)).build()
        val poster = ent.backdrop_path ?: ent.poster_path ?: emptyPoster
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) arrayOf(
            ent.id ?: "", // id
            ent.title ?: "", // name
            info.joinToString(" · "), // desc
            poster, // icon
            "video/mp4", // data type
            false, // live
            1920, // video width
            1080, // video height
            "2.0", // channels
            "$0", // purchase price
            "$0", // rental price
            Rating.RATING_5_STARS, // rating type
            ent.vote_average?.div(2) ?: 0.0,
            ent.year ?: "",
            ent.runtime?.toLong()?.times(60000L) ?: 0,
            "GLOBALSEARCH",
            ent.id ?: "",
            ent.media_type ?: ""
        ) else arrayOf( // KitKat
            ent.id ?: "",
            ent.title ?: "",
            info.joinToString(" · "),
            poster,
            "GLOBALSEARCH",
            ent.id ?: "",
            ent.media_type ?: "",
        )
    }
}