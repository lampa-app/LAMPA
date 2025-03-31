package top.rootu.lampa

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.annotation.RequiresApi
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
import top.rootu.lampa.helpers.Helpers.filterValidCubBookmarks
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.Helpers.printLog
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
import kotlin.system.exitProcess

class AndroidJS(private val mainActivity: MainActivity, private val browser: Browser) {

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun storageChange(str: String) {
        val eo: JSONObject = if (str == "\"\"") {
            JSONObject()
        } else {
            JSONObject(str)
        }
        if (!eo.has("name") || !eo.has("value")) return

        when (eo.optString("name")) {
            "activity" -> {
                MainActivity.lampaActivity = eo.optString("value", "{}")
                printLog(TAG, "lampaActivity stored: ${MainActivity.lampaActivity}")
            }

            "player_timecode" -> {
                MainActivity.playerTimeCode = eo.optString("value", MainActivity.playerTimeCode)
                printLog(TAG, "playerTimeCode stored: ${MainActivity.playerTimeCode}")
            }

            "playlist_next" -> {
                MainActivity.playerAutoNext = eo.optString("value", "true") == "true"
                printLog(TAG, "playerAutoNext stored: ${MainActivity.playerAutoNext}")
            }

            "torrserver_preload" -> {
                MainActivity.torrserverPreload = eo.optString("value", "false") == "true"
                printLog(TAG, "torrserverPreload stored: ${MainActivity.torrserverPreload}")
            }

            "internal_torrclient" -> {
                MainActivity.internalTorrserve = eo.optString("value", "false") == "true"
                printLog(TAG, "internalTorrserve stored: ${MainActivity.internalTorrserve}")
            }

            "language" -> {
                val newLang = eo.optString("value", "ru")
                if (mainActivity.appLang != newLang) {
                    App.setAppLanguage(mainActivity, newLang)
                    // mainActivity.appLang = newLang
                    // mainActivity.runOnUiThread { mainActivity.recreate() }
                    printLog(TAG, "language changed to $newLang")
                } else {
                    printLog(TAG, "language not changed")
                }
            }

            "source" -> {
                mainActivity.lampaSource = eo.optString("value", mainActivity.lampaSource)
                printLog(TAG, "lampaSource stored: ${mainActivity.lampaSource}")
            }

            "proxy_tmdb", "protocol" -> {
                mainActivity.changeTmdbUrls()
            }

            "baseUrlApiTMDB" -> {
                mainActivity.tmdbApiUrl = eo.optString("value", mainActivity.tmdbApiUrl)
                printLog(TAG, "baseUrlApiTMDB set to ${mainActivity.tmdbApiUrl}")
            }

            "baseUrlImageTMDB" -> {
                mainActivity.tmdbImgUrl = eo.optString("value", mainActivity.tmdbImgUrl)
                printLog(TAG, "baseUrlImageTMDB set to ${mainActivity.tmdbImgUrl}")
            }

            "favorite" -> {
                val json = eo.optString("value", "")
                if (isValidJson(json)) {
                    mainActivity.saveFavorite(json)
                    printLog(TAG, "favorite JSON saved to prefs")
                } else {
                    Log.e(TAG, "Not valid JSON in favorite")
                }
            }

            "account_use" -> {
                val use = eo.optBoolean("value", false)
                printLog(TAG, "set syncEnabled $use")
                mainActivity.syncEnabled = use
            }

            "recomends_list" -> {
                val json = eo.optString("value", "")
                if (isValidJson(json)) {
                    mainActivity.saveRecs(json)
                    printLog(TAG, "recomends_list JSON saved to prefs")
                } else {
                    Log.e(TAG, "Not valid JSON in recomends_list")
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
    fun openTorrentLink(str: String, str2: String): Boolean {
        val jSONObject: JSONObject = if (str2 == "\"\"") {
            JSONObject()
        } else {
            JSONObject(str2)
        }
        val intent = Intent("android.intent.action.VIEW")
        val parse = Uri.parse(str)
        if (str.startsWith("magnet")) {
            intent.data = parse
        } else {
            intent.setDataAndType(parse, "application/x-bittorrent")
        }
        val title = jSONObject.optString("title")
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra("title", title)
            intent.putExtra("displayName", title)
            intent.putExtra("forcename", title)
        }
        val poster = jSONObject.optString("poster")
        if (!TextUtils.isEmpty(poster)) {
            intent.putExtra("poster", poster)
        }
        val category = jSONObject.optString("media")
        if (!TextUtils.isEmpty(category)) {
            intent.putExtra("category", category)
        }
        if (jSONObject.optJSONObject("data") != null) {
            val optJSONObject = jSONObject.optJSONObject("data")
            if (optJSONObject != null) {
                intent.putExtra("data", optJSONObject.toString())
            }
        }
        mainActivity.runOnUiThread {
            try {
                mainActivity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, e.message, e)
                App.toast(R.string.no_torrent_activity_found, true)
            }
        }
        // update Recs to filter viewed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                LampaChannels.updateRecsChannel()
            }
        }
        return true
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    fun openYoutube(str: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$str")
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
        printLog(TAG, "httpReq JSON $str")
        val jSONObject: JSONObject?
        try {
            jSONObject = JSONObject(str)
            Http.disableH2(jSONObject.optBoolean("disableH2", false))
            val url = jSONObject.optString("url")
            val data = jSONObject.opt("post_data")
            var headers = jSONObject.optJSONObject("headers")
            val returnHeaders = jSONObject.optBoolean("returnHeaders", false)
            var contentType = jSONObject.optString("contentType")
            val timeout = jSONObject.optInt("timeout", 15000)
            var requestContent = ""
            if (data != null) {
                if (data is String) {
                    requestContent = data.toString()
                    contentType = try {
                        JSONObject(requestContent)
                        "application/json"
                    } catch (e: JSONException) {
                        "application/x-www-form-urlencoded"
                    }
                } else if (data is JSONObject) {
                    contentType = "application/json"
                    requestContent = data.toString()
                }
            }
            if (requestContent.isNotEmpty()) {
                if (headers == null) {
                    headers = JSONObject()
                    headers.put("Content-Type", contentType)
                } else if (!headers.has("Content-Type")) {
                    if (headers.has("Content-type")) {
                        contentType = headers.optString("Content-type", contentType)
                        headers.remove("Content-type")
                    }
                    if (headers.has("content-type")) {
                        contentType = headers.optString("content-type", contentType)
                        headers.remove("content-type")
                    }
                    headers.put("Content-Type", contentType)
                }
            }
            if (url.contains("jacred.", ignoreCase = true)) {
                if (headers == null) {
                    headers = JSONObject()
                }
                headers.put("Referer", MainActivity.LAMPA_URL)
            }
            val finalRequestContent = requestContent
            val finalHeaders = headers

            class LampaAsyncTask : AsyncTask<Void?, String?, String>("LampaAsyncTask") {
                override fun doInBackground(vararg params: Void?): String {
                    var s: String
                    var action = "complite"
                    val json: JSONObject?
                    val http = Http()
                    try {
                        val responseJSON = if (TextUtils.isEmpty(finalRequestContent)) {
                            // GET
                            http.Get(url, finalHeaders, timeout)
                        } else {
                            // POST
                            http.Post(url, finalRequestContent, finalHeaders, timeout)
                        }
                        s = if (returnHeaders) {
                            responseJSON.toString()
                        } else {
                            responseJSON.optString("body", "")
                        }
                    } catch (e: Exception) {
                        json = JSONObject()
                        try {
                            json.put("status", http.lastErrorCode)
                            json.put("message", "request error: " + e.message)
                        } catch (jsonException: JSONException) {
                            jsonException.printStackTrace()
                        }
                        s = json.toString()
                        action = "error"
                        e.printStackTrace()
                    }
                    reqResponse[returnI.toString()] = s
                    return action
                }

                override fun onPostExecute(result: String?) {
                    mainActivity.runOnUiThread {
                        val js = ("Lampa.Android.httpCall("
                                + returnI.toString() + ", '"
                                + result.toString()
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\\n")
                                + "')")
                        browser.evaluateJavascript(js) { value -> Log.i("JSRV", value) }
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
        printLog(TAG, "openPlayer: $link json:$jsonStr")
        val jsonObject: JSONObject = try {
            JSONObject(jsonStr.ifEmpty { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
        if (!jsonObject.has("url")) {
            try {
                jsonObject.put("url", link)
            } catch (_: JSONException) {
            }
        }
        mainActivity.runOnUiThread { mainActivity.runPlayer(jsonObject) }
        // update Recs to filter viewed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000)
                LampaChannels.updateRecsChannel()
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
        printLog(TAG, "saveBookmarks fired!")
        CoroutineScope(Dispatchers.IO).launch {
            // Filter out invalid CubBookmark objects
            val validBookmarks = filterValidCubBookmarks(json)
            if (validBookmarks.isNotEmpty()) {
                printLog(TAG, "saveBookmarks - found ${validBookmarks.size} valid elements")
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
        if (where != null && isAndroidTV) {
            printLog(TAG, "updateChannel [$where]")
            when (where) {
                LampaProvider.HIST,
                LampaProvider.BOOK,
                LampaProvider.LIKE,
                LampaProvider.LOOK,
                LampaProvider.VIEW,
                LampaProvider.SCHD,
                LampaProvider.CONT,
                LampaProvider.THRW -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        updateChanByName(where)
                    }
                }
                LampaProvider.LATE -> {
                    // Handle add to Watch Next from Lampa
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(5000)
                        updateWatchNext()
                    }
                }
            }
        }
    }

    // https://stackoverflow.com/a/41560207
    // https://copyprogramming.com/howto/android-webview-savestate
    private val store: SharedPreferences = App.context.storagePrefs
    private var keys: Array<String?>? = null
    private var values: Array<String?>? = null
    private var dumped = false

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
        store.edit().putString(key, value).apply()
    }

    @JavascriptInterface
    @org.xwalk.core.JavascriptInterface
    @Synchronized
    fun clear() {
        store.edit().clear().apply()
        keys = null
        values = null
        dumped = false
    }

    @Synchronized
    override fun toString(): String {
        return store.all.toString()
    }

    companion object {
        private const val TAG = "AndroidJS"
        var reqResponse: MutableMap<String, String> = HashMap()
    }
}