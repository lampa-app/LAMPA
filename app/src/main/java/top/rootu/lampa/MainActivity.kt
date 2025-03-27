package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEUTRAL
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
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gotev.speech.GoogleVoiceTypingDisabledException
import net.gotev.speech.Logger
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.SpeechUtil
import net.gotev.speech.ui.SpeechProgressView
import org.json.JSONArray
import org.json.JSONException
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
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.Helpers.printLog
import top.rootu.lampa.helpers.PermHelpers
import top.rootu.lampa.helpers.PermHelpers.hasMicPermissions
import top.rootu.lampa.helpers.PermHelpers.isInstallPermissionDeclared
import top.rootu.lampa.helpers.PermHelpers.verifyMicPermissions
import top.rootu.lampa.helpers.Prefs
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.addUrlHistory
import top.rootu.lampa.helpers.Prefs.appBrowser
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.appPlayer
import top.rootu.lampa.helpers.Prefs.appPrefs
import top.rootu.lampa.helpers.Prefs.appUrl
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.helpers.Prefs.clearPending
import top.rootu.lampa.helpers.Prefs.contToRemove
import top.rootu.lampa.helpers.Prefs.defPrefs
import top.rootu.lampa.helpers.Prefs.firstRun
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.lampaSource
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.lookToRemove
import top.rootu.lampa.helpers.Prefs.migrate
import top.rootu.lampa.helpers.Prefs.playActivityJS
import top.rootu.lampa.helpers.Prefs.resumeJS
import top.rootu.lampa.helpers.Prefs.schdToRemove
import top.rootu.lampa.helpers.Prefs.thrwToRemove
import top.rootu.lampa.helpers.Prefs.tvPlayer
import top.rootu.lampa.helpers.Prefs.urlHistory
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.helpers.getAppVersion
import top.rootu.lampa.helpers.hideSystemUI
import top.rootu.lampa.helpers.isSafeForUse
import top.rootu.lampa.helpers.isTvBox
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.net.HttpHelper
import top.rootu.lampa.sched.Scheduler
import java.util.Locale


