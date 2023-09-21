package top.rootu.lampa.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*


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

    fun setLocale(activity: Activity, languageCode: String?) {
        val locale = languageCode?.let { Locale(it) } ?: return
        Locale.setDefault(locale)
        val resources: Resources = activity.resources
        val config: Configuration = resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            config.setLocale(locale)
        else
            config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    @Suppress("DEPRECATION")
    private fun isConnectedOld(context: Context): Boolean {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
        } else{
            isConnectedOld(context)
        }
    }

    private val deviceName: String
        get() = String.format("%s (%s)", Build.MODEL, Build.PRODUCT)

    @JvmStatic
    val isGenymotion: Boolean
        get() {
            val deviceName = deviceName
            return deviceName.contains("(vbox86p)")
        }
}