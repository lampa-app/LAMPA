package top.rootu.lampa.tmdb

import android.os.Build
import androidx.core.text.isDigitsOnly
import com.google.gson.Gson
import okhttp3.Request
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.tmdbApiUrl
import top.rootu.lampa.tmdb.models.content_ratings.ContentRatingsModel
import top.rootu.lampa.tmdb.models.entity.Entity
import top.rootu.lampa.tmdb.models.release_dates.ReleaseDatesModel

object Certifications {

    fun get(ent: Entity) {
        val cert = when (ent.media_type) {
            "movie" -> getMovie(ent)
            "tv" -> getTV(ent)
            else -> ""
        }
        val lang = TMDB.getLang().lowercase()
        if (lang == "ru" || lang == "uk" || lang == "be") {
            when {
                // Not Rated
                cert.equals("NR", ignoreCase = true) ->
                    ent.certification = ""
                // 0+
                cert.equals("ALL", ignoreCase = true) ||
                        cert.equals("Btl", ignoreCase = true) ||
                        cert.equals("KN", ignoreCase = true) ||
                        cert.equals("L", ignoreCase = true) ||
                        cert.equals("P", ignoreCase = true) ||
                        cert.equals("S", ignoreCase = true) ||
                        cert.equals("SU", ignoreCase = true) ||
                        cert.equals("T", ignoreCase = true) ||
                        cert.equals("TE", ignoreCase = true) ||
                        cert.equals("TP", ignoreCase = true) ||
                        cert.equals("TV-Y", ignoreCase = true) ||
                        cert.equals("U", ignoreCase = true) ->
                    ent.certification = "0+"
                // 3+
                cert.equals("G", ignoreCase = true) ||
                        cert.equals("TV-G", ignoreCase = true) ->
                    ent.certification = "3+"
                // 6+
                cert.equals("PG", ignoreCase = true) ||
                        cert.equals("SM6", ignoreCase = true) ||
                        cert.equals("TV-PG", ignoreCase = true) ->
                    ent.certification = "6+"
                // 7+
                cert.equals("IIA", ignoreCase = true) ||
                        cert.equals("TV-Y7", ignoreCase = true) ->
                    ent.certification = "7+"
                // 12+
                cert.equals("12A", ignoreCase = true) ||
                        cert.equals("K-12", ignoreCase = true) ||
                        cert.equals("PG12", ignoreCase = true) ||
                        cert.equals("PG-12", ignoreCase = true) ||
                        cert.equals("UA", ignoreCase = true) ->
                    ent.certification = "12+"
                // 13+
                cert.equals("PG13", ignoreCase = true) ||
                        cert.equals("PG-13", ignoreCase = true) ->
                    ent.certification = "13+"
                // 14+
                cert.equals("IIB", ignoreCase = true) ||
                        cert.equals("K-14", ignoreCase = true) ||
                        cert.equals("TV-14", ignoreCase = true) ||
                        cert.equals("VM14", ignoreCase = true) ->
                    ent.certification = "14+"
                // 15+
                cert.equals("M", ignoreCase = true) ||
                        cert.equals("MA15+", ignoreCase = true) ||
                        cert.equals("PG15", ignoreCase = true) ||
                        cert.equals("R15+", ignoreCase = true) ->
                    ent.certification = "15+"
                // 16+
                cert.equals("K-16", ignoreCase = true) ||
                        cert.equals("NC16", ignoreCase = true) ->
                    ent.certification = "16+"
                // 17+
                cert.equals("R", ignoreCase = true) ||
                        cert.equals("TV-MA", ignoreCase = true) ->
                    ent.certification = "17+"
                // 18+
                cert.equals("M18", ignoreCase = true) ||
                        cert.equals("NC-17", ignoreCase = true) ||
                        cert.equals("R18", ignoreCase = true) ||
                        cert.equals("R18+", ignoreCase = true) ||
                        cert.equals("VM18", ignoreCase = true) ->
                    ent.certification = "18+"
                // 21+
                cert.equals("R21", ignoreCase = true) ->
                    ent.certification = "21+"

                else -> {
                    if (cert.isNotBlank() && cert.isDigitsOnly())
                        ent.certification = "$cert+"
                    else
                        ent.certification = cert
                }
            }
        } else
            ent.certification = cert
    }

    private fun getMovie(ent: Entity): String {
        // /movie/{movie_id}/release_dates
        val link =
            "${App.context.tmdbApiUrl}${ent.media_type}/${ent.id}/release_dates?api_key=${TMDB.apiKey}"
        var body: String? = null
        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                TMDB.startWithQuad9DNS() else TMDB.permissiveOkHttp()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val releases = Gson().fromJson(body, ReleaseDatesModel::class.java)
        releases?.results?.filter { it.iso_3166_1.lowercase() == TMDB.getLang() }?.apply {
            if (isNotEmpty()) {
                this.first().release_dates.firstOrNull { it.certification.isNotBlank() }?.let {
                    return it.certification
                }
            }
        }
        releases?.results?.filter { it.iso_3166_1.lowercase() == "us" }?.apply {
            if (isNotEmpty()) {
                this.first().release_dates.firstOrNull { it.certification.isNotBlank() }?.let {
                    return it.certification
                }
            }
        }
        releases?.results?.filter { it.iso_3166_1.lowercase() == "gb" }?.apply {
            if (isNotEmpty()) {
                this.first().release_dates.firstOrNull { it.certification.isNotBlank() }?.let {
                    return it.certification
                }
            }
        }
        releases?.results?.forEach { r ->
            r.release_dates.firstOrNull { it.certification.isNotBlank() }?.let {
                return it.certification
            }
        }
        return ""
    }

    private fun getTV(ent: Entity): String {
        // /tv/{tv_id}/content_ratings
        val link =
            "${App.context.tmdbApiUrl}${ent.media_type}/${ent.id}/content_ratings?api_key=${TMDB.apiKey}"

        var body: String? = null
        try {
            val request = Request.Builder()
                .url(link)
                .build()
            val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                TMDB.startWithQuad9DNS() else TMDB.permissiveOkHttp()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                body = response.body()?.string()
                response.body()?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val ratings = Gson().fromJson(body, ContentRatingsModel::class.java)
        ratings?.results?.filter { it.iso_3166_1.lowercase() == TMDB.getLang() }?.apply {
            if (isNotEmpty())
                return this.first().rating
        }
        ratings?.results?.filter { it.iso_3166_1.lowercase() == "us" }?.apply {
            if (isNotEmpty())
                return this.first().rating
        }
        ratings?.results?.filter { it.iso_3166_1.lowercase() == "gb" }?.apply {
            if (isNotEmpty())
                return this.first().rating
        }
        ratings?.results?.forEach {
            if (it.rating.isNotBlank())
                return it.rating
        }
        return ""
    }
}