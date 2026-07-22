package top.rootu.lampa.browser

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * WebView that can keep reporting itself as window-visible while the host Activity is
 * backgrounded. When [keepVisible] is true, Chromium keeps `document.visibilityState` as
 * 'visible', so the loaded web app (Lampa) does not fire `visibilitychange` and does not
 * pause its own timers / RCH socket heartbeat while an external video player is in front.
 *
 * Used together with skipping [WebView.pauseTimers] during external playback.
 */
class LampaWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var keepVisible = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepVisible) VISIBLE else visibility)
    }
}
