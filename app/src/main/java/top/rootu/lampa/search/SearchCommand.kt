package top.rootu.lampa.search

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Helpers.openSearch
import top.rootu.lampa.helpers.Helpers.openSettings
import top.rootu.lampa.helpers.Helpers.uninstallSelf
import top.rootu.lampa.search.SearchProvider.Companion.queryProjection
import java.util.Locale

object SearchCommand {
    fun exec(query: String): Cursor? {
        if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand exec($query)")
//        if (query.lowercase(Locale.getDefault())
//                .contains(App.context.getString(R.string.update_all))
//        ) {
//            Scheduler.scheduleUpdate(false)
//            App.toast(App.context.getString(R.string.update_pref_done), true)
//            return MatrixCursor(queryProjection, 0)
//        }
        // just fun
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.open_search))
        ) {
            openSearch()
            return MatrixCursor(queryProjection, 0)
        }
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.open_settings))
        ) {
            openSettings()
            return MatrixCursor(queryProjection, 0)
        }
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.lampa_suxx))
        ) {
            uninstallSelf()
            return MatrixCursor(queryProjection, 0)
        }
        return null
    }
}