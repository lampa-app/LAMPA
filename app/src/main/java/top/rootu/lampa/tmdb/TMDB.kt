package top.rootu.lampa.tmdb

import android.net.Uri
import android.os.Build
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.helpers.Prefs.tmdbImgUrl
import top.rootu.lampa.net.HttpHelper
import top.rootu.lampa.tmdb.models.entity.Entities
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.entity.Genre
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

object TMDB {
    const val APIURL = "https://api.themoviedb.org/3/"
    const val IMGURL = "https://image.tmdb.org/"
    const val APIKEY = "4ef0d7355d9ffb5151e987764708ce96"
    private var movieGenres: List<Genre?> = emptyList()
    private var tvGenres: List<Genre?> = emptyList()

    /* return lowercase 2-digit lang tag */
    fun getLang(): String {
        val appLang = App.context.appLang
        if (appLang.isNotEmpty())
            appLang.apply {
                val languageCode = this
                var loc = Locale(languageCode.lowercase())
                if (languageCode.split("-").size > 1) {
                    val language = languageCode.split("-")[0].lowercase()
                    val country = languageCode.split("-")[1].uppercase()
                    loc = Locale(language, country)
                }
                return loc.language
            }

        val lang = Locale.getDefault().language
        return when {
            lang.equals("IW", ignoreCase = true) -> {
                "he"
            }

            lang.equals("IN", ignoreCase = true) -> {
                "id"
            }

            lang.equals("JI", ignoreCase = true) -> {
                "yi"
            }

            lang.equals("LV", ignoreCase = true) -> {
                "en" // FIXME: Empty Genre Names on LV, so force EN for TMDB requests
            }

            else -> {
                lang
            }
        }
    }

