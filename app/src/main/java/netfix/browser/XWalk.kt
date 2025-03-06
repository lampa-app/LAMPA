package netfix.browser

import android.view.View
import org.xwalk.core.XWalkResourceClient
import org.xwalk.core.XWalkView
import netfix.App
import netfix.MainActivity
import netfix.R

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

                override fun onReceivedLoadError(
                    view: XWalkView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedLoadError(view, errorCode, description, failingUrl)
                    if (failingUrl.toString().trimEnd('/').equals(MainActivity.LAMPA_URL, true)) {
                        val reason = when (description) {
                            "net::ERR_INTERNET_DISCONNECTED" -> App.context.getString(R.string.error_no_internet)
                            "net::ERR_NAME_NOT_RESOLVED" -> App.context.getString(R.string.error_dns)
                            "net::ERR_TIMED_OUT" -> App.context.getString(R.string.error_timeout)
                            else -> App.context.getString(R.string.error_unknown)
                        }
                        val msg = "${view?.context?.getString(R.string.download_failed_message)} ${MainActivity.LAMPA_URL} – $reason"
                        mainActivity.showUrlInputDialog(msg)
                    }
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

    override fun setFocus() {}

}