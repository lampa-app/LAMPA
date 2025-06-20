package top.rootu.lampa.helpers

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.Process
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.CrashActivity
import top.rootu.lampa.R
import top.rootu.lampa.browser.Browser
import top.rootu.lampa.helpers.Helpers.hasSAFChooser
import top.rootu.lampa.helpers.Prefs.appLang
import java.util.Locale
import kotlin.system.exitProcess


val Context.isAmazonDev: Boolean
    get() {
        return packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }

val Context.isGoogleTV: Boolean // wide posters
    get() {
        return packageManager.hasSystemFeature("com.google.android.tv") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

val Context.isTvBox: Boolean
    get() {
        val pm = packageManager
        // TV for sure
        val uiModeManager =
            getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        if (isAmazonDev) {
            return true
        }
        // Missing Files app (DocumentsUI) means box (some boxes still have non functional app or stub)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!hasSAFChooser(pm)) {
                return true
            }
        }
        // Legacy storage no longer works on Android 11 (level 30)
        if (Build.VERSION.SDK_INT < 30) {
            // (Some boxes still report touchscreen feature)
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true
            }
            if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
                return true
            }
            if (Build.MANUFACTURER.equals("zidoo", ignoreCase = true)) {
                return true
            }
        }
        // Default: No TV - use SAF
        return false
    }

/**
 * Sets up a global exception handler to catch uncaught exceptions and display them in a crash activity.
 *
 * This function:
 * 1. Collects detailed crash information including device info, stack traces, and root causes
 * 2. Launches a crash reporting activity with the collected information
 * 3. Terminates the app to prevent unstable state
 *
 * @param showLogs Controls whether to display detailed logs in the crash activity.
 *                 If null, uses default behavior from app configuration.
 *
 * Typical usage:
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         handleUncaughtException(showLogs = BuildConfig.DEBUG)
 *     }
 * }
 * ```
 *
 * The crash report includes:
 * - Device model and Android version
 * - Main exception stack trace
 * - Root cause stack trace (if available)
 * - Thread information
 *
 * Note: Call this in your Application class's onCreate() for global exception handling.
 * Remember to initialize your crash reporting tools (Sentry, Crashlytics) before this.
 */
fun Application.handleUncaughtException(showLogs: Boolean? = null) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        /**
        here you can report the throwable exception to Sentry or Crashlytics or whatever crash reporting service you're using,
        otherwise you may set the throwable variable to _ if it'll remain unused
         */
        val errorReport = StringBuilder()

        @Suppress("DEPRECATION")
        val version = try {
            val pInfo = App.context.packageManager.getPackageInfo(App.context.packageName, 0)
            "${pInfo.versionName} (${pInfo.versionCode})"
        } catch (_: Exception) {
            "Unknown"
        }
        CoroutineScope(Dispatchers.IO).launch {
            var arr = throwable.stackTrace
            errorReport.append("---------------- Device Info ----------------\n")
            errorReport.append("| Model: ${Helpers.deviceName}\n")
            errorReport.append("| Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            errorReport.append("| App version: $version\n")
            errorReport.append("\n---------------- Main Crash ----------------\n\n")
            errorReport.append(throwable)
            errorReport.append("\n\n")
            errorReport.append("---------------- Stack Strace ----------------\n\n")
            for (i in arr) {
                errorReport.append(i)
                errorReport.append("\n")
            }
            errorReport.append("\n---------------- end of crash details ----------------\n\n")

            /** If the exception was thrown in a background thread inside
            then the actual exception can be found with getCause*/
            errorReport.append("| background thread crash log\n")
            val cause: Throwable? = throwable.cause
            if (cause != null) {
                errorReport.append("| Main Crash Name - $cause".trimIndent())

                arr = cause.stackTrace
                for (i in arr) {
                    errorReport.append(i)
                    errorReport.append("\n")
                }
            }
            errorReport.append("\n| end of background thread crash log\n\n")

            val intent = Intent(this@handleUncaughtException, CrashActivity::class.java).apply {
                putExtra("errorDetails", errorReport.toString())
                putExtra("isShownLogs", showLogs.toString())
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intent)

            Process.killProcess(Process.myPid())
            exitProcess(2)

        }
    }
}

