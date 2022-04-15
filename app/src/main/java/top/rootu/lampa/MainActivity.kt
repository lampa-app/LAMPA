package top.rootu.lampa

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONObject
import org.xwalk.core.*
import org.xwalk.core.XWalkInitializer.XWalkInitListener
import org.xwalk.core.XWalkUpdater.XWalkUpdateListener
import java.util.*
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(), XWalkInitListener, XWalkUpdateListener {
    private var browser: XWalkView? = null
    private var mXWalkInitializer: XWalkInitializer? = null
    private var mXWalkUpdater: XWalkUpdater? = null
    private var mDecorView: View? = null
    private var browserInit = false
    private var mSettings: SharedPreferences? = null
    private lateinit var resultLauncher: ActivityResultLauncher<Intent?>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent?>

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        mDecorView = window.decorView
        hideSystemUI()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        mSettings = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE)
        LAMPA_URL = mSettings?.getString(APP_URL, LAMPA_URL)
        SELECTED_PLAYER = mSettings?.getString(APP_PLAYER, SELECTED_PLAYER)

        resultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
            when (result.resultCode) {
                RESULT_OK -> Log.i(TAG, "OK: ${data?.toUri(0)}")
                RESULT_CANCELED -> Log.i(TAG, "Canceled: ${data?.toUri(0)}")
                RESULT_ERROR -> Log.e(TAG, "Error occurred: ${data?.toUri(0)}")
                else -> Log.w(
                    TAG,
                    "Undefined result code (${result.resultCode}): ${data?.toUri(0)}"
                )
            }
        }

        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                val spokenText: String? =
                    data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                        results[0]
                    }
                // Do something with spokenText.
                if (spokenText != null) {
                    runVoidJsFunc("voiceResult", "'" + spokenText.replace("'", "\\'") + "'")
                }
            }
        }

        // Must call initAsync() before anything that involves the embedding
        // API, including invoking setContentView() with the layout which
        // holds the XWalkView object.
        mXWalkInitializer = XWalkInitializer(this, this)
        mXWalkInitializer?.initAsync()

        // Until onXWalkInitCompleted() is invoked, you should do nothing with the
        // embedding API except the following:
        // 1. Instantiate the XWalkView object
        // 2. Call XWalkPreferences.setValue()
        // 3. Call mXWalkView.setXXClient(), e.g., setUIClient
        // 4. Call mXWalkView.setXXListener(), e.g., setDownloadListener
        // 5. Call mXWalkView.addJavascriptInterface()
        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true)
        XWalkPreferences.setValue(XWalkPreferences.ENABLE_JAVASCRIPT, true)
        // maybe this fixes crashes on mitv2?
        // XWalkPreferences.setValue(XWalkPreferences.ANIMATABLE_XWALK_VIEW, true);
        setContentView(R.layout.activity_main)
    }

    override fun onXWalkInitStarted() {}
    override fun onXWalkInitCancelled() {
        // Perform error handling here
        finish()
    }

    override fun onXWalkInitFailed() {
        if (mXWalkUpdater == null) {
            mXWalkUpdater = XWalkUpdater(this, this)
        }
        mXWalkUpdater?.updateXWalkRuntime()
    }

    override fun onXWalkInitCompleted() {
        // Do anyting with the embedding API
        browserInit = true
        if (browser == null) {
            browser = findViewById(R.id.xwalkview)
            browser?.setLayerType(View.LAYER_TYPE_NONE, null)
            val progressBar = findViewById<CircularProgressIndicator>(R.id.progressBar_cyclic)
            browser?.setResourceClient(object : XWalkResourceClient(browser) {
                override fun onLoadFinished(view: XWalkView, url: String) {
                    super.onLoadFinished(view, url)
                    if (view.visibility != View.VISIBLE) {
                        view.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        println("LAMPA onLoadFinished $url")
                    }
                }
            })
        }
        //val ua = browser?.userAgentString + " LAMPA_ClientForLegacyOS"
        browser?.userAgentString = "lampa_client"
        browser?.setBackgroundColor(ContextCompat.getColor(baseContext, R.color.lampa_background))
        browser?.addJavascriptInterface(AndroidJS(this, browser!!), "AndroidJS")
        if (LAMPA_URL.isNullOrEmpty()) {
            showUrlInputDialog()
        } else {
            browser?.loadUrl(LAMPA_URL)
        }
    }

    private fun showUrlInputDialog() {
        val mainActivity = this
        val builder = AlertDialog.Builder(mainActivity)
        builder.setTitle(R.string.input_url_title)

        // Set up the input
        val input = EditText(this)
        input.setSingleLine()
        // Specify the type of input expected; this, for example, sets the input as a password,
        // and will mask the text
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(if (LAMPA_URL.isNullOrEmpty()) "http://" else LAMPA_URL)
        val margin = resources.getDimensionPixelSize(R.dimen.dialog_margin)
        val container = FrameLayout(this)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        container.addView(input)
        builder.setView(container)
//        builder.setView(input, margin, 0, margin, 0)

        // Set up the buttons
        builder.setPositiveButton(R.string.save) { _: DialogInterface?, _: Int ->
            LAMPA_URL = input.text.toString()
            if (URL_PATTERN.matcher(LAMPA_URL!!).matches()) {
                println("URL '$LAMPA_URL' is valid")
                if (mSettings?.getString(APP_URL, "") != LAMPA_URL) {
                    val editor = mSettings?.edit()
                    editor?.putString(APP_URL, LAMPA_URL)
                    editor?.apply()
                    browser?.loadUrl(LAMPA_URL)
                    App.toast(R.string.change_url_press_back)
                }
            } else {
                println("URL '$LAMPA_URL' is invalid")
                App.toast(R.string.invalid_url)
                showUrlInputDialog()
            }
            hideSystemUI()
        }
        builder.setNegativeButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
            if (LAMPA_URL.isNullOrEmpty() && mSettings?.getString(APP_URL, LAMPA_URL)
                    .isNullOrEmpty()
            ) {
                appExit()
            } else {
                LAMPA_URL = mSettings?.getString(APP_URL, LAMPA_URL)
                hideSystemUI()
            }
        }
        builder.setNeutralButton(R.string.exit) { dialog: DialogInterface, _: Int ->
            dialog.cancel()
            appExit()
        }
        builder.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
            || keyCode == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU
        ) {
            println("Menu key pressed")
            showUrlInputDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            println("Back button long pressed")
            showUrlInputDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onXWalkUpdateCancelled() {
        // Perform error handling here
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (browserInit && browser != null) {
            browser?.pauseTimers()
//            browser?.onHide()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (browserInit && browser != null) {
            browser?.onDestroy()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()

        // Try to initialize again when the user completed updating and
        // returned to current activity. The initAsync() will do nothing if
        // the initialization is proceeding or has already been completed.
        mXWalkInitializer?.initAsync()
        if (browserInit && browser != null) {
            browser?.resumeTimers()
//            browser?.onShow()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        mDecorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE)
    }

    fun appExit() {
        browser?.let {
            it.clearCache(true)
            it.onDestroy()
        }
        finishAffinity()
//        exitProcess(1)
    }

    fun setPlayerPackage(packageName: String) {
        SELECTED_PLAYER = packageName.lowercase(Locale.getDefault())
        val editor = mSettings?.edit()
        editor?.putString(APP_PLAYER, SELECTED_PLAYER)
        editor?.apply()
    }

    fun runPlayer(jsonObject: JSONObject) {
        val videoUrl = jsonObject.optString("url")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndTypeAndNormalize(
            Uri.parse(videoUrl),
            if (videoUrl.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/*"
        )
        val resInfo =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resInfo.isEmpty()) {
            App.toast(R.string.no_activity_found, false)
            return
        }
        var playerPackageExist = false
        if (!SELECTED_PLAYER.isNullOrEmpty()) {
            for (info in resInfo) {
                if (info.activityInfo.packageName.lowercase(Locale.getDefault()) == SELECTED_PLAYER) {
                    playerPackageExist = true
                    break
                }
            }
        }
        if (!playerPackageExist || SELECTED_PLAYER.isNullOrEmpty()) {
            val excludedAppsPackageNames = hashSetOf(
                "com.android.gallery3d",
                "com.lonelycatgames.xplore",
                "com.android.tv.frameworkpackagestubs",
                "com.google.android.tv.frameworkpackagestubs",
                "com.instantbits.cast.webvideo",
                "com.ghisler.android.totalcommander",
                "com.google.android.apps.photos",
                "com.mixplorer.silver",
                "com.estrongs.android.pop",
                "pl.solidexplorer2"
            )
            val filteredList: MutableList<ResolveInfo> = mutableListOf()
            for (info in resInfo) {
                if (excludedAppsPackageNames.contains(info.activityInfo.packageName.lowercase(Locale.getDefault()))) {
                    continue
                }
                filteredList.add(info)
            }
            val mainActivity = this
            val listAdapter = AppListAdapter(mainActivity, filteredList)
            val playerChooser = AlertDialog.Builder(mainActivity)
            val appTitleView =
                LayoutInflater.from(mainActivity).inflate(R.layout.app_list_title, null)
            val switch = appTitleView.findViewById<SwitchCompat>(R.id.useDefault)
            playerChooser.setCustomTitle(appTitleView)

            playerChooser.setAdapter(listAdapter) { dialog, which ->
                val setDefaultPlayer = switch.isChecked
                SELECTED_PLAYER = listAdapter.getItemPackage(which)
                if (setDefaultPlayer) setPlayerPackage(SELECTED_PLAYER.toString())
                dialog.dismiss()
                runPlayer(jsonObject)
                if (!setDefaultPlayer) SELECTED_PLAYER = ""
            }
            val playerChooserDialog = playerChooser.create()
            playerChooserDialog.show()
            playerChooserDialog.listView.requestFocus()
        } else {
            var videoPosition: Long = 0
            val videoTitle =
                if (jsonObject.has("title")) jsonObject.optString("title") else "LAMPA video"
            val listTitles = ArrayList<String>()
            val listUrls = ArrayList<String>()
            var startIndex = 0

            if (jsonObject.has("timeline")) {
                val timeline = jsonObject.optJSONObject("timeline")
                if (timeline?.has("time") == true)
                    videoPosition = (jsonObject.optDouble("time") * 1000).toLong()
            }

            if (jsonObject.has("playlist")) {
                val playJSONArray = jsonObject.getJSONArray("playlist")
                for (i in 0 until playJSONArray.length()) {
                    val io = playJSONArray.getJSONObject(i)
                    if (io.has("url")) {
                        if (io.optString("url") == videoUrl)
                            startIndex = i
                        listUrls.add(io.optString("url"))
                        listTitles.add(
                            if (io.has("title")) io.optString("title") else (i + 1).toString()
                        )
                    }
                }
            }
            when (SELECTED_PLAYER) {
                "com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.beta" -> {
                    intent.setClassName(SELECTED_PLAYER!!, "$SELECTED_PLAYER.ActivityScreen")
                    intent.putExtra("title", videoTitle)
                    intent.putExtra("sticky", false)
                    if (videoPosition > 0) intent.putExtra("position", videoPosition.toInt())
                    if (listUrls.size > 1) {
                        val parcelableArr = arrayOfNulls<Parcelable>(listUrls.size)
                        for (i in 0 until listUrls.size) {
                            parcelableArr[i] = Uri.parse(listUrls[i])
                        }
                        val ta = listTitles.toTypedArray()
                        intent.putExtra("video_list", parcelableArr)
                        intent.putExtra("video_list.name", ta)
                        intent.putExtra(
                            "video_list.filename",
                            ta
                        ) // todo тут имя файла видео для поиска субтитров в интернете (не обязательно)
                        intent.putExtra("video_list_is_explicit", true)
                    }
                    intent.putExtra("return_result", true)
                }
                "org.videolan.vlc" -> {
                    intent.putExtra("title", videoTitle)
                    intent.setClassName(
                        SELECTED_PLAYER!!,
                        "$SELECTED_PLAYER.gui.video.VideoPlayerActivity"
                    )
                    if (videoPosition > 0) intent.putExtra("position", videoPosition)
                }
                "com.brouken.player" -> {
                    intent.putExtra("title", videoTitle)
                    intent.putExtra("name", videoTitle)
                    intent.setPackage(SELECTED_PLAYER)
                    if (videoPosition > 0) intent.putExtra("position", videoPosition)
                }
                "net.gtvbox.videoplayer" -> {
                    // see https://vimu.tv/player-api
                    if (listUrls.size <= 1) {
                        intent.setClassName(
                            SELECTED_PLAYER!!,
                            "$SELECTED_PLAYER.PlayerActivity"
                        )
                        intent.putExtra("forcename", videoTitle)
                    } else {
                        intent.setDataAndType(
                            Uri.parse(videoUrl),
                            "application/vnd.gtvbox.filelist"
                        )
                        intent.setPackage(SELECTED_PLAYER)
                        intent.putStringArrayListExtra(
                            "asusfilelist",
                            ArrayList(listUrls.subList(startIndex, listUrls.size))
                        )
                        intent.putStringArrayListExtra(
                            "asusnamelist",
                            ArrayList(listTitles.subList(startIndex, listUrls.size))
                        )
                    }
                    if (videoPosition > 0) {
                        intent.putExtra("position", videoPosition.toInt())
                        intent.putExtra("startfrom", videoPosition.toInt())
                    }
                    intent.putExtra("forcedirect", true)
                    intent.putExtra("forceresume", true)
                }
                else -> {
                    intent.setPackage(SELECTED_PLAYER)
                }
            }
            try {
                resultLauncher.launch(intent)
            } catch (e: Exception) {
                App.toast(R.string.no_launch_player, false)
            }
        }
    }

    // Create an intent that can start the Speech Recognizer activity
    fun displaySpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
        // This starts the activity and populates the intent with the speech text.
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            App.toast(R.string.not_found_speech, false)
        }
    }

    fun runVoidJsFunc(funcName: String, params: String) {
        val js = ("(function(w,u){"
                + "if(typeof w." + funcName.replace(".", "?.") + "===u) return u;"
                + "w." + funcName + "(" + params + ");"
                + "return 'runned';"
                + "})(window,'undefined');")
        browser?.evaluateJavascript(
            js
        ) { r: String ->
            Log.i(
                "runVoidJsFunc",
                "$funcName($params) $r"
            )
        }
    }

//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        Log.d(TAG, "onActivityResult req: $requestCode code: $resultCode data: ${data?.toUri(0)}")
//        if (data?.action.equals("com.mxtech.intent.result.VIEW")) {
//            val pos = data?.getIntExtra(
//                "position",
//                -1
//            ) // Last playback position in milliseconds. This extra will not exist if playback is completed.
//            val dur = data?.getIntExtra(
//                "duration",
//                -1
//            ) // Duration of last played video in milliseconds. This extra will not exist if playback is completed.
//            val cause = data?.getStringExtra("end_by") //  Indicates reason of activity closure.
//        }
//        if (requestCode == REQUEST_PLAYER_MX || requestCode == REQUEST_PLAYER_VLC) {
//            when (resultCode) {
//                RESULT_OK -> Log.i(TAG, "Ok: $data")
//                RESULT_CANCELED -> Log.i(TAG, "Canceled: $data")
//                RESULT_ERROR -> Log.e(TAG, "Error occurred: $data")
//                else -> Log.w(TAG, "Undefined result code ($resultCode): $data")
//            }
//            if (data != null)
//                dumpParams(data)
//            App.toast("MX or VLC")
//        } else {
//            super.onActivityResult(requestCode, resultCode, data)
//        }
//    }

    companion object {
        private const val TAG = "APP_MAIN"
        const val RESULT_ERROR = 1
        const val APP_PREFERENCES = "settings"
        const val APP_URL = "url"
        const val APP_PLAYER = "player"
        var LAMPA_URL: String? = ""
        var SELECTED_PLAYER: String? = ""
        private const val URL_REGEX = "^https?://([-A-Za-z0-9]+\\.)+[-A-Za-z]{2,}(:[0-9]+)?(/.*)?$"
        private val URL_PATTERN = Pattern.compile(URL_REGEX)

//        const val REQUEST_PLAYER_OTHER = 1
//        const val REQUEST_PLAYER_MX = 2
//        const val REQUEST_PLAYER_VLC = 3
//        const val REQUEST_PLAYER_VIMU = 4

//        private fun dumpParams(intent: Intent) {
//            val sb = StringBuilder()
//            val extras = intent.extras
//            sb.setLength(0)
//            sb.append("* dat=").append(intent.data)
//            Log.v(TAG, sb.toString())
//            sb.setLength(0)
//            sb.append("* typ=").append(intent.type)
//            Log.v(TAG, sb.toString())
//            if (extras != null && extras.size() > 0) {
//                sb.setLength(0)
//                sb.append("    << Extra >>\n")
//                for ((i, key) in extras.keySet().withIndex()) {
//                    sb.append(' ').append(i + 1).append(") ").append(key).append('=')
//                    appendDetails(sb, extras[key])
//                    sb.append('\n')
//                }
//                Log.v(TAG, sb.toString())
//            }
//        }
//
//        private fun appendDetails(sb: StringBuilder, `object`: Any?) {
//            if (`object` != null && `object`.javaClass.isArray) {
//                sb.append('[')
//                val length = Array.getLength(`object`)
//                for (i in 0 until length) {
//                    if (i > 0) sb.append(", ")
//                    appendDetails(sb, Array.get(`object`, i))
//                }
//                sb.append(']')
//            } else if (`object` is Collection<*>) {
//                sb.append('[')
//                var first = true
//                for (element in `object`) {
//                    if (first) first = false else sb.append(", ")
//                    appendDetails(sb, element)
//                }
//                sb.append(']')
//            } else sb.append(`object`)
//        }
    }
}