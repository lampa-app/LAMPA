package top.rootu.lampa.tmdb

import android.net.Uri
import com.google.gson.Gson
import okhttp3.Request
import java.io.IOException
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.entity.Images

object Images {
    fun get(entity: Entity) {
        if (entity.images != null)
            return

        val params = mutableMapOf<String, String>()
        params["api_key"] = TMDB.apiKey
        params["language"] = TMDB.getLang()
        params["include_image_language"] = "${TMDB.getLang()},en,null"

        val urlBuilder = Uri.Builder()
                .scheme("https")
                .authority(TMDB.apiHost)
                .path("/3/${entity.media_type}/${entity.id}/images")

        for (param in params) {
            urlBuilder.appendQueryParameter(param.key, param.value)
        }

        val link = urlBuilder.build().toString()

        var body: String? = ""
        try {
            val request = Request.Builder()
                    .url(link)
                    .build()
            val client = TMDB.startWithQuad9DNS()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val images = Gson().fromJson(body, Images::class.java)
        images?.let { img ->
            for (i in 0 until img.backdrops.size)
                img.backdrops[i].file_path = TMDB.imageUrl(img.backdrops[i].file_path).replace("original", "w1280")

            for (i in 0 until img.posters.size)
                img.posters[i].file_path = TMDB.imageUrl(img.posters[i].file_path).replace("original", "w500")
        }

        entity.images = images
    }
}