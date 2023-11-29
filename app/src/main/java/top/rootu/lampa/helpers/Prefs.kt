package top.rootu.lampa.helpers

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.models.CubBookmark
import top.rootu.lampa.models.Favorite
import top.rootu.lampa.models.LampaRec
import top.rootu.lampa.models.WatchNextToAdd
import java.util.Locale

object Prefs {

    const val APP_PREFERENCES = "settings"
    const val STORAGE_PREFERENCES = "storage"
    private const val APP_LAST_PLAYED = "last_played"
    private const val APP_URL = "url"
    private const val APP_PLAYER = "player"
    private const val IPTV_PLAYER = "iptv_player"
    private const val LAMPA_SOURCE = "source"
    private const val APP_BROWSER = "browser"
    private const val APP_LANG = "lang"
    private const val TMDB_API = "tmdb_api_url"
    private const val TMDB_IMG = "tmdb_image_url"
    private const val FAV_KEY = "fav"
    private const val REC_KEY = "rec"
    private const val CUB_KEY = "cub"
    private const val WNA_KEY = "wath_add"
    private const val WNR_KEY = "wath_rem"
    private const val BMR_KEY = "book_rem"
    private const val LKR_KEY = "like_rem"
    private const val HSR_KEY = "hist_rem"
    private const val SYNC_KEY = "sync_account"

    val Context.appPrefs: SharedPreferences
        get() {
            return getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
        }

    val Context.storagePrefs: SharedPreferences
        get() {
            return getSharedPreferences(STORAGE_PREFERENCES, MODE_PRIVATE)
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

    val Context.lampaSource: String
        get() {
            val pref = this.appPrefs
            return pref.getString(LAMPA_SOURCE, "tmdb") ?: "tmdb"
        }

    fun Context.setLampaSource(source: String) {
        val pref = this.appPrefs
        pref.edit().putString(LAMPA_SOURCE, source).apply()
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
            return pref.getString(TMDB_IMG, "https://image.tmdb.org/")
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
                val fav = Gson().fromJson(buf, Favorite::class.java).apply {
                    this.card?.forEach {
                        it.fixCard()
                    }
                }
                return fav
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

    val Context.CUB: List<CubBookmark>?
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(CUB_KEY, "{}") ?: "{}"
                Gson().fromJson(buf, Array<CubBookmark>::class.java).toList()
            } catch (e: Exception) {
                null
            }
        }

    fun Context.saveAccountBookmarks(json: String) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putString(CUB_KEY, json).apply()
    }

    val Context.syncEnabled: Boolean
        get() {
            val pref = this.appPrefs
            return pref.getBoolean(SYNC_KEY, false)
        }

    fun Context.setSyncEnabled(enabled: Boolean) {
        val pref = this.appPrefs
        pref.edit().putBoolean(SYNC_KEY, enabled).apply()
    }

    val Context.cubWatchNext: List<String?>
        get() {
//            val bookmarks = this.CUB?.filter { it.type == LampaProvider.Late }
//            bookmarks?.forEachIndexed { index, item ->
//                val card = Gson().fromJson(item.data, LampaCard::class.java)
//                card.fixCard()
//                Log.d("*****", "CUB WatchNext [$index] $card")
//            }
//            return bookmarks?.map { it.card_id } ?: emptyList()
            return this.getCubBookmarkCardIds(LampaProvider.Late)
        }

    fun Context.getCubBookmarkCardIds(which: String? = null): List<String?> {
        var bookmarks = this.CUB
        if (!which.isNullOrEmpty())
            bookmarks = this.CUB?.filter { it.type == which }

        return bookmarks?.map { it.card_id } ?: emptyList()
    }

    val Context.cubBook: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.Book)
        }

    val Context.cubLike: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.Like)
        }

    val Context.cubHistory: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.Hist)
        }

    fun Context.isInLampaWatchNext(id: String): Boolean {
        return if (this.syncEnabled)
            isInCubWatchNext(id) else isInFavWatchNext(id)
    }
    fun Context.isInCubWatchNext(id: String): Boolean {
        return this.cubWatchNext.contains(id)
    }

    fun Context.isInFavWatchNext(id: String): Boolean {
        return this.FAV?.wath?.contains(id) == true
    }

    val Context.wathToAdd: List<WatchNextToAdd>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(WNA_KEY, "[]")
                val arr = Gson().fromJson(buf, Array<WatchNextToAdd>::class.java)
                //arr.toList()
                arr.filter { !this.isInLampaWatchNext(it.id) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.wathToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(WNR_KEY, "[]")
                val arr = Gson().fromJson(buf, Array<String>::class.java)
                //arr.toList()
                arr.filter { this.FAV?.wath?.contains(it) == true || this.cubWatchNext.contains(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.bookToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(BMR_KEY, "[]")
                val arr = Gson().fromJson(buf, Array<String>::class.java)
                //arr.toList()
                arr.filter { this.FAV?.book?.contains(it) == true || this.cubBook.contains(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.likeToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(LKR_KEY, "[]")
                val arr = Gson().fromJson(buf, Array<String>::class.java)
                //arr.toList()
                arr.filter { this.FAV?.like?.contains(it) == true || this.cubLike.contains(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    val Context.histToRemove: List<String>
        get() {
            return try {
                val pref = PreferenceManager.getDefaultSharedPreferences(this)
                val buf = pref.getString(HSR_KEY, "[]")
                val arr = Gson().fromJson(buf, Array<String>::class.java)
                //arr.toList()
                arr.filter { this.FAV?.history?.contains(it) == true || this.cubHistory.contains(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun Context.addWatchNextToAdd(item: WatchNextToAdd) {
        val lst = this.wathToAdd.toMutableList()
        lst.add(item)
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

    fun Context.clearPending() {
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.edit()
            .putString(WNA_KEY, "[]")
            .putString(WNR_KEY, "[]")
            .putString(BMR_KEY, "[]")
            .putString(LKR_KEY, "[]")
            .putString(HSR_KEY, "[]")
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