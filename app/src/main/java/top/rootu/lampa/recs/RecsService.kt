package top.rootu.lampa.recs

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.isAmazonDev
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Prefs.appUrl
import top.rootu.lampa.models.LampaCard
import java.io.IOException
import java.net.URL
import java.util.Locale
import kotlin.math.min


object RecsService {

    private const val MAX_RECS_CAP = 20
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
            val resourceId = R.drawable.empty_poster // in-app poster
            val emptyPoster = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(this.resources.getResourcePackageName(resourceId))
                .appendPath(this.resources.getResourceTypeName(resourceId))
                .appendPath(this.resources.getResourceEntryName(resourceId))
                .build()

            val cards = getRecs()
            val itemsSend = min(cards.size, 10)

            var priority = 1f
            val delta = 1f / itemsSend

            mNotifyManager.cancelAll()

            for (i in 0 until itemsSend) {
                try {
                    val card = cards[i]

                    var poster = card.img ?: emptyPosterPath
                    if (poster.isEmpty())
                        poster = emptyPosterPath

                    getBitmapFromURL(poster, cardWidth, cardHeight)

                    val genres = card.genres?.map { g ->
                        g?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    }?.toTypedArray()
                    val info = mutableListOf<String>()

                    card.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }

                    if (card.type == "tv")
                        card.number_of_seasons?.let { info.add("S$it") }

                    card.genres?.joinToString(", ") { g ->
                        g?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }.toString()
                    }?.let { info.add(it) }

//                    var country =
//                        card.production_countries?.joinToString(", ") { it.iso_3166_1 } ?: ""
//                    if (country.isEmpty())
//                        country = card.origin_country?.joinToString(", ") ?: ""
//                    if (country.isNotEmpty())
//                        info.add(country)
//
//                    card.certification?.let {
//                        if (it.isNotBlank())
//                            info.add(it)
//                    }

                    builder.setBadgeIcon(R.drawable.lampa_icon)
                        .setIdTag("video${card.id}")
                        .setTitle(card.title)
                        .setText(info.joinToString(" · "))
                        .setGenres(genres)
                        .setContentIntentData(
                            ContentRecommendation.INTENT_TYPE_ACTIVITY,
                            buildPendingIntent(card, null),
                            0,
                            null
                        )
                        .setContentImage(bitmap)
                        .setColor(ContextCompat.getColor(this, R.color.teal_500))
                        .setRunningTime(card.runtime?.toLong()?.times(60L) ?: 0L)
                        .setGroup("lampa")
                        .setSortKey(priority.toString())

                    if (card.type == "tv")
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_SERIAL))
                    else
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_MOVIE))

                    card.background_image?.let { builder.setBackgroundImageUri(it) }

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
                            card.overview
                        )
//                        card.videos?.let {
//                            if (it.results.isNotEmpty())
//                                notification.extras.putString(
//                                    "com.amazon.extra.PREVIEW_URL",
//                                    it.results[0].link
//                                )
//                        }
                        notification.extras.putString(
                            "com.amazon.extra.CONTENT_RELEASE_DATE",
                            card.release_year
                        )
                        if (!card.imdb_id.isNullOrEmpty())
                            notification.extras.putString("com.amazon.extra.IMDB_ID", card.imdb_id)
                    }

                    card.id?.toIntOrNull()?.let { mNotifyManager.notify(it, notification) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getRecs(): List<LampaCard> {
        return LampaProvider.get(LampaProvider.Recs, true)?.items?.take(MAX_RECS_CAP).orEmpty()
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