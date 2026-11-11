package top.rootu.lampa.helpers

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import top.rootu.lampa.helpers.Helpers.debugLog

/**
 * Google Advertising ID (GAID) for the `{GUID}` / IFA macro in Lampa VAST tags.
 *
 * Empty string means "no GAID": no Google Play services, ad tracking limited
 * (opt-out of ad personalization) or the ID was deleted (Android 12+ returns zeros).
 * Lampa then falls back to its own `vast_device_guid`.
 *
 * The lookup is blocking, so [load] must be called off the main thread.
 * It is re-read on every app start, so a reset / opt-out is picked up after a restart.
 */
object AdId {
    private const val TAG = "AdId"

    @Volatile
    var gaid: String = ""
        private set

    fun load(ctx: Context) {
        gaid = try {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(ctx.applicationContext)
            val id = info.id
            // limited ad tracking, zeros (Android 12+ after ID deletion) or empty - no GAID
            if (id.isNullOrEmpty() || info.isLimitAdTrackingEnabled || id.startsWith("00000000")) "" else id
        } catch (e: Throwable) {
            // no Google services (many TVs and boxes) or any other failure
            debugLog(TAG, "GAID unavailable: $e")
            ""
        }
        debugLog(TAG, "GAID ${if (gaid.isEmpty()) "not available" else "loaded"}")
    }
}
