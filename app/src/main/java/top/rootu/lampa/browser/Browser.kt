package top.rootu.lampa.browser

import android.view.View
import android.view.ViewGroup
import top.rootu.lampa.MainActivity

interface Browser {
    val mainActivity: MainActivity
    val viewResId : Int

    var isDestroyed: Boolean

    /**
     * Interface used to update the Crosswalk runtime
     */
    interface Listener {
        fun onBrowserInitCompleted()
        fun onBrowserPageFinished(view: ViewGroup, url: String)
    }

    fun initialize() // Renamed from init() to avoid conflict

    /**
     * Sets the WebView's user-agent string. If the string is `null` or empty,
     * the system default value will be used.
     *
     *
     * If the user-agent is overridden in this way, the values of the User-Agent Client Hints
     * headers and `navigator.userAgentData` for this WebView will be empty.
     *
     *
     * Note that starting from [android.os.Build.VERSION_CODES.KITKAT] Android
     * version, changing the user-agent while loading a web page causes WebView
     * to initiate loading once again.
     *
     * @param ua new user-agent string
     */
    fun setUserAgentString(ua: String?)

    /**
     * Gets the WebView's user-agent string.
     *
     * @return the WebView's user-agent string
     * @see .setUserAgentString
     */
    fun getUserAgentString(): String?

    /**
     * Injects the supplied Java object into this WebView. The object is
     * injected into all frames of the web page, including all the iframes,
     * using the supplied name. This allows the Java object's methods to be
     * accessed from JavaScript. For applications targeted to API
     * level [android.os.Build.VERSION_CODES.JELLY_BEAN_MR1]
     * and above, only public methods that are annotated with
     * [android.webkit.JavascriptInterface] can be accessed from JavaScript.
     * For applications targeted to API level [android.os.Build.VERSION_CODES.JELLY_BEAN] or below,
     * all public methods (including the inherited ones) can be accessed, see the
     * important security note below for implications.
     *
     *  Note that injected objects will not appear in JavaScript until the page is next
     * (re)loaded. JavaScript should be enabled before injecting the object. For example:
     * <pre class="prettyprint">
     * class JsObject {
     * @JavascriptInterface
     * @org.xwalk.core.JavascriptInterface
     * public String toString() { return "injectedObject"; }
     * }
     * webview.getSettings().setJavaScriptEnabled(true);
     * webView.addJavascriptInterface(new JsObject(), "injectedObject");
     * webView.loadData(" <title></title>", "text/html", null);
     * webView.loadUrl("javascript:alert(injectedObject.toString())");</pre>
     *
     *
     * **IMPORTANT:**
     *
     *  *  This method can be used to allow JavaScript to control the host
     * application. This is a powerful feature, but also presents a security
     * risk for apps targeting [android.os.Build.VERSION_CODES.JELLY_BEAN] or earlier.
     * Apps that target a version later than [android.os.Build.VERSION_CODES.JELLY_BEAN]
     * are still vulnerable if the app runs on a device running Android earlier than 4.2.
     * The most secure way to use this method is to target [android.os.Build.VERSION_CODES.JELLY_BEAN_MR1]
     * and to ensure the method is called only when running on Android 4.2 or later.
     * With these older versions, JavaScript could use reflection to access an
     * injected object's public fields. Use of this method in a WebView
     * containing untrusted content could allow an attacker to manipulate the
     * host application in unintended ways, executing Java code with the
     * permissions of the host application. Use extreme care when using this
     * method in a WebView which could contain untrusted content.
     *  *  JavaScript interacts with Java object on a private, background
     * thread of this WebView. Care is therefore required to maintain thread
     * safety.
     *
     *  *  Because the object is exposed to all the frames, any frame could
     * obtain the object name and call methods on it. There is no way to tell the
     * calling frame's origin from the app side, so the app must not assume that
     * the caller is trustworthy unless the app can guarantee that no third party
     * content is ever loaded into the WebView even inside an iframe.
     *  *  The Java object's fields are not accessible.
     *  *  For applications targeted to API level [android.os.Build.VERSION_CODES.LOLLIPOP]
     * and above, methods of injected Java objects are enumerable from
     * JavaScript.
     *
     *
     * @param jsObject the Java/Kotlin object to inject into this WebView's JavaScript
     * context. `null` values are ignored.
     * @param name the name used to expose the object in JavaScript
     */
    fun addJavascriptInterface(jsObject: Any, name: String)

    /**
     * Loads the given URL.
     *
     *
     * Also see compatibility note on [.evaluateJavascript].
     *
     * @param url the URL of the resource to load
     */
    fun loadUrl(url: String)

    /**
     * Pauses all layout, parsing, and JavaScript timers for all WebViews. This
     * is a global requests, not restricted to just this WebView. This can be
     * useful if the application has been paused.
     */
    fun pauseTimers()

    /**
     * Resumes all layout, parsing, and JavaScript timers for all WebViews.
     * This will resume dispatching all timers.
     */
    fun resumeTimers()

    /**
     * Asynchronously evaluates JavaScript in the context of the currently displayed page.
     * If non-null, `resultCallback` will be invoked with any result returned from that
     * execution. This method must be called on the UI thread and the callback will
     * be made on the UI thread.
     *
     *
     * Compatibility note. Applications targeting [android.os.Build.VERSION_CODES.N] or
     * later, JavaScript state from an empty WebView is no longer persisted across navigations like
     * [.loadUrl]. For example, global variables and functions defined before calling
     * [.loadUrl] will not exist in the loaded page. Applications should use
     * [.addJavascriptInterface] instead to persist JavaScript objects across navigations.
     *
     * @param script the JavaScript to execute.
     * @param resultCallback A callback to be invoked when the script execution
     * completes with the result of the execution (if any).
     * May be `null` if no notification of the result is required.
     */
    fun evaluateJavascript(script: String, resultCallback: (String) -> Unit)

    /**
     * Clears the resource cache. Note that the cache is per-application, so
     * this will clear the cache for all WebViews used.
     *
     * @param includeDiskFiles if `false`, only the RAM cache is cleared
     */
    fun clearCache(includeDiskFiles: Boolean)

    /**
     * Destroys the internal state of this WebView. This method should be called
     * after this WebView has been removed from the view system. No other
     * methods may be called on this WebView after destroy.
     */
    fun destroy()
    fun setBackgroundColor(color: Int)
    fun canGoBack(): Boolean
    fun goBack()
    fun setFocus()
    fun getView(): View?
}