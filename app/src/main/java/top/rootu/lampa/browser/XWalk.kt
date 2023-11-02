package top.rootu.lampa.browser

import android.view.View
import org.xwalk.core.XWalkResourceClient
import org.xwalk.core.XWalkView
import top.rootu.lampa.MainActivity

class XWalk(override val mainActivity: MainActivity, override val viewResId: Int) : Browser {
    private var browser: XWalkView? = null
    override fun init() {
        if (browser == null) {
            browser = mainActivity.findViewById(viewResId)
            browser?.setLayerType(View.LAYER_TYPE_NONE, null)
            browser?.setResourceClient(object : XWalkResourceClient(browser) {
                override fun onLoadFinished(view: XWalkView, url: String) {
                    super.onLoadFinished(view, url)
                    mainActivity.onBrowserPageFinished(view, url)
                }
            })
            mainActivity.onBrowserInitCompleted()
        }
    }

    override fun setUserAgentString(ua: String?) {
        browser?.userAgentString = ua
    }

    override fun getUserAgentString(): String? {
        return browser?.userAgentString
    }

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

    override fun evaluateJavascript(script: String, resultCallback: (String) -> Unit) {
        browser?.evaluateJavascript(script, resultCallback)
    }

    override fun clearCache(includeDiskFiles: Boolean) {
        browser?.clearCache(includeDiskFiles)
    }

    override fun destroy() {
        browser?.onDestroy()
    }

    override fun setBackgroundColor(color: Int) {
        browser?.setBackgroundColor(color)
    }

    override fun canGoBack(): Boolean {
        return false
    }

    override fun goBack() {}

}