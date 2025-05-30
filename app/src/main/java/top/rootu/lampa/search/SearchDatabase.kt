package top.rootu.lampa.search

import android.app.SearchManager
import android.database.Cursor
import android.database.MatrixCursor
import android.media.Rating
import android.os.Build
import androidx.annotation.RequiresApi
import top.rootu.lampa.App
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Helpers.getDefaultPosterUri
import top.rootu.lampa.helpers.capitalizeFirstLetter
import top.rootu.lampa.tmdb.TMDB
import top.rootu.lampa.tmdb.models.entity.Entity
import java.io.IOException
import kotlin.concurrent.thread

object SearchDatabase {
    // Search suggestion column names
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

    /**
     * Searches TMDB for movies and TV shows matching the query.
     *
     * @param query The search query.
     * @return A list of [Entity] objects representing the search results.
     */
    fun search(query: String): List<Entity> {
        val results = mutableListOf<Entity>()

        // Search in parallel for movies and TV shows across multiple pages
        val threads = listOf(
            thread { results.addAll(searchTMDB(query, 1, true)) }, // Movies, page 1
            thread { results.addAll(searchTMDB(query, 2, true)) }, // Movies, page 2
            thread { results.addAll(searchTMDB(query, 1, false)) }, // TV shows, page 1
            thread { results.addAll(searchTMDB(query, 2, false)) }  // TV shows, page 2
        )

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Sort results by relevance and year
        results.sortWith(
            compareBy(
                { Utils.getDistance(it, query) }, // Sort by relevance
                { -(it.year?.toIntOrNull() ?: 9999) } // Sort by year (descending)
            )
        )

        return results
    }

    /**
     * Searches TMDB for movies or TV shows matching the query.
     *
     * @param query The search query.
     * @param page The page number to fetch.
     * @param movie Whether to search for movies (true) or TV shows (false).
     * @return A list of [Entity] objects representing the search results.
     */
    private fun searchTMDB(query: String, page: Int, movie: Boolean): List<Entity> {
        val params = mutableMapOf(
            "query" to query,
            "page" to page.toString()
        )

        val searchType = if (movie) "movie" else "tv"
        var results = TMDB.videos("search/$searchType", params)?.results ?: emptyList()

        // Handle the case where the query contains the letter 'ё' (Cyrillic)
        if (results.isEmpty() && query.contains('ё', ignoreCase = true)) {
            params["query"] = query.replace('ё', 'е', ignoreCase = true)
            results = TMDB.videos("search/$searchType", params)?.results ?: emptyList()
        }

        return results
    }

    /**
     * Converts a list of [Entity] objects into a [MatrixCursor].
     *
     * @param list The list of [Entity] objects.
     * @return A [Cursor] containing the search results.
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getMatrix(list: List<Entity>): Cursor {
        val matrixCursor = MatrixCursor(SearchProvider.queryProjection)
        try {
            list.forEach { entity ->
                matrixCursor.addRow(convertEntityToRow(entity))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return matrixCursor
    }

    /**
     * Converts an [Entity] object into a row for the [MatrixCursor].
     *
     * @param ent The [Entity] object.
     * @return An array of values representing the row.
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun convertEntityToRow(ent: Entity): Array<Any> {
        val info = mutableListOf<String>()

        // Add year
        ent.year?.let {
            val year = if (App.context.getString(R.string.shortyear).isNotBlank()) {
                "$it ${App.context.getString(R.string.shortyear)}"
            } else {
                it
            }
            info.add(year)
        }

        // Add media type and seasons (for TV shows)
        if (ent.media_type == "tv") {
            info.add(App.context.getString(R.string.series))
            ent.number_of_seasons?.takeIf { it > 0 }?.let {
                info.add("S$it")
            }
        }
        // Add genres if present
        ent.genres?.mapNotNull {
            it.name?.capitalizeFirstLetter()?.takeIf { genre -> genre.isNotBlank() }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let {
            info.add(it)
        }

        // Add vote average if present and > 0
        ent.vote_average?.takeIf { it > 0.0 }?.let {
            info.add("%.1f".format(it))
        }

        // Get poster URL
        val poster = ent.backdrop_path ?: ent.poster_path ?: getDefaultPosterUri()

        // Build the row based on the Android version
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arrayOf(
                ent.id ?: "", // ID
                ent.title ?: "", // Name
                info.joinToString(" · "), // Description
                poster, // Icon
                "video/mp4", // Data type
                false, // Is live
                1920, // Video width
                1080, // Video height
                "2.0", // Audio channels
                "$0", // Purchase price
                "$0", // Rental price
                Rating.RATING_5_STARS, // Rating style
                ent.vote_average?.div(2) ?: 0.0, // Rating score
                ent.year ?: "", // Production year
                ent.runtime?.toLong()?.times(60000L) ?: 0, // Duration
                "GLOBALSEARCH", // Intent action
                ent.id ?: "", // Data ID
                ent.media_type ?: "" // Extra data
            )
        } else {
            // KitKat and below
            arrayOf(
                ent.id ?: "", // ID
                ent.title ?: "", // Name
                info.joinToString(" · "), // Description
                poster, // Icon
                "GLOBALSEARCH", // Intent action
                ent.id ?: "", // Data ID
                ent.media_type ?: "" // Extra data
            )
        }
    }
}