/**
 * Hides system UI (status bar and navigation bar) for immersive mode.
 *
 * This function provides a consistent immersive experience across all Android versions:
 * - On Android R (API 30+) uses modern WindowInsetsController
 * - On older versions uses deprecated but reliable SYSTEM_UI_FLAG approach
 *
 * Features:
 * - Transient bars that appear on swipe and auto-hide (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
 * - Translucent navigation bar (80% black)
 * - Proper layout handling behind system bars
 * - Sticky immersive mode on KitKat+
 *
 * Must be called after setContentView().
 *
 * Usage:
 * ```
 * override fun onWindowFocusChanged(hasFocus: Boolean) {
 *     super.onWindowFocusChanged(hasFocus)
 *     if (hasFocus) hideSystemUI()
 * }
 * ```
 *
 * Or call in onCreate() after setContentView():
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(R.layout.activity_main)
 *     hideSystemUI()
 * }
 * ```
 */
fun Activity.hideSystemUI() {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            window?.insetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window?.navigationBarColor = ContextCompat.getColor(this, R.color.black_80)
                controller.hide(WindowInsets.Type.systemBars())
            }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            @Suppress("DEPRECATION")
            window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
        else -> {
            // For Android 4.1 (API 16) to 4.3 (API 18)
            @Suppress("DEPRECATION")
            window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
//                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
            // Set up a touch listener to hide the system UI again after interaction
//            @Suppress("DEPRECATION")
//            window?.decorView?.setOnSystemUiVisibilityChangeListener { visibility ->
//                if (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) {
//                    // System UI became visible - hide it again after a delay
//                    window?.decorView?.postDelayed({
//                        @Suppress("DEPRECATION")
//                        window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
//                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                                or View.SYSTEM_UI_FLAG_FULLSCREEN)
//                    }, 2000) // 2 seconds delay before re-hiding
//                }
//            }
        }
    }
}

/**
 * Companion function to show system UI when needed.
 *
 * Must be called after setContentView().
 *
 * Usage: `activity.showSystemUI()`
 */
fun Activity.showSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // show app content in fullscreen, i. e. behind the bars when they are shown (alternative to
        // deprecated View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        window?.setDecorFitsSystemWindows(false)
        // finally, show the system bars
        window?.insetsController?.show(WindowInsets.Type.systemBars())
    } else {
        // Shows the system bars by removing all the flags
        // except for the ones that make the content appear under the system bars.
        @Suppress("DEPRECATION")
        window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
}

/**
 * Copies text to the device clipboard with error handling.
 */
fun Context.copyToClipBoard(errorData: String) {
    try {
        val clipData = ClipData.newPlainText("label", errorData)
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//        // Handle different Android versions
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            // Android 12+ with clipboard access check
//            if (!clipboardManager.isSetPrimaryClipAllowed) {
//                Log.w("Clipboard", "Setting clipboard is not allowed")
//                return
//            }
//        }
        clipboardManager.setPrimaryClip(clipData)
    } catch (e: Exception) {
        Log.e("Clipboard", "Failed to copy to clipboard", e)
    }
}

/**
 * SAFE language configuration that works for both Application and Activity
 * without triggering resource warnings.
 */
fun Context.setLanguage(langCode: String = appLang): Context {
    if (langCode.isEmpty()) return this

    val locale = parseLocale(langCode) ?: return this

    return try {
        val config = Configuration(resources.configuration).apply {
            @Suppress("DEPRECATION")
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> setLocales(LocaleList(locale))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> setLocale(locale)
                else -> this.locale = locale
            }
        }
        when (this) {
            // Application needs direct config update
            is Application -> {
                val res = resources
                @Suppress("DEPRECATION")
                res.updateConfiguration(config, res.displayMetrics)
                this
            }
            // Activities need new context
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    createConfigurationContext(config).apply {
                        // Required for API 25+ to fully apply changes
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            @Suppress("DEPRECATION")
                            resources.updateConfiguration(config, resources.displayMetrics)
                        }
                    }
                } else {
                    this // TODO("VERSION.SDK_INT < JELLY_BEAN_MR1")
                }
            }
        }
    } catch (e: Exception) {
        Log.d("setLanguage($langCode)", "Failed to set language. $e")
        this
    }
}

private fun parseLocale(langCode: String): Locale? {
    return try {
        if (!isValidLanguageCode(langCode))
            null
        else
            langCode.trim().split("-", "_").let { parts ->
                when (parts.size) {
                    1 -> Locale(parts[0])
                    2 -> Locale(parts[0], parts[1])
                    3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Locale.Builder()
                            .setLanguage(parts[0])
                            .setScript(parts[1])
                            .setRegion(parts[2])
                            .build()
                    } else {
                        Locale(parts[0], parts[2])
                    }

                    else -> null
                }
            }
    } catch (_: Exception) {
        null
    }
}

