package top.rootu.lampa.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import top.rootu.lampa.BuildConfig
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/**
 * Builds Lampa client identification headers injected by the native [httpReq] bridge.
 *
 * Injected only for CUB / LAMPA_URL hosts and JacRed / TorrServer / Jackett / Prowlarr search paths.
 * Third-party plugin traffic is left untouched.
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

    // CUB apex + old mirrors from lampa-source Manifest.cub_mirrors / old_mirrors.
    private val OWN_APEX = arrayOf(
        "cub.best",
        "cub.black",
        "durex.monster",
        "cubnotrip.top",
        "cub.red",
        "cub.rip",
        "kurwa-bober.ninja",
        "nackhui.com",
    )

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

    fun shouldInjectClientHeaders(url: String, lampaUrl: String): Boolean {
        val uri = parseHttpUri(url) ?: return false
        val host = uri.host ?: return false
        if (isOwnHost(host, lampaUrl)) return true
        return isParserApiPath(uri.path ?: return false)
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

    private fun isOwnHost(host: String, lampaUrl: String): Boolean {
        parseHttpUri(lampaUrl)?.host?.let { origin ->
            if (host.equals(origin, ignoreCase = true)) return true
        }
        return OWN_APEX.any { apex -> hostMatchesApex(host, apex) }
    }

    private fun hostMatchesApex(host: String, apex: String): Boolean =
        host.equals(apex, ignoreCase = true) ||
            host.endsWith(".$apex", ignoreCase = true)

    // TorrServer swagger: play, stream, torrents, search (+ torznab search).
    private val TORRSERVER_EXACT = setOf("/search", "/stream", "/torrents")
    private val TORRSERVER_PREFIX = arrayOf("/play", "/stream", "/torznab/search")

    // JacRed swagger: search only (Jackett, native, Prowlarr, Torznab).
    private val JACRED_PREFIX = arrayOf(
        "/api/v2.0/indexers",
        "/api/v1.0/torrents",
        "/api/v1/search",
        "/torznab/api",
    )

    private fun isParserApiPath(path: String): Boolean {
        val p = path.lowercase(Locale.ROOT).trimEnd('/')
        if (p.isEmpty()) return false
        if (p in TORRSERVER_EXACT) return true
        return matchesPrefix(p, TORRSERVER_PREFIX) || matchesPrefix(p, JACRED_PREFIX)
    }

    private fun matchesPrefix(path: String, stems: Array<String>): Boolean =
        stems.any { stem -> path == stem || path.startsWith("$stem/") }

    private fun parseHttpUri(url: String): URI? = try {
        val uri = URI(url.trim())
        if (uri.host.isNullOrEmpty()) null else uri
    } catch (_: Exception) {
        null
    }

    private fun assertMatchers() {
        fun yes(url: String) = check(shouldInjectClientHeaders(url, "http://lampa.mx")) { url }
        fun no(url: String) = check(!shouldInjectClientHeaders(url, "http://lampa.mx")) { url }
        yes("https://cub.best/api/plugins/all")
        yes("https://tmdb.cub.black/movie/1")
        yes("https://jac.red/api/v2.0/indexers/all/results")
        yes("https://jacred.xyz/api/v2.0/indexers/all/results")
        yes("http://lampa.mx/css/app.css")
        yes("http://192.168.1.10:9117/api/v2.0/indexers/all/results")
        yes("http://10.0.0.5:9696/api/v1/search")
        yes("http://127.0.0.1:8090/search/")
        yes("http://127.0.0.1:8090/torrents")
        yes("http://127.0.0.1:8090/stream/file.mkv")
        yes("http://127.0.0.1:8090/play/h/1")
        yes("http://127.0.0.1:8090/torznab/search/?query=x")
        yes("https://jac.red/api/v1.0/torrents")
        yes("https://jac.red/torznab/api")
        no("https://fanserials.tv/")
        no("https://fanserials.tv/api/foo")
        no("https://rezka.ag/search/foo")
        no("https://kinopoiskapiunofficial.tech/api/v2.1/films")
        no("http://127.0.0.1:8090/echo")
        no("https://jac.red/health")
    }

    init {
        if (BuildConfig.DEBUG) assertMatchers()
    }
}