class MainActivity : BaseActivity(),
    Browser.Listener,
    XWalkInitializer.XWalkInitListener, MyXWalkUpdater.XWalkUpdateListener {
    // Local properties
    private var mXWalkUpdater: MyXWalkUpdater? = null
    private var mXWalkInitializer: XWalkInitializer? = null
    private var browser: Browser? = null
    private var browserInitComplete = false
    private var isMenuVisible = false
    private var isStorageListenerAdded = false
    private lateinit var loaderView: LottieAnimationView
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var playerStateManager: PlayerStateManager

    // Data class for menu items
    private data class MenuItem(
        val title: String,
        val action: String,
        val icon: Int
    )

    companion object {
        // Constants
        private const val TAG = "APP_MAIN"
        private const val RESULT_VIMU_ENDED = 2
        private const val RESULT_VIMU_START = 3
        private const val RESULT_VIMU_ERROR = 4
        const val VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE = 0.95
        const val JS_SUCCESS = "SUCCESS"
        const val JS_FAILURE = "FAILED"
        val PLAYERS_BLACKLIST = setOf(
            "com.android.gallery3d",
            "com.android.tv.frameworkpackagestubs",
            "com.google.android.tv.frameworkpackagestubs",
            "com.google.android.apps.photos",
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.pro",
            "com.ghisler.android.totalcommander",
            "com.instantbits.cast.webvideo",
            "com.lonelycatgames.xplore",
            "com.mitv.videoplayer",
            "com.mixplorer.silver",
            "com.opera.browser",
            "org.droidtv.contentexplorer",
            "pl.solidexplorer2",
            "nextapp.fx",
            // more to add...
        )
        // private const val IP4_DIG = "([01]?\\d?\\d|2[0-4]\\d|25[0-5])"
        // private const val IP4_REGEX = "(${IP4_DIG}\\.){3}${IP4_DIG}"
        // private const val IP6_DIG = "[0-9A-Fa-f]{1,4}"
        // private const val IP6_REGEX =
        //    "((${IP6_DIG}:){7}${IP6_DIG}|(${IP6_DIG}:){1,7}:|:(:${IP6_DIG}){1,7}|(${IP6_DIG}::?){1,6}${IP6_DIG})"
        // private const val URL_REGEX =
        //    "^https?://(\\[${IP6_REGEX}]|${IP4_REGEX}|([-A-Za-z\\d]+\\.)+[-A-Za-z]{2,})(:\\d+)?(/.*)?$"
        // private val URL_PATTERN = Pattern.compile(URL_REGEX)

        // Properties
        var LAMPA_URL: String = ""
        var SELECTED_PLAYER: String? = ""
        var SELECTED_BROWSER: String? =
            if (VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) "XWalk" else ""
        var delayedVoidJsFunc = mutableListOf<List<String>>()
        var playerTimeCode: String = "continue"
        var playerAutoNext: Boolean = true
        var internalTorrserve: Boolean = false
        var torrserverPreload: Boolean = false
        var lampaActivity: String = "{}" // JSON
        lateinit var urlAdapter: ArrayAdapter<String>
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LAMPA_URL = appUrl
        SELECTED_PLAYER = appPlayer
        printLog("onCreate SELECTED_BROWSER: $SELECTED_BROWSER LAMPA_URL: $LAMPA_URL SELECTED_PLAYER: $SELECTED_PLAYER")
        playerStateManager = PlayerStateManager(this)

        setupActivity()
        setupBrowser()
        setupUI()
        setupIntents()

        if (firstRun) {
            CoroutineScope(Dispatchers.IO).launch {
                printLog("First run scheduleUpdate(sync: true)")
                Scheduler.scheduleUpdate(true)
            }
        }
    }

    override fun onResume() {
        printLog("onResume()")
        super.onResume()
        hideSystemUI()
        if (!isTvBox) setupFab()
        // Try to initialize again when the user completed updating and
        // returned to current activity. The browser.onResume() will do nothing if
        // the initialization is proceeding or has already been completed.
        mXWalkInitializer?.initAsync()
        printLog("onResume() browserInitComplete $browserInitComplete isSafeForUse ${browser.isSafeForUse()}")
        if (browserInitComplete)
            browser?.resumeTimers()
        if (browser.isSafeForUse()) {
            printLog("onResume() run syncBookmarks()")
            syncBookmarks()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        printLog("onNewIntent() processIntent")
        setIntent(intent) // getIntent() should always return the most recent
        processIntent(intent)
    }

    override fun onPause() {
        if (browserInitComplete)
            browser?.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        if (browserInitComplete) {
            browser?.apply {
                // Destroy only if not already destroyed
                if (!isDestroyed) {
                    destroy()
                }
            }
        }
        try {
            Speech.getInstance()?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    // handle user pressed Home
    override fun onUserLeaveHint() {
        printLog("onUserLeaveHint()")
        if (browserInitComplete)
            browser?.apply {
                pauseTimers()
                clearCache(true)
            }
        super.onUserLeaveHint()
    }

    // handle configuration changes (language / screen orientation)
    override fun onConfigurationChanged(newConfig: Configuration) {
        printLog("onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
        hideSystemUI()
        showFab(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
            || keyCode == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU
        ) {
            printLog("Menu key pressed")
            showMenuDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            printLog("Back button long pressed")
            showMenuDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onBrowserInitCompleted() {
        browserInitComplete = true
        HttpHelper.userAgent = browser?.getUserAgentString() + " lampa_client"
        browser?.apply {
            setUserAgentString(HttpHelper.userAgent)
            setBackgroundColor(ContextCompat.getColor(baseContext, R.color.lampa_background))
            addJavascriptInterface(AndroidJS(this@MainActivity, this), "AndroidJS")
        }
        printLog("onBrowserInitCompleted LAMPA_URL: $LAMPA_URL")
        if (LAMPA_URL.isEmpty()) {
            printLog("onBrowserInitCompleted showUrlInputDialog")
            showUrlInputDialog()
        } else {
            printLog("onBrowserInitCompleted load $LAMPA_URL")
            browser?.loadUrl(LAMPA_URL)
        }
    }

    override fun onBrowserPageFinished(view: ViewGroup, url: String) {
        printLog("onBrowserPageFinished url: $url")
        // Restore Lampa settings and reload if migrate flag set
        if (migrate) {
            migrateSettings()
        }
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        }
        // Switch Loader (Note it control delayedVoidJsFunc)
        loaderView.visibility = View.GONE

        Log.d(TAG, "LAMPA onLoadFinished $url")
        // Dirty hack to skip reload from Back history
        if (url.trimEnd('/').equals(LAMPA_URL, true)) {
            // Lazy Load Intent
            processIntent(intent, 500) // 1000
            // Background update Android TV channels and Recommendations
            printLog("onBrowserPageFinished run syncBookmarks()")
            syncBookmarks()
            CoroutineScope(Dispatchers.IO).launch {
                Scheduler.scheduleUpdate(false)
            }
        }
        // Sync with Lampa localStorage
        lifecycleScope.launch {
            delay(3000)
            setupListener()
            syncStorage()
            changeTmdbUrls()
            // Create a copy for safe iteration
            val itemsToProcess = delayedVoidJsFunc.toList()
            delayedVoidJsFunc.clear() // Clear before processing
            for (item in itemsToProcess) {
                runVoidJsFunc(item[0], item[1])
            }
        }
    }

    private fun isAfterEndCreditsPosition(positionMillis: Long, duration: Long): Boolean {
        return duration > 0 && positionMillis >= duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
    }

    private fun setupActivity() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        enableEdgeToEdge()
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
                // browser?.goBack()
                // no Back with no focused webView workaround
                runVoidJsFunc("window.history.back", "")
            }
        }
    }

    private fun setupBrowser() {
        SELECTED_BROWSER = appBrowser
        if (!Helpers.isWebViewAvailable(this)
            || (SELECTED_BROWSER.isNullOrEmpty() && VERSION.SDK_INT < Build.VERSION_CODES.KITKAT)
        ) {
            SELECTED_BROWSER = "XWalk"
        }
        val wvvMajorVersion: Double = try {
            Helpers.getWebViewVersion(this).substringBefore(".").toDouble()
        } catch (_: NumberFormatException) {
            0.0
        }
        // Use WebView on RuStore builds and modern Androids by default
        if (Helpers.isWebViewAvailable(this)
            && SELECTED_BROWSER.isNullOrEmpty()
            && (BuildConfig.FLAVOR == "ruStore" || wvvMajorVersion > 53.589)
        ) {
            SELECTED_BROWSER = "SysView"
        }
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
            }

            "SysView" -> {
                useSystemWebView()
            }

            else -> {
                setContentView(R.layout.activity_empty)
                showBrowserInputDialog()
            }
        }
        // https://developer.android.com/develop/background-work/background-tasks/scheduling/wakelock
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun useCrossWalk() {
        setContentView(R.layout.activity_xwalk)
        loaderView = findViewById(R.id.loaderView)
        try {
            browser = XWalk(this, R.id.xWalkView)
            browser?.initialize()
        } catch (e: Exception) {
            Log.e("XWalk", "Init failed. Fallback to WebView.", e)
            useSystemWebView()
        }
    }

    private fun useSystemWebView() {
        setContentView(R.layout.activity_webview)
        loaderView = findViewById(R.id.loaderView)
        browser = SysView(this, R.id.webView)
        browser?.initialize()
    }

    private fun handleSpeechResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == RESULT_OK) {
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

    // Some external video player api specs:
    // vlc https://wiki.videolan.org/Android_Player_Intents/
    // justplayer https://github.com/moneytoo/Player/issues/203
    // mxplayer https://mx.j2inter.com/api
    // mpv http://mpv-android.github.io/mpv-android/intent.html
    // vimu https://www.vimu.tv/player-api
    private fun handlePlayerResult(result: androidx.activity.result.ActivityResult) {
        val data: Intent? = result.data
        val videoUrl: String = data?.data?.toString() ?: "null"
        val resultCode = result.resultCode

        if (BuildConfig.DEBUG) {
            logDebugInfo(data, resultCode, videoUrl)
        }

        data?.let { intent ->
            when (intent.action) {
                "com.mxtech.intent.result.VIEW" -> handleMxPlayerResult(
                    intent,
                    resultCode,
                    videoUrl
                )

                "org.videolan.vlc.player.result" -> handleVlcPlayerResult(
                    intent,
                    resultCode,
                    videoUrl
                )

                "is.xyz.mpv.MPVActivity.result" -> handleMpvPlayerResult(
                    intent,
                    resultCode,
                    videoUrl
                )

                "com.uapplication.uplayer.result", "com.uapplication.uplayer.beta.result" -> handleUPlayerResult(
                    intent,
                    resultCode,
                    videoUrl
                )

                "net.gtvbox.videoplayer.result", "net.gtvbox.vimuhd.result" -> handleViMuPlayerResult(
                    intent,
                    resultCode,
                    videoUrl
                )

                else -> handleGenericPlayerResult(intent, resultCode, videoUrl)
            }
        }
    }

    private fun logDebugInfo(data: Intent?, resultCode: Int, videoUrl: String) {
        printLog("Returned videoUrl: $videoUrl", TAG)
        when (resultCode) {
            RESULT_OK -> Log.d(TAG, "RESULT_OK: ${data?.toUri(0)}")
            RESULT_CANCELED -> Log.d(TAG, "RESULT_CANCELED: ${data?.toUri(0)}")
            RESULT_FIRST_USER -> Log.d(TAG, "RESULT_FIRST_USER: ${data?.toUri(0)}")
            RESULT_VIMU_ENDED -> Log.d(TAG, "RESULT_VIMU_ENDED: ${data?.toUri(0)}")
            RESULT_VIMU_START -> Log.d(TAG, "RESULT_VIMU_START: ${data?.toUri(0)}")
            RESULT_VIMU_ERROR -> Log.e(TAG, "RESULT_VIMU_ERROR: ${data?.toUri(0)}")
            else -> Log.w(TAG, "Undefined result code [$resultCode]: ${data?.toUri(0)}")
        }
    }

    private fun handleMxPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        when (resultCode) {
            RESULT_OK -> {
                when (intent.getStringExtra("end_by")) {
                    "playback_completion" -> {
                        Log.i(TAG, "Playback completed")
                        resultPlayer(videoUrl, 0, 0, true)
                    }

                    "user" -> {
                        val pos = intent.getIntExtra("position", 0)
                        val dur = intent.getIntExtra("duration", 0)
                        if (pos > 0 && dur > 0) {
                            val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                            Log.i(
                                TAG,
                                "Playback stopped [position=$pos, duration=$dur, ended:$ended]"
                            )
                            resultPlayer(videoUrl, pos, dur, ended)
                        } else {
                            Log.e(TAG, "Invalid state [position=$pos, duration=$dur]")
                        }
                    }

                    else -> Log.e(TAG, "Invalid state [endBy=${intent.getStringExtra("end_by")}]")
                }
            }

            RESULT_CANCELED -> Log.i(TAG, "Playback stopped by user")
            RESULT_FIRST_USER -> Log.e(TAG, "Playback stopped by unknown error")
            else -> Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun handleVlcPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getLongExtra("extra_position", 0L)
            val dur = intent.getLongExtra("extra_duration", 0L)
            val url =
                if (videoUrl.isEmpty() || videoUrl == "null") intent.getStringExtra("extra_uri")
                    ?: videoUrl else videoUrl
            when {
                pos > 0L && dur > 0L -> {
                    val ended = isAfterEndCreditsPosition(pos, dur)
                    Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended:$ended]")
                    resultPlayer(url, pos.toInt(), dur.toInt(), ended)
                }

                pos == 0L && dur == 0L -> {
                    Log.i(TAG, "Playback completed")
                    resultPlayer(url, 0, 0, true)
                }

                pos > 0L -> Log.i(TAG, "Playback stopped with no duration! Playback Error?")
            }
        } else {
            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun handleMpvPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getIntExtra("position", 0)
            val dur = intent.getIntExtra("duration", 0)
            when {
                dur > 0 -> {
                    val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                    Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended:$ended]")
                    resultPlayer(videoUrl, pos, dur, ended)
                }

                dur == 0 && pos == 0 -> {
                    Log.i(TAG, "Playback completed")
                    resultPlayer(videoUrl, 0, 0, true)
                }
            }
        } else {
            Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun handleUPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        when (resultCode) {
            RESULT_OK -> {
                val pos = intent.getLongExtra("position", 0L)
                val dur = intent.getLongExtra("duration", 0L)
                if (pos > 0L && dur > 0L) {
                    val ended =
                        intent.getBooleanExtra("isEnded", pos == dur) || isAfterEndCreditsPosition(
                            pos,
                            dur
                        )
                    Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended=$ended]")
                    resultPlayer(videoUrl, pos.toInt(), dur.toInt(), ended)
                }
            }

            RESULT_CANCELED -> Log.e(
                TAG,
                "Playback Error. It isn't possible to get the duration or create the playlist."
            )

            else -> Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun handleViMuPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        when (resultCode) {
            RESULT_FIRST_USER -> {
                Log.i(TAG, "Playback completed")
                resultPlayer(videoUrl, 0, 0, true)
            }

            RESULT_CANCELED, RESULT_VIMU_START, RESULT_VIMU_ENDED -> {
                val pos = intent.getIntExtra("position", 0)
                val dur = intent.getIntExtra("duration", 0)
                if (pos > 0 && dur > 0) {
                    val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                    Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended=$ended]")
                    resultPlayer(videoUrl, pos, dur, ended)
                }
            }

            RESULT_VIMU_ERROR -> Log.e(TAG, "Playback error")
            else -> Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun handleGenericPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        when (resultCode) {
            RESULT_FIRST_USER -> {
                Log.i(TAG, "Playback completed")
                resultPlayer(videoUrl, 0, 0, true)
            }

            RESULT_OK, RESULT_CANCELED, RESULT_VIMU_START, RESULT_VIMU_ENDED -> {
                val pos = intent.getIntExtra("position", 0)
                val dur = intent.getIntExtra("duration", 0)
                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended=$ended]")
                resultPlayer(videoUrl, pos, dur, ended)
            }

            else -> Log.e(TAG, "Invalid state [resultCode=$resultCode]")
        }
    }

    private fun setupIntents() {
        resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handlePlayerResult(result)
            }
        speechLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handleSpeechResult(result)
            }
    }

    private fun setupUI() {
        hideSystemUI() // Must be invoked after setContentView!
    }

    override fun onXWalkInitStarted() {
        printLog("onXWalkInitStarted()")
    }

    override fun onXWalkInitCancelled() {
        printLog("onXWalkInitCancelled()")
        // Perform error handling here
        finish()
    }

    override fun onXWalkInitFailed() {
        printLog("onXWalkInitFailed()")
        if (mXWalkUpdater == null) {
            mXWalkUpdater = MyXWalkUpdater(this, this)
        }
        setupXWalkApkUrl()
        mXWalkUpdater?.updateXWalkRuntime()
    }

    override fun onXWalkInitCompleted() {
        printLog("onXWalkInitCompleted() isXWalkReady: ${mXWalkInitializer?.isXWalkReady}")
        if (mXWalkInitializer?.isXWalkReady == true) {
            useCrossWalk()
        }
    }

    override fun onXWalkUpdateCancelled() {
        printLog("onXWalkUpdateCancelled()")
        // Perform error handling here
        finish()
    }

    private fun setupXWalkApkUrl() {
        val abi = MyXWalkEnvironment.getRuntimeAbi()
        val apkUrl = String.format(getString(R.string.xwalk_apk_link), abi)
        mXWalkUpdater!!.setXWalkApkUrl(apkUrl)
    }

    fun migrateSettings() {
        lifecycleScope.launch {
            restoreStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) {
                    Log.d(TAG, "migrateSettings - Lampa settings restored. Restart.")
                    recreate()
                } else {
                    App.toast(R.string.settings_rest_fail)
                }
            }
            this@MainActivity.migrate = false
        }
    }

    fun setupListener() {
        if (!isStorageListenerAdded)
            runVoidJsFunc(
                "Lampa.Storage.listener.add",
                "'change'," +
                        "function(o){AndroidJS.storageChange(JSON.stringify(o))}"
            )
        isStorageListenerAdded = true
    }

    fun syncStorage() {
        runJsStorageChangeField("activity", "{}") // get current lampaActivity
        runJsStorageChangeField("player_timecode")
        runJsStorageChangeField("playlist_next")
        runJsStorageChangeField("torrserver_preload")
        runJsStorageChangeField("internal_torrclient")
        runJsStorageChangeField("language") // apply language
        runJsStorageChangeField("source") // get current catalog
        runJsStorageChangeField("account_use") // get sync state
        runJsStorageChangeField("recomends_list", "[]") // force update recs
    }

    fun changeTmdbUrls() {
        lifecycleScope.launch {
            runVoidJsFunc(
                "AndroidJS.storageChange",
                "JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')})"
            )
            runVoidJsFunc(
                "AndroidJS.storageChange",
                "JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')})"
            )
        }
    }

    // Function to sync bookmarks (Required only for Android TV 8+)
    // runVoidJsFunc("Lampa.Favorite.$action", "'$catgoryName', {id: $id}")
    // runVoidJsFunc("Lampa.Favorite.add", "'wath', ${Gson().toJson(card)}") - FIXME: wrong string ID
    private fun syncBookmarks() {
        if (VERSION.SDK_INT < Build.VERSION_CODES.O || !(isAndroidTV)) return
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                runVoidJsFunc("Lampa.Favorite.init", "") // Initialize if no favorite
            }
            printLog("syncBookmarks() add to wath: ${App.context.wathToAdd}")
            App.context.wathToAdd.forEach { item ->
                val lampaCard = App.context.FAV?.card?.find { it.id == item.id } ?: item.card
                lampaCard?.let { card ->
                    card.fixCard()
                    val id = card.id?.toIntOrNull()
                    id?.let {
                        val params =
                            if (card.type == "tv") "name: '${card.name}'" else "title: '${card.title}'"
                        withContext(Dispatchers.Main) {
                            runVoidJsFunc(
                                "Lampa.Favorite.add",
                                "'${LampaProvider.LATE}', {id: $id, type: '${card.type}', source: '${card.source}', img: '${card.img}', $params}"
                            )
                        }
                    }
                }
                delay(500) // don't do it too fast
            }
            // Remove items from various categories
            listOf(
                LampaProvider.LATE to App.context.wathToRemove,
                LampaProvider.BOOK to App.context.bookToRemove,
                LampaProvider.LIKE to App.context.likeToRemove,
                LampaProvider.HIST to App.context.histToRemove,
                LampaProvider.LOOK to App.context.lookToRemove,
                LampaProvider.VIEW to App.context.viewToRemove,
                LampaProvider.SCHD to App.context.schdToRemove,
                LampaProvider.CONT to App.context.contToRemove,
                LampaProvider.THRW to App.context.thrwToRemove
            ).forEach { (category, items) ->
                printLog("syncBookmarks() remove from $category: $items")
                items.forEach { id ->
                    withContext(Dispatchers.Main) {
                        runVoidJsFunc("Lampa.Favorite.remove", "'$category', {id: $id}")
                    }
                    delay(500) // don't do it too fast
                }
            }
            // don't do it again
            App.context.clearPending()
        }
    }

    private fun dumpStorage(callback: (String) -> Unit) {
        val backupJavascript = """
            (function() {
                console.log('Backing up localStorage to App Prefs');
                try {
                    AndroidJS.clear();
                    let count = 0;
                    for (var key in localStorage) {
                        AndroidJS.set(key, localStorage.getItem(key));
                        count++;
                    }
                    return '${JS_SUCCESS}. ' + count + ' items backed up.';
                } catch (error) {
                    return '${JS_FAILURE}: ' + error.message;
                }
            })()
            """.trimIndent()
        browser?.evaluateJavascript(backupJavascript) { result ->
            if (result.contains(JS_SUCCESS, true)) {
                printLog("localStorage backed up. Result $result")
                callback(result) // Success
            } else {
                Log.e(TAG, "Failed to dump localStorage.")
                callback(result) // Failure
            }
        }
    }

    private fun restoreStorage(callback: (String) -> Unit) {
        val restoreJavascript = """
            (function() {
                console.log('Restoring localStorage from App Prefs');
                try {
                    AndroidJS.dump();
                    var len = AndroidJS.size();
                    for (i = 0; i < len; i++) {
                        var key = AndroidJS.key(i);
                        console.log('[' + key + ']');
                        localStorage.setItem(key, AndroidJS.get(key));
                    }
                    return '${JS_SUCCESS}. ' + len + ' items restored.';
                } catch (error) {
                    return '${JS_FAILURE}: ' + error.message;
                }
            })()
            """.trimIndent()
        browser?.evaluateJavascript(restoreJavascript) { result ->
            if (result.contains(JS_SUCCESS, true)) {
                printLog("localStorage restored. Result $result")
                callback(result) // Success
            } else {
                Log.e(TAG, "Failed to restore localStorage.")
                callback(result) // Failure
            }
        }
    }

    private fun clearStorage() {
        browser?.evaluateJavascript("localStorage.clear()") { Log.d(TAG, "localStorage cleared") }
    }

    private fun processIntent(intent: Intent?, delay: Long = 0) {
        // Log intent data for debugging
        logIntentData(intent)
        // Parse intent extras
        val sid = intent?.getStringExtra("id") ?: intent?.getIntExtra("id", -1)
            .toString() // Change to String
        val mediaType = intent?.getStringExtra("media") ?: ""
        val source = intent?.getStringExtra("source") ?: lampaSource.ifEmpty { "tmdb" }
        // Parse intent data
        intent?.data?.let { uri ->
            parseUriData(intent, uri, delay)
        }
        // Handle continue watch
        if (intent?.getBooleanExtra("continueWatch", false) == true) {
            handleContinueWatch(intent, delay)
        } else if (sid != "-1" && mediaType.isNotEmpty()) {
            // Handle opening a card
            handleOpenCard(intent, sid, mediaType, source, delay)
        }
        // Handle search command
        intent?.getStringExtra("cmd")?.let { cmd ->
            when (cmd) {
                "open_settings" -> showMenuDialog()
            }
        }
        // Fix initial focus
        browser?.setFocus()
    }

    // Helper function to log intent data
    @Suppress("DEPRECATION")
    private fun logIntentData(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return

        // Log basic intent info
        printLog("Intent URI: ${intent.toUri(0)}")

        // Log all extras
        intent.extras?.let { bundle ->
            val output = StringBuilder("Intent Extras:\n")

            bundle.keySet().forEach { key ->
                output.append("• $key = ${bundleValueToString(bundle.get(key))}\n")
            }

            printLog(output.toString())
        } ?: printLog("No extras found in intent")
    }

    /**
     * Safely converts bundle values to readable strings
     */
    private fun bundleValueToString(value: Any?): String {
        return when (value) {
            null -> "NULL"
            is String -> value
            is Int, is Long, is Float, is Double, is Boolean -> value.toString()
            is Parcelable -> "Parcelable(${value.javaClass.simpleName})"
            is Array<*> -> value.joinToString(
                prefix = "[",
                postfix = "]"
            ) { bundleValueToString(it) }

            is List<*> -> value.joinToString(
                prefix = "[",
                postfix = "]"
            ) { bundleValueToString(it) }

            is Bundle -> {
                @Suppress("DEPRECATION")
                val subItems =
                    value.keySet().joinToString { "$it=${bundleValueToString(value.get(it))}" }
                "Bundle{$subItems}"
            }

            else -> try {
                // Fallback for other types
                value.toString()
            } catch (_: Exception) {
                "Unprintable(${value.javaClass.simpleName ?: "null"})"
            }
        }
    }

    // Helper function to handle URI data
    private fun parseUriData(
        intent: Intent,
        uri: Uri,
        delay: Long = 0L
    ) {
        if (uri.host?.contains("themoviedb.org") == true && uri.pathSegments.size >= 2) {
            val videoType = uri.pathSegments[0]
            val sid = "\\d+".toRegex().find(uri.pathSegments[1])?.value // Keep as String
            if (videoType in listOf(
                    "movie",
                    "tv"
                ) && sid?.toIntOrNull() != null
            ) { // Change comparison to String
                handleTmdbIntent(intent, videoType, sid, delay) // Pass id as String
            }
        }
        when (intent.action) {
            "GLOBALSEARCH" -> handleGlobalSearch(intent, uri, delay)
            else -> handleChannelIntent(uri, delay)
        }
    }

    // Helper function to handle TMDB intents
    private fun handleTmdbIntent(
        intent: Intent,
        videoType: String,
        sid: String,
        delay: Long = 0
    ) { // Change id to String
        val source = intent.getStringExtra("source") ?: "tmdb"
        val card = "{id: '$sid', source: '$source'}" // Use String id in JSON
        lifecycleScope.launch {
            openLampaContent(
                "{id: '$sid', method: '$videoType', source: '$source', component: 'full', card: $card}",
                delay
            )
        }
    }

    // Helper function to handle global search
    // content://top.rootu.lampa.atvsearch/video/508883#Intent;action=GLOBALSEARCH
    private fun handleGlobalSearch(intent: Intent, uri: Uri, delay: Long = 0) {
        val sid = uri.lastPathSegment // Keep as String
        val videoType = intent.extras?.getString(SearchManager.EXTRA_DATA_KEY) ?: ""
        // Handle global search case
        if (videoType in listOf("movie", "tv") && sid?.toIntOrNull() != null)
            handleTmdbIntent(intent, videoType, sid, delay) // Pass id as String
    }

    // Helper function to handle channel intents
    private fun handleChannelIntent(uri: Uri, delay: Long = 0) {
        if (uri.encodedPath?.contains("update_channel") == true) {
            val channel = uri.encodedPath?.substringAfterLast("/") ?: ""
            val params = when (channel) {
                LampaProvider.RECS -> {
                    "{" +
                            "title: '${getString(R.string.title_main)} - ${
                                lampaSource.uppercase(
                                    Locale.getDefault()
                                )
                            }'," +
                            "component: 'main'," +
                            "source: '$lampaSource'," +
                            "url: ''" +
                            "}"
                }

                LampaProvider.LIKE, LampaProvider.BOOK, LampaProvider.HIST -> {
                    "{" +
                            "title: '${getChannelDisplayName(channel)}'," +
                            "component: '${if (channel == "book") "bookmarks" else "favorite"}'," +
                            "type: '$channel'," +
                            "url: ''," +
                            "page: 1" +
                            "}"
                }

                else -> ""
            }

            if (params.isNotEmpty()) {
                lifecycleScope.launch {
                    openLampaContent(params, delay)
                }
            }
        }
    }

    // Helper function to handle continue watch
    private fun handleContinueWatch(intent: Intent, delay: Long = 0) {
        playActivityJS?.let { json ->
            if (isValidJson(json)) {
                lifecycleScope.launch {
                    openLampaContent(json, delay)
                    delay(delay)
                    if (intent.getBooleanExtra("android.intent.extra.START_PLAYBACK", false)) {
                        resumeJS?.let { JSONObject(it) }?.let { runPlayer(it) }
                    }
                }
            }
        }
    }

    // Helper function to handle opening a card
    private fun handleOpenCard(
        intent: Intent?,
        sid: String,
        mediaType: String,
        source: String,
        delay: Long = 0
    ) { // Change intID to String
        val card = intent?.getStringExtra("LampaCardJS")
            ?: "{id: '$sid', source: '$source'}" // Use String id in JSON
        printLog("handleOpenCard sid: $sid mediaType: $mediaType source: $source")
        lifecycleScope.launch {
            openLampaContent(
                "{id: '$sid', method: '$mediaType', source: '$source', component: 'full', card: $card}",
                delay
            )
        }
    }

    private suspend fun openLampaContent(json: String, delay: Long = 0) {
        runVoidJsFunc("window.start_deep_link = ", json)
        delay(delay)
        runVoidJsFunc("Lampa.Controller.toContent", "")
        runVoidJsFunc("Lampa.Activity.push", json)
    }

    private fun showMenuDialog() {
        val mainActivity = this
        val dialogBuilder = AlertDialog.Builder(mainActivity)

        // Define menu items
        val menuItems = mutableListOf(
            MenuItem(
                title = if (isAndroidTV && VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getString(R.string.update_chan_title)
                } else if (isAndroidTV) {
                    getString(R.string.update_home_title)
                } else {
                    getString(R.string.close_menu_title)
                },
                action = "updateOrClose",
                icon = if (isAndroidTV) R.drawable.round_refresh_24 else R.drawable.round_close_24
            ),
            MenuItem(
                title = getString(R.string.change_url_title),
                action = "showUrlInputDialog",
                icon = R.drawable.round_link_24
            ),
            MenuItem(
                title = getString(R.string.change_engine),
                action = "showBrowserInputDialog",
                icon = R.drawable.round_explorer_24
            ),
            MenuItem(
                title = getString(R.string.backup_restore_title),
                action = "showBackupDialog",
                icon = R.drawable.round_settings_backup_restore_24
            ),
            MenuItem(
                title = getString(R.string.exit),
                action = "appExit",
                icon = R.drawable.round_exit_to_app_24
            )
        )

        // Hide CrossWalk switcher on RuStore builds
        if (!isInstallPermissionDeclared(this))
            menuItems.removeAt(2)

        // Set up the adapter
        val adapter = ImgArrayAdapter(
            mainActivity,
            menuItems.map { it.title }.toList(),
            menuItems.map { it.icon }.toList()
        )

        // Configure the dialog
        dialogBuilder.setTitle(getString(R.string.menu_title))
        dialogBuilder.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            when (menuItems[which].action) {
                "updateOrClose" -> {
                    if (isAndroidTV) {
                        Scheduler.scheduleUpdate(false)
                    }
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

        // Set dismiss listener
        dialogBuilder.setOnDismissListener {
            isMenuVisible = false
            showFab(true)
        }

        // Show the dialog
        showFullScreenDialog(dialogBuilder.create())
        isMenuVisible = true
    }

    // Function to handle backup all settings
    private fun backupAllSettings() {
        lifecycleScope.launch {
            dumpStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) { // .trim().removeSurrounding("\"")
                    // Proceed with saving settings if the backup was successful
                    if (saveSettings(Prefs.APP_PREFERENCES) && saveSettings(Prefs.STORAGE_PREFERENCES)) {
                        App.toast(getString(R.string.settings_saved_toast, Backup.DIR.toString()))
                    } else {
                        App.toast(R.string.settings_save_fail)
                    }
                } else {
                    App.toast(R.string.settings_save_fail)
                }
            }
        }
    }

    // Function to handle restore app settings
    private fun restoreAppSettings() {
        if (loadFromBackup(Prefs.APP_PREFERENCES)) {
            App.toast(R.string.settings_restored)
            recreate()
        } else {
            App.toast(R.string.settings_rest_fail)
        }
    }

    // Function to handle restore Lampa settings
    private fun restoreLampaSettings() {
        lifecycleScope.launch {
            if (loadFromBackup(Prefs.STORAGE_PREFERENCES)) {
                restoreStorage { callback ->
                    if (callback.contains(JS_SUCCESS, true)) {
                        App.toast(R.string.settings_restored)
                        recreate()
                    } else {
                        App.toast(R.string.settings_rest_fail)
                    }
                }
            } else {
                App.toast(R.string.settings_rest_fail)
            }
        }
    }

    // Function to handle restore default settings
    private fun restoreDefaultSettings() {
        clearStorage()
        appPrefs.edit().clear().apply()
        defPrefs.edit().clear().apply()
        lastPlayedPrefs.edit().clear().apply()
        // clearUrlHistory()
        recreate()
    }

    private fun showBackupDialog() {
        val mainActivity = this
        val dialogBuilder = AlertDialog.Builder(mainActivity)

        // Define menu items
        val menuItems = listOf(
            MenuItem(
                title = getString(R.string.backup_all_title),
                action = "backupAllSettings",
                icon = R.drawable.round_settings_backup_restore_24
            ),
            MenuItem(
                title = getString(R.string.restore_app_title),
                action = "restoreAppSettings",
                icon = R.drawable.round_refresh_24
            ),
            MenuItem(
                title = getString(R.string.restore_storage_title),
                action = "restoreLampaSettings",
                icon = R.drawable.round_refresh_24
            ),
            MenuItem(
                title = getString(R.string.default_setting_title),
                action = "restoreDefaultSettings",
                icon = R.drawable.round_close_24
            )
        )

        // Set up the adapter
        val adapter = ImgArrayAdapter(
            mainActivity,
            menuItems.map { it.title }.toList(),
            menuItems.map { it.icon }.toList()
        )

        // Configure the dialog
        dialogBuilder.setTitle(getString(R.string.backup_restore_title))
        dialogBuilder.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            when (menuItems[which].action) {
                "backupAllSettings" -> backupAllSettings()
                "restoreAppSettings" -> restoreAppSettings()
                "restoreLampaSettings" -> restoreLampaSettings()
                "restoreDefaultSettings" -> restoreDefaultSettings()
            }
        }

        // Show the dialog
        showFullScreenDialog(dialogBuilder.create())
        // Set active row
        adapter.setSelectedItem(0)

        // Check storage permissions
        if (!PermHelpers.hasStoragePermissions(this)) {
            PermHelpers.verifyStoragePermissions(this)
        }
    }

    private fun showBrowserInputDialog() {
        val mainActivity = this
        val dialogBuilder = AlertDialog.Builder(mainActivity)
        val xWalkVersion = "53.589.4"
        var selectedIndex = 0

        // Determine available browser options
        val (menuItemsTitle, menuItemsAction, icons) = if (Helpers.isWebViewAvailable(this)) {
            val webViewVersion = Helpers.getWebViewVersion(mainActivity)
            val webViewMajorVersion = try {
                webViewVersion.substringBefore(".").toDouble()
            } catch (_: NumberFormatException) {
                0.0
            }

            val isCrosswalkActive = SELECTED_BROWSER == "XWalk"

            val crosswalkTitle = if (isCrosswalkActive) {
                "${getString(R.string.engine_crosswalk)} - ${getString(R.string.engine_active)} $xWalkVersion"
            } else {
                if (webViewMajorVersion > 53.589) "${getString(R.string.engine_crosswalk_obsolete)} $xWalkVersion"
                else "${getString(R.string.engine_crosswalk)} $xWalkVersion"
            }

            val webkitTitle = if (isCrosswalkActive) {
                "${getString(R.string.engine_webkit)} $webViewVersion"
            } else {
                "${getString(R.string.engine_webkit)} - ${getString(R.string.engine_active)} $webViewVersion"
            }

            val titles = listOf(crosswalkTitle, webkitTitle)
            val actions = listOf("XWalk", "SysView")
            val icons = listOf(R.drawable.round_explorer_24, R.drawable.round_explorer_24)
            selectedIndex = if (isCrosswalkActive) 0 else 1

            Triple(titles, actions, icons)
        } else { // No WebView
            val crosswalkTitle = if (SELECTED_BROWSER == "XWalk") {
                "${getString(R.string.engine_crosswalk)} - ${getString(R.string.engine_active)} $xWalkVersion"
            } else {
                "${getString(R.string.engine_crosswalk)} $xWalkVersion"
            }

            val titles = listOf(crosswalkTitle)
            val actions = listOf("XWalk")
            val icons = listOf(R.drawable.round_explorer_24)

            Triple(titles, actions, icons)
        }

        // Set up the adapter
        val adapter = ImgArrayAdapter(mainActivity, menuItemsTitle, icons)
        adapter.setSelectedItem(selectedIndex)

        // Configure the dialog
        dialogBuilder.setTitle(getString(R.string.change_engine_title))
        dialogBuilder.setAdapter(adapter) { dialog, which ->
            dialog.dismiss()
            if (menuItemsAction[which] != SELECTED_BROWSER) {
                appBrowser = menuItemsAction[which]
                mainActivity.recreate()
            }
        }

        // Show the dialog
        showFullScreenDialog(dialogBuilder.create())
    }

    private class UrlAdapter(context: Context) :
        ArrayAdapter<String>(
            context,
            R.layout.lampa_dropdown_item, // Custom dropdown layout
            android.R.id.text1, // ID of the TextView in the custom layout
            context.urlHistory.toMutableList() // Load URL history
        )

    fun showUrlInputDialog(msg: String = "") {
        val mainActivity = this
        urlAdapter = UrlAdapter(mainActivity)
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var dialog: AlertDialog? = null

        @SuppressLint("InflateParams")
        // Inflate the dialog view
        val view = layoutInflater.inflate(R.layout.dialog_input_url, null, false)
        val tilt = view.findViewById<TextInputLayout>(R.id.tiltLampaUrl)
        val input = view.findViewById<AutoCompleteTV>(R.id.etLampaUrl)

        // Build the dialog
        val builder = AlertDialog.Builder(mainActivity).apply {
            setTitle(R.string.input_url_title)
            setView(view)
            setPositiveButton(R.string.save) { _, _ -> handleSaveButtonClick(input) }
            setNegativeButton(R.string.cancel) { di, _ -> handleCancelButtonClick(di) }
            setNeutralButton(R.string.migrate) { _, _ -> } // Override later
        }

        // Show the dialog
        dialog = builder.create()

        // Set up the input field
        setupInputField(input, tilt, msg, dialog, inputManager)

        showFullScreenDialog(dialog)

        // Set up the migrate button
        dialog.getButton(BUTTON_NEUTRAL).setOnClickListener {
            handleMigrateButtonClick(dialog)
        }

        // Automatically show the keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    // Helper function to set up the input field
    private fun setupInputField(
        input: AutoCompleteTV?,
        tilt: TextInputLayout?,
        msg: String,
        dialog: AlertDialog?,
        inputManager: InputMethodManager
    ) {
        input?.apply {
            setText(LAMPA_URL.ifEmpty { "http://lampa.mx" })
            if (msg.isNotEmpty()) {
                tilt?.isErrorEnabled = true
                tilt?.error = msg
            }
            setAdapter(urlAdapter)

            if (VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && !isPopupShowing) {
                        showDropDown()
                        dialog?.getButton(BUTTON_NEUTRAL)?.visibility = View.INVISIBLE
                    } else {
                        dismissDropDown()
                        dialog?.getButton(BUTTON_NEUTRAL)?.visibility = View.VISIBLE
                    }
                }
                setOnItemClickListener { _, _, _, _ ->
                    dialog?.getButton(BUTTON_NEUTRAL)?.visibility = View.VISIBLE
                    dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                }
                setOnClickListener {
                    dismissDropDown()
                    dialog?.getButton(BUTTON_NEUTRAL)?.visibility = View.VISIBLE
                    inputManager.showSoftInput(this, 0)
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
                        inputManager.hideSoftInputFromWindow(rootView.windowToken, 0)
                        dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    // Helper function to handle the save button click
    private fun handleSaveButtonClick(input: AutoCompleteTV?) {
        LAMPA_URL = input?.text.toString()
        if (isValidUrl(LAMPA_URL)) {
            Log.d(TAG, "URL '$LAMPA_URL' is valid")
            if (appUrl != LAMPA_URL) {
                appUrl = LAMPA_URL
                addUrlHistory(LAMPA_URL)
                browser?.loadUrl(LAMPA_URL)
                App.toast(R.string.change_url_press_back)
            } else {
                browser?.loadUrl(LAMPA_URL) // Reload current URL
            }
        } else {
            Log.d(TAG, "URL '$LAMPA_URL' is invalid")
            App.toast(R.string.invalid_url)
            showUrlInputDialog()
        }
        // hideSystemUI()
    }

    // Helper function to handle the cancel button click
    private fun handleCancelButtonClick(dialog: DialogInterface) {
        dialog.cancel()
        if (LAMPA_URL.isEmpty() && appUrl.isEmpty()) {
            appExit()
        } else {
            LAMPA_URL = appUrl
            // hideSystemUI()
        }
    }

    private fun addProgressIndicatorToDialog(dialog: AlertDialog) {
        // Access the dialog's root view
        val rootView = dialog.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
        // Create a LinearProgressIndicator
        progressIndicator = LinearProgressIndicator(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isIndeterminate = true // Set to indeterminate mode
            trackCornerRadius = dp2px(dialog.context, 2.0F)
            trackThickness = dp2px(dialog.context, 4.0F)
            visibility = LinearProgressIndicator.VISIBLE // Make it visible by default
        }
        // Add the progress indicator to the dialog's layout
        if (rootView is LinearLayout) {
            // rootView.addView(progressIndicator) // Add at the bottom
            rootView.addView(progressIndicator, 0) // Add at the top of the layout
        } else {
            // If the root view is not a LinearLayout, wrap it in a new LinearLayout
            val newLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            // Remove the rootView from its current parent (if it has one)
            (rootView?.parent as? ViewGroup)?.removeView(rootView)
            // Add the progress indicator and the existing rootView to the new layout
            newLayout.addView(progressIndicator) // Add to top
            newLayout.addView(rootView)
            // Set the new layout as the dialog's content view
            dialog.window?.setContentView(newLayout)
        }
    }

    // Function to control the visibility of the progress indicator
    private fun setProgressIndicatorVisibility(isVisible: Boolean) {
        if (::progressIndicator.isInitialized) {
            progressIndicator.visibility =
                if (isVisible) LinearProgressIndicator.VISIBLE else LinearProgressIndicator.GONE
        }
    }

    private fun handleMigrateButtonClick(dialog: AlertDialog) {
        // Add a LinearProgressIndicator to the dialog
        addProgressIndicatorToDialog(dialog)
        // Show the migrate progress
        setProgressIndicatorVisibility(true)
        lifecycleScope.launch {
            dumpStorage { callback ->
                setProgressIndicatorVisibility(false) // Hide the progress indicator
                if (callback.contains(JS_SUCCESS, true)) { // .trim().removeSurrounding("\"")
                    Log.d(TAG, "handleMigrateButtonClick: dumpStorage completed - $callback")
                    this@MainActivity.migrate = true
                    val input = dialog.findViewById<AutoCompleteTV>(R.id.etLampaUrl)
                    handleSaveButtonClick(input)
                    dialog.dismiss()
                } else {
                    Log.e(TAG, "handleMigrateButtonClick: dumpStorage failed - $callback")
                    // try use backup on Error
                    if (loadFromBackup(Prefs.STORAGE_PREFERENCES)) {
                        Log.d(TAG, "handleMigrateButtonClick: do migrate from backup")
                        this@MainActivity.migrate = true
                        val input = dialog.findViewById<AutoCompleteTV>(R.id.etLampaUrl)
                        handleSaveButtonClick(input)
                        dialog.dismiss()
                    } else App.toast(R.string.settings_migrate_fail)
                }
            }
        }
    }

    // Helper function to validate URLs
    private fun isValidUrl(url: String): Boolean {
        return Patterns.WEB_URL.matcher(url).matches()
    }

    fun appExit() {
        browser?.apply {
            clearCache(true)
            destroy()
        }
        finishAffinity() // exitProcess(1)
    }

    fun setPlayerPackage(packageName: String, isIPTV: Boolean) {
        SELECTED_PLAYER = packageName.lowercase(Locale.getDefault())
        if (isIPTV)
            tvPlayer = SELECTED_PLAYER!!
        else
            appPlayer = SELECTED_PLAYER!!
    }

//    private fun saveLastPlayed() {
//        val editor = lastPlayedPrefs.edit()
//        editor?.apply {
//            putInt("playIndex", playIndex)
//            putString("playVideoUrl", playVideoUrl)
//            putString("playJSONArray", playJSONArray.toString())
//            apply()
//        }
//        Log.d(TAG, "saveLastPlayed $playJSONArray")
//        // store to prefs for resume from WatchNext
//        playActivityJS = lampaActivity
//        Log.d(TAG, "saveLastPlayed() set playActivityJS to $playActivityJS")
//    }

    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject) {
        runPlayer(jsonObject, "")
    }

//    @SuppressLint("InflateParams")
//    fun runPlayerOld(jsonObject: JSONObject, launchPlayer: String) {
//
//        val videoUrl = jsonObject.optString("url")
//        val isIPTV = jsonObject.optBoolean("iptv", false)
//        val isLIVE = jsonObject.optBoolean("need_check_live_stream", false)
//        SELECTED_PLAYER =
//            launchPlayer.ifEmpty { if (isIPTV || isLIVE) tvPlayer else appPlayer }
//        val intent = Intent(Intent.ACTION_VIEW)
//        intent.setDataAndTypeAndNormalize(
//            Uri.parse(videoUrl),
//            if (videoUrl.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/*"
//        )
//        val resInfo =
//            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
//
//        val filteredList: MutableList<ResolveInfo> = mutableListOf()
//        for (info in resInfo) {
//            if (PLAYERS_BLACKLIST.contains(info.activityInfo.packageName.lowercase(Locale.getDefault()))) {
//                continue
//            }
//            filteredList.add(info)
//        }
//        if (filteredList.isEmpty()) {
//            App.toast(R.string.no_player_activity_found, true)
//            return
//        }
//        var playerPackageExist = false
//        if (!SELECTED_PLAYER.isNullOrEmpty()) {
//            for (info in filteredList) {
//                if (info.activityInfo.packageName.lowercase(Locale.getDefault()) == SELECTED_PLAYER) {
//                    playerPackageExist = true
//                    break
//                }
//            }
//        }
//        if (!playerPackageExist || SELECTED_PLAYER.isNullOrEmpty()) {
//            val mainActivity = this
//            val listAdapter = AppListAdapter(mainActivity, filteredList)
//            val playerChooser = AlertDialog.Builder(mainActivity)
//            val appTitleView =
//                LayoutInflater.from(mainActivity).inflate(R.layout.app_list_title, null)
//            val switch = appTitleView.findViewById<SwitchCompat>(R.id.useDefault)
//            playerChooser.setCustomTitle(appTitleView)
//
//            playerChooser.setAdapter(listAdapter) { dialog, which ->
//                val setDefaultPlayer = switch.isChecked
//                SELECTED_PLAYER = listAdapter.getItemPackage(which)
//                if (setDefaultPlayer) setPlayerPackage(SELECTED_PLAYER.toString(), isIPTV)
//                dialog.dismiss()
//                runPlayer(jsonObject, SELECTED_PLAYER!!)
//            }
//            val playerChooserDialog = playerChooser.create()
//            showFullScreenDialog(playerChooserDialog)
//            playerChooserDialog.listView.requestFocus()
//        } else {
//            var videoPosition: Long = 0
//            var videoDuration: Long = 0
//            val videoTitle =
//                if (jsonObject.has("title"))
//                    jsonObject.optString("title")
//                else if (isIPTV) "LAMPA TV" else "LAMPA video"
//            val listTitles = ArrayList<String>()
//            val listUrls = ArrayList<String>()
//            val subsTitles = ArrayList<String>()
//            val subsUrls = ArrayList<String>()
//            val headers = ArrayList<String>()
//            playIndex = -1
//
//            if (playerTimeCode == "continue" && jsonObject.has("timeline")) {
//                val timeline = jsonObject.optJSONObject("timeline")
//                if (timeline?.has("time") == true)
//                    videoPosition = (timeline.optDouble("time", 0.0) * 1000).toLong()
//                if (timeline?.has("duration") == true)
//                    videoDuration = (timeline.optDouble("duration", 0.0) * 1000).toLong()
//            }
//            // Headers
//            var ua = HttpHelper.userAgent
//            if (jsonObject.has("headers")) {
//                val headersJSON = jsonObject.optJSONObject("headers")
//                if (headersJSON != null) {
//                    val keys = headersJSON.keys()
//                    while (keys.hasNext()) {
//                        val key = keys.next()
//                        val value = headersJSON.optString(key)
//                        when (key.lowercase(Locale.getDefault())) {
//                            "user-agent" -> ua = value
//                            "content-length" -> {}
//                            else -> {
//                                headers.add(key)
//                                headers.add(value)
//                            }
//                        }
//                    }
//                }
//            }
//            headers.add("User-Agent")
//            headers.add(ua)
//            // Playlist
//            if (jsonObject.has("playlist") && playerAutoNext) {
//                playJSONArray = jsonObject.getJSONArray("playlist")
//                val badLinkPattern = "(/stream/.*?\\?link=.*?&index=\\d+)&preload\$".toRegex()
//                for (i in 0 until playJSONArray.length()) {
//                    val io = playJSONArray.getJSONObject(i)
//                    if (io.has("url")) {
//                        val url = if (torrserverPreload && internalTorrserve)
//                            io.optString("url").replace(badLinkPattern, "$1&play")
//                        else
//                            io.optString("url")
//                        if (url != io.optString("url")) {
//                            io.put("url", url)
//                            playJSONArray.put(i, io)
//                        }
//                        if (url == videoUrl)
//                            playIndex = i
//                        listUrls.add(io.optString("url"))
//                        listTitles.add(
//                            if (io.has("title")) io.optString("title") else (i + 1).toString()
//                        )
//                    }
//                }
//            }
//            // Subtitles
//            if (jsonObject.has("subtitles")) {
//                val subsJSONArray = jsonObject.optJSONArray("subtitles")
//                if (subsJSONArray != null)
//                    for (i in 0 until subsJSONArray.length()) {
//                        val io = subsJSONArray.getJSONObject(i)
//                        if (io.has("url")) {
//                            subsUrls.add(io.optString("url"))
//                            subsTitles.add(io.optString("label", "Sub " + (i + 1).toString()))
//                        }
//                    }
//            }
//            if (playIndex < 0) {
//                // current url not found in playlist or playlist missing
//                playIndex = 0
//                playJSONArray = JSONArray()
//                playJSONArray.put(jsonObject)
//            }
//            playVideoUrl = videoUrl
//
//            saveLastPlayed()
//
//            when (SELECTED_PLAYER) {
//                "com.uapplication.uplayer", "com.uapplication.uplayer.beta" -> {
//                    intent.setPackage(SELECTED_PLAYER)
//                    intent.putExtra("title", videoTitle)
//
//                    if (playerTimeCode == "continue" || playerTimeCode == "again")
//                        intent.putExtra("resume", videoPosition)
//
//                    val haveQuality = if (jsonObject.has("quality")) {
//                        var qualityListSize = 0
//                        val keys = (jsonObject["quality"] as JSONObject).keys()
//                        while (keys.hasNext() && qualityListSize < 2) {
//                            keys.next()
//                            qualityListSize++
//                        }
//                        qualityListSize > 1
//                    } else {
//                        false
//                    }
//
//                    if (listUrls.size > 1 || haveQuality) {
//                        val playObj = playJSONArray[0] as JSONObject
//                        if (playObj.has("timeline")) {
//                            val firstHash =
//                                (playObj["timeline"] as JSONObject).optString("hash", "0")
//                            if (firstHash != "0") {
//                                intent.putExtra("playlistTitle", firstHash)
//                            }
//                        }
//
//                        if (listTitles.isNotEmpty()) {
//                            intent.putStringArrayListExtra("titleList", listTitles)
//                        } else {
//                            intent.putStringArrayListExtra("titleList", arrayListOf(videoTitle))
//                        }
//                        intent.putExtra("playlistPosition", playIndex)
//
//                        if (haveQuality) {
//                            var qualitySet = ""
//                            val qualityMap = LinkedHashMap<String, ArrayList<String>>()
//                            for (i in 0 until playJSONArray.length()) {
//                                // val itemQualityMap = (playJSONArray[i] as JSONObject)["quality"] as JSONObject
//                                val itemQualityMap =
//                                    (playJSONArray[i] as JSONObject).optJSONObject("quality")
//                                itemQualityMap?.let {
//                                    val keys = itemQualityMap.keys()
//                                    while (keys.hasNext()) {
//                                        val key = keys.next()
//                                        val value = itemQualityMap.getString(key)
//                                        if (value == videoUrl) qualitySet = key
//                                        if (qualityMap.contains(key).not()) {
//                                            qualityMap[key] = arrayListOf()
//                                        }
//                                        qualityMap.getValue(key).add(value)
//                                    }
//                                }
//                            }
//                            val qualityKeys = ArrayList(qualityMap.keys.toList())
//                            val qualityIndex = qualityKeys.indexOf(qualitySet)
//                            intent.putStringArrayListExtra("videoGroupList", qualityKeys)
//                            qualityKeys.forEach {
//                                intent.putStringArrayListExtra(it, qualityMap.getValue(it))
//                            }
//                            intent.putExtra(
//                                "groupPosition",
//                                if (qualityIndex < 1) 0 else qualityIndex
//                            )
//                        } else {
//                            if (listUrls.isNotEmpty()) {
//                                intent.putStringArrayListExtra("videoList", listUrls)
//                            } else {
//                                intent.putStringArrayListExtra(
//                                    "videoList",
//                                    arrayListOf(videoUrl)
//                                )
//                            }
//                        }
//                    }
//                }
//
//                "com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.beta" -> {
//                    //intent.setPackage(SELECTED_PLAYER)
//                    intent.component = ComponentName(
//                        SELECTED_PLAYER!!,
//                        "$SELECTED_PLAYER.ActivityScreen"
//                    )
//                    intent.putExtra("title", videoTitle)
//                    intent.putExtra("sticky", false)
//                    intent.putExtra("headers", headers.toTypedArray())
//                    if (playerTimeCode == "continue" && videoPosition > 0L) {
//                        intent.putExtra("position", videoPosition.toInt())
//                    } else if (playerTimeCode == "again"
//                        || (playerTimeCode == "continue" && videoPosition == 0L)
//                    ) {
//                        intent.putExtra("position", 1)
//                    }
//                    if (listUrls.size > 1) {
//                        val parcelableVideoArr = arrayOfNulls<Parcelable>(listUrls.size)
//                        for (i in 0 until listUrls.size) {
//                            parcelableVideoArr[i] = Uri.parse(listUrls[i])
//                        }
//                        intent.putExtra("video_list", parcelableVideoArr)
//                        intent.putExtra("video_list.name", listTitles.toTypedArray())
//                        intent.putExtra("video_list_is_explicit", true)
//                    }
//                    if (subsUrls.isNotEmpty()) {
//                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
//                        for (i in 0 until subsUrls.size) {
//                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
//                        }
//                        intent.putExtra("subs", parcelableSubsArr)
//                        intent.putExtra("subs.name", subsTitles.toTypedArray())
//                    }
//                    intent.putExtra("return_result", true)
//                }
//
//                "is.xyz.mpv" -> {
//                    // http://mpv-android.github.io/mpv-android/intent.html
//                    intent.setPackage(SELECTED_PLAYER)
//                    if (subsUrls.isNotEmpty()) {
//                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
//                        for (i in 0 until subsUrls.size) {
//                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
//                        }
//                        intent.putExtra("subs", parcelableSubsArr)
//                    }
//                    if (playerTimeCode == "continue" && videoPosition > 0L) {
//                        intent.putExtra("position", videoPosition.toInt())
//                    } else if (playerTimeCode == "again"
//                        || (playerTimeCode == "continue" && videoPosition == 0L)
//                    ) {
//                        intent.putExtra("position", 1)
//                    }
//                }
//
//                "org.videolan.vlc" -> {
//                    // https://wiki.videolan.org/Android_Player_Intents
//                    if (VERSION.SDK_INT > 32) {
//                        intent.setPackage(SELECTED_PLAYER)
//                    } else {
//                        intent.component = ComponentName(
//                            SELECTED_PLAYER!!,
//                            "$SELECTED_PLAYER.gui.video.VideoPlayerActivity"
//                        ) // required for return intent
//                    }
//                    intent.putExtra("title", videoTitle)
//                    if (playerTimeCode == "continue" && videoPosition > 0L) {
//                        intent.putExtra("from_start", false)
//                        intent.putExtra("position", videoPosition)
//                    } else if (playerTimeCode == "again"
//                        || (playerTimeCode == "continue" && videoPosition == 0L)
//                    ) {
//                        intent.putExtra("from_start", true)
//                        intent.putExtra("position", 0L)
//                    }
//                    intent.putExtra("extra_duration", videoDuration)
//                }
//
//                "com.brouken.player" -> {
//                    intent.setPackage(SELECTED_PLAYER)
//                    intent.putExtra("title", videoTitle)
//                    if (playerTimeCode == "continue" || playerTimeCode == "again")
//                        intent.putExtra("position", videoPosition.toInt())
//                    if (subsUrls.isNotEmpty()) {
//                        val parcelableSubsArr = arrayOfNulls<Parcelable>(subsUrls.size)
//                        for (i in 0 until subsUrls.size) {
//                            parcelableSubsArr[i] = Uri.parse(subsUrls[i])
//                        }
//                        intent.putExtra("subs", parcelableSubsArr)
//                        intent.putExtra("subs.name", subsTitles.toTypedArray())
//                    }
//                    intent.putExtra("return_result", true)
//                }
//
//                "net.gtvbox.videoplayer", "net.gtvbox.vimuhd" -> {
//                    val vimuVersionNumber =
//                        getAppVersion(this, SELECTED_PLAYER!!)?.versionNumber ?: 0L
//                    printLog("ViMu ($SELECTED_PLAYER) version $vimuVersionNumber")
//                    intent.setPackage(SELECTED_PLAYER)
//                    intent.putExtra("headers", headers.toTypedArray())
//                    // see https://vimu.tv/player-api
//                    if (listUrls.size <= 1) {
//                        intent.putExtra("forcename", videoTitle)
//                        if (subsUrls.isNotEmpty()) {
//                            intent.putStringArrayListExtra(
//                                "asussrtlist",
//                                subsUrls
//                            )
//                        }
//                        if (isIPTV || isLIVE)
//                            intent.putExtra("forcelive", true)
//                    } else {
//                        intent.setDataAndType(
//                            Uri.parse(videoUrl),
//                            "application/vnd.gtvbox.filelist"
//                        )
//                        if (vimuVersionNumber >= 799L) { // 7.99 and above
//                            intent.putStringArrayListExtra("asusfilelist", listUrls)
//                            intent.putStringArrayListExtra("asusnamelist", listTitles)
//                            intent.putExtra("startindex", playIndex)
//                        } else {
//                            intent.putStringArrayListExtra(
//                                "asusfilelist",
//                                ArrayList(listUrls.subList(playIndex, listUrls.size))
//                            )
//                            intent.putStringArrayListExtra(
//                                "asusnamelist",
//                                ArrayList(listTitles.subList(playIndex, listUrls.size))
//                            )
//                        }
//                    }
//                    if (playerTimeCode == "continue" || playerTimeCode == "again") {
//                        intent.putExtra("position", videoPosition.toInt())
//                        intent.putExtra("startfrom", videoPosition.toInt())
//                    } else if (playerTimeCode == "ask") {
//                        // use ViMu resume
//                        intent.putExtra("forcedirect", true)
//                        intent.putExtra("forceresume", true)
//                    }
//                }
//
//                else -> {
//                    intent.setPackage(SELECTED_PLAYER)
//                }
//            }
//            @Suppress("DEPRECATION")
//            try {
//                intent.flags = 0 // https://stackoverflow.com/a/47694122
//                if (BuildConfig.DEBUG) {
//                    printLog("INTENT: " + intent.toUri(0))
//                    intent.extras?.let {
//                        for (key in it.keySet()) {
//                            if (key == "headers")
//                                printLog(
//                                    "INTENT: data extras $key : ${
//                                        it.getStringArray(key)?.toList()
//                                    }"
//                                )
//                            else
//                                printLog("INTENT: data extras $key : ${it.get(key) ?: "NULL"}")
//                        }
//                    }
//                }
//                resultLauncher.launch(intent)
//            } catch (_: Exception) {
//                App.toast(R.string.no_launch_player, true)
//            }
//        }
//    }

//    private fun resultPlayerOld(
//        endedVideoUrl: String,
//        pos: Int = 0,
//        dur: Int = 0,
//        ended: Boolean = false
//    ) {
//        // store state and duration too for WatchNext
//        val editor = lastPlayedPrefs.edit()
//        editor?.putBoolean("ended", ended)
//        editor?.putInt("position", pos)
//        editor?.putInt("duration", dur)
//        editor.apply()
//
//        lifecycleScope.launch {
//            // Add | Remove Continue to Play
//            withContext(Dispatchers.Default) {
//                val lampaActivity = this@MainActivity.playActivityJS?.let { JSONObject(it) }
//                if (lampaActivity?.has("movie") == true) {
//                    val card = getJson(
//                        lampaActivity.getJSONObject("movie").toString(),
//                        LampaCard::class.java
//                    )
//                    card?.let {
//                        it.fixCard()
//                        try {
//                            printLog("resultPlayer PlayNext $it")
//                            if (!ended)
//                                WatchNext.addLastPlayed(it)
//                            else
//                                WatchNext.removeContinueWatch()
//                        } catch (e: Exception) {
//                            printLog("resultPlayer Error add $it to WatchNext: $e")
//                        }
//                    }
//                }
//            }
//        }
//
//        val videoUrl =
//            if (endedVideoUrl == "" || endedVideoUrl == "null") playVideoUrl
//            else endedVideoUrl
//        if (videoUrl == "") return
//
//        var returnIndex = -1
//        for (i in playJSONArray.length() - 1 downTo 0) {
//            val io = playJSONArray.getJSONObject(i)
//            if (!io.has("timeline") || !io.has("url")) break
//
//            val timeline = io.optJSONObject("timeline")
//            val hash = timeline?.optString("hash", "0")
//
//            val qualityObj = io.optJSONObject("quality")
//            var foundInQuality = false
//            qualityObj?.let {
//                for (key in it.keys()) {
//                    if (it[key] == videoUrl) {
//                        foundInQuality = true
//                    }
//                }
//            }
//
//            if (io.optString("url") == videoUrl || foundInQuality) {
//                returnIndex = i
//                val time: Int = if (ended) 0 else pos / 1000
//                val duration: Int =
//                    if (ended) 0
//                    else if (dur == 0 && timeline?.has("duration") == true)
//                        timeline.optDouble("duration", 0.0).toInt()
//                    else dur / 1000
//                val percent: Int = if (duration > 0) time * 100 / duration else 100
//
//                val newTimeline = JSONObject()
//                newTimeline.put("hash", hash)
//                newTimeline.put("time", time.toDouble())
//                newTimeline.put("duration", duration.toDouble())
//                newTimeline.put("percent", percent)
//                runVoidJsFunc("Lampa.Timeline.update", newTimeline.toString())
//                // for PlayNext
//                io.put("timeline", newTimeline)
//                val resumeio = JSONObject(io.toString())
//                resumeio.put("playlist", playJSONArray)
//                resumeJS = resumeio.toString()
//            }
//            if (i in playIndex until returnIndex) {
//                printLog("mark complete index $i (in range from $playIndex to $returnIndex)")
//                val newTimeline = JSONObject()
//                newTimeline.put("hash", hash)
//                newTimeline.put("percent", 100)
//                newTimeline.put("time", 0)
//                newTimeline.put("duration", 0)
//                runVoidJsFunc("Lampa.Timeline.update", newTimeline.toString())
//                io.put("timeline", newTimeline)
//            }
//        }
//    }

    fun displaySpeechRecognizer() {
        if (VERSION.SDK_INT < 18) {
            if (!SpeechRecognizer.isRecognitionAvailable(baseContext)) {
                printLog("SpeechRecognizer not available!")
            } else {
                printLog("SpeechRecognizer available!")
            }
            // Create an intent that can start the Speech Recognizer activity
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            // This starts the activity and populates the intent with the speech text.
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                App.toast(R.string.not_found_speech, false)
            }
        } else {
            verifyMicPermissions(this)

            var dialog: AlertDialog? = null
            val view = layoutInflater.inflate(R.layout.dialog_search, null, false)
            val etSearch = view.findViewById<AppCompatEditText?>(R.id.etSearchQuery)
            val btnVoice = view.findViewById<AppCompatImageButton?>(R.id.btnVoiceSearch)
            val inputManager =
                getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

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
                        runVoidJsFunc(
                            "window.voiceResult",
                            "'" + query.replace("'", "\\'") + "'"
                        )
                    } else { // notify user
                        App.toast(R.string.search_is_empty)
                    }
                }
                .create()
                .apply {
                    window?.apply {
                        // top position (no keyboard overlap)
                        attributes = attributes.apply {
                            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            verticalMargin = 0.1F
                        }
                    }
                }
            // show fullscreen dialog
            showFullScreenDialog(dialog)
            // run voice search
            btnVoice?.performClick()
        }
    }

    @Suppress("DEPRECATION")
    private fun showFullScreenDialog(dialog: AlertDialog?) {
        dialog?.apply {
            // Set the dialog to be not focusable initially
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            if (VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Use systemUiVisibility for older APIs
                window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or if (VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                } else {
                    0
                })
                // make navbar translucent
                if (VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                }
            }
            // Show the dialog
            show()
            // Ensure the window is ready before accessing WindowInsetsController
            window?.decorView?.let { decorView ->
                if (VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Use WindowInsetsController for API 30+
                    decorView.viewTreeObserver.addOnWindowAttachListener(object :
                        ViewTreeObserver.OnWindowAttachListener {
                        override fun onWindowAttached() {
                            // Window is attached, now it's safe to access WindowInsetsController
                            decorView.windowInsetsController?.let { controller ->
                                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                controller.systemBarsBehavior =
                                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                            // Remove the listener to avoid memory leaks
                            decorView.viewTreeObserver.removeOnWindowAttachListener(this)
                        }

                        override fun onWindowDetached() {
                            // No-op
                        }
                    })
                }
            }
            // Clear the not focusable flag after the dialog is shown
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
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
                Speech.init(context, packageName)?.apply {
                    val languageToLocaleMap = mapOf(
                        "ru" to "ru-RU",
                        "en" to "en-US",
                        "be" to "be-BY",
                        "uk" to "uk-UA",
                        "zh" to "zh-CN",
                        "bg" to "bg-BG",
                        "pt" to "pt-PT",
                        "cs" to "cs-CZ"
                    )
                    val langParts = appLang.split("-")
                    val langTag = if (langParts.size >= 2) {
                        "${langParts[0]}-${langParts[1]}"
                    } else {
                        languageToLocaleMap[langParts[0]] ?: appLang
                    }
                    // Optional IETF language tag (as defined by BCP 47), for example "en-US" required for
                    // https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_LANGUAGE
                    // so Locale with Country must be provided (en_US ru_RU etc)
                    val locale = if (langTag.split("-").size >= 2) Locale(
                        langTag.split("-")[0],
                        langTag.split("-")[1]
                    ) else if (langTag.isNotEmpty()) Locale(langTag) else Locale.getDefault()
                    printLog("appLang = $appLang")
                    printLog("langTag = $langTag")
                    printLog("locale = $locale")
                    setLocale(locale)
                    startListening(progress, object : SpeechDelegate {
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
                            Log.i(
                                "speech",
                                "partial result: " + str.toString().trim { it <= ' ' })
                            onSpeech(str.toString().trim { it <= ' ' }, false, success)
                        }

                        override fun onSpeechResult(res: String) {
                            Log.i("speech", "result: $res")
                            if (res.isEmpty())
                                success = false
                            onSpeech(res, true, success)
                        }
                    })
                }
                if (BuildConfig.DEBUG)
                    Logger.setLogLevel(Logger.LogLevel.DEBUG)
                return true
            } catch (_: SpeechRecognitionNotAvailable) {
                Log.e("speech", "Speech recognition is not available on this device!")
                App.toast(R.string.search_no_voice_recognizer)
                // You can prompt the user if he wants to install Google App to have
                // speech recognition, and then you can simply call:
                SpeechUtil.redirectUserToGoogleAppOnPlayStore(context)
                // to redirect the user to the Google App page on Play Store
            } catch (_: GoogleVoiceTypingDisabledException) {
                Log.e("speech", "Google voice typing must be enabled!")
            }
        }
        return false
    }

    private fun showFab(show: Boolean = true) {
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        if (show && !isTvBox) {
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
                    context,
                    R.drawable.lampa_logo_round
                )
            )
            customSize = dp2px(context, 32f)
            setMaxImageSize(dp2px(context, 30f))
            backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    R.color.lampa_background
                )
            )
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
            "AndroidJS.storageChange",
            "JSON.stringify({" +
                    "name: '${name}'," +
                    "value: Lampa.Storage.field('${name}')" +
                    "})"
        )
    }

    private fun runJsStorageChangeField(name: String, default: String) {
        runVoidJsFunc(
            "AndroidJS.storageChange",
            "JSON.stringify({" +
                    "name: '${name}'," +
                    "value: Lampa.Storage.get('${name}', '$default')" +
                    "})"
        )
    }

    fun runVoidJsFunc(funcName: String, params: String) {
        if (browserInitComplete && loaderView.visibility == View.GONE) {
            printLog("runVoidJsFunc $funcName")
            val js = ("(function(){"
                    + "try {"
                    + funcName + "(" + params + ");"
                    + "return '${JS_SUCCESS}';"
                    + "} catch (e) {"
                    + "return '${JS_FAILURE}: ' + e.message;"
                    + "}"
                    + "})();")
            browser?.evaluateJavascript(
                js
            ) { r: String ->
                Log.i(
                    "runVoidJsFunc",
                    "$funcName($params) Result $r"
                )
            }
        } else {
            printLog("runVoidJsFunc add to delayedVoidJsFunc $funcName")
            delayedVoidJsFunc.add(listOf(funcName, params))
        }
    }

    /**
     *  AI roxxx
     */
    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject, launchPlayer: String = "") {
        //printLog("runPlayer launchPlayer[$launchPlayer] jsonObject: ${jsonObject.toString(2)}")
        val videoUrl = jsonObject.optString("url")
        if (videoUrl.isBlank()) {
            // App.toast(R.string.no_video_url_found, true)
            return
        }
        // Determine player type and package
        val isIPTV = jsonObject.optBoolean("iptv", false)
        val isLIVE = jsonObject.optBoolean("need_check_live_stream", false)
        val selectedPlayer = launchPlayer.ifEmpty {
            if (isIPTV || isLIVE) tvPlayer else appPlayer
        }.takeIf { !it.isNullOrBlank() }
        //printLog("runPlayer selectedPlayer[$selectedPlayer]")
        // Convert JSON to playlist using PlayerStateManager
        val playlist = if (jsonObject.has("playlist") && playerAutoNext) {
            playerStateManager.convertJsonToPlaylist(jsonObject.getJSONArray("playlist"))
        } else {
            listOf(playerStateManager.convertJsonToPlaylistItem(jsonObject))
        }
        // Find current index
        val currentIndex = playlist.indexOfFirst { it.url == videoUrl }
            .takeIf { it >= 0 } ?: 0

        // Save state
        val state = playerStateManager.saveState(
            activityJson = lampaActivity,
            playlist = playlist,
            currentIndex = currentIndex,
            currentUrl = videoUrl,
            extras = mapOf(
                "isIPTV" to isIPTV,
                "isLIVE" to isLIVE
            )
        )

        // Prepare intent
        val intent = createBaseIntent(state)
        intent?.let {
            // Get available players
            val availablePlayers = getAvailablePlayers(intent).takeIf { it.isNotEmpty() } ?: run {
                App.toast(R.string.no_player_activity_found, true)
                return
            }
            // Check if selected player exists
            if (selectedPlayer != null && availablePlayers.any {
                    it.activityInfo.packageName.equals(selectedPlayer, true)
                }) {
                // Configure and launch the selected player
                configurePlayerIntent(intent, jsonObject, selectedPlayer, isIPTV, state)
                // Launch Player
                launchPlayer(intent)
            } else {
                // Show player selection dialog
                showPlayerSelectionDialog(availablePlayers, jsonObject, isIPTV)
            }
        }
    }

    private fun createBaseIntent(
        state: PlayerStateManager.PlaybackState,
    ): Intent? {
        state.currentItem?.let { currentItem ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(currentItem.url)
                setDataAndType(
                    Uri.parse(currentItem.url),
                    if (currentItem.url.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/*"
                )
                flags = 0 // Clear any default flags
            }
            return intent
        }
        return null
    }

    private fun getAvailablePlayers(intent: Intent): List<ResolveInfo> {
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filterNot { info ->
                info.activityInfo.packageName.lowercase() in PLAYERS_BLACKLIST
            }
    }

    private fun configurePlayerIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        isIPTV: Boolean,
        state: PlayerStateManager.PlaybackState
    ) {
        val videoTitle = jsonObject.optString("title", if (isIPTV) "LAMPA TV" else "LAMPA video")
        val (videoPosition, videoDuration) = getPlaybackPosition(state)
        val headers = prepareHeaders(jsonObject)

        when (playerPackage.lowercase()) {
            "com.uapplication.uplayer", "com.uapplication.uplayer.beta" -> {
                configureUPlayerIntent(
                    intent,
                    jsonObject,
                    playerPackage,
                    videoTitle,
                    videoPosition,
                    state
                )
            }

            "com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.beta" -> {
                configureMxPlayerIntent(
                    intent,
                    jsonObject,
                    playerPackage,
                    videoTitle,
                    // headers,
                    videoPosition
                )
            }

            "is.xyz.mpv" -> {
                configureMpvIntent(intent, jsonObject, playerPackage, videoPosition)
            }

            "org.videolan.vlc" -> {
                configureVlcIntent(
                    intent,
                    jsonObject,
                    playerPackage,
                    videoTitle,
                    videoPosition,
                    videoDuration
                )
            }

            "com.brouken.player" -> {
                configureBroukenPlayerIntent(
                    intent,
                    jsonObject,
                    playerPackage,
                    videoTitle,
                    videoPosition
                )
            }

            "net.gtvbox.videoplayer", "net.gtvbox.vimuhd" -> {
                configureViMuIntent(
                    intent,
                    jsonObject,
                    playerPackage,
                    videoTitle,
                    state = state,
                    videoPosition,
                    isIPTV
                )
            }

            else -> {
                intent.setPackage(playerPackage)
                // Try to add headers to unknown players as a fallback
                headers?.let {
                    intent.putExtra("headers", it)
                }
            }
        }
    }

    private fun preparePlaylist(jsonObject: JSONObject): Pair<ArrayList<String>, ArrayList<String>>? {
        if (!jsonObject.has("playlist") || !playerAutoNext) return null

        return try {
            val playlistArray = jsonObject.getJSONArray("playlist")
            val listUrls = ArrayList<String>(playlistArray.length())
            val listTitles = ArrayList<String>(playlistArray.length())
            val processedItems = mutableListOf<PlayerStateManager.PlaylistItem>()
            var currentIndex = -1
            val currentUrl = jsonObject.optString("url")
            val badLinkPattern = "(/stream/.*?\\?link=.*?&index=\\d+)&preload$".toRegex()

            for (i in 0 until playlistArray.length()) {
                try {
                    val item = playlistArray.getJSONObject(i)

                    // Use your public conversion method
                    val playlistItem =
                        playerStateManager.convertJsonToPlaylistItem(item).let { originalItem ->
                            // Apply URL processing
                            val processedUrl = if (torrserverPreload && internalTorrserve) {
                                originalItem.url.replace(badLinkPattern, "$1&play")
                            } else {
                                originalItem.url
                            }

                            originalItem.copy(url = processedUrl)
                        }

                    processedItems.add(playlistItem)
                    listUrls.add(playlistItem.url)
                    listTitles.add(playlistItem.title ?: (i + 1).toString())

                    if (playlistItem.url == currentUrl) {
                        currentIndex = i
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping invalid playlist item at index $i", e)
                    continue
                }
            }

            if (listUrls.isEmpty()) return null

            // Save state using processed items
            playerStateManager.saveState(
                activityJson = lampaActivity,
                playlist = processedItems,
                currentIndex = currentIndex.coerceAtLeast(0),
                currentUrl = currentUrl
            )

            Pair(listUrls, listTitles)
        } catch (e: JSONException) {
            Log.e(TAG, "Error processing playlist array", e)
            null
        }
    }

    private fun prepareSubtitles(jsonObject: JSONObject): Pair<ArrayList<String>, ArrayList<String>>? {
        val subsUrls = ArrayList<String>()
        val subsTitles = ArrayList<String>()

        return try {
            if (jsonObject.has("subtitles")) {
                val subtitlesValue = jsonObject.get("subtitles")
                // Handle case where subtitles might be a JSONArray or other type
                when (subtitlesValue) {
                    is JSONArray -> {
                        for (i in 0 until subtitlesValue.length()) {
                            val sub = subtitlesValue.getJSONObject(i)
                            if (sub.has("url")) {
                                subsUrls.add(sub.optString("url"))
                                subsTitles.add(sub.optString("label", "Sub ${i + 1}"))
                            }
                        }
                        if (subsUrls.isNotEmpty()) Pair(subsUrls, subsTitles) else null
                    }

                    else -> {
                        Log.w(TAG, "Subtitles field exists but is not a JSONArray: $subtitlesValue")
                        null
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subtitles", e)
            null
        }
    }

    private fun hasMultiQualityStreams(jsonObject: JSONObject): Boolean {
        return jsonObject.optJSONObject("quality")?.let { qualityObj ->
            qualityObj.keys().asSequence().count() > 1
        } == true
    }

    private fun prepareHeaders(jsonObject: JSONObject): Array<String>? {
        val headersList = ArrayList<String>()
        var hasHeaders = false

        jsonObject.optJSONObject("headers")?.let { headersObj ->
            headersObj.keys().forEach { key ->
                when (key.lowercase(Locale.getDefault())) {
                    "user-agent" -> {}  // Handled separately
                    "content-length" -> {}  // Not needed
                    else -> {
                        headersList.add(key)
                        headersList.add(headersObj.optString(key))
                        hasHeaders = true
                    }
                }
            }
        }

        // Always add User-Agent if available
        HttpHelper.userAgent?.let {
            headersList.add("User-Agent")
            headersList.add(it)
            hasHeaders = true
        }

        return if (hasHeaders) headersList.toTypedArray() else null
    }

    private fun getPlaybackPosition(state: PlayerStateManager.PlaybackState): Pair<Long, Long> {
        return if (playerTimeCode == "continue") {
            state.currentItem?.timeline?.let { timeline ->
                (timeline.time * 1000).toLong() to (timeline.duration * 1000).toLong()
            } ?: (0L to 0L)
        } else {
            0L to 0L
        }
    }

    private fun configureMxPlayerIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        videoTitle: String,
        // headers: ArrayList<String>,
        position: Long
    ) {
        intent.apply {
            component = ComponentName(
                playerPackage,
                "$playerPackage.ActivityScreen"
            )
            putExtra("title", videoTitle)
            putExtra("sticky", false)
            // putExtra("headers", headers.toTypedArray())
            prepareHeaders(jsonObject)?.let {
                putExtra("headers", it)
            }
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position.toInt())

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) ->
                    putExtra("position", 1)
            }

            // Handle playlists
            preparePlaylist(jsonObject)?.let { (urls, titles) ->
                putExtra("video_list", urls.map(Uri::parse).toTypedArray())
                putExtra("video_list.name", titles.toTypedArray())
                putExtra("video_list_is_explicit", true)
            }

            // Handle subtitles
            prepareSubtitles(jsonObject)?.let { (urls, titles) ->
                putExtra("subs", urls.map(Uri::parse).toTypedArray())
                putExtra("subs.name", titles.toTypedArray())
            }

            putExtra("return_result", true)
        }
    }

    private fun configureVlcIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        videoTitle: String,
        position: Long,
        duration: Long
    ) {
        intent.apply {
            if (VERSION.SDK_INT > 32) {
                setPackage(playerPackage)
            } else {
                component = ComponentName(
                    playerPackage,
                    "$playerPackage.gui.video.VideoPlayerActivity"
                )
            }
            putExtra("title", videoTitle)
            prepareHeaders(jsonObject)?.let {
                putExtra("http-headers", it)  // VLC uses different extra name
            }
            when {
                playerTimeCode == "continue" && position > 0 -> {
                    putExtra("from_start", false)
                    putExtra("position", position)
                }

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) -> {
                    putExtra("from_start", true)
                    putExtra("position", 0L)
                }
            }

            putExtra("extra_duration", duration)
        }
    }

    private fun configureMpvIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        position: Long
    ) {
        intent.apply {
            setPackage(playerPackage)
            prepareHeaders(jsonObject)?.let { headers ->
                // MPV requires headers as a single string with \r\n delimiters
                val headerString = headers.toList()
                    .chunked(2)
                    .joinToString("\r\n") { (k, v) -> "$k: $v" }
                putExtra("headers", headerString)
            }
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position.toInt())

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) ->
                    putExtra("position", 1)
            }

            prepareSubtitles(jsonObject)?.let { (urls, _) ->
                putExtra("subs", urls.map(Uri::parse).toTypedArray())
            }
        }
    }

    private fun configureViMuIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        videoTitle: String,
        state: PlayerStateManager.PlaybackState,
        position: Long,
        isIPTV: Boolean
    ) {
        val vimuVersion = getAppVersion(this, playerPackage)?.versionNumber ?: 0L
        printLog("ViMu ($playerPackage) version $vimuVersion")

        intent.apply {
            setPackage(playerPackage)
            prepareHeaders(jsonObject)?.let { putExtra("headers", it) }

            preparePlaylist(jsonObject)?.let { (urls, titles) ->
                if (urls.isEmpty()) return@let

                val safeIndex = state.currentIndex.coerceIn(0, urls.size - 1)
                val currentUrl = state.currentItem?.url ?: urls[safeIndex]

                if (urls.size > 1) {
                    setDataAndType(Uri.parse(currentUrl), "application/vnd.gtvbox.filelist")
                    configureViMuPlaylist(this, urls, titles, safeIndex, vimuVersion)
                } else {
                    configureSingleViMuItem(this, currentUrl, videoTitle, jsonObject, isIPTV)
                }
            }
            // Handle playback position
            when (playerTimeCode) {
                "continue", "again" -> {
                    putExtra("position", position.toInt())
                    putExtra("startfrom", position.toInt())
                }

                "ask" -> {
                    putExtra("forcedirect", true)
                    putExtra("forceresume", true)
                }
            }
        }
    }

    private fun configureViMuPlaylist(
        intent: Intent,
        urls: ArrayList<String>,
        titles: ArrayList<String>,
        currentIndex: Int,
        vimuVersion: Long
    ) {
        if (vimuVersion >= 799L) { // 7.99+ version
            intent.apply {
                putStringArrayListExtra("asusfilelist", urls)
                putStringArrayListExtra("asusnamelist", titles)
                putExtra("startindex", currentIndex)
            }
        } else { // Legacy version
            intent.apply {
                putStringArrayListExtra(
                    "asusfilelist",
                    ArrayList(urls.subList(currentIndex, urls.size))
                )
                putStringArrayListExtra(
                    "asusnamelist",
                    ArrayList(titles.subList(currentIndex, urls.size))
                )
            }
        }
    }

    private fun configureSingleViMuItem(
        intent: Intent,
        currentUrl: String,
        videoTitle: String,
        jsonObject: JSONObject,
        isIPTV: Boolean
    ) {
        intent.apply {
            data = Uri.parse(currentUrl)
            putExtra("forcename", videoTitle)

            // Safely handle subtitles
            try {
                prepareSubtitles(jsonObject)?.let { (subUrls, _) ->
                    if (subUrls.isNotEmpty()) {
                        putStringArrayListExtra("asussrtlist", subUrls)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process subtitles", e)
            }

            if (isIPTV) {
                putExtra("forcelive", true)
            } else {
                removeExtra("forcelive")
            }
        }
    }

    private fun configureBroukenPlayerIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        videoTitle: String,
        position: Long
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)

            // Handle playback position
            when {
                playerTimeCode == "continue" || playerTimeCode == "again" ->
                    putExtra("position", position.toInt())
            }

            // Handle subtitles
            prepareSubtitles(jsonObject)?.let { (urls, titles) ->
                putExtra("subs", urls.map(Uri::parse).toTypedArray())
                putExtra("subs.name", titles.toTypedArray())
            }

            putExtra("return_result", true)
        }
    }

    private fun configureUPlayerIntent(
        intent: Intent,
        jsonObject: JSONObject,
        playerPackage: String,
        videoTitle: String,
        position: Long,
        state: PlayerStateManager.PlaybackState
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)

            // Handle resume/restart
            when (playerTimeCode) {
                "continue", "again" -> putExtra("resume", position)
            }

            // Handle playlists or multi-quality streams
            if (hasMultiQualityStreams(jsonObject) || preparePlaylist(jsonObject) != null) {
                configureUPlayerPlaylist(this, jsonObject, videoTitle, state = state)
            }
        }
    }

    private fun configureUPlayerPlaylist(
        intent: Intent,
        jsonObject: JSONObject,
        videoTitle: String,
        state: PlayerStateManager.PlaybackState
    ) {
        // Get first item's hash from state
        val firstHash = state.playlist.firstOrNull()?.timeline?.hash ?: "0"

        if (firstHash != "0") {
            intent.putExtra("playlistTitle", firstHash)
        }

        // Handle quality variants
        jsonObject.optJSONObject("quality")?.let { qualityObj ->
            val qualityMap = LinkedHashMap<String, ArrayList<String>>()
            qualityObj.keys().forEach { key ->
                qualityMap.getOrPut(key) { ArrayList() }.add(qualityObj.getString(key))
            }

            intent.apply {
                putStringArrayListExtra("videoGroupList", ArrayList(qualityMap.keys))
                qualityMap.forEach { (key, urls) ->
                    putStringArrayListExtra(key, urls)
                }
            }
        } ?: run {
            // Fallback to normal playlist handling
            preparePlaylist(jsonObject)?.let { (urls, titles) ->
                intent.apply {
                    putStringArrayListExtra("videoList", urls)
                    putStringArrayListExtra("titleList", titles)
                    putExtra("playlistPosition", state.currentIndex) // Use state.currentIndex
                }
            } ?: run {
                // Single item fallback
                intent.putStringArrayListExtra("titleList", arrayListOf(videoTitle))
            }
        }
    }

    private fun launchPlayer(intent: Intent) {
        try {
            logIntentData(intent)
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            printLog("Failed to launch player: ${e.message}")
            App.toast(R.string.no_launch_player, true)
        }
    }

    private fun showPlayerSelectionDialog(
        players: List<ResolveInfo>,
        jsonObject: JSONObject,
        isIPTV: Boolean
    ) {
        val mainActivity = this
        val listAdapter = AppListAdapter(mainActivity, players)
        val playerChooser = AlertDialog.Builder(mainActivity)

        val appTitleView = LayoutInflater.from(mainActivity).inflate(R.layout.app_list_title, null)
        val switch = appTitleView.findViewById<SwitchCompat>(R.id.useDefault)
        playerChooser.setCustomTitle(appTitleView)

        playerChooser.setAdapter(listAdapter) { dialog, which ->
            val setDefaultPlayer = switch.isChecked
            val selectedPlayer = listAdapter.getItemPackage(which)

            if (setDefaultPlayer) {
                setPlayerPackage(selectedPlayer, isIPTV)
            }

            dialog.dismiss()
            runPlayer(jsonObject, selectedPlayer)
        }

        val playerChooserDialog = playerChooser.create()
        showFullScreenDialog(playerChooserDialog)
        playerChooserDialog.listView.requestFocus()
    }

    private fun resultPlayer(
        endedVideoUrl: String,
        pos: Int = 0,
        dur: Int = 0,
        ended: Boolean = false
    ) {
        // Store playback state for WatchNext
        storePlaybackState(ended, pos, dur)

        // Process the current playback item
        processPlaybackResult(endedVideoUrl, pos, dur, ended)

        // Update PlayNext
        updatePlayNext(ended)
    }

    private fun storePlaybackState(ended: Boolean, pos: Int, dur: Int) {
        lastPlayedPrefs.edit().apply {
            putBoolean("ended", ended)
            putInt("position", pos)
            putInt("duration", dur)
            apply()
        }
    }

    private fun processPlaybackResult(
        endedVideoUrl: String,
        pos: Int,
        dur: Int,
        ended: Boolean
    ) {
        val currentState = playerStateManager.getState(lampaActivity)
        val videoUrl = if (endedVideoUrl.isBlank() || endedVideoUrl == "null") {
            currentState.currentUrl
        } else {
            endedVideoUrl
        } ?: return

        val updatedPlaylist = currentState.playlist.toMutableList()
        var foundIndex = -1

        // Process playlist to find and update the current item
        updatedPlaylist.forEachIndexed { i, item ->
            if (isCurrentPlaybackItem(item, videoUrl)) {
                foundIndex = i
                updatedPlaylist[i] = updatePlaylistItem(item, pos, dur, ended)
            }
        }

        if (foundIndex >= 0) {
            // Save updated state
            playerStateManager.saveState(
                activityJson = lampaActivity,
                playlist = updatedPlaylist,
                currentIndex = foundIndex,
                currentUrl = videoUrl,
                currentPosition = pos.toLong()
            )
            // TODO: implement mark only from playback start position
            // Mark previous items as completed if needed
            if (playerAutoNext) {
                markPreviousItemsComplete(updatedPlaylist, foundIndex)
            }
            // Notify JS
            updatedPlaylist[foundIndex].timeline?.let { timeline ->
                val json = playerStateManager.convertTimelineToJsonString(timeline)
                runVoidJsFunc("Lampa.Timeline.update", json)
            }
        }

        if (ended) {
            playerStateManager.clearState(lampaActivity)
        }
    }

    // Helper functions
    private fun isCurrentPlaybackItem(
        item: PlayerStateManager.PlaylistItem,
        videoUrl: String
    ): Boolean { // match quality urls too
        return item.url == videoUrl || item.quality?.values?.any { it == videoUrl } == true
    }

    private fun updatePlaylistItem(
        item: PlayerStateManager.PlaylistItem,
        pos: Int,
        dur: Int,
        ended: Boolean
    ): PlayerStateManager.PlaylistItem {
        val percent = if (dur > 0) (pos * 100 / dur) else 100
        val timeline = PlayerStateManager.PlaylistItem.Timeline(
            hash = item.timeline?.hash ?: "0",
            time = if (ended) 0.0 else pos.toDouble() / 1000,
            duration = if (ended) 0.0 else dur.toDouble() / 1000,
            percent = if (ended) 100 else percent
        )
        return item.copy(timeline = timeline)
    }

    private fun markPreviousItemsComplete(
        playlist: MutableList<PlayerStateManager.PlaylistItem>,
        currentIndex: Int
    ) {
        for (i in 0 until currentIndex) {
            playlist[i] = playlist[i].copy(
                timeline = PlayerStateManager.PlaylistItem.Timeline(
                    hash = playlist[i].timeline?.hash ?: "0",
                    time = 0.0,
                    duration = 0.0,
                    percent = 100
                )
            )
        }
    }

    private fun updatePlayNext(ended: Boolean) {
        lifecycleScope.launch(Dispatchers.Default) {
            playActivityJS?.let { jsonString ->
                try {
                    val lampaActivity = JSONObject(jsonString)
                    if (lampaActivity.has("movie")) {
                        val card = getJson(
                            lampaActivity.getJSONObject("movie").toString(),
                            LampaCard::class.java
                        )?.apply { fixCard() }

                        card?.let {
                            if (!ended) {
                                printLog("resultPlayer PlayNext $it add / update")
                                WatchNext.addLastPlayed(it)
                            } else {
                                printLog("resultPlayer PlayNext $it remove")
                                WatchNext.removeContinueWatch()
                            }
                        }
                    }
                } catch (e: Exception) {
                    printLog("resultPlayer Error processing PlayNext: $e")
                }
            }
        }
    }

    // FIXME! Remove
    private fun saveLastPlayed() {
        printLog("saveLastPlayed() set playActivityJS to $playActivityJS")
    }

}