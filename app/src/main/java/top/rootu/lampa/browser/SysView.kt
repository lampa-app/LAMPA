package top.rootu.lampa.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.MainActivity
import top.rootu.lampa.R


// https://developer.android.com/develop/ui/views/layout/webapps/webview#kotlin
class SysView(override val mainActivity: MainActivity, override val viewResId: Int) : Browser {
    private var browser: WebView? = null
    val TAG = "WEBVIEW"

    @SuppressLint("SetJavaScriptEnabled")
    override fun init() {
        browser = mainActivity.findViewById(viewResId)
        browser?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            keepScreenOn = true
        }
        setFocus()
        val settings = browser?.settings
        settings?.apply {
            javaScriptEnabled = true
            builtInZoomControls = false
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            setNeedInitialFocus(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings?.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings?.mediaPlaybackRequiresUserGesture = false
        }

        browser?.webViewClient = object : WebViewClientCompat() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                mainActivity.onBrowserPageFinished(view, url)
            }

            // https://developer.android.com/reference/android/webkit/WebViewClient#shouldOverrideUrlLoading(android.webkit.WebView,%20java.lang.String)
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "shouldOverrideUrlLoading(url) wv $view loadUrl($url)"
                )
                return false
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onReceivedError( // Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat
            ) {
                if (WebViewFeature.isFeatureSupported(
                        WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE
                    ) &&
                    WebViewFeature.isFeatureSupported(
                        WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION
                    )
                ) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "ERROR ${error.errorCode} ${error.description} on load ${request.url}"
                    )
                    if (request.url.toString().trimEnd('/')
                            .equals(MainActivity.LAMPA_URL, true)
                    ) {
                        val htmlData =
                            "<html><body><div align=\"center\" style=\"height:100%\"><!--svg /--></div></body>"
                        view.loadUrl("about:blank")
                        view.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                        view.invalidate()
                        // net::ERR_INTERNET_DISCONNECTED [-2]
                        // net::ERR_NAME_NOT_RESOLVED [-2]
                        val reason = if (error.description == "net::ERR_INTERNET_DISCONNECTED")
                            view.context.getString(R.string.error_no_internet)
                        else if (error.description == "net::ERR_NAME_NOT_RESOLVED")
                            view.context.getString(R.string.error_dns)
                        else view.context.getString(R.string.error_unknown)
                        val msg = "${
                            view.context.getString(R.string.download_failed_message)
                        } ${MainActivity.LAMPA_URL} ${error.description} – $reason"
                        mainActivity.showUrlInputDialog(msg)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError( // Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (BuildConfig.DEBUG) Log.d(
                    TAG,
                    "ERROR $errorCode $description on load $failingUrl"
                )
                if (failingUrl.toString().trimEnd('/').equals(MainActivity.LAMPA_URL, true)) {
                    val htmlData =
                        "<html><body><div align=\"center\" style=\"height:100%\"><!--svg /--></div></body>"
                    view?.loadUrl("about:blank")
                    view?.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                    view?.invalidate()
                    // TODO: check errors
                    val reason = if (description == "net::ERR_INTERNET_DISCONNECTED")
                        App.context.getString(R.string.error_no_internet)
                    else if (description == "net::ERR_NAME_NOT_RESOLVED")
                        App.context.getString(R.string.error_dns)
                    else App.context.getString(R.string.error_unknown)
                    val msg =
                        "${App.context.getString(R.string.download_failed_message)} ${MainActivity.LAMPA_URL} – $reason"
                    mainActivity.showUrlInputDialog(msg)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Ignore SSL error: $error")
                handler?.proceed() // Ignore SSL certificate errors
            }
        }

        if (BuildConfig.DEBUG)
            browser?.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d("$TAG CONSOLE", consoleMessage.message())
                    return true
                }

                override fun onJsAlert(
                    view: WebView?,
                    url: String?,
                    message: String?,
                    result: JsResult?
                ): Boolean {
                    if (message != null) {
                        App.toast(message)
                    }
                    return false
                }
            }

        mainActivity.onBrowserInitCompleted()
    }

    override fun setUserAgentString(ua: String?) {
        browser?.settings?.userAgentString = ua
    }

    override fun getUserAgentString(): String? {
        return browser?.settings?.userAgentString
    }

    @SuppressLint("AddJavascriptInterface", "JavascriptInterface")
    override fun addJavascriptInterface(jsObject: Any, name: String) {
        browser?.addJavascriptInterface(jsObject, name)
    }

    override fun loadUrl(url: String) {
        browser?.loadUrl(url)
    }

    override fun pauseTimers() {
        browser?.pauseTimers()
    }

    override fun resumeTimers() {
        browser?.resumeTimers()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun evaluateJavascript(script: String, resultCallback: (String) -> Unit) {
        browser?.evaluateJavascript(script, resultCallback)
    }

    override fun clearCache(includeDiskFiles: Boolean) {
        browser?.clearCache(includeDiskFiles)
    }

    override fun destroy() {
        browser?.destroy()
    }

    override fun setBackgroundColor(color: Int) {
        browser?.setBackgroundColor(color)
    }

    override fun canGoBack(): Boolean {
        return browser?.canGoBack() == true
    }

    override fun goBack() {
        browser?.goBack()
    }

    override fun setFocus() {
        browser?.requestFocus(View.FOCUS_DOWN)
    }

}