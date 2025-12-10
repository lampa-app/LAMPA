package top.rootu.lampa

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import top.rootu.lampa.browser.Browser
import top.rootu.lampa.channels.LampaChannels
import top.rootu.lampa.channels.LampaChannels.updateChanByName
import top.rootu.lampa.channels.WatchNext.updateWatchNext
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.debugLog
import top.rootu.lampa.helpers.Helpers.filterValidCubBookmarks
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.lampaSource
import top.rootu.lampa.helpers.Prefs.saveAccountBookmarks
import top.rootu.lampa.helpers.Prefs.saveFavorite
import top.rootu.lampa.helpers.Prefs.saveRecs
import top.rootu.lampa.helpers.Prefs.storagePrefs
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.helpers.Prefs.tmdbImgUrl
import top.rootu.lampa.net.Http
import top.rootu.lampa.recs.RecsService
import top.rootu.lampa.tmdb.TMDB
import kotlin.system.exitProcess

class AndroidJS(private val mainActivity: MainActivity, private val browser: Browser) {

    private val store: SharedPreferences = App.context.storagePrefs

    // Local properties
    private var lastEventHash: String? = null
    private var reqResponse: MutableMap<String, String> = HashMap()
    private var keys: Array<String?>? = null
    private var values: Array<String?>? = null
    private var dumped = false

