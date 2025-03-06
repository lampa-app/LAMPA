package netfix.helpers;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Environment;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import netfix.app.BuildConfig;
import netfix.app.R;

public class PermHelpers {
    // Storage Permissions
    public static final int REQUEST_EXTERNAL_STORAGE = 112;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static final String[] PERMISSIONS_STORAGE_R = {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
    };

    // Mic Permissions
    public static final int REQUEST_MIC = 113;
    private static final String[] PERMISSIONS_MIC = {
            Manifest.permission.RECORD_AUDIO
    };

    /**
     * Checks if the app has permission to write to device storage<br/>
     * If the app does not has permission then the user will be prompted to grant permissions<br/>
     * Required for the {@link Context#getExternalCacheDir()}<br/>
     * NOTE: runs async<br/>
     *
     * @param context to apply permissions to
     */
    public static void verifyStoragePermissions(Context context) {
        if (VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requestPermissions(context, PERMISSIONS_STORAGE_R, REQUEST_EXTERNAL_STORAGE);
        else
            requestPermissions(context, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
    }

    public static void verifyMicPermissions(Context context) {
        requestPermissions(context, PERMISSIONS_MIC, REQUEST_MIC);
    }

    /**
     * Only check. There is no prompt.
     *
     * @param context to apply permissions to
     * @return whether permission already granted
     */
    public static boolean hasStoragePermissions(Context context) {
        // Check if we have write permission
        if (VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return Environment.isExternalStorageManager();
        else
            return hasPermissions(context, PERMISSIONS_STORAGE);
    }

    public static boolean hasMicPermissions(Context context) {
        // Check if we have mic permission
        return hasPermissions(context, PERMISSIONS_MIC);
    }

    // Utils

    /**
     * Shows permissions dialog<br/>
     * NOTE: runs async
     */
    private static void requestPermissions(Context activity, String[] permissions, int requestId) {
        if (!hasPermissions(activity, permissions) && !Helpers.isGenymotion()) {
            if (activity instanceof Activity) {
                // We don't have permission so prompt the user
                if (permissions == PERMISSIONS_STORAGE_R) {
                    Toast.makeText(activity, R.string.app_requires_manage_storage_perm, Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(
                            ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                    );
                    try {
                        ((Activity) activity).startActivityForResult(intent, requestId);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    ActivityCompat.requestPermissions(
                            (Activity) activity,
                            permissions,
                            requestId
                    );
                }
            }
        }
    }

    /**
     * Only check. There is no prompt.
     *
     * @param context to apply permissions to
     * @return whether permission already granted
     */
    private static boolean hasPermissions(@Nullable Context context, String... permissions) {
        if (context == null) {
            return false;
        }

        if (VERSION.SDK_INT >= 23) {
            for (String permission : permissions) {
                int result = ActivityCompat.checkSelfPermission(context, permission);
                if (result != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean hasPermission(@Nullable Context context, String permission) {
        if (context == null) {
            return false;
        }

        return PackageManager.PERMISSION_GRANTED == context.getPackageManager().checkPermission(
                permission, context.getPackageName());
    }
}
