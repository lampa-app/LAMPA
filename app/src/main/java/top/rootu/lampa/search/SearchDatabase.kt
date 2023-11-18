package top.rootu.lampa.search

import android.app.SearchManager
import android.database.Cursor
import android.database.MatrixCursor
import android.media.Rating
import android.os.Build
import androidx.annotation.RequiresApi
import top.rootu.lampa.App.Companion.emptyPosterPath
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread
import top.rootu.lampa.tmdb.TMDB
import top.rootu.lampa.tmdb.models.entity.Entity

object SearchDatabase {

    const val useAltTMDBImageHost = true

    fun search(query: String): List<Entity> {
        val ents = mutableListOf<Entity>()
        val th1 = thread {
            val lst = srchTMDB(query, 1, true)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th2 = thread {
            val lst = srchTMDB(query, 2, true)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th3 = thread {
            val lst = srchTMDB(query, 3, true)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th4 = thread {
            val lst = srchTMDB(query, 1, false)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th5 = thread {
            val lst = srchTMDB(query, 2, false)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }
        val th6 = thread {
            val lst = srchTMDB(query, 3, false)
            synchronized(ents) {
                ents.addAll(lst)
            }
        }

        th1.join()
        th2.join()
        th3.join()
        th4.join()
        th5.join()
        th6.join()
//        ents.sortWith(compareBy({ Utils.getDistance(it, query) }, { -(it.year?.toIntOrNull() ?: 9999) }))
        return ents
    }

    private fun srchTMDB(query: String, page: Int, movie: Boolean): List<Entity> {
        val params = mutableMapOf<String, String>()
        params["query"] = query
        params["page"] = page.toString()

        val srchType = if (movie) "movie" else "tv"

        var ents = TMDB.videos("search/$srchType", params)
        if (ents != null && ents.results.isEmpty() && query.lowercase(Locale.getDefault()).contains('ё')) {
            params["query"] = query.replace('ё', 'е', true)
            ents = TMDB.videos("search/$srchType", params)
        }
        return ents?.results ?: emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getMatrix(list: List<Entity>): Cursor {
        val matrixCursor = MatrixCursor(SearchProvider.queryProjection)
        try {
            for (movie in list)
                matrixCursor.addRow(convertMovieIntoRow(movie))
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return matrixCursor
    }

    var KEY_NAME = SearchManager.SUGGEST_COLUMN_TEXT_1
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
    var KEY_PRODUCTION_YEAR = SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR
    var KEY_COLUMN_DURATION = SearchManager.SUGGEST_COLUMN_DURATION
    var KEY_ACTION = SearchManager.SUGGEST_COLUMN_INTENT_ACTION

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun convertMovieIntoRow(ent: Entity): Array<Any> {
        val info = mutableListOf<String>()
        ent.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }
        ent.year?.let { info.add(it) }
        if (ent.media_type == "tv")
            ent.number_of_seasons?.let { info.add("S$it") }

        ent.genres?.joinToString(", ") { g ->
            g?.name?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }.toString()
        }?.let { info.add(it) }
        // TODO: cache posters with enabled proxy
        val poster = if (useAltTMDBImageHost) {
            ent.poster_path?.replace(TMDB.imgHost, TMDB.proxyImageHost, true)
        } else {
            ent.poster_path
        } ?: emptyPosterPath

        return arrayOf(
            ent.id ?: "",
            ent.title ?: "",
            info.joinToString(" · "),
            poster,
            "video/mp4",
            false,
            460,
            720,
            "2.0",
            "$0",
            "$0",
            Rating.RATING_5_STARS,
            ent.vote_average?.div(2) ?: 0.0,
            ent.year ?: "",
            ent.runtime?.toLong()?.times(60000L) ?: 0,
            "GLOBALSEARCH",
            ent.id ?: "",
            ent.media_type ?: ""
        )
    }
}