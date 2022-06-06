package top.rootu.lampa.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import androidx.multidex.MultiDexApplication

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

    fun isConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(MultiDexApplication.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.state == NetworkInfo.State.CONNECTED
    }

    private val deviceName: String
        get() = String.format("%s (%s)", Build.MODEL, Build.PRODUCT)

    val isGenymotion: Boolean
        get() {
            val deviceName = deviceName
            return deviceName.contains("(vbox86p)")
        }
}