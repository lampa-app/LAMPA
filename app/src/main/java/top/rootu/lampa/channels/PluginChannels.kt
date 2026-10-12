package top.rootu.lampa.channels

import android.os.Build
import android.util.Log
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import java.util.concurrent.Executors

/** One queue shared by all bridge instances, including after activity recreation. */
internal object PluginChannels {
    private val publisher = PluginChannelPublisher(
        Executors.newSingleThreadExecutor { task -> Thread(task, "plugin-channels") },
        { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTvContentProviderAvailable },
        { request ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ChannelManager.replacePluginChannel(request)
            }
        },
        { error -> Log.e("PluginChannels", "Plugin channel operation failed: ${error.javaClass.simpleName}") }
    )

    fun publish(json: String?): Boolean = publisher.publish(json)
    fun clear(id: String?): Boolean = publisher.clear(id)
}
