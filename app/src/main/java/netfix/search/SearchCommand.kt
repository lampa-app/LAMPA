package netfix.search

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import netfix.App
import netfix.app.BuildConfig
import netfix.app.R
import netfix.helpers.Helpers.openApp
import netfix.helpers.Helpers.openSettings
import netfix.helpers.Helpers.uninstallSelf
import netfix.search.SearchProvider.Companion.queryProjection
import java.util.Locale

object SearchCommand {
    fun exec(query: String): Cursor? {
        if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand exec($query)")
        // just fun
        if (query.lowercase(Locale.getDefault())
                .contains(App.context.getString(R.string.open_netfix))
        ) {
            if (BuildConfig.DEBUG) Log.d("*****", "SearchCommand matched - openApp()")
            openApp()
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
                .contains(App.context.getString(R.string.netfix_suxx))
        ) {
            uninstallSelf()
            return MatrixCursor(queryProjection, 0)
        }
        return null
    }
}