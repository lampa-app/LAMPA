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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.rootu.lampa.helpers.Helpers.isConnected
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Updater
import top.rootu.lampa.helpers.handleUncaughtException
import top.rootu.lampa.helpers.setLanguage
import top.rootu.lampa.tmdb.TMDB

class App : MultiDexApplication() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // use vectors on pre-LP devices
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }

    companion object {
        private val TAG: String = App::class.java.simpleName
        private lateinit var appContext: Context

        @Volatile
        var inForeground: Boolean = false
            private set

        val context: Context
            get() = appContext

        private val lifecycleEventObserver = LifecycleEventObserver { _, event ->
            inForeground = when (event) {
                Lifecycle.Event.ON_START -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "in foreground")
                    true
                }

                Lifecycle.Event.ON_STOP -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "in background")
                    false
                }

                else -> inForeground
            }
        }

        fun toast(txt: String, long: Boolean = true) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showToast(txt, long)
            } else {
                Handler(Looper.getMainLooper()).post { showToast(txt, long) }
            }
        }

        fun toast(txt: Int, long: Boolean = true) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                showToast(context.getString(txt), long)
            } else {
                Handler(Looper.getMainLooper()).post {
                    showToast(context.getString(txt), long)
                }
            }
        }

        private fun showToast(text: String, long: Boolean) {
            val duration = if (long) android.widget.Toast.LENGTH_LONG
            else android.widget.Toast.LENGTH_SHORT
            android.widget.Toast.makeText(appContext, text, duration).show()
        }

        fun setAppLanguage(context: Context, langCode: String) {
            context.appLang = langCode
            if (context is BaseActivity) {
                context.recreateWithLanguage()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // setup applicationContext
        appContext = applicationContext.setLanguage()
        // ensure resources are properly initialized
        resources

        // register lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)

        // app crash handler
        handleUncaughtException(showLogs = true)
        //CrashHandler(this).initialize(showLogs = BuildConfig.DEBUG)

        // Initialize components
        initializeComponents()
    }

    private fun initializeComponents() {
        // self-update check
        if (BuildConfig.enableUpdate) {
            applicationScope.launch {
                checkForUpdates()
            }
        }

        // Init TMDB genres
        applicationScope.launch {
            TMDB.initGenres()
        }

    }

    private suspend fun checkForUpdates() {
        var count = 60
        try {
            while (!isConnected(appContext) && count > 0) {
                delay(1000) // wait for network
                count--
            }

            if (count > 0 && Updater.check()) {
                while (count > 0 && !inForeground) { // wait foreground
                    delay(1000)
                    count--
                }
                if (inForeground) {
                    val intent = Intent(appContext, UpdateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
        }
    }
}