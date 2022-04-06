package top.rootu.lampa

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.multidex.MultiDexApplication

class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        private lateinit var appContext: Context

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
}