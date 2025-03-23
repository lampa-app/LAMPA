package top.rootu.lampa.helpers

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.TvContractCompat
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.R
import top.rootu.lampa.content.LampaProvider.BOOK
import top.rootu.lampa.content.LampaProvider.HIST
import top.rootu.lampa.content.LampaProvider.LIKE
import java.nio.charset.Charset

/**
 * Extension property to retrieve the internal provider data as a string.
 */
val Channel.data: String
    get() {
        return this.internalProviderDataByteArray?.toString(Charset.defaultCharset()) ?: "lampa${this.id}"
    }

object ChannelHelper {

    @RequiresApi(Build.VERSION_CODES.O)
    private val CHANNELS_PROJECTION = arrayOf(
        TvContractCompat.Channels._ID,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
        // TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,
        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
    )

    /**
     * Adds a new channel if it doesn't already exist.
     *
     * @param name The internal name of the channel.
     * @param displayName The display name of the channel.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun add(name: String, displayName: String) {
        val channel = get(name)
        if (channel != null)
            return
        if (BuildConfig.DEBUG) Log.d("ChannelHelper", "add(name: $name, displayName: $displayName)")
        val builder = Channel.Builder()
        builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(displayName)
            .setInternalProviderData(name)
            .setAppLinkIntentUri(Uri.parse("lampa://${BuildConfig.APPLICATION_ID}/update_channel/$name"))

        val channelUri = App.context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )
        val channelId = channelUri?.let { ContentUris.parseId(it) }

        // channel images
        val icon = when (name) {
            BOOK -> R.drawable.ch_book_shape
            HIST -> R.drawable.ch_hist_shape
            LIKE -> R.drawable.ch_like_shape
            else -> R.drawable.lampa_logo_round
        }
        // val themedContext = ContextThemeWrapper(App.context, R.style.Theme_LAMPA)
        val bitmap = convertToBitmap(context = App.context, icon)

        if (channelId != null) {
            ChannelLogoUtils.storeChannelLogo(App.context, channelId, bitmap)
            TvContractCompat.requestChannelBrowsable(App.context, channelId)
        }
    }

    /**
     * Retrieves a channel by its internal name.
     *
     * @param name The internal name of the channel.
     * @return The [Channel] object, or `null` if not found.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun get(name: String): Channel? {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            CHANNELS_PROJECTION,
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    if (name == channel.data) {
                        return channel
                    }
                } while (it.moveToNext())
        }
        return null
    }

    /**
     * Removes a channel.
     *
     * @param channel The [Channel] to remove.
     */
    fun rem(channel: Channel) {
        App.context.contentResolver.delete(TvContractCompat.buildChannelUri(channel.id), null, null)
    }

    /**
     * Retrieves a list of all channels.
     *
     * @return A list of [Channel] objects.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun list(): List<Channel> {
        val ch = mutableListOf<Channel>()

        val cursor = App.context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            CHANNELS_PROJECTION,
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    ch.add(channel)
                } while (it.moveToNext())
        }
        return ch
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getChanByID(channelId: Long): String? {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            CHANNELS_PROJECTION,
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    if (channelId == channel.id) {
                        return channel.data
                    }
                } while (it.moveToNext())
        }
        return null
    }

    /**
     * Converts a drawable resource into a [Bitmap].
     *
     * @param context The context used to access resources.
     * @param resourceId The ID of the drawable resource.
     * @return The [Bitmap] object.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun convertToBitmap(context: Context, resourceId: Int): Bitmap {
        val drawable: Drawable? = AppCompatResources.getDrawable(context, resourceId)
        if (drawable is VectorDrawable) {
            val bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
        return BitmapFactory.decodeResource(context.resources, resourceId)
    }
}
