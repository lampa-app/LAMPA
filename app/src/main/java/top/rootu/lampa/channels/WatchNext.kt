package top.rootu.lampa.channels

//import androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3
import androidx.tvprovider.media.tv.TvContractCompat.buildWatchNextProgramUri
import androidx.tvprovider.media.tv.WatchNextProgram
import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.models.TmdbID
import top.rootu.lampa.tmdb.models.entity.Entity
import java.util.*

object WatchNext {

    private val TAG = if (BuildConfig.DEBUG) "*****: WatchNext" else "WatchNext"

    @SuppressLint("RestrictedApi")
    private val WATCH_NEXT_MAP_PROJECTION =
        arrayOf(
            TvContractCompat.BaseTvColumns._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
            TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE
        )

    @SuppressLint("RestrictedApi")
    fun add(ent: Entity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAndroidTV) {
            ent.id?.let { id ->
                val existingProgram = findProgramByMovieId(id.toString())
                val removed = removeIfNotBrowsable(existingProgram)
                val shouldUpdateProgram = existingProgram != null && !removed
                if (shouldUpdateProgram) {
                    val contentValues =
                        WatchNextProgram.Builder(existingProgram).build().toContentValues()
                    val rowsUpdated = App.context.contentResolver.update(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        contentValues, null, null
                    )
                    if (rowsUpdated < 1)
                        Log.e(TAG, "Failed to update Watch Next program ${existingProgram?.id}")
                } else {
                    val programUri = App.context.contentResolver.insert(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        getProgram(ent).toContentValues()
                    )
                    if (programUri == null || programUri == Uri.EMPTY)
                        Log.e(TAG, "Failed to insert movie $id into the Watch Next")
                }
            }
        }
    }
    fun rem(tmdbID: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAndroidTV) {
            tmdbID?.let { id ->
                deleteFromWatchNext(id)
            }
        }
    }

    // https://github.com/googlecodelabs/tv-watchnext/blob/master/step_final/src/main/java/com/example/android/watchnextcodelab/channels/WatchNextTvProvider.kt

    @SuppressLint("RestrictedApi")
    fun getInternalIdFromWatchNextProgramId(watchNextId: Long): String? {
        if (BuildConfig.DEBUG)
            Log.d(TAG, "getInternalIdFromWatchNextProgramId($watchNextId)")
        val curWatchNextUri = buildWatchNextProgramUri(watchNextId)
        var watchNextProgram: WatchNextProgram? = null
        App.context.contentResolver.query(
            curWatchNextUri, null, null, null, null
        ).use { cursor ->
            if (cursor != null && cursor.count != 0) {
                cursor.moveToFirst()
                watchNextProgram = WatchNextProgram.fromCursor(cursor)
            }
        }
        return watchNextProgram?.internalProviderId
    }


    @SuppressLint("RestrictedApi")
    fun deleteFromWatchNext(entId: String?) {
        entId?.let {
            val program = findProgramByMovieId(movieId = it)
            if (program != null) {
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "deleteFromWatchNext($entId) removeProgram(${program.id})")
                removeProgram(watchNextProgramId = program.id)
            }
        }
    }

    // Find the movie by our app's internal id.
    @SuppressLint("RestrictedApi")
    private fun findProgramByMovieId(movieId: String): WatchNextProgram? {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WATCH_NEXT_MAP_PROJECTION,
            null,
            null,
            null
        )
        cursor?.let {
            if (it.moveToFirst())
                do {
                    val program = WatchNextProgram.fromCursor(it)
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
    private fun removeIfNotBrowsable(program: WatchNextProgram?): Boolean {
        // Check is a program has been removed from the UI by the user. If so, then
        // remove the program from the content provider.
        if (program?.isBrowsable == false) {
            val watchNextProgramId = program.id
            removeProgram(watchNextProgramId)
            return true
        }
        return false
    }

    private fun removeProgram(watchNextProgramId: Long): Int {
        val rowsDeleted = App.context.contentResolver.delete(
            buildWatchNextProgramUri(watchNextProgramId),
            null, null
        )
        if (rowsDeleted < 1) {
            Log.e(TAG, "Failed to delete program $watchNextProgramId from Watch Next")
        }
        return rowsDeleted
    }

    @SuppressLint("RestrictedApi")
    private fun getProgram(ent: Entity): WatchNextProgram {
        val info = mutableListOf<String>()

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

        val builder = WatchNextProgram.Builder()
        val wb =
            builder.setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(ent.title)
                .setDescription(ent.overview)
                .setGenre(info.joinToString(" · "))
                .setReviewRating((ent.vote_average?.div(2) ?: 0).toString())
                .setIntent(Helpers.buildPendingIntent(ent.toTmdbID(), null))
                .setInternalProviderId(ent.id.toString()) // Our internal ID
                .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setDurationMillis(ent.runtime?.times(60000) ?: 0)
                .setReleaseDate(ent.year)
                .setSearchable(true)
                .setLive(false)

        if (ent.poster_path.isNullOrEmpty()) {
            val resourceId = R.drawable.empty_poster // in-app poster
            val emptyPoster = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(App.context.resources.getResourcePackageName(resourceId))
                .appendPath(App.context.resources.getResourceTypeName(resourceId))
                .appendPath(App.context.resources.getResourceEntryName(resourceId))
                .build()
            wb.setPosterArtUri(emptyPoster)
                .setPosterArtAspectRatio(ASPECT_RATIO_2_3)
        } else {
            wb.setPosterArtUri(Uri.parse(ent.poster_path))
                .setPosterArtAspectRatio(ASPECT_RATIO_2_3)
        }

//        if (!ent.backdrop_path.isNullOrEmpty() && Prefs.enableBackdrop()) {
//            wb.setThumbnailUri(Uri.parse(ent.backdrop_path)).setThumbnailAspectRatio(ASPECT_RATIO_16_9)
//        }

        return wb.build()
    }

}