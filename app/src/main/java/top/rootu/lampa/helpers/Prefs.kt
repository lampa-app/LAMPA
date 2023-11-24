package top.rootu.lampa.helpers

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.models.Favorite
import top.rootu.lampa.models.LampaRec
import java.util.Locale

object Prefs {

    private const val APP_PREFERENCES = "settings"
    private const val APP_LAST_PLAYED = "last_played"
    private const val APP_URL = "url"
    private const val APP_PLAYER = "player"
    private const val IPTV_PLAYER = "iptv_player"
    private const val APP_BROWSER = "browser"
    private const val APP_LANG = "lang"
    private const val TMDB_API = "tmdb_api_url"
    private const val TMDB_IMG = "tmdb_image_url"
    private const val FAV_KEY = "fav"
    private const val REC_KEY = "rec"
    private const val WNA_KEY = "wath_add"
    private const val WNR_KEY = "wath_rem"
    private const val BMR_KEY = "book_rem"
    private const val LKR_KEY = "like_rem"
    private const val HSR_KEY = "hist_rem"

    private val Context.appPrefs: SharedPreferences
        get() {
            return getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
        }

    val Context.lastPlayedPrefs: SharedPreferences
        get() {
            return getSharedPreferences(APP_LAST_PLAYED, MODE_PRIVATE)
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
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(FAV_KEY, json).apply()
    }

    val Context.FAV: Favorite?
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(FAV_KEY, "{}") ?: "{}"
                Gson().fromJson(buf, Favorite::class.java)
            } catch (e: Exception) {
                null
            }
        }

    fun Context.saveRecs(json: String) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(REC_KEY, json).apply()
    }

    val Context.REC: List<LampaRec>?
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(REC_KEY, "{}") ?: "{}"
                Gson().fromJson(buf, Array<LampaRec>::class.java).toList()
            } catch (e: Exception) {
                null
            }
        }

    fun Context.isInLampaWatchNext(id: String): Boolean {
        return this.FAV?.wath?.contains(id) == true
    }

    val Context.wathToAdd: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(WNA_KEY, "[]")
                Gson().fromJson(buf, Array<String>::class.java)
                    .toMutableList()
                    .filter { !this.isInLampaWatchNext(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.wathToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(WNR_KEY, "[]")
                Gson().fromJson(buf, Array<String>::class.java)
                    .toMutableList()
                    .filter { this.isInLampaWatchNext(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.bookToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(BMR_KEY, "[]")
                Gson().fromJson(buf, Array<String>::class.java)
                    .toMutableList()
                    .filter { this.FAV?.book?.contains(it) == true }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.likeToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(LKR_KEY, "[]")
                Gson().fromJson(buf, Array<String>::class.java)
                    .toMutableList()
                    .filter { this.FAV?.like?.contains(it) == true }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.histToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(HSR_KEY, "[]")
                Gson().fromJson(buf, Array<String>::class.java)
                    .toMutableList()
                    .filter { this.FAV?.history?.contains(it) == true }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun Context.addWatchNextToAdd(items: List<String>) {
        val lst = this.wathToAdd.toMutableList()
        lst += items
        val uniq = lst.distinct()
        val str = Gson().toJson(uniq)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(WNA_KEY, str)
            .apply()
    }
    fun Context.addWatchNextToRemove(items: List<String>) {
        val lst = this.wathToRemove.toMutableList()
        lst += items
        val uniq = lst.distinct()
        val str = Gson().toJson(uniq)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(WNR_KEY, str)
            .apply()
    }

    fun Context.addBookToRemove(items: List<String>) {
        val lst = this.bookToRemove.toMutableList()
        lst += items
        val uniq = lst.distinct()
        val str = Gson().toJson(uniq)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(BMR_KEY, str)
            .apply()
    }

    fun Context.addLikeToRemove(items: List<String>) {
        val lst = this.likeToRemove.toMutableList()
        lst += items
        val uniq = lst.distinct()
        val str = Gson().toJson(uniq)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(LKR_KEY, str)
            .apply()
    }

    fun Context.addHistToRemove(items: List<String>) {
        val lst = this.histToRemove.toMutableList()
        lst += items
        val uniq = lst.distinct()
        val str = Gson().toJson(uniq)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(HSR_KEY, str)
            .apply()
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