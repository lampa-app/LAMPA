package top.rootu.lampa.helpers

import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewCompat
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Prefs.addBookToRemove
import top.rootu.lampa.helpers.Prefs.addHistToRemove
import top.rootu.lampa.helpers.Prefs.addLikeToRemove
import top.rootu.lampa.helpers.Prefs.addWatchNextToAdd
import top.rootu.lampa.helpers.Prefs.addWatchNextToRemove
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.models.WatchNextToAdd
import java.util.Locale


@Suppress("DEPRECATION")
object Helpers {
    // NOTE: as of Oreo you must also add the REQUEST_INSTALL_PACKAGES permission to your manifest. Otherwise it just silently fails
    @JvmStatic
    fun installPackage(context: Context?, packagePath: String?) {
        if (packagePath == null || context == null) {
            return
        }
        val file = FileHelpers.getFileUri(context, packagePath) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(file, "application/vnd.android.package-archive")
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION // without this flag android returned a intent error!
        try {
            context.applicationContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    fun uninstallSelf() {
        App.toast("Hooray!")
        val pm = App.context.packageManager
        pm.setComponentEnabledSetting(
            ComponentName(App.context, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:" + App.context.packageName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        App.context.startActivity(intent)
    }

    fun openLampa(): Boolean {
        val intent = Intent(App.context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        App.context.startActivity(intent)
        return true
    }

    fun openSettings(): Boolean {
        val intent = Intent(App.context, MainActivity::class.java)
        intent.putExtra("cmd", "open_settings")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        App.context.startActivity(intent)
        return true
    }

    fun setLocale(activity: Activity, languageCode: String?) {
        if (BuildConfig.DEBUG) Log.d("APP_MAIN", "set Locale to [$languageCode]")
        val locale = languageCode?.let { Locale(it) } ?: return
        Locale.setDefault(locale)
        val resources: Resources = activity.resources
        val config: Configuration = resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            config.setLocales(LocaleList(locale))
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            config.setLocale(locale)
        else
            config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun Context.setLanguage() {
        if (this.appLang.isNotBlank())
            this.appLang.apply {
                val languageCode = this /* en / ru / zh-TW etc */
                var locale = Locale(languageCode.lowercase(Locale.getDefault()))
                if (languageCode.split("-").size > 1) {
                    val language = languageCode.split("-")[0].lowercase(Locale.getDefault())
                    val country = languageCode.split("-")[1].uppercase(Locale.getDefault())
                    locale = Locale(language, country)
                }
                val config: Configuration = resources.configuration
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    config.setLocales(LocaleList(locale))
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    config.setLocale(locale)
                else
                    config.locale = locale
                resources.updateConfiguration(config, resources.displayMetrics)
            }
    }

    @Suppress("DEPRECATION")
    private fun isConnectedOld(context: Context): Boolean {
        val connManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connManager.activeNetworkInfo
        return networkInfo?.isConnected == true

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isConnectedNewApi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun isConnected(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isConnectedNewApi(context)
        } else {
            isConnectedOld(context)
        }
    }

    fun dp2px(context: Context, dip: Float): Int {
        val dm = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, dm).toInt()
    }

    fun getWebViewVersion(context: Context): String {
        return try {
            var version = WebViewCompat.getCurrentWebViewPackage(context)?.versionName
            if (version.isNullOrEmpty()) version = ""
            version
        } catch (e: Exception) {
            ""
        }
    }

    fun isWebViewAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo(
                WebViewCompat.getCurrentWebViewPackage(
                    context
                )?.packageName!!, 0
            ).enabled
        } catch (e: Exception) {
            Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
        }
    }

    fun buildPendingIntent(card: LampaCard, continueWatch: Boolean?): Intent {
        val intent = Intent(App.context, MainActivity::class.java)
        // TODO: fix this id and media type mess
        val intID = card.id?.toIntOrNull() // required for processIntent()
        intID?.let { intent.putExtra("id", it) }
        intent.putExtra("source", card.source)
        intent.putExtra("media_type", card.type)

        val idStr = try { Gson().toJson(card) } catch (e: Exception) { null } // used to get card from HomeWatch
        idStr?.let { intent.putExtra("LampaCardJS", idStr) }

        continueWatch?.let { intent.putExtra("continueWatch", it) }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.action = card.id.toString()
        return intent
    }

    private val deviceName: String
        get() = String.format("%s (%s)", Build.MODEL, Build.PRODUCT)

    @JvmStatic
    val isBrokenTCL: Boolean
        get() {
            val deviceName = deviceName
            return deviceName.contains("(tcl_m7642)")
        }

    @JvmStatic
    val isGenymotion: Boolean
        get() {
            val deviceName = deviceName
            return deviceName.contains("(vbox86p)")
        }

    @JvmStatic
    val isWisdomShare: Boolean // MTK9255 mt5862 platform
        get() {
            val deviceName = deviceName
            return deviceName.contains("(m7332_eu)")
        }

    private val isHuaweiDevice: Boolean
        get() {
            val manufacturer = Build.MANUFACTURER
            val brand = Build.BRAND
            return manufacturer.lowercase(Locale.getDefault())
                .contains("huawei") || brand.lowercase(
                Locale.getDefault()
            ).contains("huawei")
        }

    val isAndroidTV: Boolean
        get() {
            return App.context.packageManager.hasSystemFeature("android.software.leanback") && !isHuaweiDevice
        }

    val isGoogleTV: Boolean // not accurate
        get() {
            return App.context.packageManager.hasSystemFeature("android.software.leanback") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        }

    val isAmazonDev: Boolean
        get() {
            return App.context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
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

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun hasSAFChooser(pm: PackageManager?): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        return intent.resolveActivity(pm!!) != null
    }

    fun isValidJson(json: String?): Boolean {
        return try {
            parseStrict(json) != null
        } catch (ex: JsonSyntaxException) {
            false
        }
    }

    private fun parseStrict(json: String?): JsonElement? {
        return try {
            // throws on almost any non-valid json
            Gson().getAdapter(JsonElement::class.java).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun manageFavorite(action: String?, where: String, id: String, card: LampaCard? = null) {
        // actions: add | rem
        if (BuildConfig.DEBUG) Log.d("*****", "manageFavorite($action, $where, $id)")
        if (action != null) {
            when (action) {
                "rem" -> {
                    when (where) {
                        LampaProvider.Late -> App.context.addWatchNextToRemove(listOf(id))
                        LampaProvider.Book -> App.context.addBookToRemove(listOf(id))
                        LampaProvider.Like -> App.context.addLikeToRemove(listOf(id))
                        LampaProvider.Hist -> App.context.addHistToRemove(listOf(id))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("*****", "book items to remove: ${App.context.bookToRemove}")
                        Log.d("*****", "like items to remove: ${App.context.likeToRemove}")
                        Log.d("*****", "wath items to remove: ${App.context.wathToRemove}")
                        Log.d("*****", "hist items to remove: ${App.context.histToRemove}")
                    }
                }

                "add" -> {
                    when (where) {
                        LampaProvider.Late -> App.context.addWatchNextToAdd(
                            WatchNextToAdd(
                                id,
                                card
                            )
                        )
                    }
                    if (BuildConfig.DEBUG)
                        Log.d("*****", "wath items to add: ${App.context.wathToAdd}")
                }
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
}
