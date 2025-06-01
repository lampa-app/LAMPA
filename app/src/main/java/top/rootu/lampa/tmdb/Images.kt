package top.rootu.lampa.tmdb

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import okhttp3.Request
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.entity.Images
import java.io.IOException

object Images {
    fun get(entity: Entity) {
        if (entity.images != null)
            return

        val apiUrl = App.context.tmdbApiUrl
        val apiUri = apiUrl.toUri()
        // Manually handle the authority part to prevent encoding of the port colon
        val authority = "${apiUri.host}${if (apiUri.port != -1) ":${apiUri.port}" else ""}"
        val basePath = apiUri.path?.removeSuffix("/") ?: "3"
        val urlBuilder = Uri.Builder()
            .scheme(apiUri.scheme)
            .encodedAuthority(authority)  // Use encodedAuthority instead of authority to prevent double encoding
            .path("$basePath/${entity.media_type}/${entity.id}/images")

        val params = mutableMapOf<String, String>()
        // key must be 1st
        params["api_key"] = TMDB.APIKEY
        params["language"] = TMDB.getLang()
        params["include_image_language"] = "${TMDB.getLang()},en,null"
        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }
        // Add all original query parameters
        if (apiUrl != TMDB.APIURL)
            apiUri.queryParameterNames.forEach { paramName ->
                apiUri.getQueryParameter(paramName)?.let { paramValue ->
                    urlBuilder.appendQueryParameter(paramName, paramValue)
                }
            }
        val link = urlBuilder.build().toString()

        var body: String? = null
        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                TMDB.startWithQuad9DNS() else TMDB.permissiveOkHttp()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val images = getJson(body, Images::class.java)
        images?.let { img ->
            for (i in 0 until img.backdrops.size)
                img.backdrops[i].file_path =
                    TMDB.imageUrl(img.backdrops[i].file_path).replace("original", "w1280")

            for (i in 0 until img.posters.size)
                img.posters[i].file_path =
                    TMDB.imageUrl(img.posters[i].file_path).replace("original", "w500")
        }

        entity.images = images
    }
}