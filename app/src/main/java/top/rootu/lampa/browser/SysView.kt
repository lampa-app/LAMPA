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
                //view?.loadUrl(url)
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceErrorCompat
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                                "<html><body><div align=\"center\" style=\"color:#ff7373\"><br/><br/><br/>ERROR ${error.errorCode}<br/>${
                                    view.context.getString(R.string.download_failed_message)
                                } ${request.url}<br/>${error.description}</div></body>"
                            view.loadUrl("about:blank")
                            view.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                            view.invalidate()
                            mainActivity.showUrlInputDialog()
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    if (BuildConfig.DEBUG) Log.d(
                        TAG,
                        "ERROR $errorCode $description on load $failingUrl"
                    )
                    if (failingUrl.toString().trimEnd('/').equals(MainActivity.LAMPA_URL, true)) {
                        val htmlData =
                            "<html><body><div align=\"center\" style=\"color:#ff7373\"><br/><br/><br/>ERROR $errorCode<br/>${
                                App.context.getString(R.string.download_failed_message)
                            } $failingUrl<br/>$description</div></body>"
                        view?.loadUrl("about:blank")
                        view?.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                        view?.invalidate()
                        mainActivity.showUrlInputDialog()
                    }
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

//            fun urlShouldBeHandledByWebView(url: String): Boolean {
//                // file: Resolve requests to local files such as files from cache folder via WebView itself
//                // todo: add more cases
//                return url.startsWith("file:") || url.startsWith("http://")
//            }
//
//            override fun shouldInterceptRequest(
//                view: WebView?,
//                url: String?
//            ): WebResourceResponse? {
//                if (!url.isNullOrEmpty())
//                    return if (urlShouldBeHandledByWebView(url) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                        super.shouldInterceptRequest(view, url)
//                    } else handleRequestViaOkHttp(url)
//                return null
//            }
//
//            private fun handleRequestViaOkHttp(url: String): WebResourceResponse {
//                Log.d(TAG, "handleRequestViaOkHttp($url)")
//                return try {
//                    val timeout = 30000
//                    val okHttpClient = HttpHelper.getOkHttpClient(timeout)
//                    val okHttpRequest = Request.Builder().url(url).build()
//                    if (BuildConfig.DEBUG) Log.d(TAG, "okHttpRequest: $okHttpRequest")
//                    val response = okHttpClient.newCall(okHttpRequest).execute()
//                    val responseHeaders: Headers = response.headers()
//                    for (i in 0 until responseHeaders.size()) {
//                        Log.d("$TAG HEADER", responseHeaders.name(i) + ": " + responseHeaders.value(i))
//                    }
//                    WebResourceResponse(
//                        response.header("content-type", "text/html"),
//                        response.header("content-encoding", "utf-8"),
//                        response.body()?.byteStream()
//                    )
//                } catch (e: Exception) {
//                    WebResourceResponse(null, null, null)
//                }
//            }
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

    @SuppressLint("AddJavascriptInterface")
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
        return browser?.canGoBack() ?: false
    }

    override fun goBack() {
        browser?.goBack()
    }

    override fun setFocus() {
        browser?.requestFocus(View.FOCUS_DOWN)
    }

}