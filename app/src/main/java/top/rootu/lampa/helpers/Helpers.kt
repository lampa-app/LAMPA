package top.rootu.lampa.helpers

import android.app.Activity
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
import androidx.annotation.RequiresApi
import androidx.webkit.WebViewCompat
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Prefs.addBookToRemove
import top.rootu.lampa.helpers.Prefs.addContToRemove
import top.rootu.lampa.helpers.Prefs.addHistToRemove
import top.rootu.lampa.helpers.Prefs.addLikeToRemove
import top.rootu.lampa.helpers.Prefs.addLookToRemove
import top.rootu.lampa.helpers.Prefs.addSchdToRemove
import top.rootu.lampa.helpers.Prefs.addThrwToRemove
import top.rootu.lampa.helpers.Prefs.addViewToRemove
import top.rootu.lampa.helpers.Prefs.addWatchNextToAdd
import top.rootu.lampa.helpers.Prefs.addWatchNextToRemove
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.helpers.Prefs.contToRemove
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.lookToRemove
import top.rootu.lampa.helpers.Prefs.schdToRemove
import top.rootu.lampa.helpers.Prefs.thrwToRemove
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.models.CubBookmark
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
        }
    }

    fun buildPendingIntent(card: LampaCard, continueWatch: Boolean?): Intent {
        val intent = Intent(App.context, MainActivity::class.java)
        // TODO: fix this id and media type mess
        val intID = card.id?.toIntOrNull() // required for processIntent()
        intID?.let { intent.putExtra("id", it) }
        intent.putExtra("source", card.source)
        intent.putExtra("media", card.type)

        val idStr = try {
            Gson().toJson(card)
        } catch (_: Exception) {
            null
        } // used to get card from HomeWatch
        idStr?.let { intent.putExtra("LampaCardJS", idStr) }

        continueWatch?.let { intent.putExtra("continueWatch", it) }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.action = card.id.toString()
        return intent
    }

    val deviceName: String
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

    private val isBrokenATV: Boolean
        get() {
            val bb = hashSetOf(
                "55u730gu",
                "ax95",
                "b861re",
                "b866",
                "box q",
                "dv8235",
                "leap-s1",
                "redbox mini 616",
                "s7xx",
                "sberbox",
                "sbdv-00006",
                "streaming box 8000",
                "vidaa_tv"
            )
            val match = bb.any { deviceName.lowercase().contains(it, ignoreCase = true) }
            return match
        }

    val isAndroidTV: Boolean
        get() {
            return App.context.packageManager.hasSystemFeature("android.software.leanback") && !isHuaweiDevice && !isBrokenATV
        }

    /**
     * Checks if a JSON string is valid.
     *
     * @param json The JSON string to validate.
     * @return True if the JSON is valid, false otherwise.
     */
    fun isValidJson(json: String?): Boolean {
        return try {
            parseStrict(json) != null
        } catch (_: JsonSyntaxException) {
            false
        }
    }

    /**
     * Parses a JSON string strictly.
     *
     * @param json The JSON string to parse.
     * @return A JsonElement if the JSON is valid, null otherwise.
     */
    private fun parseStrict(json: String?): JsonElement? {
        return try {
            Gson().getAdapter(JsonElement::class.java).fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Filters out invalid CubBookmark objects and preserves valid ones.
     *
     * @param json The JSON string containing an array of CubBookmark objects.
     * @return A list of valid CubBookmark objects.
     */
    fun filterValidCubBookmarks(json: String?): List<CubBookmark> {
        if (json.isNullOrEmpty()) return emptyList()

        val filteredList = mutableListOf<CubBookmark>()

        // Parse the JSON string into a JsonElement
        val jsonElement: JsonElement = Gson().fromJson(json, JsonElement::class.java)

        // Check if the JSON is an array
        if (jsonElement.isJsonArray) {
            // Convert the JsonElement to a List of Maps
            (jsonElement as JsonArray).forEach { el ->
                try {
                    val bookmark = Gson().fromJson(el, CubBookmark::class.java)
                    if (isValidLampaCard(Gson().toJson(bookmark.data)))
                        filteredList.add(bookmark)
                    else
                        Log.e(
                            "filterValidCubBookmarks",
                            "CubBookmark ${bookmark.card_id} data is invalid"
                        )
                } catch (e: Exception) {
                    Log.e("filterValidCubBookmarks", "CubBookmark $el error $e")
                }
            }
            return filteredList
        } else return emptyList()
    }

    /**
     * Checks if a JSON string is a valid LampaCard.
     *
     * @param json The JSON string to validate.
     * @return True if the JSON is a valid LampaCard, false otherwise.
     */
    fun isValidLampaCard(json: String?): Boolean {
        if (json.isNullOrEmpty()) return false
        return try {
            // Parse the JSON string into a LampaCard
            Gson().fromJson(json, LampaCard::class.java)
            // If all checks pass, the JSON is a valid LampaCard
            true
        } catch (e: JsonSyntaxException) {
            // Invalid JSON syntax
            Log.e("isValidLampaCard", "JsonSyntaxException: $e")
            false
        } catch (e: Exception) {
            Log.e("isValidLampaCard", "Exception: $e")
            // Other errors (e.g., type casting issues)
            false
        }
    }

    fun <T> getJson(json: String?, cls: Class<T>?): T? {
        return try {
            Gson().fromJson(json, cls)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d("*****", "Error getJson $json as ${cls!!.name}: $e")
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
                        LampaProvider.BOOK -> App.context.addBookToRemove(listOf(id))
                        LampaProvider.LATE -> App.context.addWatchNextToRemove(listOf(id))
                        LampaProvider.LIKE -> App.context.addLikeToRemove(listOf(id))
                        LampaProvider.HIST -> App.context.addHistToRemove(listOf(id))
                        LampaProvider.LOOK -> App.context.addLookToRemove(listOf(id))
                        LampaProvider.VIEW -> App.context.addViewToRemove(listOf(id))
                        LampaProvider.SCHD -> App.context.addSchdToRemove(listOf(id))
                        LampaProvider.CONT -> App.context.addContToRemove(listOf(id))
                        LampaProvider.THRW -> App.context.addThrwToRemove(listOf(id))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("*****", "book items to remove: ${App.context.bookToRemove}")
                        Log.d("*****", "wath items to remove: ${App.context.wathToRemove}")
                        Log.d("*****", "like items to remove: ${App.context.likeToRemove}")
                        Log.d("*****", "hist items to remove: ${App.context.histToRemove}")
                        Log.d("*****", "look items to remove: ${App.context.lookToRemove}")
                        Log.d("*****", "view items to remove: ${App.context.viewToRemove}")
                        Log.d("*****", "schd items to remove: ${App.context.schdToRemove}")
                        Log.d("*****", "cont items to remove: ${App.context.contToRemove}")
                        Log.d("*****", "thrw items to remove: ${App.context.thrwToRemove}")
                    }
                }

                "add" -> {
                    when (where) {
                        LampaProvider.LATE -> App.context.addWatchNextToAdd(
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
}
