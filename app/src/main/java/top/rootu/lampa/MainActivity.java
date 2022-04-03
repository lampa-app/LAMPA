package top.rootu.lampa;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.json.JSONObject;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUpdater;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkInitializer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements
        XWalkInitializer.XWalkInitListener,
        XWalkUpdater.XWalkUpdateListener {

    private static final String TAG = "APP_MAIN";
    public static final int RESULT_ERROR = RESULT_FIRST_USER + 0;
    private XWalkView browser;
    private XWalkInitializer mXWalkInitializer;
    private XWalkUpdater mXWalkUpdater;
    private View mDecorView;
    private boolean browserInit = false;
    SharedPreferences mSettings;
    public static final String APP_PREFERENCES = "settings";
    public static final String APP_URL = "url";
    public static final String APP_PLAYER = "player";
    public static String LAMPA_URL = "";
    public static String SELECTED_PLAYER = "";
    private static final String URL_REGEX =
            "^https?://([-A-Za-z0-9]+\\.)+[-A-Za-z]{2,}(:[0-9]+)?(/.*)?$";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);
    public static final Integer REQUEST_PLAYER_SELECT = 1;
    public static final Integer REQUEST_PLAYER_OTHER = 2;
    public static final Integer REQUEST_PLAYER_MX = 222;
    public static final Integer REQUEST_PLAYER_VLC = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        mDecorView = getWindow().getDecorView();
        hideSystemUI();

        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        LAMPA_URL = mSettings.getString(APP_URL, LAMPA_URL);
        SELECTED_PLAYER = mSettings.getString(APP_PLAYER, SELECTED_PLAYER);

        // Must call initAsync() before anything that involves the embedding
        // API, including invoking setContentView() with the layout which
        // holds the XWalkView object.

        mXWalkInitializer = new XWalkInitializer(this, this);
        mXWalkInitializer.initAsync();

        // Until onXWalkInitCompleted() is invoked, you should do nothing with the
        // embedding API except the following:
        // 1. Instantiate the XWalkView object
        // 2. Call XWalkPreferences.setValue()
        // 3. Call mXWalkView.setXXClient(), e.g., setUIClient
        // 4. Call mXWalkView.setXXListener(), e.g., setDownloadListener
        // 5. Call mXWalkView.addJavascriptInterface()

        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);
        XWalkPreferences.setValue(XWalkPreferences.ENABLE_JAVASCRIPT, true);
        // maybe this fixes crashes on mitv2?
        // XWalkPreferences.setValue(XWalkPreferences.ANIMATABLE_XWALK_VIEW, true);

        setContentView(R.layout.activity_main);
    }

    @Override
    public void onXWalkInitStarted() {
    }

    @Override
    public void onXWalkInitCancelled() {
        // Perform error handling here

        finish();
    }

    @Override
    public void onXWalkInitFailed() {
        if (mXWalkUpdater == null) {
            mXWalkUpdater = new XWalkUpdater(this, this);
        }
        mXWalkUpdater.updateXWalkRuntime();
    }

    @Override
    public void onXWalkInitCompleted() {
        // Do anyting with the embedding API
        browserInit = true;
        if (browser == null) {
            browser = findViewById(R.id.xwalkview);
            browser.setLayerType(View.LAYER_TYPE_NONE, null);
            ProgressBar progressBar = findViewById(R.id.progressBar_cyclic);
            browser.setResourceClient(new XWalkResourceClient(browser) {
                //                @Override
//                public void onReceivedSslError(XWalkView view, ValueCallback<Boolean> callback, SslError error) {
//
//                    Log.i("SSL", "XWalkResourceClient.onReceivedSslError is invoked. Event: " + error.toString());
//                    callback.onReceiveValue(true);
////                    super.onReceivedSslError(view, callback, error);
//                }
                @Override
                public void onLoadFinished(XWalkView view, String url) {
                    super.onLoadFinished(view, url);
                    if (view.getVisibility() != View.VISIBLE) {
                        view.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                        System.out.println("LAMPA onLoadFinished " + url);
                    }
                }
            });
        }
        String ua = browser.getUserAgentString() + " LAMPA_ClientForLegacyOS";
        browser.setUserAgentString(ua);
        browser.setBackgroundColor(getResources().getColor(R.color.lampa_background));
        browser.addJavascriptInterface(new AndroidJS(this, browser), "AndroidJS");
        if (LAMPA_URL.isEmpty()) {
            showUrlInputDialog();
        } else {
            browser.loadUrl(LAMPA_URL);
        }
    }

    public void showUrlInputDialog() {
        MainActivity mainActivity = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);
        builder.setTitle(R.string.input_url_title);

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password,
        // and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(LAMPA_URL.isEmpty() ? "http://" : LAMPA_URL);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            LAMPA_URL = input.getText().toString();
            if (URL_PATTERN.matcher(LAMPA_URL).matches()) {
                System.out.println("URL '" + LAMPA_URL + "' is valid");
                if (!mSettings.getString(APP_URL, "").equals(LAMPA_URL)) {
                    SharedPreferences.Editor editor = mSettings.edit();
                    editor.putString(APP_URL, LAMPA_URL);
                    editor.apply();
                    browser.loadUrl(LAMPA_URL);
                    Toast.makeText(mainActivity, R.string.change_url_press_back, Toast.LENGTH_LONG)
                            .show();
                }
            } else {
                System.out.println("URL '" + LAMPA_URL + "' is invalid");
                Toast.makeText(mainActivity, R.string.invalid_url, Toast.LENGTH_LONG).show();
                showUrlInputDialog();
            }
            hideSystemUI();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            dialog.cancel();
            if (LAMPA_URL.isEmpty() && mSettings.getString(APP_URL, LAMPA_URL).isEmpty()) {
                appExit();
            } else {
                LAMPA_URL = mSettings.getString(APP_URL, LAMPA_URL);
                hideSystemUI();
            }
        });
        builder.setNeutralButton(R.string.exit, (dialog, which) -> {
            dialog.cancel();
            appExit();
        });

        builder.show();
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            System.out.println("Back button long pressed");
            showUrlInputDialog();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void onXWalkUpdateCancelled() {
        // Perform error handling here

        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (browserInit && browser != null) {
            browser.pauseTimers();
//            browser.onHide();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (browserInit && browser != null) {
            browser.onDestroy();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();

        // Try to initialize again when the user completed updating and
        // returned to current activity. The initAsync() will do nothing if
        // the initialization is proceeding or has already been completed.

        mXWalkInitializer.initAsync();

        if (browserInit && browser != null) {
            browser.resumeTimers();
//            browser.onShow();
        }
    }

    private void hideSystemUI() {
        mDecorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
        );
    }

    public void appExit() {
        if (browser != null) {
            browser.clearCache(true);
            browser.onDestroy();
        }
        System.exit(1);
        throw new RuntimeException("System.exit returned normally, while it was supposed to halt JVM.");
    }

    public void runPlayer(JSONObject jsonObject) {
        String link = jsonObject.optString("url");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                Uri.parse(link),
                link.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/*"
        );

        List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(intent, 0);

        if (resInfo.isEmpty()) {
            Toast.makeText(
                    this,
                    R.string.no_activity_found,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        boolean playerPackageExist = false;
        if (!SELECTED_PLAYER.isEmpty()) {
            for (ResolveInfo info : resInfo) {
                if (info.activityInfo.packageName.toLowerCase().equals(SELECTED_PLAYER)) {
                    playerPackageExist = true;
                    break;
                }
            }
        }

        if (!playerPackageExist || SELECTED_PLAYER.isEmpty()) {
            List<Intent> targetedShareIntents = new ArrayList<>();

            for (ResolveInfo info : resInfo) {
                Intent targetedShare = new Intent(Intent.ACTION_VIEW);
                targetedShare.setDataAndType(
                        Uri.parse(link),
                        link.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/*"
                );
                if (jsonObject.has("title")) {
                    targetedShare.putExtra("title", jsonObject.optString("title"));
                }
                targetedShare.setPackage(info.activityInfo.packageName.toLowerCase());
                targetedShareIntents.add(targetedShare);
            }
            // Then show the ACTION_PICK_ACTIVITY to let the user select it
            Intent intentPick = new Intent();
            intentPick.setAction(Intent.ACTION_PICK_ACTIVITY);
            // Set the title of the dialog
            intentPick.putExtra(Intent.EXTRA_TITLE, "Выберите плеер для просмотра");
            intentPick.putExtra(Intent.EXTRA_INTENT, intent);
            intentPick.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray());
            // Call StartActivityForResult so we can get the app name selected by the user
            startActivityForResult(intentPick, MainActivity.REQUEST_PLAYER_SELECT);
        } else {
            int requestCode;
            if (jsonObject.has("title")) {
                intent.putExtra("title", jsonObject.optString("title"));
            }

            switch (SELECTED_PLAYER) {
                case "com.mxtech.videoplayer.pro":
                case "com.mxtech.videoplayer.ad":
//                case "com.mxtech.videoplayer.beta":
                    requestCode = MainActivity.REQUEST_PLAYER_MX;
                    intent.putExtra("sticky", false);
                    intent.putExtra("return_result", true);
                    intent.setClassName(SELECTED_PLAYER, SELECTED_PLAYER + ".ActivityScreen");
                    break;
                case "org.videolan.vlc":
                    requestCode = MainActivity.REQUEST_PLAYER_VLC;
                    intent.setPackage(SELECTED_PLAYER);
                    break;
                default:
                    requestCode = MainActivity.REQUEST_PLAYER_OTHER;
                    intent.setPackage(SELECTED_PLAYER);
                    break;
            }

            if (requestCode != MainActivity.REQUEST_PLAYER_OTHER) {
                if (jsonObject.has("timeline")) {
                    JSONObject timeline = jsonObject.optJSONObject("timeline");
                    if (timeline.has("time")) {
                        long position = (long) (jsonObject.optDouble("time") * 1000);
                        if (position > 0)
                            intent.putExtra("position", position);
                    }
                }
            }
            try {
                startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage(), e);
                Toast.makeText(
                        this,
                        R.string.no_activity_found,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PLAYER_SELECT) {
            if (data != null
                && data.getComponent() != null
                && !TextUtils.isEmpty(data.getComponent().flattenToShortString())
            ) {
                SELECTED_PLAYER = data.getComponent().flattenToShortString().split("/")[0];

                SharedPreferences.Editor editor = mSettings.edit();
                editor.putString(APP_PLAYER, SELECTED_PLAYER);
                editor.apply();

                // Now you know the app being picked.
                // data is a copy of your launchIntent with this important extra info added.

                // Start the selected activity
                startActivity(data);
            }
        } else if(requestCode == REQUEST_PLAYER_MX || requestCode == REQUEST_PLAYER_VLC) {
            switch( resultCode )
            {
                case RESULT_OK:
                    Log.i( TAG, "Ok: " + data );
                    break;

                case RESULT_CANCELED:
                    Log.i( TAG, "Canceled: " + data );
                    break;

                case RESULT_ERROR:
                    Log.e( TAG, "Error occurred: " + data );
                    break;

                default:
                    Log.w( TAG, "Undefined result code (" + resultCode  + "): " + data );
                    break;
            }

            if( data != null )
                dumpParams(data);
            Toast.makeText(
                    this,
                    "MX or VLC returned",
                    Toast.LENGTH_LONG
            ).show();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private static void dumpParams( Intent intent )
    {
        StringBuilder sb = new StringBuilder();
        Bundle extras = intent.getExtras();

        sb.setLength(0);
        sb.append("* dat=").append(intent.getData());
        Log.v(TAG, sb.toString());

        sb.setLength(0);
        sb.append("* typ=").append(intent.getType());
        Log.v(TAG, sb.toString());

        if( extras != null && extras.size() > 0 )
        {
            sb.setLength(0);
            sb.append("    << Extra >>\n");

            int i = 0;
            for( String key : extras.keySet() )
            {
                sb.append( ' ' ).append( ++i ).append( ") " ).append( key ).append( '=' );
                appendDetails( sb, extras.get( key ) );
                sb.append( '\n' );
            }

            Log.v(TAG, sb.toString());
        }
    }

    private static void appendDetails( StringBuilder sb, Object object )
    {
        if( object != null && object.getClass().isArray() )
        {
            sb.append('[');

            int length = Array.getLength(object);
            for( int i = 0; i < length; ++i )
            {
                if( i > 0 )
                    sb.append(", ");

                appendDetails(sb, Array.get(object, i));
            }

            sb.append(']');
        }
        else if( object instanceof Collection )
        {
            sb.append('[');

            boolean first = true;
            for( Object element : (Collection)object )
            {
                if( first )
                    first = false;
                else
                    sb.append(", ");

                appendDetails(sb, element);
            }

            sb.append(']');
        }
        else
            sb.append(object);
    }
}