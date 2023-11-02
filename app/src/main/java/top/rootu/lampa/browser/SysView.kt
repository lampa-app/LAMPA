package top.rootu.lampa.browser

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import top.rootu.lampa.MainActivity

class SysView(override val mainActivity: MainActivity, override val viewResId: Int) : Browser {
    private var browser: WebView? = null
    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun init() {
        browser = mainActivity.findViewById(viewResId)
        browser?.isFocusable = true
        browser?.isFocusableInTouchMode = true
        browser?.settings?.javaScriptEnabled = true
        browser?.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        browser?.settings?.setRenderPriority(WebSettings.RenderPriority.HIGH)
        browser?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
        browser?.settings?.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        browser?.settings?.domStorageEnabled = true
        browser?.settings?.databaseEnabled = true
        browser?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                mainActivity.onBrowserPageFinished(view, url)
            }
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                view?.loadUrl(url)
                return true
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
}