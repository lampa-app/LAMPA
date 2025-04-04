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
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.models.CubBookmark
import top.rootu.lampa.models.Favorite
import top.rootu.lampa.models.LampaRec
import top.rootu.lampa.models.WatchNextToAdd
import top.rootu.lampa.tmdb.TMDB
import java.util.Locale

object Prefs {

    // Constants for SharedPreferences keys
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
    private const val TMDB_API_KEY = "tmdb_api_url"
    private const val TMDB_IMG_KEY = "tmdb_image_url"
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
    private const val MIGRATE_KEY = "migrate"

    // Extension properties for SharedPreferences
    val Context.appPrefs: SharedPreferences
        get() = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)

    val Context.storagePrefs: SharedPreferences
        get() = getSharedPreferences(STORAGE_PREFERENCES, MODE_PRIVATE)

    val Context.lastPlayedPrefs: SharedPreferences
        get() = getSharedPreferences(APP_LAST_PLAYED, MODE_PRIVATE)

    val Context.defPrefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(this)

    // Extension properties for app settings
    var Context.appUrl: String
        get() = appPrefs.getString(APP_URL, BuildConfig.defaultAppUrl) ?: ""
        set(url) = appPrefs.edit().putString(APP_URL, url).apply()

    var Context.appPlayer: String?
        get() = appPrefs.getString(APP_PLAYER, "")
        set(player) = appPrefs.edit().putString(APP_PLAYER, player).apply()

    var Context.tvPlayer: String?
        get() = appPrefs.getString(IPTV_PLAYER, "")
        set(player) = appPrefs.edit().putString(IPTV_PLAYER, player).apply()

    var Context.lampaSource: String
        get() = appPrefs.getString(LAMPA_SOURCE, "tmdb") ?: "tmdb"
        set(source) = appPrefs.edit().putString(LAMPA_SOURCE, source).apply()

    var Context.appBrowser: String?
        get() = appPrefs.getString(APP_BROWSER, MainActivity.SELECTED_BROWSER)
        set(browser) = appPrefs.edit().putString(APP_BROWSER, browser).apply()

    var Context.appLang: String
        get() = appPrefs.getString(APP_LANG, Locale.getDefault().language)
            ?: Locale.getDefault().language
        set(lang) = appPrefs.edit().putString(APP_LANG, lang).apply()

    var Context.tmdbApiUrl: String
        get() = appPrefs.getString(TMDB_API_KEY, TMDB.APIURL) ?: TMDB.APIURL
        set(url) = appPrefs.edit().putString(TMDB_API_KEY, url).apply()

    var Context.tmdbImgUrl: String
        get() = appPrefs.getString(TMDB_IMG_KEY, TMDB.IMGURL) ?: TMDB.IMGURL
        set(url) = appPrefs.edit().putString(TMDB_IMG_KEY, url).apply()

    val Context.firstRun: Boolean
        get() {
            val lastRunVersion = defPrefs.getString("last_run_version", "")
            val isFirstRun = BuildConfig.VERSION_NAME != lastRunVersion
            if (isFirstRun) defPrefs.edit().putString("last_run_version", BuildConfig.VERSION_NAME)
                .apply()
            return isFirstRun
        }

    // Extension properties for favorites and bookmarks
    val Context.FAV: Favorite?
        get() = defPrefs.getString(FAV_KEY, "{}")?.let { json ->
            getJson(json, Favorite::class.java)?.apply {
                card?.forEach { it.fixCard() }
            }
        }

    val Context.CUB: List<CubBookmark>?
        get() = defPrefs.getString(CUB_KEY, "[]")?.let { json ->
            getJson(json, Array<CubBookmark>::class.java)?.toList()
        }

    val Context.REC: List<LampaRec>?
        get() = defPrefs.getString(REC_KEY, "[]")?.let { json ->
            getJson(json, Array<LampaRec>::class.java)?.toList()
        }

    var Context.syncEnabled: Boolean
        get() = appPrefs.getBoolean(SYNC_KEY, false)
        set(enabled) = appPrefs.edit().putBoolean(SYNC_KEY, enabled).apply()

    /**
     * A property to get or set the migration status in SharedPreferences.
     * - `true`: Migration is enabled.
     * - `false`: Migration is disabled or not set.
     */
    var Context.migrate: Boolean
        get() = defPrefs.getBoolean(MIGRATE_KEY, false) // Default to false if key doesn't exist
        set(enabled) {
            defPrefs.edit().apply {
                if (enabled) {
                    putBoolean(MIGRATE_KEY, true)
                } else {
                    remove(MIGRATE_KEY)
                }
                apply() // Save changes asynchronously
            }
        }

    // Helper functions for store lampa json to app prefs
    fun Context.saveFavorite(json: String) = defPrefs.edit().putString(FAV_KEY, json).apply()

    fun Context.saveAccountBookmarks(json: String) =
        defPrefs.edit().putString(CUB_KEY, json).apply()

    fun Context.saveRecs(json: String) = defPrefs.edit().putString(REC_KEY, json).apply()

    // Helper functions for managing watch next and bookmarks
    private fun Context.getCubBookmarkCardIds(which: String? = null): List<String?> {
        return CUB?.filter { which == null || it.type == which }?.map { it.card_id } ?: emptyList()
    }

    private val Context.cubWatchNext: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.LATE).reversed()

    private val Context.cubBook: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.BOOK)

    private val Context.cubLike: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.LIKE)

    private val Context.cubHistory: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.HIST)

    private val Context.cubLook: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.LOOK)

    private val Context.cubViewed: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.VIEW)

    private val Context.cubScheduled: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.SCHD)

    private val Context.cubContinued: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.CONT)

    private val Context.cubThrown: List<String?>
        get() = getCubBookmarkCardIds(LampaProvider.THRW)

    fun Context.isInWatchNext(id: String): Boolean {
        return FAV?.wath?.contains(id) == true || cubWatchNext.contains(id)
    }

    // Extension properties for pending actions
    val Context.wathToAdd: List<WatchNextToAdd>
        get() = defPrefs.getString(WNA_KEY, "[]")?.let { json ->
            getJson(json, Array<WatchNextToAdd>::class.java)?.filter {
                !isInWatchNext(it.id)
            } ?: emptyList()
        } ?: emptyList()

    val Context.wathToRemove: List<String>
        get() = defPrefs.getString(WNR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.wath?.contains(it) == true || cubWatchNext.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.bookToRemove: List<String>
        get() = defPrefs.getString(BMR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.book?.contains(it) == true || cubBook.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.likeToRemove: List<String>
        get() = defPrefs.getString(LKR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.like?.contains(it) == true || cubLike.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.histToRemove: List<String>
        get() = defPrefs.getString(HSR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.history?.contains(it) == true || cubHistory.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.lookToRemove: List<String>
        get() = defPrefs.getString(LOR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.look?.contains(it) == true || cubLook.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.viewToRemove: List<String>
        get() = defPrefs.getString(VIR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.viewed?.contains(it) == true || cubViewed.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.schdToRemove: List<String>
        get() = defPrefs.getString(SCR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.scheduled?.contains(it) == true || cubScheduled.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.contToRemove: List<String>
        get() = defPrefs.getString(COR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.continued?.contains(it) == true || cubContinued.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    val Context.thrwToRemove: List<String>
        get() = defPrefs.getString(THR_KEY, "[]")?.let { json ->
            getJson(json, Array<String>::class.java)?.filter {
                FAV?.thrown?.contains(it) == true || cubThrown.contains(it)
            } ?: emptyList()
        } ?: emptyList()

    // Generic helper function to add items to a list and save it to SharedPreferences
    private inline fun <reified T> Context.addItemsToPreference(
        key: String,
        currentList: List<T>,
        newItems: List<T>
    ) {
        val updatedList = (currentList + newItems).distinct()
        defPrefs.edit().putString(key, Gson().toJson(updatedList)).apply()
    }

    // Extension functions for adding items to specific preference lists
    fun Context.addWatchNextToAdd(item: WatchNextToAdd) {
        addItemsToPreference(WNA_KEY, wathToAdd, listOf(item))
    }

    fun Context.addWatchNextToRemove(items: List<String>) {
        addItemsToPreference(WNR_KEY, wathToRemove, items)
    }

    fun Context.addBookToRemove(items: List<String>) {
        addItemsToPreference(BMR_KEY, bookToRemove, items)
    }

    fun Context.addLikeToRemove(items: List<String>) {
        addItemsToPreference(LKR_KEY, likeToRemove, items)
    }

    fun Context.addHistToRemove(items: List<String>) {
        addItemsToPreference(HSR_KEY, histToRemove, items)
    }

    fun Context.addLookToRemove(items: List<String>) {
        addItemsToPreference(LOR_KEY, lookToRemove, items)
    }

    fun Context.addViewToRemove(items: List<String>) {
        addItemsToPreference(VIR_KEY, viewToRemove, items)
    }

    fun Context.addSchdToRemove(items: List<String>) {
        addItemsToPreference(SCR_KEY, schdToRemove, items)
    }

    fun Context.addContToRemove(items: List<String>) {
        addItemsToPreference(COR_KEY, contToRemove, items)
    }

    fun Context.addThrwToRemove(items: List<String>) {
        addItemsToPreference(THR_KEY, thrwToRemove, items)
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

    // Data class to represent URL history entries
    data class InputHistory(
        val input: String,
        val timestamp: Long
    )

    // Extension property to get URL history
    val Context.urlHistory: List<String>
        get() {
            val json = defPrefs.getString(APP_URL_HISTORY, "[]")
            return parseUrlHistory(json)
                .sortedByDescending { it.timestamp } // Sort by timestamp in descending order
                .map { it.input } // Extract the URL strings
        }

    // Extension function to add a URL to history
    fun Context.addUrlHistory(url: String) {
        val history = parseUrlHistory(defPrefs.getString(APP_URL_HISTORY, "[]"))
            .filter { it.input != url } // Remove duplicates
            .toMutableList()
        history.add(InputHistory(url, System.currentTimeMillis())) // Add new entry
        saveUrlHistory(history)
    }

    // Extension function to remove a URL from history
    fun Context.remUrlHistory(url: String) {
        val history = parseUrlHistory(defPrefs.getString(APP_URL_HISTORY, "[]"))
            .filter { it.input != url } // Remove the specified URL
        saveUrlHistory(history)
    }

    // Extension function to clear URL history
    fun Context.clearUrlHistory() {
        saveUrlHistory(emptyList())
    }

    // Helper function to parse URL history from JSON
    private fun parseUrlHistory(json: String?): List<InputHistory> {
        return try {
            Gson().fromJson(json, Array<InputHistory>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList() // Return an empty list if parsing fails
        }
    }

    // Helper function to save URL history to SharedPreferences
    private fun Context.saveUrlHistory(history: List<InputHistory>) {
        val json = Gson().toJson(history)
        defPrefs.edit()
            .putString(APP_URL_HISTORY, json)
            .apply()
    }

    // Generic function to get preferences
    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String, def: T): T {
        return try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(App.context)
            prefs.all[name] as? T ?: def
        } catch (_: Exception) {
            def
        }
    }
}