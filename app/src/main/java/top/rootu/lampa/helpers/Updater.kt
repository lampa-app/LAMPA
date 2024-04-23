package top.rootu.lampa.helpers

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.google.gson.Gson
import info.guardianproject.netcipher.client.TlsOnlySocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.models.Release
import top.rootu.lampa.models.Releases
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object Updater {
    private const val RELEASE_LINK =
        "https://api.github.com/repos/lampa-app/LAMPA/releases"
    private var releases: Releases? = null
    private var newVersion: Release? = null

    /**
     * Avoid SSLv3 as the only protocol available.
     * Trust all certs and hostnames (insecure).
     */
    init {
        val trustAllCertificates = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf() // Not relevant.
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
                    // Do nothing. Just allow them all.
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                    // Do nothing. Just allow them all.
                }
            }
        )
        val trustAllHostnames = HostnameVerifier { _, _ ->
            true // Just allow them all.
        }
        try {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCertificates, SecureRandom())
            val noSSLv3Factory: SSLSocketFactory = TlsOnlySocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory)
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnames)
        } catch (_: GeneralSecurityException) {
        }
    }

    fun check(): Boolean {
        try {
            val url = URL(RELEASE_LINK)
            val connection = if (RELEASE_LINK.startsWith("https"))
                url.openConnection() as HttpsURLConnection? // NetCipher.getHttpsURLConnection(url)
            else
                url.openConnection() as HttpURLConnection? // NetCipher.getHttpURLConnection(url)
            connection?.connect()
            val body =
                connection?.inputStream?.bufferedReader(Charset.defaultCharset())?.readText()
                    ?: return false
            releases = try {
                Gson().fromJson(body, Releases::class.java)
            } catch (e: Exception) {
                null
            }
            releases?.let {
                it.forEach { rel ->
                    val majorVersionDouble: Double = try {
                        rel.tag_name.replace("v", "").substringBefore(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val lastVersionDouble: Double = try {
                        rel.tag_name.replace("v", "").substringAfter(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val majorCurrVersionDouble: Double = try {
                        BuildConfig.VERSION_NAME.substringBefore(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    val currVersionDouble: Double = try {
                        BuildConfig.VERSION_NAME.substringAfter(".").toDouble()
                    } catch (npe: NumberFormatException) {
                        0.0
                    }
                    if (majorVersionDouble >= majorCurrVersionDouble && majorVersionDouble < 2 && lastVersionDouble > currVersionDouble) {
                        newVersion = rel
                        connection.disconnect()
                        return true
                    }
                }
            }
            connection.disconnect()
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVersion(): String {
        if (newVersion == null)
            CoroutineScope(Dispatchers.IO).launch {
                check()
            }
        return newVersion?.tag_name?.replace("v", "") ?: ""
    }

    fun getOverview(): Spanned {
        var ret = ""

        releases?.forEach { rel ->
            val majorVersionDouble: Double = try {
                rel.tag_name.replace("v", "").substringBefore(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val lastVersionDouble: Double = try {
                rel.tag_name.replace("v", "").substringAfter(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val majorCurrVersionDouble: Double = try {
                BuildConfig.VERSION_NAME.substringBefore(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            val currVersionDouble: Double = try {
                BuildConfig.VERSION_NAME.substringAfter(".").toDouble()
            } catch (npe: NumberFormatException) {
                0.0
            }
            if (majorVersionDouble >= majorCurrVersionDouble && majorVersionDouble < 2 && lastVersionDouble > currVersionDouble) {
                ret += "<font color='white'><b>${rel.tag_name}</b></font> <br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            } else {
                ret += "${rel.tag_name}<br>"
                ret += "<i>${rel.body.replace("\r\n", "<br/>")}</i><br/><br/>"
            }
        }
        return HtmlCompat.fromHtml(ret.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private val download = Any()

    private fun downloadApk(file: File, onProgress: ((prc: Int) -> Unit)?) {
        synchronized(download) {
            newVersion?.let { rel ->
                if (file.exists())
                    file.delete()
                var link = ""
                for (asset in rel.assets) {
                    link = asset.browser_download_url
                }
                if (link.isNotEmpty()) {
                    try {
                        val url = URL(link)
                        val connection = if (link.startsWith("https"))
                            url.openConnection() as HttpsURLConnection? // NetCipher.getHttpsURLConnection(url)
                        else
                            url.openConnection() as HttpURLConnection? // NetCipher.getHttpURLConnection(url)
                        connection?.connect()
                        connection?.inputStream.use { input ->
                            FileOutputStream(file).use { fileOut ->
                                val contentLength = connection?.contentLength ?: 0
                                if (onProgress == null)
                                    input?.copyTo(fileOut)
                                else {
                                    val buffer = ByteArray(65535)
                                    val length = contentLength + 1
                                    var offset: Long = 0
                                    while (true) {
                                        val read = input?.read(buffer) ?: 0
                                        offset += read
                                        val prc = (offset * 100 / length).toInt()
                                        onProgress(prc)
                                        if (read <= 0)
                                            break
                                        fileOut.write(buffer, 0, read)
                                    }
                                    fileOut.flush()
                                }
                                fileOut.flush()
                                fileOut.close()
                            }
                        }
                        connection?.disconnect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun installNewVersion(onProgress: ((prc: Int) -> Unit)?) {
        val ctx = App.context
        if (newVersion == null && !check())
            return

        newVersion?.let {
            val destination = File(
                ctx.getExternalFilesDir(null),
                "LAMPA.apk"
            ).apply {
                mkdirs()
                deleteOnExit()
            }

            downloadApk(destination, onProgress)

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
                val uri = Uri.fromFile(destination)
                val install = Intent(Intent.ACTION_VIEW)
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                install.setDataAndType(uri, "application/vnd.android.package-archive")
                if (install.resolveActivity(ctx.packageManager) != null)
                    App.context.startActivity(install)
                else
                    App.toast(R.string.error_app_not_found)
            } else {
                val fileUri =
                    FileProvider.getUriForFile(
                        ctx,
                        BuildConfig.APPLICATION_ID + ".update_provider",
                        destination
                    )
                val install = Intent(Intent.ACTION_VIEW, fileUri)
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (install.resolveActivity(ctx.packageManager) != null)
                    ctx.startActivity(install)
                else
                    App.toast(R.string.error_app_not_found)
            }
        }
    }
}