// val VALID_LANGUAGE_CODES = setOf("en", "ru", "uk", "be", "zh", "pt", "bg", "he", "cs")
fun isValidLanguageCode(code: String): Boolean {
    // return code.lowercase() in VALID_LANGUAGE_CODES // Strict validation
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        try {
            val locale = Locale.forLanguageTag(code)
            locale.language.isNotEmpty() && !locale.language.equals("und", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    } else {
        // Fallback to manual ISO 639 check for older Android versions
        code.length in 2..3 && Locale.getISOLanguages()
            .any { it.equals(code, ignoreCase = true) }
    }
}

/**
 * Retrieves the name of the app responsible for the installation of this app.
 * This can help in identifying which market this app was installed from or whether the user
 * sideloaded it using an APK (Package Installer).
 */
fun Context.getAppInstaller(): String {
    val appContext = applicationContext

    @Suppress("DEPRECATION")
    val installerPackageName = try {
        val appPackageManager = appContext.packageManager
        val appPackageName = appContext.packageName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            appPackageManager.getInstallSourceInfo(appPackageName).installingPackageName
        else
            appPackageManager.getInstallerPackageName(appPackageName)
    } catch (e: Exception) {
        e.printStackTrace()
        "--"
    }

    return when (installerPackageName) {
        "com.android.vending" -> "Google Play Store"
        "com.amazon.venezia" -> "Amazon AppStore"
        "com.huawei.appmarket" -> "Huawei AppGallery"
        "ru.vk.store" -> "RuStore"
        "ru.vk.store.tv" -> "RuStoreTV"
        "com.google.android.packageinstaller" -> "Package Installer"
        else -> installerPackageName ?: "Unknown"
    }
}

/**
 * Returns a localized user-friendly error message for common network error codes.
 *
 * This function maps technical network error codes (typically from WebView or network requests)
 * to human-readable strings that can be displayed to users. The messages are localized
 * based on the device's current language settings.
 *
 * @param errorCode The network error code string to translate (e.g., "net::ERR_TIMED_OUT")
 * @return Localized string describing the error in user-friendly terms
 *
 * Supported error codes:
 * - net::ERR_FAILED: Generic failure
 * - net::ERR_TIMED_OUT / net::ERR_CONNECTION_TIMED_OUT: Connection timeout
 * - net::ERR_CONNECTION_CLOSED: Connection closed unexpectedly
 * - net::ERR_CONNECTION_RESET: Connection reset by peer
 * - net::ERR_CONNECTION_REFUSED: Connection refused
 * - net::ERR_CONNECTION_FAILED: General connection failure
 * - net::ERR_NAME_NOT_RESOLVED: DNS resolution failed
 * - net::ERR_ADDRESS_UNREACHABLE: Network address unreachable
 * - net::ERR_NETWORK_ACCESS_DENIED: Network access denied
 * - net::ERR_PROXY_CONNECTION_FAILED: Proxy connection issue
 * - net::ERR_INTERNET_DISCONNECTED: No internet connection
 * - net::ERR_TOO_MANY_REDIRECTS: Redirect loop detected
 * - net::ERR_EMPTY_RESPONSE: Server returned empty response
 * - Various SSL-related errors
 * - Various header-related errors
 * - Administrative blocks
 *
 * Example usage:
 * ```
 * val errorMessage = context.getNetworkErrorString("net::ERR_TIMED_OUT")
 * Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
 * ```
 */
fun Context.getNetworkErrorString(errorCode: String): String {
    return when (errorCode) {
        "net::ERR_FAILED" -> getString(R.string.net_error_failed)
        "net::ERR_TIMED_OUT", "net::ERR_CONNECTION_TIMED_OUT" -> getString(R.string.net_error_timed_out)
        "net::ERR_CONNECTION_CLOSED" -> getString(R.string.net_error_connection_closed)
        "net::ERR_CONNECTION_RESET" -> getString(R.string.net_error_connection_reset)
        "net::ERR_CONNECTION_REFUSED" -> getString(R.string.net_error_connection_refused)
        "net::ERR_CONNECTION_FAILED" -> getString(R.string.net_error_connection_failed)
        "net::ERR_NAME_NOT_RESOLVED" -> getString(R.string.net_error_name_not_resolved)
        "net::ERR_ADDRESS_UNREACHABLE" -> getString(R.string.net_error_address_unreachable)
        "net::ERR_NETWORK_ACCESS_DENIED" -> getString(R.string.net_error_network_access_denied)
        "net::ERR_PROXY_CONNECTION_FAILED" -> getString(R.string.net_error_proxy_connection_failed)
        "net::ERR_INTERNET_DISCONNECTED" -> getString(R.string.net_error_internet_disconnected)
        "net::ERR_TOO_MANY_REDIRECTS" -> getString(R.string.net_error_too_many_redirects)
        "net::ERR_EMPTY_RESPONSE" -> getString(R.string.net_error_empty_response)
        "net::ERR_RESPONSE_HEADERS_MULTIPLE_CONTENT_LENGTH" -> getString(R.string.net_error_multiple_content_length)
        "net::ERR_RESPONSE_HEADERS_MULTIPLE_CONTENT_DISPOSITION" -> getString(R.string.net_error_multiple_content_disposition)
        "net::ERR_RESPONSE_HEADERS_MULTIPLE_LOCATION" -> getString(R.string.net_error_multiple_location)
        "net::ERR_CONTENT_LENGTH_MISMATCH" -> getString(R.string.net_error_content_length_mismatch)
        "net::ERR_INCOMPLETE_CHUNKED_ENCODING" -> getString(R.string.net_error_incomplete_chunked_encoding)
        "net::ERR_SSL_PROTOCOL_ERROR" -> getString(R.string.net_error_ssl_protocol_error)
        "net::ERR_SSL_UNSAFE_NEGOTIATION" -> getString(R.string.net_error_ssl_unsafe_negotiation)
        "net::ERR_BAD_SSL_CLIENT_AUTH_CERT" -> getString(R.string.net_error_bad_ssl_client_auth_cert)
        "net::ERR_SSL_WEAK_SERVER_EPHEMERAL_DH_KEY" -> getString(R.string.net_error_ssl_weak_server_key)
        "net::ERR_SSL_PINNED_KEY_NOT_IN_CERT_CHAIN" -> getString(R.string.net_error_ssl_pinned_key_missing)
        "net::ERR_TEMPORARILY_THROTTLED" -> getString(R.string.net_error_temporarily_throttled)
        "net::ERR_BLOCKED_BY_CLIENT" -> getString(R.string.net_error_blocked_by_client)
        "net::ERR_NETWORK_CHANGED" -> getString(R.string.net_error_network_changed)
        "net::ERR_BLOCKED_BY_ADMINISTRATOR" -> getString(R.string.net_error_blocked_by_admin)
        "net::ERR_BLOCKED_ENROLLMENT_CHECK_PENDING" -> getString(R.string.net_error_blocked_enrollment_check)
        else -> getString(R.string.net_error_failed) // Default fallback
    }
}

/**
 * Checks whether a [View] is currently attached to a window in a backward-compatible way.
 *
 * This is a safer alternative to [View.isAttachedToWindow], which is only available in API 19+ (Android 4.4+).
 * On older devices (API < 19), it checks the view's [View.windowToken] as a fallback.
 *
 * @return `true` if the view is attached to a window, `false` otherwise.
 *
 * Usage example:
 * ```kotlin
 * if (myView.isAttachedToWindowCompat()) {
 *     // Safe to interact with the view
 * }
 * ```
 */
fun View.isAttachedToWindowCompat(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        this.isAttachedToWindow
    } else {
        // Fallback for pre-KitKat: Check if the view has a window token
        this.windowToken != null
    }
}

/**
 * Checks if a WebView (or any View) is detached from window safely
 * Works on all Android versions
 */
fun View.isDetachedFromWindowCompat(): Boolean {
    return when {
        // Modern API (KitKat 4.4+)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> !isAttachedToWindow
        // Legacy API fallback
        else -> {
            @Suppress("DEPRECATION")
            windowToken == null || parent == null
        }
    }
}

/**
 * Checks if the Browser instance is safe to use by verifying:
 * 1. The browser is not null
 * 2. The underlying view exists and isn't destroyed
 * 3. The view is properly attached to a window
 *
 * @return true if the browser can be safely used, false otherwise
 */
fun Browser?.isSafeForUse(): Boolean {
    // Early return for null browser
    if (this == null) return false

    val view = this.getView()

    // Check view existence and attachment first
    if (view == null || !view.isAttachedToWindowCompat()) {
        return false
    }
    return !this.isDestroyed
}

fun String.capitalizeFirstLetter(): String {
    return replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale(App.context.appLang)) else it.toString() // Locale.getDefault()
    }
}