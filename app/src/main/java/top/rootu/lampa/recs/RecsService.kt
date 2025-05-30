package top.rootu.lampa.recs

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recommendation.app.ContentRecommendation
import com.bumptech.glide.Glide
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Helpers.buildPendingIntent
import top.rootu.lampa.helpers.Helpers.getDefaultPosterUri
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.capitalizeFirstLetter
import top.rootu.lampa.helpers.isAmazonDev
import top.rootu.lampa.models.LampaCard
import kotlin.math.min


object RecsService {

    private const val TAG = "RecsService"
    private const val MAX_RECS_CAP = 10
    private const val TEXT_SIZE = 18f
    private const val TEXT_PADDING = 12f
    private const val TEXT_SHADOW_RADIUS = 1f
    private const val TEXT_BACKGROUND_ALPHA = 0x80
    private const val TEXT_COLOR = "#eeeeee"

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun updateRecs() {
        if (!isAndroidTV) return

        val context = App.context
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        val recommendations =
            LampaProvider.get(LampaProvider.RECS, true)?.items?.take(MAX_RECS_CAP).orEmpty()
        val itemsToSend = min(recommendations.size, MAX_RECS_CAP)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Sending $itemsToSend items to TV Recs")
        }

        recommendations.take(itemsToSend).forEachIndexed { index, card ->
            try {
                val recommendation = buildRecommendation(card, index, itemsToSend)
                card.id?.toIntOrNull()?.let { notificationManager.notify(it, recommendation) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build recommendation for card: ${card.id}", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun buildRecommendation(card: LampaCard, index: Int, totalItems: Int): Notification {
        val context = App.context
        val builder = ContentRecommendation.Builder()
        val isAmazon = context.isAmazonDev

        val title = card.title ?: card.name ?: ""
        val originalTitle = card.original_title ?: card.original_name ?: title
        val posterUri = if (isAmazon) {
            card.background_image?.takeIf { it.isNotEmpty() }
                ?: card.img?.takeIf { it.isNotEmpty() }
                ?: getDefaultPosterUri(R.drawable.empty_poster).toString()
        } else card.img ?: getDefaultPosterUri(R.drawable.empty_poster).toString()
        val (posterWidth, posterHeight) = if (isAmazon && !card.background_image.isNullOrEmpty()) {
            Pair(640, 360) // dp2px(context, 320f), dp2px(context, 180f)
        } else {
            Pair(400, 600) // dp2px(context, 200f), dp2px(context, 300f)
        }

        val posterBitmap = loadPosterBitmap(posterUri, posterWidth, posterHeight)
        val finalBitmap = if (isAmazon && posterBitmap.width > posterBitmap.height) {
                context.drawTextToBitmap(posterBitmap, originalTitle) // originalTitle
        } else {
            posterBitmap
        }

        val info = buildRecommendationInfo(card)
        val genres = card.genres?.mapNotNull {
            it?.name?.capitalizeFirstLetter()?.takeIf { genre -> genre.isNotBlank() }
        }?.takeIf { it.isNotEmpty() }?.toTypedArray()

        builder.setBadgeIcon(R.drawable.lampa_logo_icon)
            .setIdTag("${card.id}")
            .setTitle(title)
            .setContentImage(finalBitmap)
            .setText(info.joinToString(" · "))
            .setGenres(genres)
            .setContentIntentData(
                ContentRecommendation.INTENT_TYPE_ACTIVITY,
                buildPendingIntent(card, null, null),
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
        @Suppress("DEPRECATION")
        notification.priority = index - 5000

        if (isAmazon) {
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

    private fun loadPosterBitmap(uri: String, width: Int, height: Int): Bitmap {
        val defaultResId = if (width > height) R.drawable.lampa_banner else R.drawable.empty_poster
        return try {
            Glide.with(App.context)
                .asBitmap()
                .load(uri)
                .error(
                    Glide.with(App.context)
                        .asBitmap()
                        .load(getDefaultPosterUri(defaultResId))
                )
                .centerCrop()
                .submit(width, height)
                .get()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load poster: $uri", e)
            try {
                // Try loading default with Glide one more time
                Glide.with(App.context)
                    .asBitmap()
                    .load(getDefaultPosterUri(defaultResId))
                    .centerCrop()
                    .submit(width, height)
                    .get()
            } catch (_: Exception) {
                // Final fallback
                BitmapFactory.decodeResource(App.context.resources, defaultResId)
            }
        }
    }

    private fun buildRecommendationInfo(card: LampaCard): List<String> {
        val info = mutableListOf<String>()
        // Add vote average if present and > 0
        card.vote_average?.takeIf { it > 0.0 }?.let {
            info.add("%.1f".format(it))
        }
        // Add series info if TV show
        if (card.type == "tv") {
            info.add(App.context.getString(R.string.series))
            card.number_of_seasons?.takeIf { it > 0 }?.let {
                info.add("S$it")
            }
        }
        // Add genres if present
        card.genres?.mapNotNull {
            it?.name?.capitalizeFirstLetter()?.takeIf { genre -> genre.isNotBlank() }
        }?.takeIf { it.isNotEmpty() }?.joinToString(", ")?.let {
            info.add(it)
        }
        return info
    }

    private fun Context.drawTextToBitmap(bitmap: Bitmap?, text: String): Bitmap? {
        // Early return if the bitmap is null or the text is blank
        if (bitmap == null || text.isBlank()) return bitmap

        return try {
            val bitmapConfig =
                Bitmap.Config.ARGB_8888 // bitmap.config // ?: Bitmap.Config.ARGB_8888
            // Create a mutable copy of the bitmap to draw on
            val mutableBitmap = bitmap.copy(bitmapConfig, true)
            val canvas = Canvas(mutableBitmap)

            // Set up text properties
            val scale = 2.0f // resources.displayMetrics.density
            val fontSize = TEXT_SIZE * scale
            val fontPadding = TEXT_PADDING * scale
            val textColor = TEXT_COLOR.toColorInt()
            val shadowColor = Color.BLACK
            val shadowRadius = TEXT_SHADOW_RADIUS * scale
            val shadowDx = 0f
            val shadowDy = 0f

            // Set up text paint
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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
            // Measure text width
            val textWidth = textPaint.measureText(text)
            val availableWidth = mutableBitmap.width - (fontPadding) // fontPadding * 2
            // Ellipsize text if too long
            val finalText = if (textWidth > availableWidth) {
                TextUtils.ellipsize(
                    text,
                    textPaint,
                    availableWidth,
                    TextUtils.TruncateAt.END
                ).toString()
            } else {
                text
            }

            // Set up background rectangle paint
            val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(TEXT_BACKGROUND_ALPHA, 0, 0, 0) // Black with 50% opacity
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
            canvas.drawText(finalText, textX, textY, textPaint) // text

            // Return the modified bitmap
            mutableBitmap
        } catch (e: Exception) {
            // Log the error and return the original bitmap if something goes wrong
            Log.e(TAG, "Failed to draw text on bitmap: ${e.message}")
            bitmap
        }
    }
}