    companion object {
        // Constants
        private const val TAG = "AndroidJS"
        private const val UPDATE_DELAY = 5000L // in ms, wait before update TV channel
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun storageChange(json: String?) {
        val hash = json.hashCode().toString()
        if (hash == lastEventHash) {
            debugLog(TAG, "Ignoring duplicate storage change event: $json")
            return
        }
        lastEventHash = hash
        json?.let {
            val eo: JSONObject = if (json == "\"\"") {
                JSONObject()
            } else {
                JSONObject(json)
            }
            if (!eo.has("name") || !eo.has("value")) return

            when (eo.optString("name")) {
                "activity" -> {
                    MainActivity.lampaActivity = eo.optString("value", "{}")
                    debugLog(TAG, "lampaActivity stored: ${MainActivity.lampaActivity}")
                }

                "player_timecode" -> {
                    MainActivity.playerTimeCode = eo.optString("value", MainActivity.playerTimeCode)
                    debugLog(TAG, "playerTimeCode stored: ${MainActivity.playerTimeCode}")
                }

                "playlist_next" -> {
                    MainActivity.playerAutoNext = eo.optString("value", "true") == "true"
                    debugLog(TAG, "playerAutoNext stored: ${MainActivity.playerAutoNext}")
                }

                "language" -> {
                    val newLang = eo.optString("value", "ru")
                    if (newLang != "undefined" && mainActivity.appLang != newLang) {
                        App.setAppLanguage(mainActivity, newLang)
                        // mainActivity.appLang = newLang
                        // mainActivity.runOnUiThread { mainActivity.recreate() }
                        debugLog(TAG, "language changed to $newLang")
                    } else {
                        debugLog(TAG, "language not changed [${mainActivity.appLang}]")
                    }
                }

                "source" -> {
                    mainActivity.lampaSource = eo.optString("value", mainActivity.lampaSource)
                    debugLog(TAG, "lampaSource stored: ${mainActivity.lampaSource}")
                }

                "protocol" -> {
                    if (MainActivity.proxyTmdbEnabled) {
                        debugLog(TAG, "protocol changed. run getLampaTmdbUrls()")
                        mainActivity.getLampaTmdbUrls()
                    } else {
                        debugLog(TAG, "protocol changed. TMDB Proxy disabled, nothing to do.")
                    }
                }

                "proxy_tmdb" -> {
                    val newState = eo.optString("value", "true") == "true"
                    MainActivity.proxyTmdbEnabled = newState
                    debugLog(TAG, "proxyTmdbEnabled set to $newState")
                    if (MainActivity.proxyTmdbEnabled) {
                        mainActivity.getLampaTmdbUrls()
                    } else {
                        // store defaults to prefs
                        debugLog(TAG, "Apply default TMDB URLs")
                        mainActivity.tmdbApiUrl = TMDB.APIURL
                        mainActivity.tmdbImgUrl = TMDB.IMGURL
                    }
                }

                "baseUrlApiTMDB" -> {
                    val newUrl = eo.optString("value", TMDB.APIURL)
                    if (newUrl.startsWith("http", true) &&
                        !newUrl.contains(mainActivity.tmdbApiUrl, true)
                    ) {
                        mainActivity.tmdbApiUrl = newUrl
                        debugLog(
                            TAG,
                            "baseUrlApiTMDB changed, tmdbApiUrl ${mainActivity.tmdbApiUrl}"
                        )
                    } else {
                        debugLog(
                            TAG,
                            "baseUrlApiTMDB changed, tmdbApiUrl not changed: ${mainActivity.tmdbApiUrl}"
                        )
                    }
                }

                "baseUrlImageTMDB" -> {
                    val newUrl = eo.optString("value", TMDB.IMGURL)
                    if (newUrl.startsWith("http", true) &&
                        !newUrl.contains(mainActivity.tmdbImgUrl, true)
                    ) {
                        mainActivity.tmdbImgUrl = newUrl
                        debugLog(
                            TAG,
                            "baseUrlImageTMDB changed, tmdbImgUrl ${mainActivity.tmdbImgUrl}"
                        )
                    } else {
                        debugLog(
                            TAG,
                            "baseUrlImageTMDB changed, tmdbImgUrl not changed: ${mainActivity.tmdbImgUrl}"
                        )
                    }
                }

                "favorite" -> {
                    val json = eo.optString("value", "")
                    if (isValidJson(json)) {
                        mainActivity.saveFavorite(json)
                        debugLog(TAG, "favorite JSON saved to prefs")
                    } else {
                        Log.e(TAG, "Not valid JSON in favorite")
                    }
                }

                "account_use" -> {
                    val use = eo.optBoolean("value", false)
                    debugLog(TAG, "set syncEnabled $use")
                    mainActivity.syncEnabled = use
                }

                "recomends_list" -> {
                    val json = eo.optString("value", "")
                    if (isValidJson(json)) {
                        mainActivity.saveRecs(json)
                        debugLog(TAG, "recomends_list JSON saved to prefs")
                    } else {
                        Log.e(TAG, "Not valid JSON in recomends_list")
                    }
                }

                else -> { // no op
                }
            }
        }
    }


    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun appVersion(): String {
        return BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun exit() {
        try {
            mainActivity.runOnUiThread { mainActivity.appExit() }
        } catch (_: Exception) {
            exitProcess(1)
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Throws(JSONException::class)
    fun openTorrentLink(url: String, jsonString: String): Boolean {
        val jsonData = if (jsonString == "\"\"") JSONObject() else JSONObject(jsonString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (url.startsWith("magnet", ignoreCase = true)) {
                data = url.toUri()
            } else {
                setDataAndType(url.toUri(), "application/x-bittorrent")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addCategory(Intent.CATEGORY_BROWSABLE)

            jsonData.optString("title").takeIf { it.isNotEmpty() }?.let { title ->
                putExtra("title", title)
                putExtra("displayName", title)
                putExtra("forcename", title)
            }

            jsonData.optString("poster").takeIf { it.isNotEmpty() }?.let { poster ->
                putExtra("poster", poster)
            }

            jsonData.optString("media").takeIf { it.isNotEmpty() }?.let { category ->
                putExtra("category", category)
            }

            jsonData.optJSONObject("data")?.let { dataObj ->
                putExtra("data", dataObj.toString())
            }
        }

        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open torrent link", e)
                App.toast(R.string.no_torrent_activity_found, true)
            }
        }
        // Force update Recs to filter viewed
        CoroutineScope(Dispatchers.Default).launch {
            delay(UPDATE_DELAY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LampaChannels.updateRecsChannel()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                RecsService.updateRecs()
            }
        }

        return true
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openYoutube(str: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://www.youtube.com/watch?v=$str".toUri()
        )
        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                App.toast(R.string.no_youtube_activity_found, true)
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No browser found: ${e.message}")
                App.toast(R.string.no_activity_found, true)
            } catch (e: Exception) {
                Log.e(TAG, "Browser launch failed: ${e.message}", e)
                App.toast(R.string.generic_error, true)
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun clearDefaultPlayer() {
        mainActivity.runOnUiThread {
            mainActivity.setPlayerPackage("", false)
            mainActivity.setPlayerPackage("", true)
            App.toast(R.string.select_player_reset)
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun httpReq(str: String, returnI: Int) {
        debugLog(TAG, "httpReq JSON $str")
        try {
            val jsonObject = JSONObject(str)
            Http.disableH2(jsonObject.optBoolean("disableH2", false))

            // Extract all parameters upfront
            val url = jsonObject.optString("url").takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("URL cannot be empty")
            val data = jsonObject.opt("post_data")
            val returnHeaders = jsonObject.optBoolean("returnHeaders", false)
            val timeout = jsonObject.optInt("timeout", 15000).coerceAtLeast(1000)
            val headers = jsonObject.optJSONObject("headers")?.let {
                JSONObject(it.toString()) // Create a copy to avoid modifying the original
            } ?: JSONObject()

            var contentType = jsonObject.optString("contentType")
            var requestContent = ""

            if (data != null) {
                if (data is String) {
                    requestContent = data.toString()
                    contentType = try {
                        JSONObject(requestContent)
                        "application/json"
                    } catch (_: JSONException) {
                        "application/x-www-form-urlencoded"
                    }
                } else if (data is JSONObject) {
                    contentType = "application/json"
                    requestContent = data.toString()
                }
            }
            // Ensure Content-Type header is properly set
            if (requestContent.isNotEmpty()) {
                if (!headers.has("Content-Type")) {
                    if (headers.has("Content-type")) {
                        contentType = headers.optString("Content-type", contentType)
                        headers.remove("Content-type")
                    }
                    if (headers.has("content-type")) {
                        contentType = headers.optString("content-type", contentType)
                        headers.remove("content-type")
                    }
                    headers.putSafe("Content-Type", contentType)
                }
            }
            if (url.contains("jacred.", ignoreCase = true)) {
                headers.putSafe("Referer", MainActivity.LAMPA_URL)
            }
            val finalRequestContent = requestContent
            val finalHeaders = headers

            class LampaAsyncTask : AsyncTask<Void?, Int?, String>("LampaAsyncTask") {
                override fun doInBackground(vararg params: Void?): String {
                    val http = Http()
                    return try {
                        val responseJSON = if (finalRequestContent.isEmpty()) {
                            // GET
                            http.Get(url, finalHeaders, timeout)
                        } else {
                            // POST
                            http.Post(url, finalRequestContent, finalHeaders, timeout)
                        }
                        reqResponse[returnI.toString()] = if (returnHeaders) {
                            responseJSON.toString()
                        } else {
                            responseJSON.optString("body", "")
                        }
                        "complite"
                    } catch (e: Exception) {
                        val errorJson = JSONObject().apply {
                            putSafe("status", http.lastErrorCode)
                            putSafe("message", "request error: ${e.message ?: "unknown error"}")
                        }
                        reqResponse[returnI.toString()] = errorJson.toString()
                        debugLog(TAG, "error: ${e.message ?: "unknown error"}")
                        "error"
                    }
                }

                override fun onPostExecute(result: String?) {
                    mainActivity.runOnUiThread {
                        val js = ("Lampa.Android.httpCall("
                                + returnI.toString() + ", '"
                                + result
                            ?.replace("\\", "\\\\")
                            ?.replace("'", "\\'")
                            ?.replace("\n", "\\\n")
                                + "')")
                        browser.evaluateJavascript(js) { debugLog(TAG, js) }
                    }
                }
            }
            LampaAsyncTask().execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun getResp(str: String): String? {
        var string: String? = ""
        if (reqResponse.containsKey(str)) {
            string = reqResponse[str]
            reqResponse.remove(str)
        }
        return string
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openPlayer(link: String, jsonStr: String) {
        debugLog(TAG, "openPlayer: $link json:$jsonStr")
        val jsonObject = try {
            JSONObject(jsonStr.ifEmpty { "{}" }).apply {
                if (!has("url")) {
                    putSafe("url", link)
                }
            }
        } catch (_: Exception) {
            JSONObject().apply { putSafe("url", link) }
        }

        mainActivity.runOnUiThread { mainActivity.runPlayer(jsonObject) }

        // Force update Recs to filter viewed
        CoroutineScope(Dispatchers.Default).launch {
            delay(UPDATE_DELAY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LampaChannels.updateRecsChannel()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                RecsService.updateRecs()
            }
        }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun setProxyPAC(link: String): Boolean {
        return Http.setProxyPAC(link)
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun getProxyPAC(): String {
        return Http.getProxyPAC()
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun voiceStart() {
        // Голосовой ввод с последующей передачей результата через JS
        mainActivity.runOnUiThread {
            try {
                mainActivity.displaySpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
                // Очищаем поле ввода
                mainActivity.runVoidJsFunc("window.voiceResult", "''")
            }
        }
    }

    /**
     * Saves valid bookmarks after filtering out invalid ones.
     *
     * @param json The JSON string containing bookmarks.
     */
    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun saveBookmarks(json: String?) {
        debugLog(TAG, "saveBookmarks fired!")
        CoroutineScope(Dispatchers.Default).launch {
            // Filter out invalid CubBookmark objects
            val validBookmarks = filterValidCubBookmarks(json)
            if (validBookmarks.isNotEmpty()) {
                debugLog(TAG, "saveBookmarks - found ${validBookmarks.size} valid elements")
                // Save the valid bookmarks
                mainActivity.saveAccountBookmarks(Gson().toJson(validBookmarks))
            } else {
                Log.e(TAG, "saveBookmarks - no valid CUB bookmarks found in the JSON")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun updateChannel(where: String?) {
        // https://github.com/yumata/lampa-source/blob/e5505b0e9cf5f95f8ec49bddbbb04086fccf26c8/src/app.js#L203
        if (where != null && isTvContentProviderAvailable) {
            debugLog(TAG, "updateChannel [$where]")
            when (where) {
                LampaProvider.HIST,
                LampaProvider.BOOK,
                LampaProvider.LIKE,
                LampaProvider.LOOK,
                LampaProvider.VIEW,
                LampaProvider.SCHD,
                LampaProvider.CONT,
                LampaProvider.THRW -> {
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(UPDATE_DELAY)
                        updateChanByName(where)
                    }
                }

                LampaProvider.LATE -> {
                    // Handle add to Watch Next from Lampa
                    CoroutineScope(Dispatchers.Default).launch {
                        delay(UPDATE_DELAY)
                        updateWatchNext()
                    }
                }
            }
        }
    }

    // https://stackoverflow.com/a/41560207
    // https://copyprogramming.com/howto/android-webview-savestate
    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun dump() {
        check(!dumped) { "already dumped" }
        val map = store.all
        val size = map?.size ?: 0
        keys = arrayOfNulls(size)
        values = arrayOfNulls(size)
        for ((cur, key) in map!!.keys.withIndex()) {
            keys!![cur] = key
            values!![cur] = (map[key] as String?)!!
        }
        dumped = true
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun size(): Int {
        check(dumped) { "dump() first" }
        return keys!!.size
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun key(i: Int): String? {
        check(dumped) { "dump() first" }
        return keys!![i]
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun value(i: Int): String? {
        check(dumped) { "dump() first" }
        return values!![i]
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    operator fun get(key: String?): String? {
        return store.getString(key, null)
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    operator fun set(key: String?, value: String?) {
        check(!dumped) { "already dumped" }
        store.edit { putString(key, value) }
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun clear() {
        store.edit { clear() }
        keys = null
        values = null
        dumped = false
    }

    @Synchronized
    override fun toString(): String {
        return store.all.toString()
    }

    private fun JSONObject.putSafe(key: String, value: Any) = try {
        put(key, value)
    } catch (_: JSONException) { /* Ignore */
    }
}