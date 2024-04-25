package top.rootu.lampa

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.rootu.lampa.helpers.Helpers.isConnected
import top.rootu.lampa.helpers.Helpers.setLanguage
import top.rootu.lampa.helpers.Updater
import top.rootu.lampa.tmdb.TMDB

class App : MultiDexApplication() {
    init {
        // use vectors on pre-LP devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    companion object {

        private val TAG: String = App::class.java.simpleName
        private lateinit var appContext: Context
        var inForeground: Boolean = false

        val context: Context
            get() {
                return appContext
            }

        private val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (BuildConfig.DEBUG) Log.d(TAG, "in background")
                inForeground = false
            } else if (event == Lifecycle.Event.ON_START) {
                if (BuildConfig.DEBUG) Log.d(TAG, "in foreground")
                inForeground = true
            }
        }

        fun toast(txt: String, long: Boolean = true) {
            Handler(Looper.getMainLooper()).post {
                val duration = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(appContext, txt, duration).show()
            }
        }

        fun toast(txt: Int, long: Boolean = true) {
            Handler(Looper.getMainLooper()).post {
                val duration = if (long)
                    android.widget.Toast.LENGTH_LONG
                else
                    android.widget.Toast.LENGTH_SHORT
                android.widget.Toast.makeText(appContext, txt, duration).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // register lifecycle observer
        ProcessLifecycleOwner
            .get().lifecycle
            .addObserver(lifecycleEventObserver)

        App.context.setLanguage()
        // app crash handler
        handleUncaughtException(showLogs = true)
        // self-update check
        val checkUpdates = true
        if (checkUpdates) {
            CoroutineScope(Dispatchers.IO).launch {
                var count = 60
                try {
                    while (!isConnected(appContext) && count > 0) {
                        delay(1000) // wait for network
                        count--
                    }
                    if (Updater.check()) {
                        val intent = Intent(appContext, UpdateActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
            }
        }
        // Init TMBD.genres
        CoroutineScope(Dispatchers.IO).launch {
            TMDB.initGenres()
        }
    }
}