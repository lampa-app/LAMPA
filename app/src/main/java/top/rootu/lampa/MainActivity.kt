package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
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
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.gotev.speech.GoogleVoiceTypingDisabledException
import net.gotev.speech.Logger
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.SpeechUtil
import net.gotev.speech.ui.SpeechProgressView
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
import top.rootu.lampa.helpers.Backup.backupSettings
import top.rootu.lampa.helpers.Backup.validateStorageBackup
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.debugLogIntentData
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import top.rootu.lampa.helpers.Helpers.isValidJson
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
import top.rootu.lampa.helpers.Prefs.schdToRemove
import top.rootu.lampa.helpers.Prefs.thrwToRemove
import top.rootu.lampa.helpers.Prefs.tvPlayer
import top.rootu.lampa.helpers.Prefs.urlHistory
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.helpers.getAppVersion
import top.rootu.lampa.helpers.hideSystemUI
import top.rootu.lampa.helpers.isAmazonDev
import top.rootu.lampa.helpers.isSafeForUse
import top.rootu.lampa.helpers.isTvBox
import top.rootu.lampa.models.LAMPA_CARD_KEY
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.net.HttpHelper
import top.rootu.lampa.sched.Scheduler
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.net.toUri


class MainActivity : BaseActivity(),
    Browser.Listener,
    XWalkInitializer.XWalkInitListener, MyXWalkUpdater.XWalkUpdateListener {
    // Local properties
    private var mXWalkUpdater: MyXWalkUpdater? = null
    private var mXWalkInitializer: XWalkInitializer? = null
    private var browser: Browser? = null
    private var browserInitComplete = false
    private var isMenuVisible = false
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

    // Class for URL history
    private class UrlAdapter(context: Context) :
        ArrayAdapter<String>(
            context,
            R.layout.lampa_dropdown_item, // Custom dropdown layout
            android.R.id.text1, // ID of the TextView in the custom layout
            context.urlHistory.toMutableList() // Load URL history
        )

    companion object {
        // Constants
        const val VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE = 96
        private const val TAG = "APP_MAIN"
        private const val RESULT_VIMU_ENDED = 2
        private const val RESULT_VIMU_START = 3
        private const val RESULT_VIMU_ERROR = 4
        private const val JS_SUCCESS = "SUCCESS"
        private const val JS_FAILURE = "FAILED"
        private const val IP4_DIG = "([01]?\\d?\\d|2[0-4]\\d|25[0-5])"
        private const val IP4_REGEX = "(${IP4_DIG}\\.){3}${IP4_DIG}"
        private const val IP6_DIG = "[0-9A-Fa-f]{1,4}"
        private const val IP6_REGEX =
            "((${IP6_DIG}:){7}${IP6_DIG}|(${IP6_DIG}:){1,7}:|:(:${IP6_DIG}){1,7}|(${IP6_DIG}::?){1,6}${IP6_DIG})"
        private const val DOMAIN_REGEX = "([-A-Za-z\\d]+\\.)+[-A-Za-z]{2,}"
        private const val URL_REGEX = "^https?://" + // Mandatory protocol
                // "^(https?://)?" + // Protocol (http or https, optional)
                "(\\[${IP6_REGEX}]|${IP4_REGEX}|${DOMAIN_REGEX})" +  // IPv6, IPv4, or domain
                "(:\\d+)?" +                      // Optional port
                "(/[-\\w@:%._+~#=&]*(/[-\\w@:%._+~#=&]*)*)?" + // Optional path (allows subpaths)
                "(\\?[\\w@:%._+~#=&-]*)?" +       // Optional query string
                "(#[\\w-]*)?" +                   // Optional fragment
                "$"

        // Player Packages
        private val MX_PACKAGES = setOf(
            "com.mxtech.videoplayer.ad", // Standard
            "com.mxtech.videoplayer.pro", // Pro
            "com.mxtech.videoplayer.beta", // Beta
        )
        private val UPLAYER_PACKAGES = setOf(
            "com.uapplication.uplayer",  // Standard
            "com.uapplication.uplayer.beta", // Beta
        )
        private val VIMU_PACKAGES = setOf(
            "net.gtvbox.videoplayer",  // Standard
            "net.gtvbox.vimuhd",       // ViMu HD
            "net.gtvbox.vimu",         // Legacy
        )
        private val DDD_PLAYER_PACKAGES = setOf(
            "top.rootu.dddplayer"
        )
        private val EXO_PLAYER_PACKAGES = setOf(
            "com.google.android.exoplayer2.demo", // v2, Legacy
            "androidx.media3.demo.main", // v3, current
        )
        private val PLAYERS_BLACKLIST = setOf(
            "com.android.gallery3d",
            "com.android.tv.frameworkpackagestubs",
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.pro",
            "com.ghisler.android.totalcommander",
            "com.google.android.apps.photos",
            "com.google.android.tv.frameworkpackagestubs",
            "com.instantbits.cast.webvideo",
            "com.lonelycatgames.xplore",
            "com.mitv.videoplayer",
            "com.mixplorer.silver",
            "com.opera.browser",
            "com.tcl.browser",
            "nextapp.fx",
            "org.droidtv.contentexplorer",
            "pl.solidexplorer2",
            // more to add...
        )
        private val URL_PATTERN = Pattern.compile(URL_REGEX)
        private val listenerMutex = Mutex()

        // Properties
        var LAMPA_URL: String = ""
        var SELECTED_PLAYER: String? = ""
        var SELECTED_BROWSER: String? =
            if (VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) "XWalk" else ""
        var delayedVoidJsFunc = mutableListOf<List<String>>()
        var playerTimeCode: String = "continue"
        var playerAutoNext: Boolean = true
        var proxyTmdbEnabled: Boolean = false
        var lampaActivity: String = "{}" // JSON
        lateinit var urlAdapter: ArrayAdapter<String>
    }

    inline fun <reified T> T.logDebug(message: String) {
        if (BuildConfig.DEBUG)
            Log.d(T::class.simpleName, message)
    }

    // adb shell setprop log.tag.MainActivity DEBUG
    // usage: debugLog<MainActivity> { "..." }
    inline fun <reified T> debugLog(block: () -> String) {
        if (BuildConfig.DEBUG || Log.isLoggable(T::class.simpleName, Log.DEBUG)) {
            Log.d(T::class.simpleName, block())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LAMPA_URL = appUrl
        SELECTED_PLAYER = appPlayer
        logDebug("onCreate SELECTED_BROWSER: $SELECTED_BROWSER")
        logDebug("onCreate LAMPA_URL: $LAMPA_URL")
        logDebug("onCreate SELECTED_PLAYER: $SELECTED_PLAYER")
        playerStateManager = PlayerStateManager(this).apply {
            purgeOldStates()
        }

        setupActivity()
        setupBrowser()
        setupUI()
        setupIntents()

        if (firstRun) {
            CoroutineScope(Dispatchers.Default).launch {
                logDebug("First run scheduleUpdate(sync: true)")
                Scheduler.scheduleUpdate(true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // getIntent() should always return the most recent
        logDebug("onNewIntent() processIntent")
        processIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (!isTvBox) setupFab()
        // Try to initialize again when the user completed updating and
        // returned to current activity. The browser.onResume() will do nothing if
        // the initialization is proceeding or has already been completed.
        mXWalkInitializer?.initAsync()
        logDebug("onResume() browserInitComplete $browserInitComplete")
        if (browserInitComplete)
            browser?.resumeTimers()
        logDebug("onResume() isSafeForUse ${browser.isSafeForUse()}")
        if (browser.isSafeForUse()) {
            lifecycleScope.launch {
                logDebug("onResume() run syncBookmarks()")
                syncBookmarks()
            }
        }
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
        logDebug("onUserLeaveHint()")
        if (browserInitComplete)
            browser?.apply {
                pauseTimers()
                clearCache(true)
            }
        super.onUserLeaveHint()
    }

    // handle configuration changes (language / screen orientation)
    override fun onConfigurationChanged(newConfig: Configuration) {
        logDebug("onConfigurationChanged()")
        super.onConfigurationChanged(newConfig)
        lifecycleScope.launch {
            delay(300) // Small delay for configuration to settle
            hideSystemUI()
            showFab(true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
            || keyCode == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU
        ) {
            logDebug("Menu key pressed")
            showMenuDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            logDebug("Back button long pressed")
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
        logDebug("onBrowserInitCompleted LAMPA_URL: $LAMPA_URL")
        if (LAMPA_URL.isEmpty()) {
            logDebug("onBrowserInitCompleted showUrlInputDialog")
            showUrlInputDialog()
        } else {
            logDebug("onBrowserInitCompleted load $LAMPA_URL")
            browser?.loadUrl(LAMPA_URL)
        }
    }

    override fun onBrowserPageFinished(view: ViewGroup, url: String) {
        logDebug("onBrowserPageFinished url: $url")
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

        lifecycleScope.launch {
            syncLanguage()
        }
        // Hack to skip reload from Back history
        if (url.trimEnd('/').equals(LAMPA_URL, true)) {
            // 1s delay after deep link to load content and after storage listener setup
            val waitDelay = 1000L
            // Lazy Load Intent
            processIntent(intent, waitDelay)
            lifecycleScope.launch {
                listenerCleanupAndSetup()
                delay(waitDelay)
                syncStorage() // Sync with Lampa settings
                syncBookmarks() // Sync Android TV Home user changes
                // Process delayed functions with safe iteration
                val itemsToProcess = delayedVoidJsFunc.toList()
                delayedVoidJsFunc.clear() // Clear before processing
                for (item in itemsToProcess) {
                    runVoidJsFunc(item[0], item[1])
                }
                // Background update Android TV channels and recommendations
                withContext(Dispatchers.Default) {
                    delay(waitDelay)
                    Scheduler.scheduleUpdate(false) // false for one shot, true is onBoot
                }
            }
        }
    }

    private fun isAfterEndCreditsPosition(positionMillis: Long, duration: Long): Boolean {
        return duration > 0 && positionMillis >= duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE / 100
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
            // Clear any pending intents that might cause reloads
            // intent = Intent() // Reset the intent
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
            Log.d(TAG, "Returned videoUrl: $videoUrl")
            when (resultCode) {
                RESULT_OK -> Log.d(TAG, "RESULT_OK")
                RESULT_CANCELED -> Log.d(TAG, "RESULT_CANCELED")
                RESULT_FIRST_USER -> Log.d(TAG, "RESULT_FIRST_USER")
                RESULT_VIMU_ENDED -> Log.d(TAG, "RESULT_VIMU_ENDED")
                RESULT_VIMU_START -> Log.d(TAG, "RESULT_VIMU_START")
                RESULT_VIMU_ERROR -> Log.e(TAG, "RESULT_VIMU_ERROR")
                else -> Log.w(TAG, "Undefined result code [$resultCode]")
            }
        }
        debugLogIntentData(TAG, data)

        data?.let { intent ->
            when (intent.action) {
                // MX & Just Player
                "com.mxtech.intent.result.VIEW" ->
                    handleMxPlayerResult(intent, resultCode, videoUrl)
                // VLC
                "org.videolan.vlc.player.result" ->
                    handleVlcPlayerResult(intent, resultCode, videoUrl)
                // MPV
                "is.xyz.mpv.MPVActivity.result" ->
                    handleMpvPlayerResult(intent, resultCode, videoUrl)
                // UPlayer
                "com.uapplication.uplayer.result", "com.uapplication.uplayer.beta.result" ->
                    handleUPlayerResult(intent, resultCode, videoUrl)
                // ViMu
                "net.gtvbox.videoplayer.result", "net.gtvbox.vimuhd.result" ->
                    handleViMuPlayerResult(intent, resultCode, videoUrl)
                "top.rootu.dddplayer.intent.result.VIEW" ->
                    handleDddPlayerResult(intent, resultCode, videoUrl)
                // Others
                else -> handleGenericPlayerResult(intent, resultCode, videoUrl)
            }
        }
    }

    private fun handleDddPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        when (resultCode) {
            RESULT_OK -> {
                val pos = intent.getLongExtra("position", 0L)
                val dur = intent.getLongExtra("duration", 0L)
                // val endBy = intent.getStringExtra("end_by") // "user" or "playback_completion"

                if (pos > 0 && dur > 0) {
                    val ended = isAfterEndCreditsPosition(pos, dur)
                    Log.i(TAG, "Playback stopped [position=$pos, duration=$dur, ended:$ended]")
                    resultPlayer(videoUrl, pos.toInt(), dur.toInt(), ended)
                } else if (pos == 0L && dur == 0L) {
                    // Возможно воспроизведение завершилось полностью
                    Log.i(TAG, "Playback completed (DDD)")
                    resultPlayer(videoUrl, 0, 0, true)
                }
            }
            RESULT_CANCELED -> Log.i(TAG, "Playback stopped by user")
            else -> Log.e(TAG, "Invalid state [resultCode=$resultCode]")
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
                    Log.i(TAG, "Playback error?")
                    resultPlayer(url, 0, 0, false)
                }

                pos > 0L -> {
                    Log.i(TAG, "Playback stopped with no duration! ENDED")
                    resultPlayer(url, pos.toInt(), pos.toInt(), true)
                }
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
                if (pos > 0 && dur > 0) { // ViMu duration can be -1 on playback error
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
        logDebug("onXWalkInitStarted()")
    }

    override fun onXWalkInitCancelled() {
        logDebug("onXWalkInitCancelled()")
        // Perform error handling here
        finish()
    }

    override fun onXWalkInitFailed() {
        logDebug("onXWalkInitFailed()")
        if (mXWalkUpdater == null) {
            mXWalkUpdater = MyXWalkUpdater(this, this)
        }
        setupXWalkApkUrl()
        mXWalkUpdater?.updateXWalkRuntime()
    }

    override fun onXWalkInitCompleted() {
        logDebug("onXWalkInitCompleted() isXWalkReady: ${mXWalkInitializer?.isXWalkReady}")
        if (mXWalkInitializer?.isXWalkReady == true) {
            useCrossWalk()
        }
    }

    override fun onXWalkUpdateCancelled() {
        logDebug("onXWalkUpdateCancelled()")
        // Perform error handling here
        finish()
    }

    private fun setupXWalkApkUrl() {
        val abi = MyXWalkEnvironment.getRuntimeAbi()
        val apkUrl = String.format(getString(R.string.xwalk_apk_link), abi)
        mXWalkUpdater!!.setXWalkApkUrl(apkUrl)
    }

    private fun migrateSettings() {
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

    suspend fun listenerCleanupAndSetup() {
        listenerMutex.withLock {
            cleanupListener()
            // Wait for cleanup to complete
            delay(1000) // Ensure cleanup completes
            // Verify cleanup
            browser?.evaluateJavascript(
                """
                (function() {
                    return {
                        listenerExists: typeof window._androidStorageListener !== 'undefined',
                        lampaReady: !!window.Lampa
                    };
                })()
                """.trimIndent()
            ) { result ->
                logDebug("Listener cleanup verification: $result")
            }
            setupListener()
        }
    }

    private fun setupListener() {
        logDebug("Setting up storage change listener...")
        browser?.evaluateJavascript(
            """
            (function() {
                // Only setup if not already exists
                if (typeof window._androidStorageListener === 'undefined') {
                    window._androidStorageListener = function(o) {
                        AndroidJS.storageChange(JSON.stringify(o));
                    };
                    if (Lampa && Lampa.Storage && Lampa.Storage.listener) {
                        try {
                            if (Lampa.Storage.listener.follow) {
                                console.log('Use listener.follow');
                                Lampa.Storage.listener.follow('change', window._androidStorageListener);
                            } else {
                                console.log('Use listener.add');
                                Lampa.Storage.listener.add('change', window._androidStorageListener);
                            }
                        } catch (e) {
                            console.error('Error adding listener:', e);
                        }
                        return 'LISTENER_ADDED';
                    }
                    return 'NO_LAMPA_STORAGE';
                }
                return 'LISTENER_EXISTS';
            })()
            """.trimIndent()
        ) { result ->
            logDebug("Listener setup result: $result")
        }
    }

    // Call this when the page is destroyed or navigated away
    fun cleanupListener() {
        logDebug("Starting listener cleanup...")
        browser?.evaluateJavascript(
            """
            (function() {
                if (typeof window._androidStorageListener !== 'undefined') {
                    if (Lampa && Lampa.Storage && Lampa.Storage.listener) {
                        try {
                            console.log('Use listener.remove');
                            Lampa.Storage.listener.remove('change', window._androidStorageListener);
                        } catch (e) {
                            console.error('Error removing listener:', e);
                        }
                    }
                    delete window._androidStorageListener;
                    console.log('Listener fully cleaned up');
                    return 'LISTENER_REMOVED';
                }
                return 'NO_LISTENER';
            })()
            """.trimIndent()
        ) { result ->
            logDebug("Listener cleanup result: $result")
        }
    }

    private fun syncLanguage() {
        runJsStorageChangeField("language") // apply App language
    }

    private fun syncStorage() {
        runJsStorageChangeField("activity", "{}") // get current lampaActivity
        runJsStorageChangeField("player_timecode") // for player params
        runJsStorageChangeField("playlist_next") // for player playlist
        runJsStorageChangeField("source") // get current catalog for Recs
        runJsStorageChangeField("account_use") // get bookmarks sync state
        runJsStorageChangeField("recomends_list", "[]") // force update recs
        runJsStorageChangeField("proxy_tmdb") // to get current baseUrlApiTMDB and baseUrlImageTMDB
    }

    fun getLampaTmdbUrls() {
        lifecycleScope.launch {
            browser?.evaluateJavascript(
                """
                (function() {
                    if(window.appready) {
                        console.log('Lampa ready, store baseUrlApiTMDB and baseUrlImageTMDB...');
                        AndroidJS.storageChange(JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')}))
                        AndroidJS.storageChange(JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')}))
                    } else {
                        console.log('Lampa not ready, wait load...');
                        Lampa.Listener.follow('app', function (e) {
                        if(e.type =='ready')
                            console.log('Lampa ready, store baseUrlApiTMDB and baseUrlImageTMDB...');
                            AndroidJS.storageChange(JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')}))
                            AndroidJS.storageChange(JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')}))
                        })
                    }
                    return '${JS_SUCCESS} setup TMDB URLs';
                })()
                """.trimIndent()
            ) { result -> logDebug(result.removeSurrounding("\"")) }

//            runVoidJsFunc(
//                "AndroidJS.storageChange",
//                "JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')})"
//            )
//            runVoidJsFunc(
//                "AndroidJS.storageChange",
//                "JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')})"
//            )
        }
    }

    // Function to sync bookmarks (Required only for Android TV 8+)
    // runVoidJsFunc("Lampa.Favorite.$action", "'$catgoryName', {id: $id}")
    // runVoidJsFunc("Lampa.Favorite.add", "'wath', ${Gson().toJson(card)}")
    private suspend fun syncBookmarks() = withContext(Dispatchers.Default) {
        if (!isTvContentProviderAvailable) return@withContext
        withContext(Dispatchers.Main) {
            runVoidJsFunc("Lampa.Favorite.init", "") // Initialize if no favorite
        }
        // logDebug("syncBookmarks() add to wath: ${App.context.wathToAdd}")
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
            // logDebug("syncBookmarks() remove from $category: $items")
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

    private fun dumpStorage(callback: (String) -> Unit) {
        val backupJavascript = """
            (function() {
                console.log('Backing up localStorage to App Prefs');
                try {
                    AndroidJS.clear();
                    let count = 0;
                    for (let i = 0; i < localStorage.length; i++) {
                        const key = localStorage.key(i);
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
                logDebug("localStorage backed up. Result $result")
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
                logDebug("localStorage restored. Result $result")
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
        debugLogIntentData(TAG, intent)
        intent ?: return
        // Parse intent extras
        val sid = intent.getStringExtra("id") ?: intent.getIntExtra("id", -1)
            .toString() // Change to String
        val mediaType = intent.getStringExtra("media") ?: ""
        val source = intent.getStringExtra("source") ?: lampaSource.ifEmpty { "tmdb" }
        // Parse intent data
        intent.data?.let { uri ->
            parseUriData(intent, uri, delay)
        }
        // Handle PlayNext
        if (intent.getBooleanExtra("continueWatch", false) == true) {
            handleContinueWatch(intent, delay)
            // Handle opening a card
        } else if (sid != "-1" && mediaType.isNotEmpty()) {
            handleOpenCard(intent, sid, mediaType, source, delay)
        }
        // Handle search command
        intent.getStringExtra("cmd")?.let { cmd ->
            when (cmd) {
                "open_settings" -> showMenuDialog()
            }
        }
        // Fix initial focus
        browser?.setFocus()
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
        val card = "{id: '$sid', source: 'tmdb'}" // Use String id in JSON
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

    private fun handleContinueWatch(intent: Intent, delay: Long = 0) {
        lifecycleScope.launch {
            // fallback to lampaActivity?
            val activityJson = intent.getStringExtra("lampaActivity") ?: return@launch
            if (isValidJson(activityJson)) {
                openLampaContent(activityJson, delay) // needed to match state
                delay(delay) // need to sure content loaded and activity stored
                if (intent.getBooleanExtra("android.intent.extra.START_PLAYBACK", false)) {
                    val card = getCardFromActivity(activityJson) ?: return@launch
                    val state = playerStateManager.findStateByCard(card) ?: return@launch
                    // val matchingStates = playerStateManager.findMatchingStates(activityJson)
                    // playerStateManager.debugKeyMatching(activityJson)
                    when {
                        state.currentItem != null -> { // matchingStates.isNotEmpty()
                            // val state = matchingStates.maxByOrNull { it.lastUpdated }!!
                            val currentItem = state.playlist.getOrNull(state.currentIndex)
                            val playJsonObj = playerStateManager.getStateJson(state).apply {
                                // Set title
                                currentItem?.title?.takeIf { it.isNotEmpty() }?.let { title ->
                                    if (!has("title")) put("title", title)
                                }
                                // Apply current item's timeline to root
                                currentItem?.timeline?.let { timeline ->
                                    put("timeline", JSONObject().apply {
                                        put("hash", timeline.hash)
                                        put("time", timeline.time)
                                        put("duration", timeline.duration)
                                        put("percent", timeline.percent)
                                        timeline.profile?.let { put("profile", it) }
                                    })
                                    // Also ensure the current position is set
                                    put("position", timeline.time.toLong())
                                }
                                // Player-specific flag
                                put("from_state", true)
                            }
                            // logDebug("playJsonObj ${playJsonObj.toString(2)}")
                            runPlayer(playJsonObj, "", activityJson)
                        }

                        else -> {
                            logDebug("No matching state found for card")
                        }
                    }
                }
            }
        }
    }

    // Helper function to check card match
    private fun cardMatchesState(
        card: LampaCard,
        state: PlayerStateManager.PlaybackState
    ): Boolean {
        return (state.extras[LAMPA_CARD_KEY] as? String)?.let { storedJson ->
            getJson(storedJson, LampaCard::class.java)?.let { storedCard ->
                storedCard.id == card.id ||
                        (storedCard.title == card.title && storedCard.release_year == card.release_year)
            }
        } == true
    }

    // Helper function to handle opening a card
    private fun handleOpenCard(
        intent: Intent?,
        sid: String,
        mediaType: String,
        source: String,
        delay: Long = 0
    ) { // Change intID to String
        val cardJson = intent?.getStringExtra(LAMPA_CARD_KEY)
            ?: "{id: '$sid', source: '$source'}" // Use String id in JSON
        lifecycleScope.launch {
            openLampaContent(
                "{id: '$sid', method: '$mediaType', source: '$source', component: 'full', card: $cardJson}",
                delay
            )
        }
    }

    private suspend fun openLampaContent(json: String, delay: Long = 0) {
        runVoidJsFunc("window.start_deep_link = ", json)
        delay(delay)
        runVoidJsFunc("Lampa.Controller.toContent", "")
        runVoidJsFunc("Lampa.Activity.push", json)
        // TODO: Clear back stack after deep link
        // browser?.clearHistory()
    }

    private fun showMenuDialog() {
        // Define menu items
        val menuItems = mutableListOf(
            MenuItem(
                title = if (isTvContentProviderAvailable) {
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
            this,
            menuItems.map { it.title }.toList(),
            menuItems.map { it.icon }.toList()
        )

        // Configure the dialog
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.menu_title))
            setAdapter(adapter) { dialog, which ->
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
            setOnDismissListener {
                isMenuVisible = false
                showFab(true)
            }
        }.create()
        // Show full screen dialog
        showFullScreenDialog(dialog)
        isMenuVisible = true
    }

    // Function to handle backup all settings
    private fun backupAllSettings() {
        lifecycleScope.launch {
            dumpStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) { // .trim().removeSurrounding("\"")
                    val itemsCount = callback.substringAfter("$JS_SUCCESS.")
                        .substringBefore("items")
                        .trim()
                        .toIntOrNull() ?: 0
                    // Proceed with saving settings if dumpStorage successful
                    if (backupSettings(Prefs.APP_PREFERENCES) &&
                        backupSettings(Prefs.STORAGE_PREFERENCES) &&
                        validateStorageBackup(itemsCount)
                    ) {
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
        appPrefs.edit { clear() }
        defPrefs.edit { clear() }
        lastPlayedPrefs.edit { clear() }
        // clearUrlHistory()
        recreate()
    }

    private fun showBackupDialog() {

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
            this,
            menuItems.map { it.title }.toList(),
            menuItems.map { it.icon }.toList()
        )

        // Configure the dialog
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.backup_restore_title))
            setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                when (menuItems[which].action) {
                    "backupAllSettings" -> backupAllSettings()
                    "restoreAppSettings" -> restoreAppSettings()
                    "restoreLampaSettings" -> restoreLampaSettings()
                    "restoreDefaultSettings" -> restoreDefaultSettings()
                }
            }
        }.create()
        // Show the dialog
        showFullScreenDialog(dialog)
        // Set active row
        adapter.setSelectedItem(0)

        // Check storage permissions
        if (!PermHelpers.hasStoragePermissions(this)) {
            PermHelpers.verifyStoragePermissions(this)
        }
    }

    private fun showBrowserInputDialog() {

        val xWalkVersion = "53.589.4"
        var selectedIndex = 0

        // Determine available browser options
        val (menuItemsTitles, menuItemsActions, menuIcons) =
            if (Helpers.isWebViewAvailable(this)) {
                val webViewVersion = Helpers.getWebViewVersion(this)
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
        val adapter = ImgArrayAdapter(this, menuItemsTitles, menuIcons)

        // Configure the dialog
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.change_engine_title))
            setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                if (menuItemsActions[which] != SELECTED_BROWSER) {
                    appBrowser = menuItemsActions[which]
                    this@MainActivity.recreate()
                }
            }
        }.create()
        // Show the dialog
        showFullScreenDialog(dialog)
        // Set active row
        adapter.setSelectedItem(selectedIndex)
    }

    fun showUrlInputDialog(msg: String = "") {
        val mainActivity = this
        urlAdapter = UrlAdapter(mainActivity)
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

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
        val dialog = builder.create()
            .apply {
                window?.apply {
                    // top position (no keyboard overlap)
                    attributes = attributes.apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        verticalMargin = 0.1F
                    }
                    // Automatically show the keyboard
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                }
            }

        showFullScreenDialog(dialog)

        // Set up the input field
        setupInputField(input, tilt, msg, dialog, inputManager)

        // Set up the migrate button
        dialog.getButton(BUTTON_NEUTRAL).setOnClickListener {
            handleMigrateButtonClick(dialog)
        }
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
            // Handle popup and overlayed buttons visibility
            onPopupVisibilityChanged = { isOverlay ->
                try {
                    dialog?.apply {
                        if (isOverlay) {
                            getButton(BUTTON_NEUTRAL)?.visibility = View.INVISIBLE
                            getButton(BUTTON_NEGATIVE)?.visibility = View.INVISIBLE
                        } else {
                            getButton(BUTTON_NEUTRAL)?.visibility = View.VISIBLE
                            getButton(BUTTON_NEGATIVE)?.visibility = View.VISIBLE
                        }
                    }
                } catch (_: Exception) {
                }
            }
            // Common setup for all API levels
            setOnItemClickListener { _, _, _, _ ->
                try {
                    dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                } catch (_: Exception) {
                }
            }

            setOnKeyListener { view, keyCode, event ->
                try {
                    when {
                        // Handle ENTER key
                        keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP -> {
                            if (isPopupShowing) {
                                dismissDropDown()
                            }
                            if (isAmazonDev)
                                inputManager.showSoftInput(this, 0)
                            else
                                inputManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            true
                        }
                        // Handle BACK key
                        keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP -> {
                            if (!isPopupShowing) {
                                showDropDown()
                            }
                            inputManager.hideSoftInputFromWindow(view.windowToken, 0)
                            true
                        }
                        // For other keys or action phases
                        else -> false
                    }
                } catch (_: Exception) {
                    false
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                try {
                    when (actionId) {
                        EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
                            inputManager.hideSoftInputFromWindow(windowToken, 0)
                            dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                            true
                        }

                        else -> false
                    }
                } catch (_: Exception) {
                    false
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
        return URL_PATTERN.matcher(url).matches()
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

    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject) {
        logDebug("runPlayer(jsonObject) - add lampaActivity to params")
        runPlayer(jsonObject, "", lampaActivity)
    }

    fun displaySpeechRecognizer() {
        if (VERSION.SDK_INT < 18) {
            if (!SpeechRecognizer.isRecognitionAvailable(baseContext)) {
                logDebug("SpeechRecognizer not available!")
            } else {
                logDebug("SpeechRecognizer available!")
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
                    if (isAmazonDev)
                        inputManager?.showSoftInput(this, 0)
                    else
                        inputManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
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
                    logDebug("appLang = $appLang")
                    logDebug("langTag = $langTag")
                    logDebug("locale = $locale")
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
        if (browserInitComplete && loaderView.isGone) {
            logDebug("runVoidJsFunc $funcName")
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
            logDebug("runVoidJsFunc add to delayedVoidJsFunc $funcName")
            delayedVoidJsFunc.add(listOf(funcName, params))
        }
    }

    /**
     *  AI roxxx
     */

    @SuppressLint("InflateParams")
    fun runPlayer(jsonObject: JSONObject, launchPlayer: String = "", activity: String? = null) {
        try {
            val playActivity = activity?.takeIf { it.isNotEmpty() } ?: lampaActivity
            val videoUrl = jsonObject.optString("url").takeIf { it.isNotBlank() } ?: run {
                App.toast(R.string.invalid_url, true)
                return
            }

            // Pre-compute frequently used values
            val isIPTV = jsonObject.optBoolean("iptv", false)
            val isLIVE = jsonObject.optBoolean("need_check_live_stream", false)
            val isContinueWatch = jsonObject.optBoolean("from_state", false)
            val selectedPlayer = launchPlayer.takeIf { it.isNotBlank() }
                ?: if (isIPTV || isLIVE) tvPlayer else appPlayer
            val videoTitle =
                jsonObject.optString("title", if (isIPTV) "LAMPA TV" else "LAMPA video")
            val card = getCardFromActivity(playActivity)

            // Headers handling
            var headers = prepareHeaders(jsonObject)

            val state = if (isContinueWatch && card != null) {
                // Get most recent state for card
                playerStateManager.findStateByCard(card)?.also {
                    headers = getHeadersFromState(it) ?: headers
                }
            } else {
                // Create new state
                val playlist = try {
                    when {
                        jsonObject.has("playlist") && playerAutoNext ->
                            playerStateManager.convertJsonToPlaylist(jsonObject.getJSONArray("playlist"))

                        else -> listOf(playerStateManager.convertJsonToPlaylistItem(jsonObject))
                    }
                } catch (e: Exception) {
                    logDebug("Error converting playlist: ${e.message}")
                    listOf(playerStateManager.convertJsonToPlaylistItem(jsonObject))
                }
                // safe start index
                val currentIndex = playlist.indexOfFirst { it.url == videoUrl }.coerceAtLeast(0)
                // fill required extras
                val extras = mutableMapOf<String, Any>().apply {
                    put("isIPTV", isIPTV)
                    put("isLIVE", isLIVE)
                    // NOTE: it used in PlayerStateManager for state match
                    activity?.let { put("lampaActivity", it) }
                    // Store headers as raw string array to extras
                    headers?.let { put("headers_array", it.asList()) }
                }
                // save new state
                playerStateManager.saveState(
                    activityJson = playActivity,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    currentUrl = videoUrl,
                    startIndex = currentIndex,
                    extras = extras,
                    card = card
                )
            }
            state?.let {
                createBaseIntent(it)?.let {
                    // Get available players
                    val availablePlayers =
                        getAvailablePlayers(it).takeIf { it.isNotEmpty() } ?: run {
                            App.toast(R.string.no_player_activity_found, true)
                            return
                        }
                    // Check if selected player exists
                    if (selectedPlayer != null && availablePlayers.any {
                            it.activityInfo.packageName.equals(selectedPlayer, true)
                        }) {
                        // Configure and launch the selected player
                        configurePlayerIntent(
                            it,
                            selectedPlayer,
                            videoTitle,
                            isIPTV,
                            state,
                            headers
                        )
                        // Launch Player
                        launchPlayer(it)
                    } else {
                        // Show player selection dialog
                        showPlayerSelectionDialog(availablePlayers, jsonObject, isIPTV)
                    }
                }
            }
        } catch (e: Exception) {
            logDebug("Unexpected error: ${e.message}")
        }
    }

    private fun getHeadersFromState(state: PlayerStateManager.PlaybackState): Array<String>? {
        return (state.extras["headers_array"] as? List<*>)?.filterIsInstance<String>()
            ?.toTypedArray()
    }

    private fun createBaseIntent(
        state: PlayerStateManager.PlaybackState,
    ): Intent? {
        state.currentItem?.let { currentItem ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = currentItem.url.toUri()
                setDataAndType(
                    currentItem.url.toUri(),
                    /* if (currentItem.url.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else */
                    "video/*"
                )
                flags = 0 // Clear any default flags
            }
            return intent
        }
        return null
    }

    private fun getAvailablePlayers(intent: Intent): List<ResolveInfo> {
        return if (VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .filterNot { info ->
                    info.activityInfo.packageName.lowercase() in PLAYERS_BLACKLIST
                }
        } else {
            packageManager.queryIntentActivities(intent, 0) // PackageManager.MATCH_DEFAULT_ONLY
                .filterNot { info ->
                    info.activityInfo.packageName.lowercase() in PLAYERS_BLACKLIST
                }
        }
    }

    private fun configurePlayerIntent(
        intent: Intent,
        playerPackage: String,
        videoTitle: String,
        isIPTV: Boolean,
        state: PlayerStateManager.PlaybackState,
        headers: Array<String>? = null,
    ) {
        val position = getPlaybackPosition(state)

        when (playerPackage.lowercase()) {
            // UPlayer
            in UPLAYER_PACKAGES -> {
                configureUPlayerIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position
                )
            }
            // DDD Video Player
            in DDD_PLAYER_PACKAGES -> {
                configureDddPlayerIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    headers = headers
                )
            }
            // MX Player
            in MX_PACKAGES -> {
                configureMxPlayerIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    headers = headers
                )
            }
            // MPV
            "is.xyz.mpv" -> {
                configureMpvIntent(
                    intent,
                    playerPackage,
                    state = state,
                    position,
                    headers = headers

                )
            }
            // VLC
            "org.videolan.vlc" -> {
                configureVlcIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    // headers = headers
                )
            }
            // Just Player
            "com.brouken.player" -> {
                configureBroukenPlayerIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    // headers = headers
                )
            }
            // ViMu
            in VIMU_PACKAGES -> {
                configureViMuIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    isIPTV,
                    headers = headers
                )
            }
            // Exo Variants
            in EXO_PLAYER_PACKAGES -> {
                configureExoPlayerIntent(
                    intent,
                    playerPackage,
                    state = state,
                    videoTitle,
                    position,
                    headers = headers
                )
            }
            // All others
            else -> { // Generic
                intent.setPackage(playerPackage)
                // Common title
                intent.putExtra(Intent.EXTRA_TITLE, videoTitle)
                intent.putExtra("title", videoTitle) // fallback
                // Try to add headers to unknown players as a fallback
                headers?.let { intent.putExtra("headers", it) }
                // App playback position
                when {
                    playerTimeCode == "continue" || playerTimeCode == "again" && position > 0 -> {
                        intent.putExtra("position", position.toInt())
                        // Add precise position if available from state
                        state.currentItem?.timeline?.time?.toLong()?.let {
                            intent.putExtra("precise_position", it)
                        }
                    }
                }
                // Handle quality variants if available
                state.currentItem?.quality?.takeIf { it.isNotEmpty() }?.let { qualities ->
                    intent.putExtra("quality_levels", qualities.keys.toTypedArray())
                    intent.putExtra(
                        "quality_urls",
                        qualities.values.map { it.toUri() }.toTypedArray()
                    )
                }
            }
        }
    }

    private fun prepareHeaders(jsonObject: JSONObject): Array<String>? {
        val headers = mutableListOf<String>()
        var hasHeaders = false

        // Process headers from JSON
        jsonObject.optJSONObject("headers")?.let { headersObj ->
            val keys = headersObj.keys()
            var hasCustomUserAgent = false

            while (keys.hasNext()) {
                val key = keys.next()
                when (key.lowercase(Locale.getDefault())) {
                    "user-agent" -> {
                        headers.add(key)
                        headers.add(headersObj.optString(key))
                        hasCustomUserAgent = true
                        hasHeaders = true
                    }

                    "content-length" -> continue  // Skip
                    else -> {
                        headers.add(key)
                        headers.add(headersObj.optString(key))
                        hasHeaders = true
                    }
                }
            }
            // Add default User-Agent only if not already specified
            if (!hasCustomUserAgent) {
                HttpHelper.userAgent?.let {
                    headers.add("User-Agent")
                    headers.add(it)
                    hasHeaders = true
                }
            }
        } ?: HttpHelper.userAgent?.let {  // No headers object case
            headers.add("User-Agent")
            headers.add(it)
            hasHeaders = true
        }

        return if (hasHeaders) headers.toTypedArray() else null
    }


    private fun getPlaybackPosition(state: PlayerStateManager.PlaybackState): Long {
        return if (playerTimeCode == "continue") {
            state.currentItem?.timeline?.let { timeline ->
                (timeline.time * 1000).toLong()
            } ?: state.currentPosition
        } else {
            0L
        }
    }

    // https://github.com/androidx/media/blob/release/demos/main/src/main/java/androidx/media3/demo/main/IntentUtil.java
    // https://github.com/androidx/media/blob/release/demos/main/src/main/java/androidx/media3/demo/main/PlayerActivity.java
    private fun configureExoPlayerIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        headers: Array<String>? = null
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)
            // putExtra("secure_uri", true)
            // headers?.let { putExtra("headers", it) }
            // Handle playback position
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position)

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) ->
                    putExtra("position", 0L)
            }
            // Handle current video URL
            state.currentItem?.let { currentItem ->
                // Handle subtitles from state
                currentItem.subtitles?.takeIf { it.isNotEmpty() }?.let { subtitles ->
                    if (playerPackage == "com.google.android.exoplayer2.demo") {
                        putExtra(
                            "subtitle_uris", // arrayOf("http://example.com/sub.srt"))
                            subtitles.map { it.url.toUri().toString() }.toTypedArray()
                        )
                        putExtra(
                            "subtitle_labels", // arrayOf("English"))
                            subtitles.mapIndexed { index, item ->
                                item.label.takeIf { it.isNotEmpty() }
                                    ?: "Sub ${
                                        item.language?.uppercase().takeIf { !it.isNullOrEmpty() }
                                            ?: "${index + 1}"
                                    }"
                            }.toTypedArray()
                        )
                    } else { // v3 Subtitles (single track)
                        subtitles.firstOrNull()?.let { sub ->
                            putExtra("subtitle_uri", sub.url)
                            putExtra("subtitle_mime_type", "text/vtt") // Set actual MIME type
                            putExtra("subtitle_language", sub.language ?: "") // N/A
                        }
                    }
                }
            }
            // Handle playlist if available (Old format)
            if (state.playlist.size > 1) {
                if (playerPackage == "com.google.android.exoplayer2.demo") {
                    intent.putExtra(
                        "media_uris", // String[]
                        state.playlist.map { it.url.toUri().toString() }.toTypedArray()
                    )
                    intent.putExtra(
                        "media_titles", // String[]
                        state.playlist.mapIndexed { index, item ->
                            item.title ?: "Video ${index + 1}"
                        }
                            .toTypedArray()
                    )
                    intent.putExtra( // Int
                        "start_index",
                        state.currentIndex.coerceIn(0, state.playlist.size - 1)
                    )
                } else { // v3
                    // Playlist (indexed format)
                    state.playlist.forEachIndexed { index, item ->
                        intent.putExtra("uri_$index", item.url.toUri().toString())
                        intent.putExtra(
                            "title_$index",
                            item.title.takeIf { !it.isNullOrEmpty() } ?: "Video ${index + 1}")
                    }
                }
            }
        }
    }

    private fun configureDddPlayerIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        headers: Array<String>? = null
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)
            putExtra("return_result", true)

            // Headers
            headers?.let { putExtra("headers", it) }

            // Position
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position.toInt())
                playerTimeCode == "again" ->
                    putExtra("position", 0)
            }

            // Playlist Logic
            if (state.playlist.size > 1) {
                val urls = ArrayList<Uri>()
                val titles = ArrayList<String>()
                val filenames = ArrayList<String>()
                val posters = ArrayList<String>()
                val subtitlesList = ArrayList<Bundle>()

                state.playlist.forEach { item ->
                    urls.add(item.url.toUri())
                    titles.add(item.title ?: "")
                    filenames.add(item.url.toUri().lastPathSegment ?: "")
                    // todo добавить постер
                    val poster = ""
                    posters.add(poster)

                    // Per-item subtitles
                    val itemSubsBundle = Bundle()
                    item.subtitles?.takeIf { it.isNotEmpty() }?.let { subs ->
                        val subUris = subs.map { it.url.toUri() }.toTypedArray()
                        val subNames = subs.map { it.label ?: "Sub" }.toTypedArray()
                        itemSubsBundle.putParcelableArray("uris", subUris)
                        itemSubsBundle.putStringArray("names", subNames)
                    }
                    subtitlesList.add(itemSubsBundle)
                }

                state.currentItem?.let { item ->
                    setDataAndType(item.url.toUri(), "video/*")
                }

                // ВАЖНО: Передаем как TypedArray (Parcelable[]), так как IntentUtils использует getParcelableArray
                putExtra("video_list", urls.toTypedArray())
                putStringArrayListExtra("video_list.name", titles)
                putStringArrayListExtra("video_list.filename", filenames)
                putStringArrayListExtra("video_list.poster", posters)
                // ArrayList<Bundle> передается нормально через putParcelableArrayListExtra
                putParcelableArrayListExtra("video_list.subtitles", subtitlesList)

            } else {
                // Single Video Logic
                state.currentItem?.let { item ->
                    setDataAndType(item.url.toUri(), "video/*")
                    putExtra("filename", item.url.toUri().lastPathSegment)
                    putExtra("poster", "") // todo Lampa add image

                    // Subtitles for single video
                    item.subtitles?.takeIf { it.isNotEmpty() }?.let { subs ->
                        val subUris = subs.map { it.url.toUri() }.toTypedArray()
                        val subNames = subs.map { it.label ?: "Sub" }.toTypedArray()

                        putExtra("subs", subUris)
                        putExtra("subs.name", subNames)
                    }
                }
            }
        }
    }

    private fun configureMxPlayerIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        headers: Array<String>? = null
    ) {
        intent.apply {
            component = ComponentName(
                playerPackage,
                "$playerPackage.ActivityScreen"
            )
            putExtra("title", videoTitle)
            putExtra("sticky", false)
            headers?.let { putExtra("headers", it) }
            // Handle playback position
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position.toInt())

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) ->
                    putExtra("position", 1)
            }
            // Handle playlist from state
            if (state.playlist.isNotEmpty()) {
                val urls = state.playlist.map { it.url }
                val titles = state.playlist.mapIndexed { index, item ->
                    item.title ?: "Item ${index + 1}" // Fallback to "Item 1", "Item 2", etc.
                }

                putExtra("video_list", urls.map(Uri::parse).toTypedArray())
                putExtra("video_list.name", titles.toTypedArray())
                putExtra("video_list_is_explicit", true)
            }
            // Handle subtitles from current item
            state.currentItem?.subtitles?.takeIf { it.isNotEmpty() }?.let { subtitles ->
                val subUrls = subtitles.map { it.url }
                val subTitles = subtitles.mapIndexed { index, item ->
                    item.label.takeIf { it.isNotEmpty() } ?: "Sub ${index + 1}"
                }

                putExtra("subs", subUrls.map(Uri::parse).toTypedArray())
                putExtra("subs.name", subTitles.toTypedArray())
            }
            putExtra("return_result", true)
        }
    }

    // VLC Player configuration with state integration
    // https://code.videolan.org/videolan/vlc-android/-/blob/master/application/resources/src/main/java/org/videolan/resources/Constants.kt
    // https://code.videolan.org/videolan/vlc-android/-/blob/master/application/vlc-android/src/org/videolan/vlc/gui/video/VideoPlayerActivity.kt
    private fun configureVlcIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        // headers: Array<String>? = null
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
            // Basic video info
            putExtra("title", videoTitle)
            // Headers
            // headers?.let { putExtra("http-headers", it) }
            // Handle playback position
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
            // Add duration from current item's timeline if available
            // state.currentItem?.timeline?.duration?.toLong()?.let {
            //    if (it > 0) putExtra("extra_duration", it)
            // }
            // Handle subtitles from state
            // state.currentItem?.subtitles?.firstOrNull()?.let { firstSub ->
            // putExtra("subtitles_location", firstSub.url)
            // For VLC 3.5+ that supports multiple subtitles (this is for local URIs)
            // if (VERSION.SDK_INT >= 30 && state.currentItem?.subtitles?.size!! > 1) {
            //    putExtra(
            //        "subtitles_extra",
            //        state.currentItem?.subtitles?.drop(1)?.map { Uri.parse(it.url).toString() }?.toTypedArray()
            //    )
            // }
            // }
        }
    }

    // MPV Player configuration with state integration
    // https://github.com/pepeloni-away/mpv-android/blob/2b28598fd9f5ba8fd54652e3aee54b1b05ef936c/app/src/main/java/is/xyz/mpv/MPVActivity.kt#L966-L990
    private fun configureMpvIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        position: Long,
        headers: Array<String>? = null
    ) {
        intent.apply {
            setPackage(playerPackage)
            // Handle headers with MPV's required format
            headers?.let {
                val headerString = it.toList()
                    .chunked(2)
                    .joinToString("\r\n") { (k, v) -> "$k: $v" }
                putExtra("headers", headerString)
            }
            // Handle playback position
            when {
                playerTimeCode == "continue" && position > 0 ->
                    putExtra("position", position.toInt())

                playerTimeCode == "again" || (playerTimeCode == "continue" && position == 0L) ->
                    putExtra("position", 1)
            }
            // Handle subtitles from state
            state.currentItem?.subtitles?.takeIf { it.isNotEmpty() }?.let { subs ->
                // MPV can handle multiple subtitle tracks
                putExtra("subs", subs.map { it.url.toUri() }.toTypedArray()) // Parcelable[]
                // Add language information if available
                subs.mapNotNull { it.language }.takeIf { it.isNotEmpty() }?.let { langs ->
                    putExtra("subs_langs", langs.toTypedArray())
                }
            }
        }
    }

    private fun configureViMuIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        isIPTV: Boolean,
        headers: Array<String>? = null
    ) {
        val vimuVersion = getAppVersion(this, playerPackage)?.versionNumber ?: 0L
        logDebug("ViMu ($playerPackage) version $vimuVersion")
        intent.apply {
            setPackage(playerPackage)
            headers?.let { putExtra("headers", it) }
            // Handle playlist
            when {
                state.playlist.size > 1 -> {
                    state.currentItem?.url?.let { url ->
                        setDataAndType(url.toUri(), "application/vnd.gtvbox.filelist")
                        configureViMuPlaylist(this, state, vimuVersion)
                    }
                }

                !state.playlist.isEmpty() -> {
                    configureSingleViMuItem(this, state, videoTitle, isIPTV)
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
        state: PlayerStateManager.PlaybackState,
        vimuVersion: Long
    ) {
        val urls = state.playlist.map { it.url.toUri().toString() }
        val titles = state.playlist.mapIndexed { index, item ->
            item.title ?: "Item ${index + 1}" // Fallback to "Item 1", "Item 2", etc.
        }
        val safeIndex = state.currentIndex.coerceIn(0, urls.size - 1)

        if (vimuVersion >= 799L) { // 7.99+ version
            intent.apply {
                putStringArrayListExtra("asusfilelist", ArrayList(urls))
                putStringArrayListExtra("asusnamelist", ArrayList(titles))
                putExtra("startindex", safeIndex)
            }
        } else { // Legacy version
            intent.apply {
                putStringArrayListExtra(
                    "asusfilelist",
                    ArrayList(urls.subList(safeIndex, urls.size))
                )
                putStringArrayListExtra(
                    "asusnamelist",
                    ArrayList(titles.subList(safeIndex, urls.size))
                )
            }
        }
    }

    private fun configureSingleViMuItem(
        intent: Intent,
        state: PlayerStateManager.PlaybackState,
        fallbackTitle: String,
        isIPTV: Boolean
    ) {
        state.currentItem?.let { currentItem ->
            intent.apply {
                putExtra("forcename", currentItem.title ?: fallbackTitle)
                currentItem.subtitles?.takeIf { it.isNotEmpty() }?.let { subtitles ->
                    putStringArrayListExtra(
                        "asussrtlist",
                        subtitles.map { it.url.toUri().toString() }.toCollection(ArrayList())
                    )
                }
                if (isIPTV) {
                    putExtra("forcelive", true)
                } else {
                    removeExtra("forcelive")
                }
            }
        }
    }

    private fun configureBroukenPlayerIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        // headers: Array<String>? = null,
        additionalExtras: Bundle? = null
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)
            // headers?.let { putExtra("headers", it) }
            // Handle playback position
            when {
                playerTimeCode == "continue" || playerTimeCode == "again" -> {
                    putExtra("position", position.toInt())
                }
            }
            // Handle current media item
            state.currentItem?.let { currentItem ->
                // Handle subtitles from state
                currentItem.subtitles?.takeIf { it.isNotEmpty() }?.let { subtitles ->
                    putExtra(
                        "subs",
                        subtitles.map { it.url.toUri() }.toTypedArray()
                    ) // Parcelable[]
                    putExtra("subs.name", subtitles.mapIndexed { index, item -> // String[]
                        item.label.takeIf { it.isNotEmpty() } ?: "Sub ${index + 1}"
                    }.toTypedArray())
                    // Add language codes if available
                    // subtitles.mapNotNull { it.language }.takeIf { it.isNotEmpty() }?.let { langs ->
                    //    putExtra("subs.lang", langs.toTypedArray())
                    // }
                }
            }
            // Additional custom extras
            additionalExtras?.let { putExtras(it) }
            // Common Brouken player flags
            putExtra("return_result", true)
        }
    }

    private fun configureUPlayerIntent(
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long
    ) {
        intent.apply {
            setPackage(playerPackage)
            putExtra("title", videoTitle)
            // Handle resume/restart
            when (playerTimeCode) {
                "continue", "again" -> putExtra("resume", position)
            }
            // Check for multi-quality streams or playlist
            val hasQualityVariants = state.currentItem?.quality?.isNotEmpty() == true
            val hasPlaylist = state.playlist.size > 1
            // Handle playlists or multi-quality streams
            if (hasQualityVariants || hasPlaylist) {
                configureUPlayerPlaylist(this, videoTitle, state)
            } else {
                // Single video fallback
                state.currentItem?.let { currentItem ->
                    putExtra("video", currentItem.url)
                }
            }
        }
    }

    private fun configureUPlayerPlaylist(
        intent: Intent,
        videoTitle: String,
        state: PlayerStateManager.PlaybackState
    ) {
        // Handle quality variants from current item
        state.currentItem?.quality?.let { qualityMap ->
            if (qualityMap.isNotEmpty()) {
                intent.apply {
                    // Set Title
                    putStringArrayListExtra("titleList", arrayListOf(videoTitle))
                    // Add Quality
                    putStringArrayListExtra("videoGroupList", ArrayList(qualityMap.keys))
                    qualityMap.forEach { (key, url) ->
                        putStringArrayListExtra(key, arrayListOf(url))
                    }
                    // Find and set current index
                    val qualityIndex = qualityMap.values.indexOfFirst { url ->
                        url == state.currentItem?.url
                    }.takeIf { it != -1 } ?: 0
                    putExtra("groupPosition", qualityIndex)
                }
                return
            }
        }
        // Fallback to normal playlist handling
        if (state.playlist.size > 1) {
            // Get first item's hash from state
            val firstHash = state.playlist.firstOrNull()?.timeline?.hash ?: "0"
            if (firstHash != "0") {
                intent.putExtra("playlistTitle", firstHash)
            }

            val urls = ArrayList<String>()
            val titles = ArrayList<String>()

            state.playlist.forEach { item ->
                urls.add(item.url.toUri().toString())
                titles.add(item.title ?: "Item ${state.playlist.indexOf(item) + 1}")
            }

            intent.apply {
                putStringArrayListExtra("videoList", urls)
                putStringArrayListExtra("titleList", titles)
                putExtra("playlistPosition", state.currentIndex)
            }
        }
    }

    private fun launchPlayer(intent: Intent) {
        try {
            debugLogIntentData(TAG, intent)
            resultLauncher.launch(intent)
        } catch (e: Exception) {
            logDebug("Failed to launch player: ${e.message}")
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

        @SuppressLint("InflateParams")
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

    /**
     * Processes playback results and updates the player state accordingly.
     *
     * This function handles:
     * 1. Identifying the current playing item in the playlist
     * 2. Updating its playback position and completion status
     * 3. Marking previous items as completed (if auto-next is enabled)
     * 4. Sending timeline updates to the UI
     * 5. Sending updates to WatchNext channel on Android TV
     * 6. Clearing state when playback ends
     *
     * @param endedVideoUrl The URL of the video that ended playback (may be blank/null for current item)
     * @param positionMillis The current playback position in milliseconds
     * @param durationMillis The total duration of the media in milliseconds
     * @param ended Boolean indicating whether playback has fully completed
     *
     */
    private fun resultPlayer(
        endedVideoUrl: String,
        positionMillis: Int,
        durationMillis: Int,
        ended: Boolean
    ) {
        // Skip invalid updates where position and duration are 0 but playback hasn't ended
        if (!ended && positionMillis == 0 && durationMillis == 0) {
            logDebug("Skipping invalid update - zero position/duration for non-ended playback")
            return
        }
        lifecycleScope.launch {
            // Get current state and resolve which video URL we're processing
            val currentState = playerStateManager.getState(lampaActivity)
            val videoUrl = endedVideoUrl.takeUnless { it.isBlank() || it == "null" }
                ?: currentState.currentUrl
                ?: return@launch  // Exit if no valid URL found
            // Find and update the current playlist item
            val (updatedPlaylist, foundIndex) = updateCurrentPlaylistItem(
                currentState.playlist,
                videoUrl,
                positionMillis,
                durationMillis,
                ended
            ) ?: return@launch  // Exit if current item not found
            // Persist the updated state
            playerStateManager.saveState(
                activityJson = lampaActivity,
                playlist = updatedPlaylist,
                currentIndex = foundIndex,
                currentUrl = videoUrl,
                currentPosition = positionMillis.toLong(),
                startIndex = currentState.startIndex, // Maintain original starting point
                extras = currentState.extras // Don't loose extras
            )
            // Handle automatic marking of previous items as complete
            if (playerAutoNext) {
                updatePreviousItemsCompletion(
                    updatedPlaylist,
                    currentState.startIndex,
                    foundIndex
                )
            }
            // Notify UI of current item's timeline update
            updatedPlaylist[foundIndex].timeline?.let { timeline ->
                runVoidJsFunc(
                    "Lampa.Timeline.update",
                    playerStateManager.convertTimelineToJsonString(timeline)
                )
            }
            // Update PlayNext (must finish before clearState)
            // Critical section - ensure ordering
            if (ended) {
                val updateJob = launch(Dispatchers.Default) {
                    updatePlayNext(true) // Runs in background
                }
                updateJob.join() // Wait for completion before clearing state
                playerStateManager.clearState(lampaActivity)
            } else {
                launch(Dispatchers.Default) {
                    updatePlayNext(false) // Fire-and-forget for non-ended case
                }
            }
        }
    }

    /**
     * Updates the current playing item in the playlist and returns the modified playlist with found index
     */
    private fun updateCurrentPlaylistItem(
        playlist: List<PlayerStateManager.PlaylistItem>,
        videoUrl: String,
        positionMillis: Int,
        durationMillis: Int,
        ended: Boolean
    ): Pair<MutableList<PlayerStateManager.PlaylistItem>, Int>? {
        val updatedPlaylist = playlist.toMutableList()
        val foundIndex =
            updatedPlaylist.indexOfFirst { isCurrentPlaybackItem(it, videoUrl) }.takeIf { it >= 0 }
                ?: return null

        updatedPlaylist[foundIndex] = createUpdatedPlaylistItem(
            item = updatedPlaylist[foundIndex],
            positionMillis = positionMillis,
            durationMillis = durationMillis,
            ended = ended
        )

        return updatedPlaylist to foundIndex
    }

    /**
     * Marks items as completed from startIndex to currentIndex-1 (excludes current item)
     * and sends timeline updates for each marked item
     */
    private fun updatePreviousItemsCompletion(
        playlist: MutableList<PlayerStateManager.PlaylistItem>,
        startIndex: Int,
        currentIndex: Int
    ) {
        getCompletionRange(startIndex, currentIndex, playlist.lastIndex).forEach { index ->
            playlist[index] = createCompletedPlaylistItem(playlist[index])

            playlist[index].timeline?.let { timeline ->
                runVoidJsFunc(
                    "Lampa.Timeline.update",
                    playerStateManager.convertTimelineToJsonString(timeline)
                )
                logDebug("Marked item $index as completed (100%)")
            }
        }
    }

    /**
     * Creates a range of indices to mark as completed (startIndex to currentIndex-1)
     */
    private fun getCompletionRange(
        startIndex: Int,
        currentIndex: Int,
        lastValidIndex: Int
    ): IntRange {
        val safeStart = startIndex.coerceIn(0, lastValidIndex)
        val safeEnd = currentIndex.coerceIn(0, lastValidIndex)

        return if (safeStart <= safeEnd) {
            safeStart until safeEnd  // Excludes currentIndex
        } else {
            safeEnd + 1..safeStart   // Reverse range, excludes currentIndex
        }
    }

    /**
     * Creates a new playlist item with updated timeline
     */
    private fun createUpdatedPlaylistItem(
        item: PlayerStateManager.PlaylistItem,
        positionMillis: Int,
        durationMillis: Int,
        ended: Boolean
    ): PlayerStateManager.PlaylistItem {
        val percent = if (durationMillis > 0) (positionMillis * 100 / durationMillis) else 100
        return item.copy(
            timeline = PlayerStateManager.PlaylistItem.Timeline(
                hash = item.timeline?.hash ?: "0",
                time = if (ended) 0.0 else positionMillis / 1000.0,
                duration = if (ended) 0.0 else durationMillis / 1000.0,
                percent = if (ended) 100 else percent
            )
        )
    }

    /**
     * Creates a playlist item marked as 100% completed
     */
    private fun createCompletedPlaylistItem(item: PlayerStateManager.PlaylistItem): PlayerStateManager.PlaylistItem {
        return item.copy(
            timeline = PlayerStateManager.PlaylistItem.Timeline(
                hash = item.timeline?.hash ?: "0",
                time = 0.0, // item.timeline?.duration ?: 0.0,
                duration = 0.0, // item.timeline?.duration ?: 0.0,
                percent = 100
            )
        )
    }

    /**
     * Determines if a playlist item matches the currently playing video URL.
     */
    private fun isCurrentPlaybackItem(
        item: PlayerStateManager.PlaylistItem,
        videoUrl: String
    ): Boolean {
        val normalizedInputUrl = videoUrl.toUri().toString()

        return item.url.toUri().toString() == normalizedInputUrl ||
                item.quality?.values?.any { qualityUrl ->
                    qualityUrl.isNotEmpty() && qualityUrl.toUri()
                        .toString() == normalizedInputUrl
                } == true
    }

    private fun getCardFromActivity(activityJson: String?): LampaCard? {
        return try {
            JSONObject(activityJson ?: return null)
                .optJSONObject("movie")
                ?.let { movieObj ->
                    getJson(movieObj.toString(), LampaCard::class.java)?.apply {
                        fixCard()
                    }
                }
        } catch (e: JSONException) {
            logDebug("Invalid activity JSON: ${e.message}")
            null
        }
    }

    /**
     * Updates Watch Next on Android TV.
     */
    private suspend fun updatePlayNext(ended: Boolean) = withContext(Dispatchers.Default) {
        if (!isTvContentProviderAvailable) return@withContext
        try {
            val card = getCardFromActivity(lampaActivity) ?: return@withContext
            // Get current playback state
            // val state = playerStateManager.getState(lampaActivity)
            // Get state by matching card (must be added with saveState!)
            val state = playerStateManager.findStateByCard(card) ?: return@withContext
            // playerStateManager.debugKeyMatching(lampaActivity)
            when {
                ended -> { // Case 1: Playback ended - remove from Continue Watching
                    logDebug("PlayNext: remove ${card.id} and clearState [ended]")
                    WatchNext.removeContinueWatch(card) // FIXME: don't remove if added to PlayNext by user
                    playerStateManager.clearState(lampaActivity)
                }
                // Case 2: Valid ongoing playback - update Continue Watching
                state.currentItem != null && !state.isEnded -> {
                    logDebug("PlayNext: Updating ${card.id}")
                    WatchNext.addLastPlayed(card, lampaActivity)
                }

                else -> { // Case 3: No valid state - just log
                    logDebug("PlayNext: No valid playback state for ${card.id}")
                }
            }
        } catch (e: Exception) {
            logDebug("Error in updatePlayNext: ${e.javaClass.simpleName} - ${e.message}")
        }
    }
}