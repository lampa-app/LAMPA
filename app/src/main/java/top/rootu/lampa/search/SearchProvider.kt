package top.rootu.lampa.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException

class SearchProvider : ContentProvider() {
    private val mUriMatcher: UriMatcher = buildUriMatcher()

    override fun onCreate(): Boolean {
        return true
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        if (mUriMatcher.match(uri) == SEARCH_SUGGEST) {
            var rawQuery = ""
            if (!selectionArgs.isNullOrEmpty()) {
                rawQuery = selectionArgs[0]
            }

            if (rawQuery.isEmpty())
                uri.lastPathSegment?.let {
                    rawQuery = it
                }

            if (rawQuery.isEmpty())
                return MatrixCursor(queryProjection, 0)

            if (rawQuery == "dummy")
                return MatrixCursor(queryProjection, 0)

            val ret = SearchCommand.exec(rawQuery)
            ret?.let {
                return ret
            }

            return search(rawQuery)
        } else {
            Log.i("*****", "Unknown Uri: $uri")
            return MatrixCursor(queryProjection, 0)
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun search(query: String?): Cursor {
        val matrixCursor = MatrixCursor(queryProjection)
        query ?: return matrixCursor
        try {
            val results = SearchDatabase.search(query)
            if (results.isNotEmpty()) {
                return SearchDatabase.getMatrix(results)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return matrixCursor
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, contentValues: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert is not implemented.")
    }

    override fun delete(uri: Uri, s: String?, strings: Array<String>?): Int {
        throw UnsupportedOperationException("Delete is not implemented.")
    }

    override fun update(
        uri: Uri,
        contentValues: ContentValues?,
        s: String?,
        strings: Array<String>?
    ): Int {
        throw UnsupportedOperationException("Update is not implemented.")
    }

    companion object {
        private const val AUTHORITY = "top.rootu.lampa.atvsearch"
        private const val SEARCH_SUGGEST = 1

        val queryProjection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            arrayOf(
                BaseColumns._ID,
                SearchDatabase.KEY_NAME,
                SearchDatabase.KEY_DESCRIPTION,
                SearchDatabase.KEY_ICON,
                SearchDatabase.KEY_DATA_TYPE,
                SearchDatabase.KEY_IS_LIVE,
                SearchDatabase.KEY_VIDEO_WIDTH,
                SearchDatabase.KEY_VIDEO_HEIGHT,
                SearchDatabase.KEY_AUDIO_CHANNEL_CONFIG,
                SearchDatabase.KEY_PURCHASE_PRICE,
                SearchDatabase.KEY_RENTAL_PRICE,
                SearchDatabase.KEY_RATING_STYLE,
                SearchDatabase.KEY_RATING_SCORE,
                SearchDatabase.KEY_PRODUCTION_YEAR,
                SearchDatabase.KEY_COLUMN_DURATION,
                SearchDatabase.KEY_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA
            ) else
            arrayOf(
                BaseColumns._ID,
                SearchDatabase.KEY_NAME,
                SearchDatabase.KEY_DESCRIPTION,
                SearchDatabase.KEY_ICON,
                SearchDatabase.KEY_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA
            )

        private fun buildUriMatcher(): UriMatcher {
            val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
            uriMatcher.addURI(
                AUTHORITY, "/search/" + SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST
            )
            uriMatcher.addURI(
                AUTHORITY,
                "/search/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*",
                SEARCH_SUGGEST
            )
            return uriMatcher
        }
    }
}