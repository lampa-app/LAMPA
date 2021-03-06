package top.rootu.lampa

import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.xwalk.core.JavascriptInterface
import org.xwalk.core.XWalkView
import top.rootu.lampa.net.Http
import kotlin.system.exitProcess

class AndroidJS(var mainActivity: MainActivity?, var XWalkView: XWalkView) {
    @JavascriptInterface
    fun StorageChange(str: String) {
        val eo: JSONObject = if (str == "\"\"") {
            JSONObject()
        } else {
            JSONObject(str)
        }
        if (!eo.has("name") || !eo.has("value")) return
        val name = eo.optString("name")
        when (name) {
            "player_timecode" -> {
                MainActivity.playerTimeCode = eo.optString("value", MainActivity.playerTimeCode)
            }
            "file_view" -> {
                MainActivity.playerFileView = eo.optJSONObject("value")
            }
            "playlist_next" -> {
                MainActivity.playerAutoNext = eo.optString("value", "true") == "true"
            }
            "torrserver_preload" -> {
                MainActivity.torrserverPreload = eo.optString("value", "false") == "true"
            }
            "internal_torrclient" -> {
                MainActivity.internalTorrserve = eo.optString("value", "false") == "true"
            }
        }
    }

    @JavascriptInterface
    fun appVersion(): String {
        // версия AndroidJS для сайта указывается через тире, например 1.0.1-16 - 16 версия
        return BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE
    }

    @JavascriptInterface
    fun exit() {
        try {
            mainActivity?.runOnUiThread { mainActivity?.appExit() }
        } catch (unused: Exception) {
            exitProcess(1)
        }
    }

    @JavascriptInterface
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
        if (jSONObject.optJSONObject("data") != null) {
            val optJSONObject = jSONObject.optJSONObject("data")
            if (optJSONObject != null) {
                intent.putExtra("data", optJSONObject.toString())
            }
        }
        mainActivity?.runOnUiThread {
            try {
                mainActivity?.startActivity(intent)
            } catch (e: Exception) {
                Log.d(TAG, e.message, e)
                App.toast(R.string.no_activity_found, false)
            }
        }
        return true
    }

    @JavascriptInterface
    fun openYoutube(str: String) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$str")
        )
        mainActivity?.runOnUiThread {
            try {
                mainActivity?.startActivity(intent)
            } catch (e: Exception) {
                Log.d(TAG, e.message, e)
                App.toast(R.string.no_activity_found, false)
            }
        }
    }

    @JavascriptInterface
    fun clearDefaultPlayer() {
        mainActivity?.runOnUiThread {
            mainActivity?.setPlayerPackage("")
            App.toast(R.string.select_player_reset)
        }
    }

    @JavascriptInterface
    fun httpReq(str: String?, returnI: Int) {
        Log.d("JS", str!!)
        val jSONObject: JSONObject?
        try {
            jSONObject = JSONObject(str)
            val url = jSONObject.optString("url")
            val data = jSONObject.opt("post_data")
            var contentType = jSONObject.optString("contentType")
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
            val finalRequestContent = requestContent
            val finalContentType = contentType

            class LampaAsyncTask : CSyncTask<Void?, String?, String>("LampaAsyncTask") {
                override fun doInBackground(vararg params: Void?): String {
                    var s: String
                    var action = "complite"
                    val json: JSONObject?
                    try {
                        s = if (TextUtils.isEmpty(finalContentType)) {
                            // GET
                            Http.Get(url)
                        } else {
                            // POST
                            Http.Post(url, finalRequestContent, finalContentType)
                        }
                    } catch (e: Exception) {
                        json = JSONObject()
                        try {
                            json.put("status", Http.lastErrorCode)
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
                    mainActivity?.runOnUiThread {
                        val js = ("Lampa.Android.httpCall("
                                + returnI.toString() + ", '" + result + "')")
                        XWalkView.evaluateJavascript(js) { value -> Log.i("JSRV", value!!) }
                    }
                }
            }

            LampaAsyncTask().execute()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun getResp(str: String): String? {
        var string: String? = ""
        if (reqResponse.containsKey(str)) {
            string = reqResponse[str]
            reqResponse.remove(str)
        }
        return string
    }

    @JavascriptInterface
    fun openPlayer(link: String, jsonStr: String) {
        Log.d(TAG, "openPlayer: $link json:$jsonStr")
        val jsonObject: JSONObject = try {
            JSONObject(jsonStr.ifEmpty { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
        if (!jsonObject.has("url")) {
            try {
                jsonObject.put("url", link)
            } catch (ignored: JSONException) {
            }
        }
        mainActivity?.runOnUiThread { mainActivity?.runPlayer(jsonObject) }
    }

    @JavascriptInterface
    fun voiceStart() {
        // todo Голосовой ввод с последующей передачей результата через JS
        mainActivity?.runOnUiThread {
            try {
                mainActivity?.displaySpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
                // Очищаем поле ввода
                mainActivity?.runVoidJsFunc("window.voiceResult", "''")
            }
        }
    }

    @JavascriptInterface
    fun showInput(inputText: String?) {
        // todo Ввод с андройд клавиатуры с последующей передачей результата через JS
    }

    @JavascriptInterface
    fun updateChannel(where: String?) {
        // todo https://github.com/yumata/lampa-source/blob/e5505b0e9cf5f95f8ec49bddbbb04086fccf26c8/src/app.js#L203
    }

    companion object {
        private const val TAG = "AndroidJS"
        var reqResponse: MutableMap<String, String> = HashMap()
    }
}