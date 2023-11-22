package top.rootu.lampa.helpers

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.helpers.Prefs.saveRecs
import top.rootu.lampa.models.Favorite
import top.rootu.lampa.models.LampaRec
import java.util.Locale

object Prefs {

    const val APP_PREFERENCES = "settings"
    const val APP_LAST_PLAYED = "last_played"
    const val APP_URL = "url"
    const val APP_PLAYER = "player"
    const val IPTV_PLAYER = "iptv_player"
    const val APP_BROWSER = "browser"
    const val APP_LANG = "lang"
    const val TMDB_API = "tmdb_api_url"
    const val TMDB_IMG = "tmdb_image_url"
    const val FAV_KEY = "fav"
    const val REC_KEY = "rec"

    val Context.lastPlayedPrefs: SharedPreferences
        get() {
            return getSharedPreferences(APP_LAST_PLAYED, MODE_PRIVATE)
        }

    val Context.appPrefs: SharedPreferences
        get() {
            return getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
        }


    val Context.appUrl: String
        get() {
            val pref = this.appPrefs
            return pref.getString(APP_URL, "") ?: ""
        }

    fun Context.setAppUrl(url: String) {
        val pref = this.appPrefs
        pref.edit().putString(APP_URL, url).apply()
    }

    val Context.appPlayer: String?
        get() {
            val pref = this.appPrefs
            return pref.getString(APP_PLAYER, "")
        }

    fun Context.setAppPlayer(player: String) {
        val pref = this.appPrefs
        pref.edit().putString(APP_PLAYER, player).apply()
    }

    val Context.tvPlayer: String?
        get() {
            val pref = this.appPrefs
            return pref.getString(IPTV_PLAYER, "")
        }

    fun Context.setTvPlayer(player: String) {
        val pref = this.appPrefs
        pref.edit().putString(IPTV_PLAYER, player).apply()
    }

    val Context.appBrowser: String?
        get() {
            val pref = this.appPrefs
            return pref.getString(APP_BROWSER, MainActivity.SELECTED_BROWSER)
        }

    fun Context.setAppBrowser(browser: String) {
        val pref = this.appPrefs
        pref.edit().putString(APP_BROWSER, browser).apply()
    }

    val Context.appLang: String
        get() {
            val pref = this.appPrefs
            return pref.getString(APP_LANG, Locale.getDefault().language)
                ?: Locale.getDefault().language
        }

    fun Context.setAppLang(lang: String) {
        val pref = this.appPrefs
        pref.edit().putString(APP_LANG, lang).apply()
    }

    val Context.tmdbApiUrl: String
        get() {
            val pref = this.appPrefs
            return pref.getString(TMDB_API, "https://api.themoviedb.org/3/")
                ?: "https://api.themoviedb.org/3/"
        }

    fun Context.setTmdbApiUrl(url: String) {
        val pref = this.appPrefs
        pref.edit().putString(TMDB_API, url).apply()
    }

    val Context.tmdbImgUrl: String
        get() {
            val pref = this.appPrefs
            return pref.getString(TMDB_IMG, "https://api.themoviedb.org/3/")
                ?: "https://image.tmdb.org/"
        }

    fun Context.setTmdbImgUrl(url: String) {
        val pref = this.appPrefs
        pref.edit().putString(TMDB_IMG, url).apply()
    }

    val Context.useWatchNext: Boolean
        get() {
            val pref = this.appPrefs
            return true
        }

    val Context.firstRun: Boolean
        get() {
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val lrv = pref.getString("last_run_version", "") ?: ""
            val firstRun = BuildConfig.VERSION_NAME != lrv
            if (firstRun)
                pref.edit().putString("last_run_version", BuildConfig.VERSION_NAME).apply()
            return firstRun
        }

    fun Context.saveFavorite(json: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putString(FAV_KEY, json).apply()
    }

    val Context.favorite: String
        get() {
            return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(FAV_KEY, "{}") ?: "{}"
        }

    fun Context.saveRecs(json: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit().putString(REC_KEY, json).apply()
    }

    val Context.recs: String
        get() {
            return PreferenceManager.getDefaultSharedPreferences(this)
                .getString(REC_KEY, "{}") ?: "{}"
        }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String, def: T): T {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
            if (prefs.all.containsKey(name))
                return prefs.all[name] as T
            return def
        } catch (e: Exception) {
            return def
        }
    }
}