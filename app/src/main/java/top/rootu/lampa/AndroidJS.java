package top.rootu.lampa;

import android.annotation.SuppressLint;
import org.json.JSONException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.webkit.ValueCallback;

import org.xwalk.core.JavascriptInterface;
import org.xwalk.core.XWalkView;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.util.Log;
import android.net.Uri;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.speech.RecognizerIntent;

import top.rootu.lampa.net.Http;

public final class AndroidJS {
    private static final String TAG = "AndroidJS";
    public MainActivity mainActivity;
    public XWalkView XWalkView;
    public static Map<String, String> reqResponse = new HashMap<>();

    public AndroidJS(MainActivity mainActivity, XWalkView XWalkView){
        this.mainActivity = mainActivity;
        this.XWalkView = XWalkView;
    }

    @JavascriptInterface
    public final String appVersion() {
        // версия AndroidJS для сайта указывается через тире, например 1.0.1-16 - 16 версия
        return BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE; // todo последния версия от Немирова 7.7.7-77
    }

    @JavascriptInterface
    public final void exit() {
        try {
            if (mainActivity != null) {
                mainActivity.runOnUiThread(() -> {
                    mainActivity.appExit();
                });
            }
        } catch (Exception unused) {
            System.exit(1);
            throw new RuntimeException(
                    "System.exit returned normally, while it was supposed to halt JVM."
            );
        }
    }

    @JavascriptInterface
    public final boolean openTorrentLink(String str, String str2) throws JSONException {
        JSONObject jSONObject;
        if (str2.equals("\"\"")) {
            jSONObject = new JSONObject();
        } else {
            jSONObject = new JSONObject(str2);
        }
        Intent intent = new Intent("android.intent.action.VIEW");
        Uri parse = Uri.parse(str);
        if (str.startsWith("magnet")) {
            intent.setData(parse);
        } else {
            intent.setDataAndType(parse, "application/x-bittorrent");
        }
        String title = jSONObject.optString("title");
        if (!TextUtils.isEmpty(title)) {
            intent.putExtra("title", title);
            intent.putExtra("displayName", title);
            intent.putExtra("forcename", title);
        }
        String poster = jSONObject.optString("poster");
        if (!TextUtils.isEmpty(poster)) {
            intent.putExtra("poster", poster);
        }
        if (jSONObject.optJSONObject("data") != null) {
            JSONObject optJSONObject = jSONObject.optJSONObject("data");
            if (optJSONObject != null) {
                intent.putExtra("data", optJSONObject.toString());
            }
        }
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> {
                try {
                    mainActivity.startActivity(intent);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                    Toast.makeText(
                            mainActivity, R.string.no_activity_found, Toast.LENGTH_SHORT).show();
                }
            });
        }
        return true;
    }

    @JavascriptInterface
    public final void openYoutube(String str) {
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=" + str)
        );
        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> {
                try {
                    mainActivity.startActivity(intent);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                    Toast.makeText(
                            mainActivity,
                            R.string.no_activity_found,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    }

    @JavascriptInterface
    public final void clearDefaultPlayer() {
        MainActivity.SELECTED_PLAYER = "";
        SharedPreferences.Editor editor = mainActivity.mSettings.edit();
        editor.putString(MainActivity.APP_PLAYER, MainActivity.SELECTED_PLAYER);
        editor.apply();
        Toast.makeText(
                mainActivity,
                R.string.select_player_reseted,
                Toast.LENGTH_LONG
        ).show();
    }

    @JavascriptInterface
    public final void httpReq(String str, int returnI) {
        Log.d("JS", str);
        JSONObject jSONObject = null;
        try {
            jSONObject = new JSONObject(str);
            String url = jSONObject.optString("url");
            Object data = jSONObject.opt("post_data");
            String contentType = jSONObject.optString("contentType");
            String requestContent = "";
            if (data != null) {
                if (data instanceof java.lang.String) {
                    requestContent = data.toString();
                    try {
                        new JSONObject(requestContent);
                        contentType = "application/json";
                    } catch (JSONException e) {
                        contentType = "application/x-www-form-urlencoded";
                    }
                } else if (data instanceof org.json.JSONObject) {
                    contentType = "application/json";
                    requestContent = data.toString();
                }
            }
            String finalRequestContent = requestContent;
            String finalContentType = contentType;
            new AsyncTask<Void, String, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    String s = "";
                    String action = "complite";
                    try {
                        if (TextUtils.isEmpty(finalContentType)) {
                            // GET
                            s = Http.Get(url);
                        } else {
                            // POST
                            s = Http.Post(url, finalRequestContent, finalContentType);
                        }
                    } catch (Exception e) {
                        JSONObject jSONObject = new JSONObject();
                        try {
                            jSONObject.put("status", Http.lastErrorCode);
                            jSONObject.put("message", "request error: " + e.getMessage());
                        } catch (JSONException jsonException) {
                            jsonException.printStackTrace();
                        }
                        s = jSONObject.toString();
                        action = "error";
                        e.printStackTrace();
                    }
                    reqResponse.put(String.valueOf(returnI), s);
                    return action;
                }

                @Override
                protected void onPostExecute(final String result) {
                    mainActivity.runOnUiThread(() -> {
                        String js = "Lampa.Android.httpCall("
                                + String.valueOf(returnI) + ", '" + result + "')";
                        XWalkView.evaluateJavascript(js, new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                Log.i("JSRV", value);
                            }
                        });
                    });
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @JavascriptInterface
    public final String getResp(String str) {
        String string = "";
        if (reqResponse.containsKey(str)) {
            string = reqResponse.get(str);
            reqResponse.remove(str);
        }
        return string;
    }

    @JavascriptInterface
    public final void openPlayer(String link, String jsonStr) {
        Log.d(TAG, "openPlayer: " + link + " json:" + jsonStr);

        JSONObject jsonObject;

        try {
            jsonObject = new JSONObject(jsonStr.isEmpty() ? "{}" : jsonStr);
        } catch (Exception e) {
            jsonObject = new JSONObject();
        }

        if (!jsonObject.has("url")) {
            try {
                jsonObject.put("url", link);
            } catch (JSONException ignored) {}
        }

        JSONObject finalJsonObject = jsonObject;
        mainActivity.runOnUiThread(() -> {
            mainActivity.runPlayer(finalJsonObject);
        });
    }

    @JavascriptInterface
    public final void voiceStart() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        if (mainActivity != null) {
            mainActivity.runOnUiThread(() -> {
                try {
                    mainActivity.startActivityForResult(intent, MainActivity.REQUEST_SPEECH);
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage(), e);
                    Toast.makeText(
                            mainActivity,
                            R.string.not_found_speech,
                            Toast.LENGTH_SHORT
                    ).show();
                    // Очищаем поле ввода
                    mainActivity.runVoidJsFunc("voiceResult", "''");
                }
            });
        }
    }

    @JavascriptInterface
    public final void showInput(String inputText) {
        // todo Ввод с андройд клавиатуры с последующей передачей результата через JS
        // где конкретно используется пока не понял, пример возврата ниже:
//      mainActivity.runOnUiThread(() -> {
//          String example = "Возвращ'аемый' текст";
//          mainActivity.runVoidJsFunc("androidInput", "'" + example.replace("'", "\\'") + "'");
//      });
    }

    @JavascriptInterface
    public final void updateChannel(String where) {
        // todo https://github.com/yumata/lampa-source/blob/e5505b0e9cf5f95f8ec49bddbbb04086fccf26c8/src/app.js#L203
    }
}
