package top.netfix.search

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import top.netfix.App
import top.netfix.BuildConfig
import top.netfix.R
import top.netfix.helpers.Helpers.openLampa
import top.netfix.helpers.Helpers.openSettings
import top.netfix.helpers.Helpers.uninstallSelf
import top.netfix.search.SearchProvider.Companion.queryProjection
import java.util.Locale

object SearchCommand {
    fun exec(query: String): Cursor? {
        if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand exec($query)")
        // just fun
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.open_lampa))
        ) {
            if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand matched - openLampa()")
            openLampa()
            return MatrixCursor(queryProjection, 0)
        }
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.open_settings))
        ) {
            if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand matched - openSettings()")
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