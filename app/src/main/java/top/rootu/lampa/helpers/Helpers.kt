package top.rootu.lampa.helpers

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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
import top.rootu.lampa.R
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
import top.rootu.lampa.models.CubBookmark
import top.rootu.lampa.models.LAMPA_CARD_KEY
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

    // Helper function to serialize LampaCard to JSON
    private fun serializeCardToJson(card: LampaCard): String? {
        return try {
            Gson().toJson(card)
        } catch (e: Exception) {
            Log.e("serializeCardToJson", "Failed to serialize card $card to JSON", e)
            null
        }
    }

    fun buildPendingIntent(
        card: LampaCard,
        continueWatch: Boolean?,
        activityJson: String?
    ): Intent {
        val intent = Intent(App.context, MainActivity::class.java).apply {
            // Set ID, source, and media type from the card
            card.id?.let { putExtra("id", it) }
            card.source?.let { putExtra("source", it) }
            card.type?.let { putExtra("media", it) }

            // Serialize the card to JSON and add it to the intent
            val cardJson = serializeCardToJson(card)
            cardJson?.let { putExtra(LAMPA_CARD_KEY, it) } // used to get card from HomeWatch

            // Add continueWatch flag if provided with lampaActivity
            continueWatch?.let {
                putExtra("continueWatch", it)
                activityJson?.let { putExtra("lampaActivity", it) }
            }

            // Set intent flags and action
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK)
            action = card.id ?: ""
        }
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
                "vidaa_tv",
                "i-905",
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

    fun getDefaultPosterUri(resId: Int = R.drawable.empty_poster): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(App.context.resources.getResourcePackageName(resId))
            .appendPath(App.context.resources.getResourceTypeName(resId))
            .appendPath(App.context.resources.getResourceEntryName(resId))
            .build()
    }

    // Function to manage favorites, actions: add | rem
    fun manageFavorite(action: String?, where: String, id: String, card: LampaCard? = null) {
        if (BuildConfig.DEBUG) Log.d("Prefs", "manageFavorite($action, $where, $id)")
        when (action) {
            "rem" -> when (where) {
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

            "add" -> when (where) {
                LampaProvider.LATE -> card?.let {
                    App.context.addWatchNextToAdd(
                        WatchNextToAdd(
                            id,
                            it
                        )
                    )
                }
            }
        }
    }

    fun printLog(message: String) {
        printLog("DEBUG", message)
    }

    fun printLog(tag: String = "DEBUG", message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun hasSAFChooser(pm: PackageManager?): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/*"
        return intent.resolveActivity(pm!!) != null
    }

    /**
     * Checks if Telegram (official or unofficial) is installed on the device.
     * Supports checking multiple package names for unofficial clients.
     *
     * @param context The application context
     * @return true if any Telegram client is installed, false otherwise
     */
    fun isTelegramInstalled(context: Context): Boolean {
        val telegramPackages = listOf(
            "org.telegram.messenger",     // Official Telegram
            "org.telegram.plus",         // Telegram Plus
            "org.telegram.messenger.web", // Telegram Web
            "nekox.messenger",           // Nekogram
            "org.thunderdog.challegram",  // Challegram
            "uz.dilijan.messenger"       // Other unofficial clients
        )

        return telegramPackages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    // Helper function to log intent data
    @Suppress("DEPRECATION")
    fun debugLogIntentData(tag: String = "DEBUG", intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        // Log basic intent info
        Log.d(tag, "Intent URI: ${intent.toUri(0)}")
        // Log all extras
        intent.extras?.let { bundle ->
            val output = StringBuilder("Intent Extras:\n")
            bundle.keySet().forEach { key ->
                output.append("• $key = ${bundleValueToString(bundle.get(key))}\n")
            }
            Log.d(tag, output.toString())
        } ?: Log.d(tag, "No extras found in intent")
    }

    fun logIntentContent(tag: String, intent: Intent?) {
        if (intent == null) {
            Log.d(tag, "Intent is null")
            return
        }

        Log.d(tag, "Intent Action: ${intent.action ?: "null"}")
        Log.d(tag, "Intent Data: ${intent.dataString ?: "null"}")
        Log.d(tag, "Intent Type: ${intent.type ?: "null"}")
        Log.d(tag, "Intent Package: ${intent.`package` ?: "null"}")
        Log.d(tag, "Intent Component: ${intent.component?.flattenToString() ?: "null"}")
        Log.d(tag, "Intent Flags: ${intent.flags} (hex: 0x${Integer.toHexString(intent.flags)})")

        // Log categories if they exist
        if (intent.categories != null) {
            for (category in intent.categories) {
                Log.d(tag, "Intent Category: $category")
            }
        } else {
            Log.d(tag, "Intent Categories: null")
        }

        // Log extras
        val extras: Bundle? = intent.extras
        if (extras != null && !extras.isEmpty) {
            Log.d(tag, "Intent Extras:")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.d(tag, "  $key = $value (${value?.javaClass?.simpleName ?: "null"})")
            }
        } else {
            Log.d(tag, "Intent Extras: null or empty")
        }

        // Log clipboard data if it exists
        if (intent.clipData != null) {
            Log.d(tag, "Intent ClipData:")
            val clipData = intent.clipData
            for (i in 0 until clipData!!.itemCount) {
                val item = clipData.getItemAt(i)
                Log.d(tag, "  Item $i:")
                Log.d(tag, "    Text: ${item.text}")
                Log.d(tag, "    URI: ${item.uri}")
                Log.d(tag, "    Intent: ${item.intent}")
            }
        }
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
}