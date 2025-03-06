package netfix.helpers

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import netfix.App
import netfix.app.BuildConfig
import netfix.MainActivity
import netfix.content.LampaProvider
import netfix.helpers.Helpers.getJson
import netfix.models.CubBookmark
import netfix.models.Favorite
import netfix.models.LampaRec
import netfix.models.WatchNextToAdd
import netfix.tmdb.TMDB
import java.util.Locale

object Prefs {

    const val APP_PREFERENCES = "settings"
    const val STORAGE_PREFERENCES = "storage"
    private const val APP_LAST_PLAYED = "last_played"
    private const val APP_URL = "url"
    private const val APP_URL_HISTORY = "lampa_history"
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
    private const val LOR_KEY = "look_rem"
    private const val VIR_KEY = "view_rem"
    private const val SCR_KEY = "schd_rem"
    private const val COR_KEY = "cont_rem"
    private const val THR_KEY = "thrw_rem"
    private const val SYNC_KEY = "sync_account"
    private const val PLAY_ACT_KEY = "playActivityJS"
    private const val RESUME_KEY = "resumeJS"

    data class InputHistory(val input: String, val timestamp: Long)

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

    val Context.defPrefs: SharedPreferences
        get() {
            return PreferenceManager.getDefaultSharedPreferences(this)
        }

    var Context.appUrl: String
        get() {
            return this.appPrefs.getString(APP_URL, BuildConfig.defaultAppUrl) ?: ""
        }
        set(url) {
            this.appPrefs.edit().putString(APP_URL, url).apply()
        }

    var Context.appPlayer: String?
        get() {
            return this.appPrefs.getString(APP_PLAYER, "")
        }
        set(player) {
            this.appPrefs.edit().putString(APP_PLAYER, player).apply()
        }

    var Context.tvPlayer: String?
        get() {
            return this.appPrefs.getString(IPTV_PLAYER, "")
        }
        set(player) {
            this.appPrefs.edit().putString(IPTV_PLAYER, player).apply()
        }

    var Context.lampaSource: String
        get() {
            return this.appPrefs.getString(LAMPA_SOURCE, "tmdb") ?: "tmdb"
        }
        set(source) {
            this.appPrefs.edit().putString(LAMPA_SOURCE, source).apply()
        }

    var Context.appBrowser: String?
        get() {
            return this.appPrefs.getString(APP_BROWSER, MainActivity.SELECTED_BROWSER)
        }
        set(browser) {
            this.appPrefs.edit().putString(APP_BROWSER, browser).apply()
        }

    var Context.appLang: String
        get() {
            return this.appPrefs.getString(APP_LANG, Locale.getDefault().language)
                ?: Locale.getDefault().language
        }
        set(lang) {
            this.appPrefs.edit().putString(APP_LANG, lang).apply()
        }

    var Context.tmdbApiUrl: String
        get() {
            return this.appPrefs.getString(TMDB_API, TMDB.APIURL)
                ?: TMDB.APIURL
        }
        set(url) {
            this.appPrefs.edit().putString(TMDB_API, url).apply()
        }

    var Context.tmdbImgUrl: String
        get() {
            return this.appPrefs.getString(TMDB_IMG, TMDB.IMGURL)
                ?: TMDB.IMGURL
        }
        set(url) {
            this.appPrefs.edit().putString(TMDB_IMG, url).apply()
        }

    val Context.firstRun: Boolean
        get() {
            val lrv = defPrefs.getString("last_run_version", "") ?: ""
            val firstRun = BuildConfig.VERSION_NAME != lrv
            if (firstRun)
                defPrefs.edit().putString("last_run_version", BuildConfig.VERSION_NAME).apply()
            return firstRun
        }

    var Context.playActivityJS: String?
        get() {
            return defPrefs.getString(PLAY_ACT_KEY, "{}")
        }
        set(json) {
            defPrefs.edit().putString(PLAY_ACT_KEY, json).apply()
        }

    var Context.resumeJS: String?
        get() {
            return defPrefs.getString(RESUME_KEY, "{}")
        }
        set(json) {
            defPrefs.edit().putString(RESUME_KEY, json).apply()
        }

