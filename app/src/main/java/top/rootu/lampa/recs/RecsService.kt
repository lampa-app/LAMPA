package top.rootu.lampa.recs

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recommendation.app.ContentRecommendation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.isAmazonDev
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Prefs.appUrl
import top.rootu.lampa.models.TmdbId
import top.rootu.lampa.models.getEntity
import java.io.IOException
import java.net.URL
import java.util.Locale
import kotlin.math.min


object RecsService {

    private var bitmap: Bitmap? = null

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun updateRecs() {
        with(App.context) {

            if (!isAndroidTV)
                return

            val mNotifyManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (BuildConfig.DEBUG)
                Log.i("*****", "RecsService: updateRecs()")

            val builder = ContentRecommendation.Builder()

            val cardWidth = dp2px(this, 170f)
            val cardHeight = dp2px(this, 300f)
            val emptyPosterPath = this.appUrl + "/img/video_poster.png"

            val ids = getRecs()
            val entities = ids.mapNotNull { it.getEntity() }
            val itemsSend = min(entities.size, 10)

            var priority = 1f
            val delta = 1f / itemsSend

            mNotifyManager.cancelAll()

            for (i in 0 until itemsSend) {
                try {
                    val ent = entities[i]

                    var poster = ent.poster_path ?: emptyPosterPath
                    if (poster.isEmpty())
                        poster = emptyPosterPath

                    getBitmapFromURL(poster, cardWidth, cardHeight)

                    val genres = ent.genres?.map { g ->
                        g?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    }?.toTypedArray()
                    val info = mutableListOf<String>()

                    ent.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }

                    if (ent.media_type == "tv")
                        ent.number_of_seasons?.let { info.add("S$it") }

                    ent.genres?.joinToString(", ") { g ->
                        g?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }.toString()
                    }?.let { info.add(it) }

                    var country =
                        ent.production_countries?.joinToString(", ") { it.iso_3166_1 } ?: ""
                    if (country.isEmpty())
                        country = ent.origin_country?.joinToString(", ") ?: ""
                    if (country.isNotEmpty())
                        info.add(country)

                    ent.certification?.let {
                        if (it.isNotBlank())
                            info.add(it)
                    }

                    builder.setBadgeIcon(R.drawable.lampa_icon)
                        .setIdTag("Video${ent.id}")
                        .setTitle(ent.title)
                        .setText(info.joinToString(" · "))
                        .setGenres(genres)
                        .setContentIntentData(
                            ContentRecommendation.INTENT_TYPE_ACTIVITY,
                            buildPendingIntent(ent.toTmdbID(), "lampa"),
                            0,
                            null
                        )
                        .setContentImage(bitmap)
                        .setColor(ContextCompat.getColor(this, R.color.teal_500))
                        .setRunningTime(ent.runtime?.toLong()?.times(60L) ?: 0L)
                        .setGroup("lampa")
                        .setSortKey(priority.toString())

                    if (ent.media_type == "tv")
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_SERIAL))
                    else
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_MOVIE))

                    ent.backdrop_path?.let { builder.setBackgroundImageUri(it) }

                    priority -= delta
                    val notification = builder.build().getNotificationObject(applicationContext)
                    notification.priority = i - 5000

                    if (isAmazonDev) {
                        notification.extras.putString(
                            "com.amazon.extra.DISPLAY_NAME",
                            getString(R.string.app_name)
                        )
                        notification.extras.putInt("com.amazon.extra.RANK", i)
                        notification.extras.putString(
                            "com.amazon.extra.LONG_DESCRIPTION",
                            ent.overview
                        )
                        ent.videos?.let {
                            if (it.results.isNotEmpty())
                                notification.extras.putString(
                                    "com.amazon.extra.PREVIEW_URL",
                                    it.results[0].link
                                )
                        }
                        notification.extras.putString(
                            "com.amazon.extra.CONTENT_RELEASE_DATE",
                            ent.year
                        )
                        if (!ent.imdb_id.isNullOrEmpty())
                            notification.extras.putString("com.amazon.extra.IMDB_ID", ent.imdb_id)
                    }

                    ent.id?.let { mNotifyManager.notify(it, notification) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getRecs(): List<TmdbId> {
        return emptyList()
    }

    // https://stackoverflow.com/questions/8992964/android-load-from-url-to-bitmap
    private fun getBitmapFromURL(src: String?, width: Int, height: Int) {
        CoroutineScope(Job() + Dispatchers.IO).launch {
            try {
                val url = URL(src)
                val bitMap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                bitmap = Bitmap.createScaledBitmap(bitMap, width, height, true)
            } catch (e: IOException) {
                // Log exception
            }
        }
    }
}