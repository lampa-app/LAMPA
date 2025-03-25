package top.rootu.lampa.browser

import android.view.View
import org.xwalk.core.XWalkResourceClient
import org.xwalk.core.XWalkView
import top.rootu.lampa.App
import top.rootu.lampa.MainActivity
import top.rootu.lampa.R
import top.rootu.lampa.helpers.getNetworkErrorString

class XWalk(override val mainActivity: MainActivity, override val viewResId: Int) : Browser {
    private var browser: XWalkView? = null
    override var isDestroyed = false
    override fun initialize() {
        if (browser == null) {
            browser = mainActivity.findViewById(viewResId)
            browser?.let { xWalkView ->
                xWalkView.setLayerType(View.LAYER_TYPE_NONE, null)
                xWalkView.setResourceClient(object : XWalkResourceClient(xWalkView) {
                    override fun onLoadFinished(view: XWalkView, url: String) {
                        super.onLoadFinished(view, url)
                        mainActivity.onBrowserPageFinished(view, url)
                    }

                    override fun onReceivedLoadError(
                        view: XWalkView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedLoadError(view, errorCode, description, failingUrl)
                        if (failingUrl.toString().trimEnd('/')
                                .equals(MainActivity.LAMPA_URL, true)
                        ) {
                            val reason = App.context.getNetworkErrorString(description.toString())
                            val msg =
                                "${view?.context?.getString(R.string.download_failed_message)} ${MainActivity.LAMPA_URL} – $reason"
                            mainActivity.showUrlInputDialog(msg)
                        }
                    }
                })
                mainActivity.onBrowserInitCompleted()
            }
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
        if (!isDestroyed)
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
        isDestroyed = true
    }

    override fun setBackgroundColor(color: Int) {
        browser?.setBackgroundColor(color)
    }

    override fun canGoBack(): Boolean {
        return false
    }

    override fun goBack() {}

    override fun setFocus() {}

    override fun getView(): View? {
        return browser
    }

}