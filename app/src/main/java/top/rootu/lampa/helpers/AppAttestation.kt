package top.rootu.lampa.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import top.rootu.lampa.BuildConfig
import java.security.MessageDigest
import java.util.Locale

/**
 * Builds Lampa client identification headers injected by the native [httpReq] bridge.
 *
 * Server-side WAF allowlist for [HEADER_CERT_SHA256]:
 * ```
 * keytool -list -v -keystore release.keystore -alias your_alias | grep SHA256
 * apksigner verify --print-certs app-release.apk
 * ```
 *
 * Example Cloudflare expression (Tier 1 + 2):
 * ```
 * http.request.headers["x-lampa-client"][0] contains "lampa-android"
 * and http.request.headers["x-lampa-package"][0] eq "top.rootu.lampa"
 * and http.request.headers["x-lampa-repo"][0] eq "lampa-app/LAMPA"
 * and http.request.headers["x-lampa-cert-sha256"][0] eq "YOUR_RELEASE_CERT_SHA256"
 * ```
 */
object AppAttestation {

    const val HEADER_CLIENT = "X-Lampa-Client"
    const val HEADER_PACKAGE = "X-Lampa-Package"
    const val HEADER_INSTALLER = "X-Lampa-Installer"
    const val HEADER_PLATFORM = "X-Lampa-Platform"
    const val HEADER_REPO = "X-Lampa-Repo"
    const val HEADER_CERT_SHA256 = "X-Lampa-Cert-SHA256"

    private var cachedCertSha256: String? = null
    private var certComputed = false

    fun clientId(): String =
        "lampa-android/${BuildConfig.VERSION_NAME}/${BuildConfig.VERSION_CODE}/${BuildConfig.FLAVOR}"

    fun signingCertSha256(context: Context): String? {
        if (certComputed) return cachedCertSha256
        certComputed = true
        cachedCertSha256 = computeSigningCertSha256(context)
        return cachedCertSha256
    }

    fun buildClientHeaders(context: Context): Map<String, String> {
        val headers = linkedMapOf(
            HEADER_CLIENT to clientId(),
            HEADER_PACKAGE to BuildConfig.APPLICATION_ID,
            HEADER_REPO to BuildConfig.REPO_ID,
            HEADER_INSTALLER to context.getAppInstaller(),
            HEADER_PLATFORM to "android",
        )
        signingCertSha256(context)?.let { headers[HEADER_CERT_SHA256] = it }
        return headers
    }

    @Suppress("DEPRECATION")
    private fun computeSigningCertSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo
                    ?.apkContentsSigners
                    ?.firstOrNull()
                    ?.toByteArray()
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures
                    ?.firstOrNull()
                    ?.toByteArray()
            } ?: return null

            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            formatSha256Hex(digest)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatSha256Hex(digest: ByteArray): String =
        digest.joinToString(":") { byte ->
            String.format(Locale.ROOT, "%02X", byte)
        }
}
