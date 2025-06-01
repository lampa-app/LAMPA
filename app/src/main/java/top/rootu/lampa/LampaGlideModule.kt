package top.rootu.lampa

import android.content.Context
import android.os.Build
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import top.rootu.lampa.tmdb.TMDB.permissiveOkHttp
import top.rootu.lampa.tmdb.TMDB.startWithQuad9DNS
import java.io.InputStream

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
class LampaGlideModule : AppGlideModule() {
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            startWithQuad9DNS() else permissiveOkHttp()
        val factory = OkHttpUrlLoader.Factory(client)
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            factory
        )
    }
}