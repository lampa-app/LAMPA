package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Parcelable
import android.speech.RecognizerIntent
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gotev.speech.GoogleVoiceTypingDisabledException
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.SpeechUtil
import net.gotev.speech.ui.SpeechProgressView
import org.json.JSONArray
import org.json.JSONObject
import org.xwalk.core.MyXWalkEnvironment
import org.xwalk.core.MyXWalkUpdater
import org.xwalk.core.XWalkInitializer
import org.xwalk.core.XWalkPreferences
import top.rootu.lampa.browser.Browser
import top.rootu.lampa.browser.SysView
import top.rootu.lampa.browser.XWalk
import top.rootu.lampa.channels.ChannelManager.getChannelDisplayName
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Backup
import top.rootu.lampa.helpers.Backup.loadFromBackup
import top.rootu.lampa.helpers.Backup.saveSettings
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.hideSystemUI
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Helpers.isTvBox
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.PermHelpers
import top.rootu.lampa.helpers.PermHelpers.hasMicPermissions
import top.rootu.lampa.helpers.PermHelpers.verifyMicPermissions
import top.rootu.lampa.helpers.Prefs
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.appBrowser
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.appPlayer
import top.rootu.lampa.helpers.Prefs.appPrefs
import top.rootu.lampa.helpers.Prefs.appUrl
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.helpers.Prefs.clearPending
import top.rootu.lampa.helpers.Prefs.firstRun
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.lampaSource
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.playActivityJS
import top.rootu.lampa.helpers.Prefs.resumeJS
import top.rootu.lampa.helpers.Prefs.tvPlayer
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.helpers.getAppVersion
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.net.HttpHelper
import top.rootu.lampa.sched.Scheduler
import java.util.Locale
import java.util.regex.Pattern


