package top.rootu.lampa.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION
import androidx.core.app.ActivityCompat
import top.rootu.lampa.helpers.Helpers.isGenymotion

object PermHelpers {
    // Storage Permissions
    const val REQUEST_EXTERNAL_STORAGE: Int = 112
    private val PERMISSIONS_STORAGE = arrayOf<String>(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Mic Permissions
    const val REQUEST_MIC: Int = 113
    private val PERMISSIONS_MIC = arrayOf<String>(
        Manifest.permission.RECORD_AUDIO
    )

    /**
     * Checks if the app has permission to write to device storage<br></br>
     * If the app does not has permission then the user will be prompted to grant permissions<br></br>
     * Required for the [Context.getExternalCacheDir]<br></br>
     * NOTE: runs async<br></br>
     *
     * @param context to apply permissions to
     */
    @JvmStatic
    fun verifyStoragePermissions(context: Context?) {
        requestPermissions(context, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE)
    }

    fun verifyMicPermissions(context: Context?) {
        requestPermissions(context, PERMISSIONS_MIC, REQUEST_MIC)
    }

    /**
     * Only check. There is no prompt.
     *
     * @param context to apply permissions to
     * @return whether permission already granted
     */
    @JvmStatic
    fun hasStoragePermissions(context: Context?): Boolean {
        // Check if we have write permission
        return hasPermissions(context, *PERMISSIONS_STORAGE)
    }

    fun hasMicPermissions(context: Context?): Boolean {
        // Check if we have mic permission
        return hasPermissions(context, *PERMISSIONS_MIC)
    }

    // Utils
    /**
     * Shows permissions dialog<br></br>
     * NOTE: runs async
     */
    private fun requestPermissions(activity: Context?, permissions: Array<String>, requestId: Int) {
        if (!hasPermissions(activity, *permissions) && !isGenymotion) {
            if (activity is Activity) {
                // We don't have permission so prompt the user
                ActivityCompat.requestPermissions(
                    activity,
                    permissions,
                    requestId
                )
            }
        }
    }

    /**
     * Only check. There is no prompt.
     *
     * @param context to apply permissions to
     * @return whether permission already granted
     */
    private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
        if (context == null) {
            return false
        }

        if (VERSION.SDK_INT >= 23) {
            for (permission in permissions) {
                val result = ActivityCompat.checkSelfPermission(context, permission)
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }

        return true
    }

    fun hasPermission(context: Context?, permission: String): Boolean {
        if (context == null) {
            return false
        }

        return PackageManager.PERMISSION_GRANTED == context.packageManager.checkPermission(
            permission, context.packageName
        )
    }

    fun isInstallPermissionDeclared(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
            packageInfo.requestedPermissions?.any { it == "android.permission.REQUEST_INSTALL_PACKAGES" } == true
        } catch (_: Exception) {
            false
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            // Permission not required before Android 8.0
            true
        }
    }
}
