package top.rootu.lampa

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
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
    fun appVersion(): String {
        // версия AndroidJS для сайта указывается через тире, например 1.0.1-16 - 16 версия
        return BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE // todo последняя версия от Немирова 7.7.7-77
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
        MainActivity.SELECTED_PLAYER = ""
        val editor = mainActivity?.mSettings?.edit()
        editor?.putString(MainActivity.APP_PLAYER, MainActivity.SELECTED_PLAYER)
        editor?.apply()
        App.toast(R.string.select_player_reseted)
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

            class LampaAsyncTask : AsyncTask<Void?, String?, String>() {
                override fun doInBackground(vararg voids: Void?): String {
                    var s: String
                    var action = "complite"
                    try {
                        s = if (TextUtils.isEmpty(finalContentType)) {
                            // GET
                            Http.Get(url)
                        } else {
                            // POST
                            Http.Post(url, finalRequestContent, finalContentType)
                        }
                    } catch (e: Exception) {
                        val jSONObject = JSONObject()
                        try {
                            jSONObject.put("status", Http.lastErrorCode)
                            jSONObject.put("message", "request error: " + e.message)
                        } catch (jsonException: JSONException) {
                            jsonException.printStackTrace()
                        }
                        s = jSONObject.toString()
                        action = "error"
                        e.printStackTrace()
                    }
                    reqResponse[returnI.toString()] = s
                    return action
                }

                override fun onPostExecute(result: String) {
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
        // App.toast(R.string.no_working, false)
        // check permissions
        mainActivity?.runOnUiThread {
            try {
                mainActivity?.displaySpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
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