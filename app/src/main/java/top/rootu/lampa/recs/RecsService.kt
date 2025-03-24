package top.rootu.lampa.recs

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import top.rootu.lampa.helpers.Helpers.getDefaultPosterUri
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.isAmazonDev
import top.rootu.lampa.models.LampaCard
import java.util.Locale
import kotlin.math.min


object RecsService {

    private const val MAX_RECS_CAP = 10

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun updateRecs() {
        val context = App.context

        if (!isAndroidTV) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        val recommendations = getRecs()
        val itemsToSend = min(recommendations.size, MAX_RECS_CAP)

        if (BuildConfig.DEBUG) {
            Log.d("RecsService", "Sending $itemsToSend items to TV Recs")
        }

        recommendations.take(itemsToSend).forEachIndexed { index, card ->
            try {
                val recommendation = buildRecommendation(card, index, itemsToSend)
                card.id?.toIntOrNull()?.let { notificationManager.notify(it, recommendation) }
            } catch (e: Exception) {
                Log.e("RecsService", "Failed to build recommendation for card: ${card.id}", e)
            }
        }
    }

    private fun getRecs(): List<LampaCard> {
        return LampaProvider.get(LampaProvider.RECS, true)?.items?.take(MAX_RECS_CAP).orEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun buildRecommendation(card: LampaCard, index: Int, totalItems: Int): Notification {
        val context = App.context
        val builder = ContentRecommendation.Builder()

        val title = card.title ?: card.name ?: ""
        val originalTitle = card.original_title ?: card.original_name ?: title
        val posterUri = card.img ?: getDefaultPosterUri(R.drawable.empty_poster).toString()
        val widePosterUri =
            card.background_image ?: getDefaultPosterUri(R.drawable.lampa_banner).toString()

        val (posterWidth, posterHeight) = if (context.isAmazonDev && widePosterUri.isNotEmpty()) {
            Pair(dp2px(context, 300f), dp2px(context, 170f))
        } else {
            Pair(dp2px(context, 200f), dp2px(context, 300f))
        }

        val posterBitmap = loadPosterBitmap(posterUri, posterWidth, posterHeight)
        val finalBitmap = if (context.isAmazonDev && widePosterUri.isNotEmpty()) {
            context.drawTextToBitmap(posterBitmap, originalTitle)
        } else {
            posterBitmap
        }

        val info = buildRecommendationInfo(card)
        val genres = card.genres?.mapNotNull {
            it.name?.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }?.toTypedArray()

        builder.setBadgeIcon(R.drawable.lampa_logo_icon)
            .setIdTag("${card.id}")
            .setTitle(title)
            .setContentImage(finalBitmap)
            .setText(info.joinToString(" · "))
            .setGenres(genres)
            .setContentIntentData(
                ContentRecommendation.INTENT_TYPE_ACTIVITY,
                buildPendingIntent(card, null),
                0,
                null
            )
            .setColor(ContextCompat.getColor(context, R.color.teal_500))
            .setRunningTime(card.runtime?.toLong()?.times(60L) ?: 0L)
            .setGroup("lampa")
            .setSortKey((1f - (index.toFloat() / totalItems)).toString())

        if (card.type == "tv") {
            builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_SERIAL))
        } else {
            builder.setContentTypes(arrayOf(ContentRecommendation.CONTENT_TYPE_MOVIE))
        }

        card.background_image?.let { builder.setBackgroundImageUri(it) }

        val notification = builder.build().getNotificationObject(context)
        notification.priority = index - 5000

        if (context.isAmazonDev) {
            notification.extras.apply {
                putString("com.amazon.extra.DISPLAY_NAME", context.getString(R.string.app_name))
                putInt("com.amazon.extra.RANK", index)
                putString("com.amazon.extra.LONG_DESCRIPTION", card.overview)
                putString("com.amazon.extra.CONTENT_RELEASE_DATE", card.release_year)
                card.imdb_id?.let { putString("com.amazon.extra.IMDB_ID", it) }
            }
        }

        return notification
    }

    private fun loadPosterBitmap(uri: String, width: Int, height: Int): Bitmap? {
        return try {
            Glide.with(App.context)
                .asBitmap()
                .load(uri)
                .submit(width, height)
                .get()
        } catch (e: Exception) {
            Log.e("RecsService", "Failed to load poster: $uri", e)
            null
        }
    }

    private fun buildRecommendationInfo(card: LampaCard): List<String> {
        val info = mutableListOf<String>()
        card.vote_average?.let { if (it > 0.0) info.add("%.1f".format(it)) }
        if (card.type == "tv") {
            info.add(App.context.getString(R.string.series))
            card.number_of_seasons?.let { info.add("S$it") }
        }
        card.genres?.mapNotNull {
            it.name?.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }
        }?.joinToString(", ")?.let { info.add(it) }
        return info
    }

    private fun Context.drawTextToBitmap(bitmap: Bitmap?, text: String): Bitmap? {
        // Early return if the bitmap is null or the text is blank
        if (bitmap == null || text.isBlank()) return bitmap

        return try {
            // Create a mutable copy of the bitmap to draw on
            val mutableBitmap = bitmap.copy(bitmap.config, true)
            val canvas = Canvas(mutableBitmap)

            // Set up text properties
            val scale = resources.displayMetrics.density
            val fontSize = 18f * scale
            val fontPadding = 12f * scale
            val textColor = Color.parseColor("#eeeeee")
            val shadowColor = Color.BLACK
            val shadowRadius = 1f * scale
            val shadowDx = 0f
            val shadowDy = 0f

            // Set up text paint
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = fontSize
                typeface = try {
                    // Load custom font from assets
                    Typeface.createFromAsset(assets, "cineplex.ttf")
                } catch (_: Exception) {
                    // Fallback to default typeface if custom font fails to load
                    Typeface.DEFAULT
                }
                setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
            }

            // Set up background rectangle paint
            val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80000000") // Semi-transparent black
            }

            // Calculate text position
            val textX = fontPadding
            val textY = mutableBitmap.height - fontPadding

            // Draw background rectangle
            canvas.drawRect(
                0f,
                textY - fontSize - fontPadding / 2,
                mutableBitmap.width.toFloat(),
                mutableBitmap.height.toFloat(),
                rectPaint
            )

            // Draw text on the bitmap
            canvas.drawText(text, textX, textY, textPaint)

            // Return the modified bitmap
            mutableBitmap
        } catch (e: Exception) {
            // Log the error and return the original bitmap if something goes wrong
            Log.e("drawTextToBitmap", "Failed to draw text on bitmap: ${e.message}")
            bitmap
        }
    }
}