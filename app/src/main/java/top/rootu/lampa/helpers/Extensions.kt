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
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.CrashActivity
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Prefs.appLang
import java.util.Locale
import kotlin.system.exitProcess


fun Application.handleUncaughtException(showLogs: Boolean? = null) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        /**
        here you can report the throwable exception to Sentry or Crashlytics or whatever crash reporting service you're using,
        otherwise you may set the throwable variable to _ if it'll remain unused
         */
        val errorReport = StringBuilder()
        CoroutineScope(Dispatchers.IO).launch {
            var arr = throwable.stackTrace
            errorReport.append("---------------- Device Info ----------------\n")
            errorReport.append("Model: ${Helpers.deviceName}\n")
            errorReport.append("Android SDK: ${Build.VERSION.SDK_INT}\n")
            errorReport.append("\n---------------- Main Crash ----------------\n")
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
            errorReport.append("- background thread Crash Log ----------------\n")
            val cause: Throwable? = throwable.cause
            if (cause != null) {
                errorReport.append("Main Crash Name - $cause".trimIndent())

                arr = cause.stackTrace
                for (i in arr) {
                    errorReport.append(i)
                    errorReport.append("\n")
                }
            }
            errorReport.append("\n- end of background thread Crash Log ----------------\n\n")

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

/* NOTE! must be called after setContentView */
fun Activity.hideSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window?.insetsController?.let {
            // Default behavior is that if navigation bar is hidden, the system will "steal" touches
            // and show it again upon user's touch. We just want the user to be able to show the
            // navigation bar by swipe, touches are handled by custom code -> change system bar behavior.
            // Alternative to deprecated SYSTEM_UI_FLAG_IMMERSIVE.
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // make navigation bar translucent (alternative to deprecated
            // WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            // - do this already in hideSystemUI() so that the bar
            // is translucent if user swipes it up
            window?.navigationBarColor = getColor(R.color.black_80)
            // Finally, hide the system bars, alternative to View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            // and SYSTEM_UI_FLAG_FULLSCREEN.
            it.hide(WindowInsets.Type.systemBars())
        }
    } else {
        @Suppress("DEPRECATION")
        window?.decorView?.systemUiVisibility = (
                // Hide the nav bar and status bar
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        // Keep the app content behind the bars even if user swipes them up
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // use immersive sticky mode
            window?.decorView?.systemUiVisibility =
                window?.decorView?.systemUiVisibility?.or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)!!
            // make navbar translucent
            window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }
}

/* NOTE! must be called after setContentView */
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
        window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }
}

fun Context.copyToClipBoard(errorData: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("label", errorData)
    clipboardManager.setPrimaryClip(clipData)
}

fun Context.setLanguage() {
    if (appLang.isNotBlank()) {
        val languageCode = appLang
        val locale = languageCode.split("-").let { parts ->
            when (parts.size) {
                1 -> Locale(parts[0].lowercase(Locale.getDefault()))
                else -> Locale(parts[0].lowercase(Locale.getDefault()), parts[1].uppercase(Locale.getDefault()))
            }
        }

        val config: Configuration = resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
        } else {
            config.locale = locale
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}

/**
 * Retrieves the name of the app responsible for the installation of this app.
 * This can help in identifying which market this app was installed from or whether the user
 * sideloaded it using an APK (Package Installer).
 */
fun Context.getAppInstaller(): String {
    val appContext = applicationContext

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

val Context.isAmazonDev: Boolean
    get() {
        return packageManager.hasSystemFeature("amazon.hardware.fire_tv")
    }

val Context.isGoogleTV: Boolean // wide posters
    get() {
        return packageManager.hasSystemFeature("com.google.android.tv") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

@RequiresApi(Build.VERSION_CODES.KITKAT)
fun hasSAFChooser(pm: PackageManager?): Boolean {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "video/*"
    return intent.resolveActivity(pm!!) != null
}