    val Context.FAV: Favorite?
        get() {
            val buf = defPrefs.getString(FAV_KEY, "{}") ?: "{}"
            val fav = getJson(buf, Favorite::class.java)?.apply {
                this.card?.forEach {
                    it.fixCard()
                }
            }
            return fav
        }

    fun Context.saveFavorite(json: String) {
        defPrefs.edit().putString(FAV_KEY, json).apply()
    }

    val Context.REC: List<LampaRec>?
        get() {
            val buf = defPrefs.getString(REC_KEY, "[]") ?: "[]"
            return getJson(buf, Array<LampaRec>::class.java)?.toList()
        }


    fun Context.saveRecs(json: String) {
        defPrefs.edit().putString(REC_KEY, json).apply()
    }


    val Context.CUB: List<CubBookmark>?
        get() {
            val buf = defPrefs.getString(CUB_KEY, "[]") ?: "[]"
            return getJson(buf, Array<CubBookmark>::class.java)?.toList()
        }

    fun Context.saveAccountBookmarks(json: String) {
        defPrefs.edit().putString(CUB_KEY, json).apply()
    }

    var Context.syncEnabled: Boolean
        get() {
            return this.appPrefs.getBoolean(SYNC_KEY, false)
        }
        set(enabled) {
            this.appPrefs.edit().putBoolean(SYNC_KEY, enabled).apply()
        }

    private fun Context.getCubBookmarkCardIds(which: String? = null): List<String?> {
        var bookmarks = this.CUB
        if (!which.isNullOrEmpty())
            bookmarks = this.CUB?.filter { it.type == which }
        return bookmarks?.map { it.card_id } ?: emptyList()
    }

