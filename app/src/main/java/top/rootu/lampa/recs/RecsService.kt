package top.rootu.lampa.recs

import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recommendation.app.ContentRecommendation
import com.bumptech.glide.Glide
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.isAmazonDev
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.models.LampaCard
import java.util.Locale
import kotlin.math.min


object RecsService {

    private const val MAX_RECS_CAP = 10

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

            val tallId = R.drawable.empty_poster // in-app poster
            val wideId = R.drawable.lampa_banner // in-app poster
            val emptyPoster = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(this.resources.getResourcePackageName(tallId))
                .appendPath(this.resources.getResourceTypeName(tallId))
                .appendPath(this.resources.getResourceEntryName(tallId))
                .build()
            val emptyWidePoster = Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(this.resources.getResourcePackageName(wideId))
                .appendPath(this.resources.getResourceTypeName(wideId))
                .appendPath(this.resources.getResourceEntryName(wideId))
                .build()

            val cards = getRecs()
            val itemsSend = min(cards.size, MAX_RECS_CAP)

            var priority = 1f
            val delta = 1f / itemsSend

            mNotifyManager.cancelAll()

            for (i in 0 until itemsSend) {
                try {
                    val card = cards[i]
                    // title
                    var recTitle = card.title ?: ""
                    if (recTitle.isEmpty())
                        recTitle = card.name ?: ""
                    if (recTitle.isEmpty())
                        recTitle = ""
                    // original title
                    var recOriginal = card.original_title ?: ""
                    if (recOriginal.isEmpty())
                        recOriginal = card.original_name ?: ""
                    if (recOriginal.isEmpty())
                        recOriginal = recTitle

                    var recPoster = card.img
                    if (recPoster.isNullOrEmpty())
                        recPoster = emptyPoster.toString()

                    var widePoster = card.background_image
                    if (widePoster.isNullOrEmpty())
                        widePoster = emptyWidePoster.toString()

                    // 2:3
                    var posterWidth = dp2px(this, 200f)
                    var posterHeight = dp2px(this, 300f)
                    // 16:9
                    if (isAmazonDev && widePoster.isNotEmpty()) {
                        recPoster = widePoster
                        posterWidth = dp2px(this, 300f)
                        posterHeight = dp2px(this, 170f)
                    }

                    var bitmap = Glide.with(this)
                        .asBitmap()
                        .load(recPoster)
                        .submit(posterWidth, posterHeight)
                        .get()

                    if (isAmazonDev && widePoster.isNotEmpty())
                        bitmap = drawTextToBitmap(bitmap, recOriginal)

                    val info = mutableListOf<String>()
                    card.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }
                    if (card.type == "tv") {
                        info.add(getString(R.string.series))
                        card.number_of_seasons?.let { info.add("S$it") }
                    }
                    val genres = card.genres?.map { g ->
                        g?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    }?.toTypedArray()
                    card.genres?.joinToString(", ") { genre ->
                        genre?.name?.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }.toString()
                    }?.let { info.add(it) }

                    builder.setBadgeIcon(R.drawable.logo_icon)
                        .setIdTag("${card.id}")
                        .setTitle(recTitle)
                        .setContentImage(bitmap)
                        .setText(info.joinToString(" · "))
                        .setGenres(genres)
                        .setContentIntentData(
                            ContentRecommendation.INTENT_TYPE_ACTIVITY,
                            buildPendingIntent(card, null),
                            0,
                            null
                        )
                        .setColor(ContextCompat.getColor(this, R.color.teal_500))
                        .setRunningTime(card.runtime?.toLong()?.times(60L) ?: 0L)
                        .setGroup("lampa")
                        .setSortKey(priority.toString())
                    // type
                    if (card.type == "tv")
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_SERIAL))
                    else
                        builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_MOVIE))
                    // backdrops
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

    private fun Context.drawTextToBitmap(bitmap: Bitmap?, mText: String): Bitmap? {
        if (mText.isBlank() || bitmap == null)
            return bitmap
        try {
            val scale = this.resources.displayMetrics.density
            val fontSize = 18f * scale
            val fontPading = 12f * scale
            val bitmapConfig = bitmap.config
            //if (bitmapConfig == null) bitmapConfig = Bitmap.Config.ARGB_8888

            val draw = bitmap.copy(bitmapConfig, true)
            val canvas = Canvas(draw)
            val paintTxt = Paint(Paint.ANTI_ALIAS_FLAG)
            // Text color
            paintTxt.color = Color.parseColor("#eeeeee")
            // Text size
            paintTxt.textSize = fontSize
            // Cineplex Bold Font
            val condfont = Typeface.createFromAsset(this.assets, "cineplex.ttf")
            paintTxt.typeface = condfont
            // Fallback Font type
            val currentTypeFace: Typeface = paintTxt.typeface
            val normal = Typeface.create(currentTypeFace, Typeface.NORMAL)
            paintTxt.typeface = normal
            // Text shadow
            paintTxt.setShadowLayer(1f * scale, 0f, 0f, Color.BLACK)
            val y = draw.height - fontPading // text baseline

            val paintRect = Paint(Paint.ANTI_ALIAS_FLAG)
            paintRect.color = Color.parseColor("#80000000")
            paintRect.textAlign = Paint.Align.CENTER

            canvas.drawRect(
                0f,
                y - fontSize - fontPading / 2,
                draw.width.toFloat(),
                draw.height.toFloat(),
                paintRect
            )
            canvas.drawText(mText, fontPading, y, paintTxt)

            return draw
        } catch (e: Exception) {
            return bitmap
        }
    }
}