class MainActivity : AppCompatActivity(),
    Browser.Listener,
    XWalkInitializer.XWalkInitListener, MyXWalkUpdater.XWalkUpdateListener {
    private var mXWalkUpdater: MyXWalkUpdater? = null
    private var mXWalkInitializer: XWalkInitializer? = null
    private var browser: Browser? = null
    private var progressBar: LottieAnimationView? = null // CircularProgressIndicator
    private var browserInit = false
    private lateinit var resultLauncher: ActivityResultLauncher<Intent?>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent?>
    private var isMenuVisible = false

    companion object {
        private const val TAG = "APP_MAIN"
        const val RESULT_VIMU = 2
        const val RESULT_VIMU_ERROR = 4
        var delayedVoidJsFunc = mutableListOf<List<String>>()
        var LAMPA_URL: String = ""
        var SELECTED_PLAYER: String? = ""
        var SELECTED_BROWSER: String? =
            if (VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) "XWalk" else ""
        var playerTimeCode: String = "continue"
        var playerFileView: JSONObject? = null
        var playerAutoNext: Boolean = true
        var internalTorrserve: Boolean = false
        var torrserverPreload: Boolean = false
        var playJSONArray: JSONArray = JSONArray()
        var playIndex = 0
        var playVideoUrl: String = ""
        var lampaActivity: String = "{}" // JSON

        private const val IP4_DIG = "([01]?\\d?\\d|2[0-4]\\d|25[0-5])"
        private const val IP4_REGEX = "(${IP4_DIG}\\.){3}${IP4_DIG}"
        private const val IP6_DIG = "[0-9A-Fa-f]{1,4}"
        private const val IP6_REGEX =
            "((${IP6_DIG}:){7}${IP6_DIG}|(${IP6_DIG}:){1,7}:|:(:${IP6_DIG}){1,7}|(${IP6_DIG}::?){1,6}${IP6_DIG})"
        private const val URL_REGEX =
            "^https?://(\\[${IP6_REGEX}]|${IP4_REGEX}|([-A-Za-z\\d]+\\.)+[-A-Za-z]{2,})(:\\d+)?(/.*)?$"
        private val URL_PATTERN = Pattern.compile(URL_REGEX)
        private const val VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE = 0.96
    }

    private fun isAfterEndCreditsPosition(positionMillis: Long, duration: Long): Boolean {
        val durationMillis = duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
        return positionMillis >= durationMillis
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        if (VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        else // API > 33
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        onBackPressedDispatcher.addCallback {
            if (browser?.canGoBack() == true) {
                browser?.goBack()
            }
        }
        LAMPA_URL = this.appUrl
        SELECTED_PLAYER = this.appPlayer
        Helpers.setLocale(this, this.appLang)

        playIndex = this.lastPlayedPrefs.getInt("playIndex", playIndex)
        playVideoUrl = this.lastPlayedPrefs.getString("playVideoUrl", playVideoUrl)!!
        playJSONArray = try {
            JSONArray(this.lastPlayedPrefs.getString("playJSONArray", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        // Some external video player api specs:
        // vlc https://wiki.videolan.org/Android_Player_Intents/
        // justplayer https://github.com/moneytoo/Player/issues/203
        // mxplayer https://mx.j2inter.com/api
        // mpv http://mpv-android.github.io/mpv-android/intent.html
        // vimu https://www.vimu.tv/player-api
        resultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val data: Intent? = result.data
//            if (BuildConfig.DEBUG) {
//                val bundle = data?.extras
//                bundle?.let { b ->
//                    for (key in b.keySet()) {
//                        Log.d(
//                            TAG,
//                            ("onActivityResult: data $key : ${b.get(key) ?: "NULL"}")
//                        )
//                    }
//                }
//            }
            val videoUrl: String = data?.data.toString()
            val resultCode = result.resultCode
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Returned video url: $videoUrl")
                when (resultCode) { // just for debug
                    RESULT_OK -> Log.i(TAG, "RESULT_OK: ${data?.toUri(0)}") // -1
                    RESULT_CANCELED -> Log.i(TAG, "RESULT_CANCELED: ${data?.toUri(0)}") // 0
                    RESULT_FIRST_USER -> Log.i(TAG, "RESULT_FIRST_USER: ${data?.toUri(0)}") // 1
                    RESULT_VIMU -> Log.e(TAG, "RESULT_VIMU: ${data?.toUri(0)}") // 2
                    RESULT_VIMU_ERROR -> Log.e(TAG, "RESULT_VIMU_ERROR: ${data?.toUri(0)}") // 4
                    else -> Log.w(
                        TAG,
                        "Undefined result code ($resultCode): ${data?.toUri(0)}"
                    )
                }
            }
            data?.let {
                if (it.action.equals("com.mxtech.intent.result.VIEW")) { // MX / Just
                    when (resultCode) {
                        RESULT_OK -> {
                            when (val endBy = it.getStringExtra("end_by")) {
                                "playback_completion" -> {
                                    Log.i(TAG, "Playback completed")
                                    resultPlayer(videoUrl, 0, 0, true)
                                }

                                "user" -> {
                                    val pos = it.getIntExtra("position", 0)
                                    val dur = it.getIntExtra("duration", 0)
                                    if (pos > 0 && dur > 0) {
                                        val ended =
                                            isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                                        Log.i(
                                            TAG,
                                            "Playback stopped [position=$pos, duration=$dur, ended:$ended]"
                                        )
                                        resultPlayer(videoUrl, pos, dur, ended)
                                    } else {
                                        Log.e(TAG, "Invalid state [position=$pos, duration=$dur]")
                                    }
                                }

                                else -> {
                                    Log.e(TAG, "Invalid state [endBy=$endBy]")
                                }
                            }
                        }

                        RESULT_CANCELED -> {
                            Log.i(TAG, "Playback stopped by user")
                        }

                        RESULT_FIRST_USER -> {
                            Log.e(TAG, "Playback stopped by unknown error")
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                } else if (it.action.equals("org.videolan.vlc.player.result")) { // VLC
                    when (resultCode) {
                        RESULT_OK -> {
                            val pos = it.getLongExtra("extra_position", 0L)
                            val dur = it.getLongExtra("extra_duration", 0L)
                            if (pos > 0L) {
                                val ended = isAfterEndCreditsPosition(pos, dur)
                                Log.i(
                                    TAG,
                                    "Playback stopped [position=$pos, duration=$dur, ended:$ended]"
                                )
                                resultPlayer(videoUrl, pos.toInt(), dur.toInt(), ended)
                            } else {
                                if (dur == 0L && pos == 0L) {
                                    Log.i(TAG, "Playback completed")
                                    resultPlayer(videoUrl, 0, 0, true)
                                }
                            }
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                } else if (it.action.equals("is.xyz.mpv.MPVActivity.result")) { // MPV
                    when (resultCode) {
                        RESULT_OK -> {
                            val pos = it.getIntExtra("position", 0)
                            val dur = it.getIntExtra("duration", 0)
                            if (dur > 0) {
                                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                                Log.i(
                                    TAG,
                                    "Playback stopped [position=$pos, duration=$dur, ended:$ended]"
                                )
                                resultPlayer(videoUrl, pos, dur, ended)
                            } else if (dur == 0 && pos == 0) {
                                Log.i(TAG, "Playback completed")
                                resultPlayer(videoUrl, 0, 0, true)
                            }
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                } else if (it.action.equals("com.uapplication.uplayer.result") ||
                    it.action.equals("com.uapplication.uplayer.beta.result")
                ) { // UPlayer
                    when (resultCode) {
                        RESULT_OK -> {
                            val pos = it.getLongExtra("position", 0L).toInt()
                            val dur = it.getLongExtra("duration", 0L).toInt()
                            val ended = it.getBooleanExtra("isEnded", pos == dur)
                            if (pos > 0 && dur > 0) {
                                Log.i(
                                    TAG,
                                    "Playback stopped [position=$pos, duration=$dur, ended=$ended]"
                                )
                                resultPlayer(videoUrl, pos, dur, ended)
                            }
                        }

                        RESULT_CANCELED -> {
                            Log.e(
                                TAG,
                                "Playback Error. It isn't possible to get the duration or create the playlist."
                            )
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                } else if (it.action.equals("net.gtvbox.videoplayer.result") ||
                    it.action.equals("net.gtvbox.vimuhd.result")
                ) { // ViMu
                    when (resultCode) {
                        RESULT_FIRST_USER -> {
                            Log.i(TAG, "Playback completed")
                            resultPlayer(videoUrl, 0, 0, true)
                        }

                        RESULT_CANCELED, RESULT_VIMU -> {
                            val pos = it.getIntExtra("position", 0)
                            val dur = it.getIntExtra("duration", 0)
                            if (pos > 0 && dur > 0) {
                                Log.i(TAG, "Playback stopped [position=$pos, duration=$dur]")
                                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                                resultPlayer(videoUrl, pos, dur, ended)
                            }
                        }

                        RESULT_VIMU_ERROR -> {
                            Log.e(TAG, "Playback error")
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                } else { // others
                    when (resultCode) {
                        RESULT_OK, RESULT_CANCELED -> {
                            val pos = it.getIntExtra("position", 0)
                            val dur = it.getIntExtra("duration", 0)
                            if (pos > 0 && dur > 0) {
                                Log.i(TAG, "Playback stopped [position=$pos, duration=$dur]")
                                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                                resultPlayer(videoUrl, pos, dur, ended)
                            }
                        }

                        else -> {
                            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
                        }
                    }
                }
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
                    runVoidJsFunc("window.voiceResult", "'" + spokenText.replace("'", "\\'") + "'")
                }
            }
        }

        SELECTED_BROWSER = this.appBrowser
        if (!Helpers.isWebViewAvailable(this)
            || (SELECTED_BROWSER.isNullOrEmpty() && playVideoUrl.isNotEmpty())
        ) {
            // If SELECTED_BROWSER not set, but there is information about the latest video,
            // then the user used Crosswalk in previous versions of the application
            SELECTED_BROWSER = "XWalk"
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "onCreate() SELECTED_BROWSER: $SELECTED_BROWSER")
        when (SELECTED_BROWSER) {
            "XWalk" -> {
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
                setContentView(R.layout.activity_xwalk)
            }

            "SysView" -> {
                setContentView(R.layout.activity_webview)
                browser = SysView(this, R.id.webView)
            }

            else -> {
                setContentView(R.layout.activity_empty)
                showBrowserInputDialog()
            }
        }
        hideSystemUI()

        progressBar = findViewById(R.id.progressBar_cyclic)
        browser?.init()

        if (this.firstRun) {
            CoroutineScope(Dispatchers.IO).launch {
                if (BuildConfig.DEBUG) Log.d("*****", "First run scheduleUpdate(sync)")
                Scheduler.scheduleUpdate(true)
            }
        }
    }

    override fun onXWalkInitStarted() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onXWalkInitStarted()")
    }

    override fun onXWalkInitCancelled() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onXWalkInitCancelled()")
        // Perform error handling here
        finish()
    }

    override fun onXWalkInitFailed() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onXWalkInitFailed()")
        if (mXWalkUpdater == null) {
            mXWalkUpdater = MyXWalkUpdater(this, this)
        }
        setupXWalkApkUrl()
        mXWalkUpdater?.updateXWalkRuntime()
    }

    private fun setupXWalkApkUrl() {
        val abi = MyXWalkEnvironment.getRuntimeAbi()
        val apkUrl = String.format(getString(R.string.xwalk_apk_link), abi)
        mXWalkUpdater!!.setXWalkApkUrl(apkUrl)
    }

    override fun onXWalkInitCompleted() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onXWalkInitCompleted()")
        browser = XWalk(this, R.id.xWalkView)
        browser?.init()
    }

    override fun onXWalkUpdateCancelled() {
        if (BuildConfig.DEBUG) Log.d(TAG, "onXWalkUpdateCancelled()")
        // Perform error handling here
        finish()
    }

    override fun onBrowserPageFinished(view: ViewGroup, url: String) {
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
            progressBar?.visibility = View.GONE
            Log.d("*****", "LAMPA onLoadFinished $url")
            if (BuildConfig.DEBUG) Log.d("*****", "onBrowserPageFinished() processIntent")
            processIntent(intent, 1000)
            lifecycleScope.launch {
                delay(3000)
                runVoidJsFunc(
                    "Lampa.Storage.listener.add",
                    "'change'," +
                            "function(o){AndroidJS.StorageChange(JSON.stringify(o))}"
                )
                runJsStorageChangeField("activity", "{}") // get current lampaActivity
                runJsStorageChangeField("player_timecode")
                runJsStorageChangeField("playlist_next")
                runJsStorageChangeField("torrserver_preload")
                runJsStorageChangeField("internal_torrclient")
                runJsStorageChangeField("language")
                runJsStorageChangeField("source")
                runJsStorageChangeField("account_use") // get sync state
                runJsStorageChangeField("recomends_list", "[]") // force update recs var
                changeTmdbUrls()
                syncBookmarks()
                for (item in delayedVoidJsFunc) runVoidJsFunc(item[0], item[1])
                delayedVoidJsFunc.clear()
            }
            CoroutineScope(Dispatchers.IO).launch {
                Scheduler.scheduleUpdate(false)
            }
        }
    }

    override fun onBrowserInitCompleted() {
        browserInit = true
        HttpHelper.userAgent = browser?.getUserAgentString() + " lampa_client"
        browser?.apply {
            setUserAgentString(HttpHelper.userAgent)
            setBackgroundColor(ContextCompat.getColor(baseContext, R.color.lampa_background))
            addJavascriptInterface(AndroidJS(this@MainActivity, this), "AndroidJS")
        }
        if (LAMPA_URL.isEmpty()) {
            showUrlInputDialog()
        } else {
            browser?.loadUrl(LAMPA_URL)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Log.d("*****", "onNewIntent() processIntent")
        processIntent(intent)
    }

    fun changeTmdbUrls() {
        lifecycleScope.launch {
            runVoidJsFunc(
                "AndroidJS.StorageChange",
                "JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')})"
            )
            runVoidJsFunc(
                "AndroidJS.StorageChange",
                "JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')})"
            )
        }
    }

    // runVoidJsFunc("Lampa.Favorite.$action", "'$catgoryName', {id: $id}")
    // runVoidJsFunc("Lampa.Favorite.add", "'wath', ${Gson().toJson(card)}") - FIXME: wrong string ID
    private fun syncBookmarks() {
        // initialize if no favorite (undefined)
        runVoidJsFunc("Lampa.Favorite.init", "")

        if (BuildConfig.DEBUG) Log.d("*****", "syncBookmarks() wathToAdd: ${this.wathToAdd}")
        this.wathToAdd.forEach { // we need full card data here to add
            val lampaCard = App.context.FAV?.card?.find { card -> card.id == it.id }
                ?: it.card // js from intent
            lampaCard?.let { card ->
                card.fixCard()
                if (BuildConfig.DEBUG) Log.d(
                    "*****",
                    "syncBookmarks() wathToAdd $card"
                )
                val id = card.id?.toIntOrNull()
                id?.let {
                    val params =
                        if (card.type == "tv") "name: '${card.name}'" else "title: '${card.title}'"
                    runVoidJsFunc(
                        "Lampa.Favorite.add",
                        "'${LampaProvider.Late}', {id: $id, type: '${card.type}', source: '${card.source}', img: '${card.img}', $params}"
                    )
                }
            }
        }
        if (BuildConfig.DEBUG) Log.d("*****", "syncBookmarks() wathToRemove: ${this.wathToRemove}")
        this.wathToRemove.forEach {// delete items from later
            runVoidJsFunc("Lampa.Favorite.remove", "'${LampaProvider.Late}', {id: $it}")
        }
        if (BuildConfig.DEBUG) Log.d("*****", "syncBookmarks() bookToRemove: ${this.bookToRemove}")
        this.bookToRemove.forEach {// delete items from bookmarks
            runVoidJsFunc("Lampa.Favorite.remove", "'${LampaProvider.Book}', {id: $it}")
        }
        if (BuildConfig.DEBUG) Log.d("*****", "syncBookmarks() likeToRemove: ${this.likeToRemove}")
        this.likeToRemove.forEach {// delete items from likes
            runVoidJsFunc("Lampa.Favorite.remove", "'${LampaProvider.Like}', {id: $it}")
        }
        if (BuildConfig.DEBUG) Log.d("*****", "syncBookmarks() histToRemove: ${this.histToRemove}")
        this.histToRemove.forEach {// delete items from history
            runVoidJsFunc("Lampa.Favorite.remove", "'${LampaProvider.Hist}', {id: $it}")
        }
        // don't do it again
        App.context.clearPending()
    }

    private fun dumpStorage() {
        val backupJavascript = "(function() {" +
                "console.log('Backing up localStorage');" +
                "AndroidJS.clear();" +
                "for (var key in localStorage) { AndroidJS.set(key, localStorage.getItem(key)); }" +
                "})()"
        browser?.evaluateJavascript(backupJavascript) { Log.d(TAG, "localStorage backed up") }
    }

    private fun restoreStorage() {
        val restoreJavascript = "(function() {" +
                "console.log('Restoring localStorage');" +
                "AndroidJS.dump();" +
                "var len = AndroidJS.size();" +
                "for (i = 0; i < len; i++) { var key = AndroidJS.key(i);  console.log(key); localStorage.setItem(key, AndroidJS.get(key)); }" +
                "})()"
        browser?.evaluateJavascript(restoreJavascript) { Log.d(TAG, "localStorage restored") }
    }

    private fun clearStorage() {
        browser?.evaluateJavascript("localStorage.clear()") { Log.d(TAG, "localStorage cleared") }
    }

    private fun processIntent(intent: Intent?, delay: Long = 0) {
        var intID = -1
        var mediaType = ""
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "***** processIntent data: " + intent?.toUri(0))
            intent?.extras?.let {
                for (key in it.keySet()) {
                    Log.d(
                        TAG,
                        ("***** processIntent: data extras $key : ${it.get(key) ?: "NULL"}")
                    )
                }
            }
        }
        if (intent?.hasExtra("id") == true)
            intID = intent.getIntExtra("id", -1)

        if (intent?.hasExtra("media_type") == true)
            mediaType = intent.getStringExtra("media_type") ?: ""

        intent?.data?.let {
            if (it.host?.contains("themoviedb.org") == true && it.pathSegments.size >= 2) {
                val videoType = it.pathSegments[0]
                var id = it.pathSegments[1]
                if (videoType == "movie" || videoType == "tv") {
                    id = "\\d+".toRegex().find(id)?.value ?: "-1"
                    if (id.isNotEmpty()) {
                        intID = id.toInt()
                        mediaType = videoType
                    }
                }
            }
            when (intent.action) { // handle search
                "GLOBALSEARCH" -> {
                    val uri = it
                    val ids = uri.lastPathSegment
                    if (uri.lastPathSegment == "update_channel")
                        intID = -1
                    else {
                        intID = ids?.toIntOrNull() ?: -1
                        mediaType = intent.extras?.getString(SearchManager.EXTRA_DATA_KEY) ?: ""
                    }
                }

                else -> { // handle open from channels
                    if (it.encodedPath?.contains("update_channel") == true) {
                        intID = -1
                        val params = when (val channel = it.encodedPath?.substringAfterLast("/")) {
                            LampaProvider.Recs -> {
                                // Open Main
                                "{" +
                                        "title: '" + getString(R.string.title_main) + "' + ' - " + lampaSource.uppercase(
                                    Locale.getDefault()
                                ) + "'," +
                                        "component: 'main'," +
                                        "source: '" + lampaSource + "'," +
                                        "url: ''" +
                                        "}"
                            }

                            LampaProvider.Like, LampaProvider.Book, LampaProvider.Hist -> {
                                "{" +
                                        "title: '" + getChannelDisplayName(channel) + "'," +
                                        "component: '$channel' == 'book' ? 'bookmarks' : 'favorite'," +
                                        "type: '$channel'," +
                                        "url: ''," +
                                        "page: 1" +
                                        "}"
                            }

                            else -> ""
                        }
                        if (params != "") {
                            lifecycleScope.launch {
                                runVoidJsFunc("window.start_deep_link = ", params)
                                delay(delay)
                                runVoidJsFunc("Lampa.Controller.toContent", "")
                                runVoidJsFunc("Lampa.Activity.push", params)
                            }
                        }
                    }
                }
            }
        }
        // continue watch
        if (intent?.getBooleanExtra("continueWatch", false) == true) {
            this.playActivityJS?.let { json ->
                if (isValidJson(json)) {
                    lifecycleScope.launch {
                        runVoidJsFunc("window.start_deep_link = ", json)
                        delay(delay)
                        runVoidJsFunc("Lampa.Controller.toContent", "")
                        runVoidJsFunc("Lampa.Activity.push", json)
                        delay(500)
                        if (intent.getBooleanExtra("android.intent.extra.START_PLAYBACK", false)) {
                            if (isValidJson(this@MainActivity.resumeJS)) {
                                this@MainActivity.resumeJS?.let { JSONObject(it) }
                                    ?.let { runPlayer(it) }
                            }
                        }
                    }
                }
            }
        } else
        // open card
            if (intID >= 0 && mediaType.isNotEmpty()) {

                var source = intent?.getStringExtra("source")
                if (source.isNullOrEmpty())
                    source = "tmdb"

//            ID in card json _must_ be INT in case TMDB at least, or bookmarks don't match
//            var card = intent?.getStringExtra("LampaCardJS")
//            if (card.isNullOrEmpty())
                val card = "{id: $intID, source: '$source'}"

                lifecycleScope.launch {
                    runVoidJsFunc(
                        "window.start_deep_link = ",
                        "{id: $intID, method: '$mediaType', source: '$source', component: 'full', card: $card}"
                    )
                    delay(delay)
                    runVoidJsFunc("Lampa.Controller.toContent", "")
                    runVoidJsFunc(
                        "Lampa.Activity.push",
                        "{id: $intID, method: '$mediaType', source: '$source', component: 'full', card: $card}"
                    )
                }
            }
        // process search cmd
        val cmd = intent?.getStringExtra("cmd")
        if (!cmd.isNullOrBlank()) {
            when (cmd) {
                "open_settings" -> {
                    showMenuDialog()
                }
            }
        }
    }

    private fun showMenuDialog() {
        val mainActivity = this
        val menu = AlertDialog.Builder(mainActivity)
        val menuItemsTitle = arrayOfNulls<String?>(5)
        val menuItemsAction = arrayOfNulls<String?>(5)

        menuItemsTitle[0] =
            if (isAndroidTV && VERSION.SDK_INT >= Build.VERSION_CODES.O) getString(R.string.update_chan_title)
            else if (isAndroidTV) getString(R.string.update_home_title) else getString(R.string.close_menu_title)
        menuItemsAction[0] = "closeMenu"
        menuItemsTitle[1] = getString(R.string.change_url_title)
        menuItemsAction[1] = "showUrlInputDialog"
        menuItemsTitle[2] = getString(R.string.change_engine)
        menuItemsAction[2] = "showBrowserInputDialog"
        menuItemsTitle[3] = getString(R.string.backup_restore_title)
        menuItemsAction[3] = "showBackupDialog"
        menuItemsTitle[4] = getString(R.string.exit)
        menuItemsAction[4] = "appExit"

        val icons = arrayOf(
            if (isAndroidTV) R.drawable.round_refresh_24 else R.drawable.round_close_24,
            R.drawable.round_link_24,
            R.drawable.round_explorer_24,
            R.drawable.round_settings_backup_restore_24,
            R.drawable.round_exit_to_app_24,
        )
        val adapter: ListAdapter = ImgArrayAdapter(mainActivity, menuItemsTitle, icons)

        menu.setTitle(getString(R.string.menu_title))
        menu.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            when (menuItemsAction[which]) {
                "closeMenu" ->
                    if (isAndroidTV) {
                        Scheduler.scheduleUpdate(false)
                    }

                "showUrlInputDialog" -> {
                    App.toast(R.string.change_note)
                    showUrlInputDialog()
                }

                "showBrowserInputDialog" -> {
                    App.toast(R.string.change_note)
                    showBrowserInputDialog()
                }

                "showBackupDialog" -> showBackupDialog()
                "appExit" -> appExit()
            }
        }
        menu.setOnDismissListener {
            isMenuVisible = false
            showFab(true)
        }
        val menuDialog = menu.create()
        menuDialog.show()
        isMenuVisible = true
    }

    private fun showBackupDialog() {
        val mainActivity = this
        val menu = AlertDialog.Builder(mainActivity)
        val menuItemsTitle = arrayOfNulls<String?>(4)
        val menuItemsAction = arrayOfNulls<String?>(4)
        val icons = arrayOf(
            R.drawable.round_settings_backup_restore_24,
            R.drawable.round_refresh_24,
            R.drawable.round_refresh_24,
            R.drawable.round_close_24
        )
        menuItemsTitle[0] = getString(R.string.backup_all_title)
        menuItemsAction[0] = "backupAllSettings"
        menuItemsTitle[1] = getString(R.string.restore_app_title)
        menuItemsAction[1] = "restoreAppSettings"
        menuItemsTitle[2] = getString(R.string.restore_storage_title)
        menuItemsAction[2] = "restoreLampaSettings"
        menuItemsTitle[3] = getString(R.string.default_setting_title)
        menuItemsAction[3] = "restoreDefaultSettings"


        val adapter: ListAdapter = ImgArrayAdapter(mainActivity, menuItemsTitle, icons)
        menu.setTitle(getString(R.string.backup_restore_title))
        menu.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            when (menuItemsAction[which]) {
                "backupAllSettings" -> {
                    lifecycleScope.launch {
                        dumpStorage() // fixme: add callback
                        delay(3000)
                        if (saveSettings(Prefs.APP_PREFERENCES) && saveSettings(Prefs.STORAGE_PREFERENCES))
                            App.toast(
                                getString(
                                    R.string.settings_saved_toast,
                                    Backup.DIR.toString()
                                )
                            )
                        else
                            App.toast(R.string.settings_save_fail)
                    }
                }

                "restoreAppSettings" -> {
                    if (loadFromBackup(Prefs.APP_PREFERENCES)) {
                        App.toast(R.string.settings_restored)
                        recreate()
                    } else
                        App.toast(R.string.settings_rest_fail)
                }

                "restoreLampaSettings" -> {
                    lifecycleScope.launch {
                        if (loadFromBackup(Prefs.STORAGE_PREFERENCES)) {
                            restoreStorage() // fixme: add callback
                            App.toast(R.string.settings_restored)
                            delay(3000)
                            recreate()
                        } else
                            App.toast(R.string.settings_rest_fail)
                    }
                }

                "restoreDefaultSettings" -> {
                    clearStorage()
                    appPrefs.edit().clear().apply()
                    recreate()
                }
            }
        }
        val menuDialog = menu.create()
        menuDialog.show()
        if (!PermHelpers.hasStoragePermissions(this)) {
            PermHelpers.verifyStoragePermissions(this)
        }
    }

    private fun showBrowserInputDialog() {
        val mainActivity = this
        val menu = AlertDialog.Builder(mainActivity)
        val menuItemsTitle: Array<String?>
        val menuItemsAction: Array<String?>
        val icons: Array<Int>
        if (Helpers.isWebViewAvailable(this)) {
            menuItemsTitle = arrayOfNulls(2)
            menuItemsAction = arrayOfNulls(2)
            val wvv = Helpers.getWebViewVersion(mainActivity)
            menuItemsAction[0] = "XWalk"
            menuItemsAction[1] = "SysView"
            if (menuItemsAction[0] == SELECTED_BROWSER) {
                menuItemsTitle[0] =
                    "${getString(R.string.engine_crosswalk)} - ${getString(R.string.engine_active)}"
                menuItemsTitle[1] = "${getString(R.string.engine_webkit)} $wvv"
            } else {
                menuItemsTitle[0] = getString(R.string.engine_crosswalk_obsolete)
                menuItemsTitle[1] =
                    "${getString(R.string.engine_webkit)} - ${getString(R.string.engine_active)} $wvv"
            }
            icons = arrayOf(
                R.drawable.round_explorer_24,
                R.drawable.round_explorer_24
            )
        } else {
            menuItemsTitle = arrayOfNulls(1)
            menuItemsAction = arrayOfNulls(1)
            menuItemsAction[0] = "XWalk"
            if (menuItemsAction[0] == SELECTED_BROWSER) {
                menuItemsTitle[0] =
                    "${getString(R.string.engine_crosswalk)} - ${getString(R.string.engine_active)}"
            } else {
                menuItemsTitle[0] = getString(R.string.engine_crosswalk)
            }
            icons = arrayOf(R.drawable.round_explorer_24)
        }
        val adapter: ListAdapter = ImgArrayAdapter(mainActivity, menuItemsTitle, icons)
        menu.setTitle(getString(R.string.change_engine_title))
        menu.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            if (menuItemsAction[which] != SELECTED_BROWSER) {
                menuItemsAction[which]?.let { this.appBrowser = it }
                mainActivity.recreate()
            }
        }
        val menuDialog = menu.create()
        menuDialog.show()
    }

    fun showUrlInputDialog() {
        val mainActivity = this
        val builder = AlertDialog.Builder(mainActivity)
        builder.setTitle(R.string.input_url_title)
        // Set up the input
        val wrapper = TextInputLayout(this)
        val input = TextInputEditText(wrapper.context)
        input.setSingleLine()
        input.textSize = 18f
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(LAMPA_URL.ifEmpty { "http://lampa.mx" })
        val margin =
            dp2px(mainActivity, 14.5f)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(margin)
        input.layoutParams = params
        wrapper.addView(input)
        builder.setView(wrapper)

        // Set up the buttons
        builder.setPositiveButton(R.string.save) { _: DialogInterface?, _: Int ->
            LAMPA_URL = input.text.toString()
            if (URL_PATTERN.matcher(LAMPA_URL).matches()) {
                println("URL '$LAMPA_URL' is valid")
                if (this.appUrl != LAMPA_URL) {
                    this.appUrl = LAMPA_URL
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
            if (LAMPA_URL.isEmpty() && this.appUrl.isEmpty()) {
                appExit()
            } else {
                LAMPA_URL = this.appUrl
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
            showMenuDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            println("Back button long pressed")
            showMenuDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onPause() {
        if (browserInit) {
            browser?.pauseTimers()
            // dumpStorage()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (browserInit) {
            browser?.destroy()
        }
        try {
            Speech.getInstance()?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (!this.isTvBox) setupFab()
        // Try to initialize again when the user completed updating and
        // returned to current activity. The browser.onResume() will do nothing if
        // the initialization is proceeding or has already been completed.
        mXWalkInitializer?.initAsync()
        if (browserInit) {
            browser?.resumeTimers()
            syncBookmarks()
        }
    }

    // handle user pressed Home
    override fun onUserLeaveHint() {
        Log.d(TAG, "onUserLeaveHint()")
        if (browserInit) {
            browser?.pauseTimers()
            browser?.clearCache(true)
        }
        super.onUserLeaveHint()
    }

    // handle configuration changes (language / screen orientation)
    override fun onConfigurationChanged(newConfig: Configuration) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
        showFab(true)
    }

    fun appExit() {
        browser?.let {
            it.clearCache(true)
            it.destroy()
        }
        finishAffinity() // exitProcess(1)
    }

    fun setLang(lang: String) {
        this.appLang = lang
        Helpers.setLocale(this, lang)
    }

    fun setPlayerPackage(packageName: String, isIPTV: Boolean) {
        SELECTED_PLAYER = packageName.lowercase(Locale.getDefault())
        if (isIPTV)
            this.tvPlayer = SELECTED_PLAYER!!
        else
            this.appPlayer = SELECTED_PLAYER!!
    }

    private fun saveLastPlayed() {
        val editor = this.lastPlayedPrefs.edit()
        editor?.apply {
            putInt("playIndex", playIndex)
            putString("playVideoUrl", playVideoUrl)
            putString("playJSONArray", playJSONArray.toString())
            apply()
        }
        Log.d("*****", "saveLastPlayed $playJSONArray")
        // store to prefs for resume from WatchNext
        this.playActivityJS = lampaActivity
    }

    private fun resultPlayer(
        endedVideoUrl: String,
        pos: Int = 0,
        dur: Int = 0,
        ended: Boolean = false
    ) {
        // store state and duration too for WatchNext
        val editor = this.lastPlayedPrefs.edit()
        editor?.putBoolean("ended", ended)
        editor?.putInt("position", pos)
        editor?.putInt("duration", dur)
        editor.apply()

        lifecycleScope.launch {
            // Add | Remove Continue to Play
            withContext(Dispatchers.Default) {
                val lampaActivity = this@MainActivity.playActivityJS?.let { JSONObject(it) }
                if (lampaActivity?.has("movie") == true) {
                    val card = Gson().fromJson(
                        lampaActivity.getJSONObject("movie").toString(),
                        LampaCard::class.java
                    )
                    card?.let {
                        it.fixCard()
                        try {
                            if (BuildConfig.DEBUG) Log.d("*****", "resultPlayer PlayNext $it")
                            if (!ended)
                                WatchNext.addLastPlayed(it)
                            else
                                WatchNext.removeContinueWatch()
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) Log.d(
                                "*****",
                                "resultPlayer Error add $it to WatchNext: $e"
                            )
                        }
                    }
                }
            }
        }

        val videoUrl =
            if (endedVideoUrl == "" || endedVideoUrl == "null") playVideoUrl
            else endedVideoUrl
        if (videoUrl == "") return

        for (i in 0 until playJSONArray.length()) {
            val io = playJSONArray.getJSONObject(i)
            if (!io.has("timeline") || !io.has("url")) break

            val timeline = io.optJSONObject("timeline")
            val hash = timeline?.optString("hash", "0")
            if (io.optString("url") == videoUrl) {
                val time: Int = if (ended) 0 else pos / 1000
                val duration: Int =
                    if (ended) 0
                    else if (dur == 0 && timeline?.has("duration") == true)
                        timeline.optDouble("duration", 0.0).toInt()
                    else dur / 1000

                val percent: Int = if (duration > 0) time * 100 / duration else 100

                val newTimeline = JSONObject()
                newTimeline.put("hash", hash)
                newTimeline.put("time", time.toDouble())
                newTimeline.put("duration", duration.toDouble())
                newTimeline.put("percent", percent)
                runVoidJsFunc("Lampa.Timeline.update", newTimeline.toString())
                // for PlayNext
                io.put("timeline", newTimeline)
                val resumeio = JSONObject(io.toString())
                resumeio.put("playlist", playJSONArray)
                this.resumeJS = resumeio.toString()
                break
            }
            if (i >= playIndex) {
                val newTimeline = JSONObject()
                newTimeline.put("hash", hash)
                newTimeline.put("percent", 100)
                newTimeline.put("time", 0)
                newTimeline.put("duration", 0)
                runVoidJsFunc("Lampa.Timeline.update", newTimeline.toString())
                io.put("timeline", newTimeline)
            }
        }
    }

    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject) {
        runPlayer(jsonObject, "")
    }

    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject, launchPlayer: String) {

        val videoUrl = jsonObject.optString("url")
        val isIPTV = jsonObject.optBoolean("iptv", false)
        val isLIVE = jsonObject.optBoolean("need_check_live_stream", false)
        SELECTED_PLAYER =
            launchPlayer.ifEmpty { if (isIPTV || isLIVE) this.tvPlayer else this.appPlayer }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndTypeAndNormalize(
            Uri.parse(videoUrl),
            if (videoUrl.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/*"
        )
        val resInfo =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
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
        if (filteredList.isEmpty()) {
            App.toast(R.string.no_activity_found, true)
            return
        }
        var playerPackageExist = false
        if (!SELECTED_PLAYER.isNullOrEmpty()) {
            for (info in filteredList) {
                if (info.activityInfo.packageName.lowercase(Locale.getDefault()) == SELECTED_PLAYER) {
                    playerPackageExist = true
                    break
                }
            }
        }
        if (!playerPackageExist || SELECTED_PLAYER.isNullOrEmpty()) {
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
                if (setDefaultPlayer) setPlayerPackage(SELECTED_PLAYER.toString(), isIPTV)
                dialog.dismiss()
                runPlayer(jsonObject, SELECTED_PLAYER!!)
            }
            val playerChooserDialog = playerChooser.create()
            playerChooserDialog.show()
            playerChooserDialog.listView.requestFocus()
        } else {
            var videoPosition: Long = 0
            val videoTitle =
                if (jsonObject.has("title"))
                    jsonObject.optString("title")
                else if (isIPTV) "LAMPA TV" else "LAMPA video"
            val listTitles = ArrayList<String>()
            val listUrls = ArrayList<String>()
            val subsTitles = ArrayList<String>()
            val subsUrls = ArrayList<String>()
            val headers = ArrayList<String>()
            playIndex = -1

            if (playerTimeCode == "continue" && jsonObject.has("timeline")) {
                val timeline = jsonObject.optJSONObject("timeline")
                if (timeline?.has("time") == true) {
                    val hash = timeline.optString("hash", "0")
                    val latestTimeline =
                        if (playerFileView?.has(hash) == true) playerFileView?.optJSONObject(hash) else null
                    videoPosition = if (latestTimeline?.has("time") == true)
                        (latestTimeline.optDouble("time", 0.0) * 1000).toLong()
                    else
                        (timeline.optDouble("time", 0.0) * 1000).toLong()
                }
            }
            // Headers
            var ua = HttpHelper.userAgent
            if (jsonObject.has("headers")) {
                val headersJSON = jsonObject.optJSONObject("headers")
                if (headersJSON != null) {
                    val keys = headersJSON.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = headersJSON.optString(key)
                        when (key.lowercase(Locale.getDefault())) {
                            "user-agent" -> ua = value
                            "content-length" -> {}
                            else -> {
                                headers.add(key)
                                headers.add(value)
                            }
                        }
                    }
                }
            }
            headers.add("User-Agent")
            headers.add(ua)
            // Playlist
            if (jsonObject.has("playlist") && playerAutoNext) {
                playJSONArray = jsonObject.getJSONArray("playlist")
                val badLinkPattern = "(/stream/.*?\\?link=.*?&index=\\d+)&preload\$".toRegex()
                for (i in 0 until playJSONArray.length()) {
                    val io = playJSONArray.getJSONObject(i)
                    if (io.has("url")) {
                        val url = if (torrserverPreload && internalTorrserve)
                            io.optString("url").replace(badLinkPattern, "$1&play")
                        else
                            io.optString("url")
                        if (url != io.optString("url")) {
                            io.put("url", url)
                            playJSONArray.put(i, io)
                        }
                        if (url == videoUrl)
                            playIndex = i
                        listUrls.add(io.optString("url"))
                        listTitles.add(
                            if (io.has("title")) io.optString("title") else (i + 1).toString()
                        )
                    }
                }
            }
            // Subtitles
            if (jsonObject.has("subtitles")) {
                val subsJSONArray = jsonObject.optJSONArray("subtitles")
                if (subsJSONArray != null)
                    for (i in 0 until subsJSONArray.length()) {
                        val io = subsJSONArray.getJSONObject(i)
                        if (io.has("url")) {
                            subsUrls.add(io.optString("url"))
                            subsTitles.add(io.optString("label", "Sub " + (i + 1).toString()))
                        }
                    }
            }
            if (playIndex < 0) {
                // current url not found in playlist or playlist missing
                playIndex = 0
                playJSONArray = JSONArray()
                playJSONArray.put(jsonObject)
            }
            playVideoUrl = videoUrl

            saveLastPlayed()

            when (SELECTED_PLAYER) {
                "com.uapplication.uplayer", "com.uapplication.uplayer.beta" -> {
                    intent.setPackage(SELECTED_PLAYER)
                    intent.putExtra("title", videoTitle)

                    if (playerTimeCode == "continue" || playerTimeCode == "again")
                        intent.putExtra("resume", videoPosition)

                    val haveQuality = if (jsonObject.has("quality")) {
                        var qualityListSize = 0
                        val keys = (jsonObject["quality"] as JSONObject).keys()
                        while (keys.hasNext() && qualityListSize < 2) {
                            keys.next()
                            qualityListSize++
                        }
                        qualityListSize > 1
                    } else {
                        false
                    }

                    if (listUrls.size > 1 || haveQuality) {
                        val firstHash =
                            ((playJSONArray[0] as JSONObject)["timeline"] as JSONObject).optString(
                                "hash",
                                "0"
                            )
                        if (firstHash != "0") {
                            intent.putExtra("playlistTitle", firstHash)
                        }

                        if (listTitles.size > 0) {
                            intent.putStringArrayListExtra("titleList", listTitles)
                        } else {
                            intent.putStringArrayListExtra("titleList", arrayListOf(videoTitle))
                        }
                        intent.putExtra("playlistPosition", playIndex)

                        if (haveQuality) {
                            var qualitySet = ""
                            val qualityMap = LinkedHashMap<String, ArrayList<String>>()
                            for (i in 0 until playJSONArray.length()) {
                                val itemQualityMap =
                                    (playJSONArray[i] as JSONObject)["quality"] as JSONObject
                                val keys = itemQualityMap.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val value = itemQualityMap.getString(key)
                                    if (value == videoUrl) qualitySet = key
                                    if (qualityMap.contains(key).not()) {
                                        qualityMap[key] = arrayListOf()
                                    }
                                    qualityMap.getValue(key).add(value)
                                }
                            }
                            val qualityKeys = ArrayList(qualityMap.keys.toList())
                            val qualityIndex = qualityKeys.indexOf(qualitySet)
                            intent.putStringArrayListExtra("videoGroupList", qualityKeys)
                            qualityKeys.forEach {
                                intent.putStringArrayListExtra(it, qualityMap.getValue(it))
                            }
                            intent.putExtra(
                                "groupPosition",
                                if (qualityIndex < 1) 0 else qualityIndex
                            )
                        } else {
                            if (listUrls.size > 0) {
                                intent.putStringArrayListExtra("videoList", listUrls)
                            } else {
                                intent.putStringArrayListExtra("videoList", arrayListOf(videoUrl))
                            }
                        }
                    }
                }

                "com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.beta" -> {
                    //intent.setPackage(SELECTED_PLAYER)
                    intent.component = ComponentName(
                        SELECTED_PLAYER!!,
                        "$SELECTED_PLAYER.ActivityScreen"
                    )
                    intent.putExtra("title", videoTitle)
                    intent.putExtra("sticky", false)
                    intent.putExtra("headers", headers.toTypedArray())
                    if (playerTimeCode == "continue" && videoPosition > 0L) {
                        intent.putExtra("position", videoPosition.toInt())
                    } else if (playerTimeCode == "again"
                        || (playerTimeCode == "continue" && videoPosition == 0L)
                    ) {
                        intent.putExtra("position", 1)
                    }
                    if (listUrls.size > 1) {
                        val parcelableVideoArr = arrayOfNulls<Parcelable>(listUrls.size)
                        for (i in 0 until listUrls.size) {
                            parcelableVideoArr[i] = Uri.parse(listUrls[i])
                        }
                        intent.putExtra("video_list", parcelableVideoArr)
                        intent.putExtra("video_list.name", listTitles.toTypedArray())
                        intent.putExtra("video_list_is_explicit", true)
                    }
                    if (subsUrls.size > 0) {
                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
                        for (i in 0 until subsUrls.size) {
                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
                        }
                        intent.putExtra("subs", parcelableSubsArr)
                        intent.putExtra("subs.name", subsTitles.toTypedArray())
                    }
                    intent.putExtra("return_result", true)
                }

                "is.xyz.mpv" -> {
                    // http://mpv-android.github.io/mpv-android/intent.html
                    intent.setPackage(SELECTED_PLAYER)
                    if (subsUrls.size > 0) {
                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
                        for (i in 0 until subsUrls.size) {
                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
                        }
                        intent.putExtra("subs", parcelableSubsArr)
                    }
                    if (playerTimeCode == "continue" && videoPosition > 0L) {
                        intent.putExtra("position", videoPosition.toInt())
                    } else if (playerTimeCode == "again"
                        || (playerTimeCode == "continue" && videoPosition == 0L)
                    ) {
                        intent.putExtra("position", 1)
                    }
                }

                "org.videolan.vlc" -> {
                    // https://wiki.videolan.org/Android_Player_Intents
                    if (VERSION.SDK_INT > 32) {
                        intent.setPackage(SELECTED_PLAYER)
                    } else
                        intent.component = ComponentName(
                            SELECTED_PLAYER!!,
                            "$SELECTED_PLAYER.gui.video.VideoPlayerActivity"
                        ) // required for return intent
                    intent.putExtra("title", videoTitle)
                    if (playerTimeCode == "continue" && videoPosition > 0L) {
                        intent.putExtra("from_start", false)
                        intent.putExtra("position", videoPosition)
                    } else if (playerTimeCode == "again"
                        || (playerTimeCode == "continue" && videoPosition == 0L)
                    ) {
                        intent.putExtra("from_start", true)
                        intent.putExtra("position", 0L)
                    }
                }

                "com.brouken.player" -> {
                    intent.setPackage(SELECTED_PLAYER)
                    intent.putExtra("title", videoTitle)
                    if (playerTimeCode == "continue" || playerTimeCode == "again")
                        intent.putExtra("position", videoPosition.toInt())
                    if (subsUrls.size > 0) {
                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
                        for (i in 0 until subsUrls.size) {
                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
                        }
                        intent.putExtra("subs", parcelableSubsArr)
                        intent.putExtra("subs.name", subsTitles.toTypedArray())
                    }
                    intent.putExtra("return_result", true)
                }

                "net.gtvbox.videoplayer", "net.gtvbox.vimuhd" -> {
                    intent.setPackage(SELECTED_PLAYER)
                    intent.putExtra("headers", headers.toTypedArray())
                    // see https://vimu.tv/player-api
                    if (listUrls.size <= 1) {
                        intent.putExtra("forcename", videoTitle)
                        if (subsUrls.size > 0) {
                            intent.putStringArrayListExtra(
                                "asussrtlist",
                                subsUrls
                            )
                        }
                        if (isIPTV || isLIVE)
                            intent.putExtra("forcelive", true)
                    } else {
                        intent.setDataAndType(
                            Uri.parse(videoUrl),
                            "application/vnd.gtvbox.filelist"
                        )
                        if ((getAppVersion(this, SELECTED_PLAYER!!)?.versionNumber
                                ?: 0L) >= 1000L // 10.0 and above
                        ) {
                            intent.putStringArrayListExtra("asusfilelist", listUrls)
                            intent.putStringArrayListExtra("asusnamelist", listTitles)
                            intent.putExtra("startindex", playIndex)
                        } else {
                            intent.putStringArrayListExtra(
                                "asusfilelist",
                                ArrayList(listUrls.subList(playIndex, listUrls.size))
                            )
                            intent.putStringArrayListExtra(
                                "asusnamelist",
                                ArrayList(listTitles.subList(playIndex, listUrls.size))
                            )
                        }
                    }
                    if (playerTimeCode == "continue" || playerTimeCode == "again") {
                        intent.putExtra("position", videoPosition.toInt())
                        intent.putExtra("startfrom", videoPosition.toInt())
                    } else if (playerTimeCode == "ask") {
                        // use ViMu resume
                        intent.putExtra("forcedirect", true)
                        intent.putExtra("forceresume", true)
                    }
                }

                else -> {
                    intent.setPackage(SELECTED_PLAYER)
                }
            }
            try {
                intent.flags = 0 // https://stackoverflow.com/a/47694122
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "INTENT: " + intent.toUri(0))
                    intent.extras?.let {
                        for (key in it.keySet()) {
                            if (key == "headers")
                                Log.d(
                                    TAG,
                                    ("INTENT: data extras $key : ${
                                        it.getStringArray(key)?.toList()
                                    }")
                                )
                            else
                                Log.d(
                                    TAG,
                                    ("INTENT: data extras $key : ${it.get(key) ?: "NULL"}")
                                )
                        }
                    }
                }
                resultLauncher.launch(intent)
            } catch (e: Exception) {
                App.toast(R.string.no_launch_player, true)
            }
        }
    }

    fun displaySpeechRecognizer() {
        if (VERSION.SDK_INT < 18) {
            // Create an intent that can start the Speech Recognizer activity
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
        } else {
            // Verify permissions
            verifyMicPermissions(this)

            var dialog: AlertDialog? = null
            val view = layoutInflater.inflate(R.layout.dialog_search, null, false)
            val etSearch = view.findViewById<AppCompatEditText?>(R.id.etSearchQuery)
            val btnVoice = view.findViewById<AppCompatImageButton?>(R.id.btnVoiceSearch)
            val inputManager =
                getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager

            etSearch?.apply {
                setOnClickListener {
                    inputManager?.showSoftInput(this, 0) // SHOW_IMPLICIT
                }
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            }?.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    dialog?.getButton(BUTTON_POSITIVE)?.performClick()
                    return@OnEditorActionListener true
                }
                false
            })

            btnVoice?.apply {
                val context = this.context
                if (!hasMicPermissions(context)) {
                    this.isEnabled = false
                    etSearch?.requestFocus()
                } else {
                    this.isEnabled = true
                }
                setOnClickListener {
                    val dots = view.findViewById<LinearLayout>(R.id.searchDots)
                    val progress = view.findViewById<SpeechProgressView>(R.id.progress)
                    val heights = intArrayOf(40, 56, 38, 55, 35)
                    progress.setBarMaxHeightsInDp(heights)
                    if (hasMicPermissions(context)) {
                        etSearch?.hint = context.getString(R.string.search_voice_hint)
                        btnVoice.visibility = View.GONE
                        dots?.visibility = View.VISIBLE
                    } else {
                        App.toast(R.string.search_requires_record_audio)
                        btnVoice.visibility = View.VISIBLE
                        dots?.visibility = View.GONE
                        etSearch?.hint = context.getString(R.string.search_is_empty)
                        etSearch?.requestFocus()
                    }
                    // start Speech
                    startSpeech(
                        getString(R.string.search_voice_hint),
                        progress
                    ) { result, final, success ->
                        etSearch?.hint = ""
                        etSearch?.setText(result)
                        if (final) {
                            btnVoice.visibility = View.VISIBLE
                            dots?.visibility = View.GONE
                        }
                        if (final && success) {
                            dialog?.getButton(BUTTON_POSITIVE)?.requestFocus() //.performClick()
                        }
                    }
                }
            }

            dialog = AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    dialog?.dismiss()
                    val query = etSearch.text.toString()
                    if (query.isNotEmpty()) {
                        runVoidJsFunc("window.voiceResult", "'" + query.replace("'", "\\'") + "'")
                    } else { // notify user
                        App.toast(R.string.search_is_empty)
                    }
                }
                .create()

            // top position (no keyboard overlap)
            val lp = dialog.window?.attributes
            lp?.apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                verticalMargin = 0.1F
            }
            // show fullscreen dialog
            dialog.window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            // set fullscreen mode (immersive sticky):
            @Suppress("DEPRECATION")
            var uiFlags: Int = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            if (VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                @Suppress("DEPRECATION")
                uiFlags = uiFlags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
            @Suppress("DEPRECATION")
            dialog.window?.decorView?.systemUiVisibility = uiFlags
            dialog.show()
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

            // run voice search
            btnVoice?.performClick()
        }
    }

    private fun startSpeech(
        msg: String,
        progress: SpeechProgressView,
        onSpeech: (result: String, final: Boolean, success: Boolean) -> Unit
    ): Boolean {
        val context = this.baseContext
        if (hasMicPermissions(context)) {
            try {
                // you must have android.permission.RECORD_AUDIO granted at this point
                Speech.init(context, packageName)
                    ?.startListening(progress, object : SpeechDelegate {
                        private var success = true
                        override fun onStartOfSpeech() {
                            Log.i("speech", "speech recognition is now active. $msg")
                        }

                        override fun onSpeechRmsChanged(value: Float) {
                            //Log.d("speech", "rms is now: $value")
                        }

                        override fun onSpeechPartialResults(results: List<String>) {
                            val str = StringBuilder()
                            for (res in results) {
                                str.append(res).append(" ")
                            }
                            Log.i("speech", "partial result: " + str.toString().trim { it <= ' ' })
                            onSpeech(str.toString().trim { it <= ' ' }, false, success)
                        }

                        override fun onSpeechResult(res: String) {
                            Log.i("speech", "result: $res")
                            if (res.isEmpty())
                                success = false
                            onSpeech(res, true, success)
                        }
                    })
                return true
            } catch (exc: SpeechRecognitionNotAvailable) {
                Log.e("speech", "Speech recognition is not available on this device!")
                App.toast(R.string.search_no_voice_recognizer)
                // You can prompt the user if he wants to install Google App to have
                // speech recognition, and then you can simply call:
                SpeechUtil.redirectUserToGoogleAppOnPlayStore(context)
                // to redirect the user to the Google App page on Play Store
            } catch (exc: GoogleVoiceTypingDisabledException) {
                Log.e("speech", "Google voice typing must be enabled!")
            }
        }
        return false
    }

    private fun showFab(show: Boolean = true) {
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        if (show && !this.isTvBox) {
            fab?.show()
            lifecycleScope.launch {
                delay(15000)
                fab?.hide()
            }
        } else
            fab?.hide()
    }

    private fun setupFab() { // FAB
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        fab?.apply {
            setImageDrawable(
                AppCompatResources.getDrawable(
                    this.context,
                    R.drawable.lampa_logo_round
                )
            )
            customSize = dp2px(this.context, 32f)
            setMaxImageSize(dp2px(this.context, 30f))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this.context,
                    R.color.lampa_background
                )
            )
            //setAlpha(0.5f)
            setOnClickListener {
                showMenuDialog()
                showFab(false)
            }
        }
        if (!isMenuVisible) {
            showFab(true)
        }
    }

    private fun runJsStorageChangeField(name: String) {
        runVoidJsFunc(
            "AndroidJS.StorageChange",
            "JSON.stringify({" +
                    "name: '${name}'," +
                    "value: Lampa.Storage.field('${name}')" +
                    "})"
        )
    }

    private fun runJsStorageChangeField(name: String, default: String) {
        runVoidJsFunc(
            "AndroidJS.StorageChange",
            "JSON.stringify({" +
                    "name: '${name}'," +
                    "value: Lampa.Storage.get('${name}', '$default')" +
                    "})"
        )
    }

    fun runVoidJsFunc(funcName: String, params: String) {
        if (browserInit && progressBar?.visibility == View.GONE) {
            val js = ("(function(){"
                    + "try {"
                    + funcName + "(" + params + ");"
                    + "return 'ok';"
                    + "} catch (e) {"
                    + "return 'error: ' + e.message;"
                    + "}"
                    + "})();")
            browser?.evaluateJavascript(
                js
            ) { r: String ->
                Log.i(
                    "runVoidJsFunc",
                    "$funcName($params) $r"
                )
            }
        } else {
            delayedVoidJsFunc.add(listOf(funcName, params))
        }
    }
}