    fun initGenres() {
        try {
            // https://developers.themoviedb.org/3/genres/get-movie-list
            var ent = video("genre/movie/list")
            ent?.genres?.let {
                movieGenres = it
            }
            // https://developers.themoviedb.org/3/genres/get-tv-list
            ent = video("genre/tv/list")
            ent?.genres?.let {
                tvGenres = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val genres: Map<Int, String>
        get() {
            val ret = hashMapOf<Int, String>()

            if (movieGenres.isNotEmpty())
                for (g in movieGenres) {
                    g?.let {
                        if (!g.name.isNullOrEmpty()) {
                            ret[g.id] =
                                g.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        }
                    }
                }

            if (tvGenres.isNotEmpty())
                for (g in tvGenres) {
                    g?.let {
                        if (!g.name.isNullOrEmpty()) {
                            ret[g.id] =
                                g.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        }
                    }
                }

            return ret
        }

    // Quad9 over HTTPS resolver
    fun startWithQuad9DNS(): OkHttpClient {

        val bootstrapClient = OkHttpClient.Builder().build()
        val okUrl = HttpUrl.parse("https://dns.quad9.net/dns-query")

        var dns: Dns? = okUrl?.let {
            DnsOverHttps.Builder().client(bootstrapClient)
                .url(it)
                .bootstrapDnsHosts(
                    InetAddress.getByName("9.9.9.9"),
                    InetAddress.getByName("149.112.112.112"),
                    Inet6Address.getByName("2620:fe::fe")
                )
                .build()
        }
        if (dns == null)
            dns = Dns.SYSTEM

        return bootstrapClient.newBuilder()
            .connectTimeout(15000L, TimeUnit.MILLISECONDS)
            .dns(dns!!)
            .build()
    }

    // For KitKat
    fun permissiveOkHttp(): OkHttpClient {
        val timeout = 30000
        return HttpHelper.getOkHttpClient(timeout)
    }

    fun videos(endpoint: String, params: MutableMap<String, String>): Entities? {
        params["api_key"] = APIKEY
        params["language"] = getLang()
        val apiUrl = App.context.tmdbApiUrl
        val authority = Uri.parse(apiUrl).authority
        val scheme = Uri.parse(apiUrl).scheme
        val urlBuilder = Uri.Builder()
            .scheme(scheme)
            .authority(authority)
            .path("/3/$endpoint")

        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }
        var body: String? = null
        val link = urlBuilder.build().toString()
        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                startWithQuad9DNS() else permissiveOkHttp()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (body.isNullOrEmpty())
            return null

        val entities = getJson(body, Entities::class.java)
        val ret = mutableListOf<Entity>()

        entities?.results?.forEach {
            if (it.media_type == null)
                fixEntity(it)
            if (it.media_type == "movie" || it.media_type == "tv") {
                val ent = video("${it.media_type}/${it.id}")
                ent?.let {
                    fixEntity(ent)
                    ret.add(ent)
                }
            }
        }
        entities?.results = ret
        return entities
    }

    fun video(endpoint: String): Entity? {
        val appLang = getLang()
        return videoDetail(endpoint, appLang)
    }

    private fun videoDetail(endpoint: String, lang: String = ""): Entity? {
        val params = mutableMapOf<String, String>()
        params["api_key"] = APIKEY
        if (lang.isBlank())
            params["language"] = getLang()
        else params["language"] = lang
        val apiUrl = App.context.tmdbApiUrl
        val authority = Uri.parse(apiUrl).authority
        val scheme = Uri.parse(apiUrl).scheme
        val urlBuilder = Uri.Builder()
            .scheme(scheme)
            .authority(authority)
            .path("/3/$endpoint")

        params["append_to_response"] = "videos,images,alternative_titles"
        params["include_image_language"] = "${getLang()},ru,en,null"

        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }

        var body: String? = null
        val link = urlBuilder.build().toString()

        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                startWithQuad9DNS() else permissiveOkHttp()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (body.isNullOrEmpty())
            return null

        val ent = getJson(body, Entity::class.java)
        ent?.let { fixEntity(it) }
        return ent
    }

    private fun fixEntity(ent: Entity) {
        if (ent.title == null && ent.name == null)
            return
        // media types
        if (ent.media_type.isNullOrEmpty()) {
            if (ent.title.isNullOrEmpty())
                ent.media_type = "tv"
            else if (ent.name.isNullOrEmpty())
                ent.media_type = "movie"
        }
        // titles
        if (ent.title.isNullOrEmpty() && !ent.name.isNullOrEmpty())
            ent.title = ent.name
        if (ent.original_title.isNullOrEmpty() && !ent.original_name.isNullOrEmpty())
            ent.original_title = ent.original_name
        // release_date
        if (!ent.release_date.isNullOrEmpty() && ent.release_date?.length!! >= 4)
            ent.year = ent.release_date?.substring(0, 4) ?: ""
        else if (!ent.first_air_date.isNullOrEmpty() && ent.first_air_date?.length!! >= 4)
            ent.year = ent.first_air_date?.substring(0, 4) ?: ""
        if (ent.release_date.isNullOrEmpty() && !ent.first_air_date.isNullOrEmpty())
            ent.release_date = ent.first_air_date
        // images
        ent.poster_path = imageUrl(ent.poster_path).replace("original", "w342")
        ent.backdrop_path = imageUrl(ent.backdrop_path).replace("original", "w1280")
        ent.images?.let { img ->
            for (i in img.backdrops.indices)
                ent.images!!.backdrops[i].file_path =
                    imageUrl(img.backdrops[i].file_path).replace("original", "w1280")

            for (i in img.posters.indices)
                ent.images!!.posters[i].file_path =
                    imageUrl(img.posters[i].file_path).replace("original", "w342")
        }
        ent.production_companies?.let {
            it.forEach { co ->
                co.logo_path = imageUrl(co.logo_path).replace("original", "w185")
            }
        }
        ent.seasons?.let { sn ->
            sn.forEach {
                it.poster_path = imageUrl(it.poster_path).replace("original", "w342")
            }
        }
    }

    fun imageUrl(path: String?): String {
        path?.let {
            if (it.startsWith("http"))
                return it
        }
        if (path.isNullOrEmpty())
            return ""

        // "https://image.tmdb.org/t/p/original$path"
        val imgUrl = App.context.tmdbImgUrl
        val authority = Uri.parse(imgUrl).authority
        val scheme = Uri.parse(imgUrl).scheme
        return Uri.Builder()
            .scheme(scheme)
            .authority(authority)
            .path("/t/p/original$path")
            .build().toString()
    }
}