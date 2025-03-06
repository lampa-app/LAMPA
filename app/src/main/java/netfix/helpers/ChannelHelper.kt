package netfix.helpers

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
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.TvContractCompat
import netfix.App
import netfix.app.BuildConfig
import netfix.app.R
import netfix.content.LampaProvider.BOOK
import netfix.content.LampaProvider.HIST
import netfix.content.LampaProvider.LIKE
import java.nio.charset.Charset


object ChannelHelper {

    @RequiresApi(Build.VERSION_CODES.O)
    private val CHANNELS_PROJECTION = arrayOf(
        TvContractCompat.Channels._ID,
        TvContract.Channels.COLUMN_DISPLAY_NAME,
//        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,
        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun add(name: String, displayName: String) {
        val channel = get(name)
        if (channel != null)
            return

        val builder = Channel.Builder()
        builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(displayName)
            .setInternalProviderData(name)
            .setAppLinkIntentUri(Uri.parse("netfix://${BuildConfig.APPLICATION_ID}/update_channel/$name"))

        val channelUri = App.context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )
        val channelId = channelUri?.let { ContentUris.parseId(it) }
        // set channel images
        val icon = when (name) {
            BOOK -> R.drawable.ch_book_shape
            HIST -> R.drawable.ch_hist_shape
            LIKE -> R.drawable.ch_like_shape
            else -> R.drawable.netfix_logo_round
        }
//        val themedContext = ContextThemeWrapper(App.context, R.style.Theme_LAMPA)
        val bitmap = convertToBitmap(context = App.context, icon)
        if (channelId != null) {
            ChannelLogoUtils.storeChannelLogo(App.context, channelId, bitmap)
            TvContractCompat.requestChannelBrowsable(App.context, channelId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun get(name: String): Channel? {
        val cursor = App.context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            CHANNELS_PROJECTION,
            null,
            null,
            null
        )

        cursor?.let {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    if (name == channel.data) {
                        cursor.close()
                        return channel
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return null
    }

    fun rem(ch: Channel) {
        App.context.contentResolver.delete(TvContractCompat.buildChannelUri(ch.id), null, null)
    }

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

        cursor?.let {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    ch.add(channel)
                } while (it.moveToNext())
            cursor.close()
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

        cursor?.let {
            if (it.moveToFirst())
                do {
                    val channel = Channel.fromCursor(it)
                    if (channelId == channel.id) {
                        cursor.close()
                        return channel.data
                    }
                } while (it.moveToNext())
            cursor.close()
        }
        return null
    }

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

val Channel.data: String
    get() {
        return this.internalProviderDataByteArray?.toString(Charset.defaultCharset()) ?: "fake${this.id}"
    }