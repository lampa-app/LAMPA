package top.rootu.lampa

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.OkHttpClient
import java.io.InputStream
import java.util.concurrent.TimeUnit

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
class LampaGlide : AppGlideModule() {
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val timeout = 15L // in seconds
        val builder: OkHttpClient.Builder = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
        // TODO: Use permissive HTTP client
        val client: OkHttpClient = builder.build()
        val factory = OkHttpUrlLoader.Factory(client)
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            factory
        )
    }
}