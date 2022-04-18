package top.rootu.lampa.helpers;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public final class Helpers {
    // NOTE: as of Oreo you must also add the REQUEST_INSTALL_PACKAGES permission to your manifest. Otherwise it just silently fails
    public static void installPackage(Context context, String packagePath) {
        if (packagePath == null || context == null) {
            return;
        }

        Uri file = FileHelpers.getFileUri(context, packagePath);

        if (file == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(file, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION); // without this flag android returned a intent error!

        try {
            context.getApplicationContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String getDeviceName() {
        return String.format("%s (%s)", Build.MODEL, Build.PRODUCT);
    }

    public static boolean isGenymotion() {
        String deviceName = getDeviceName();

        return deviceName.contains("(vbox86p)");
    }
}