    private val Context.cubWatchNext: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.LATE).reversed()
        }

    private val Context.cubBook: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.BOOK)
        }

    private val Context.cubLike: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.LIKE)
        }

    private val Context.cubHistory: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.HIST)
        }

    private val Context.cubLook: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.LOOK)
        }

    private val Context.cubViewed: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.VIEW)
        }

    private val Context.cubSheduled: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.SCHD)
        }

    private val Context.cubContinued: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.CONT)
        }

    private val Context.cubThrown: List<String?>
        get() {
            return this.getCubBookmarkCardIds(LampaProvider.THRW)
        }

    fun Context.isInLampaWatchNext(id: String): Boolean {
        return if (this.syncEnabled) isInCubWatchNext(id) else isInFavWatchNext(id)
    }

    private fun Context.isInCubWatchNext(id: String): Boolean {
        return this.cubWatchNext.contains(id)
    }

    private fun Context.isInFavWatchNext(id: String): Boolean {
        return this.FAV?.wath?.contains(id) == true
    }

    val Context.wathToAdd: List<WatchNextToAdd>
        get() {
            val buf = defPrefs.getString(WNA_KEY, "[]")
            val arr = getJson(buf, Array<WatchNextToAdd>::class.java)
            return if (this.syncEnabled)
                arr?.filter { !this.isInCubWatchNext(it.id) } ?: emptyList()
            else
                arr?.filter { !this.isInLampaWatchNext(it.id) } ?: emptyList()
        }

    val Context.wathToRemove: List<String>
        get() {
            val buf = defPrefs.getString(WNR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.wath?.contains(it) == true || this.cubWatchNext.contains(it) }
                ?: emptyList()
        }

    val Context.bookToRemove: List<String>
        get() {
            val buf = defPrefs.getString(BMR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.book?.contains(it) == true || this.cubBook.contains(it) }
                ?: emptyList()
        }

    val Context.likeToRemove: List<String>
        get() {
            val buf = defPrefs.getString(LKR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.like?.contains(it) == true || this.cubLike.contains(it) }
                ?: emptyList()
        }

    val Context.histToRemove: List<String>
        get() {
            val buf = defPrefs.getString(HSR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.history?.contains(it) == true || this.cubHistory.contains(it) }
                ?: emptyList()
        }

    val Context.lookToRemove: List<String>
        get() {
            val buf = defPrefs.getString(LOR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.look?.contains(it) == true || this.cubLook.contains(it) }
                ?: emptyList()
        }

    val Context.viewToRemove: List<String>
        get() {
            val buf = defPrefs.getString(VIR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.viewed?.contains(it) == true || this.cubViewed.contains(it) }
                ?: emptyList()
        }

    val Context.schdToRemove: List<String>
        get() {
            val buf = defPrefs.getString(SCR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.scheduled?.contains(it) == true || this.cubSheduled.contains(it) }
                ?: emptyList()
        }

    val Context.contToRemove: List<String>
        get() {
            val buf = defPrefs.getString(COR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter {
                    this.FAV?.continued?.contains(it) == true || this.cubContinued.contains(
                        it
                    )
                }
                ?: emptyList()
        }

    val Context.thrwToRemove: List<String>
        get() {
            val buf = defPrefs.getString(THR_KEY, "[]")
            return getJson(buf, Array<String>::class.java)
                ?.filter { this.FAV?.thrown?.contains(it) == true || this.cubThrown.contains(it) }
                ?: emptyList()
        }

    fun Context.addWatchNextToAdd(item: WatchNextToAdd) {
        val lst = this.wathToAdd.toMutableList()
        lst.add(item)
        defPrefs.edit().putString(WNA_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addWatchNextToRemove(items: List<String>) {
        val lst = this.wathToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(WNR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addBookToRemove(items: List<String>) {
        val lst = this.bookToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(BMR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addLikeToRemove(items: List<String>) {
        val lst = this.likeToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(LKR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addHistToRemove(items: List<String>) {
        val lst = this.histToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(HSR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addLookToRemove(items: List<String>) {
        val lst = this.lookToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(LOR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addViewToRemove(items: List<String>) {
        val lst = this.viewToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(VIR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addSchdToRemove(items: List<String>) {
        val lst = this.schdToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(SCR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addContToRemove(items: List<String>) {
        val lst = this.contToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(COR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.addThrwToRemove(items: List<String>) {
        val lst = this.thrwToRemove.toMutableList()
        lst += items
        defPrefs.edit().putString(THR_KEY, Gson().toJson(lst.distinct())).apply()
    }

    fun Context.clearPending() {
        defPrefs.edit()
            .putString(WNA_KEY, "[]")
            .putString(WNR_KEY, "[]")
            .putString(BMR_KEY, "[]")
            .putString(LKR_KEY, "[]")
            .putString(HSR_KEY, "[]")
            .putString(LOR_KEY, "[]")
            .putString(VIR_KEY, "[]")
            .putString(SCR_KEY, "[]")
            .putString(COR_KEY, "[]")
            .putString(THR_KEY, "[]")
            .apply()
    }

    val Context.urlHistory: List<String>
        get() {
            val buf = defPrefs.getString(APP_URL_HISTORY, "[]")
            return getJson(buf, Array<InputHistory>::class.java)
                ?.sortedBy { -it.timestamp }
                ?.map { it.input }
                ?: emptyList()
        }

    fun Context.addUrlHistory(v: String) {
        var buf = defPrefs.getString(APP_URL_HISTORY, "[]")
        val lst = getJson(buf, Array<InputHistory>::class.java)
            ?.filter { it.input != v }
            ?.sortedBy { it.timestamp }
            ?.toMutableList()
        lst?.add(InputHistory(v, System.currentTimeMillis()))
        buf = Gson().toJson(lst)
        defPrefs.edit()
            .putString(APP_URL_HISTORY, buf)
            .apply()
    }

    fun Context.remUrlHistory(v: String) {
        var buf = defPrefs.getString(APP_URL_HISTORY, "[]")
        val lst = getJson(buf, Array<InputHistory>::class.java)
            ?.filter { it.input != v }
            ?.sortedBy { it.timestamp }
            ?.toMutableList()
        buf = Gson().toJson(lst)
        defPrefs.edit()
            .putString(APP_URL_HISTORY, buf)
            .apply()
    }

    fun Context.clearUrlHistory() {
        defPrefs.edit()
            .putString(APP_URL_HISTORY, "[]")
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