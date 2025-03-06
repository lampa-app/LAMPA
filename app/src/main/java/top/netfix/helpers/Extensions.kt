package top.netfix.helpers

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.netfix.CrashActivity
import kotlin.system.exitProcess


fun Application.handleUncaughtException(showLogs: Boolean? = null) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        /**
        here you can report the throwable exception to Sentry or Crashlytics or whatever crash reporting service you're using,
        otherwise you may set the throwable variable to _ if it'll remain unused
         */
        val errorReport = StringBuilder()
        CoroutineScope(Dispatchers.IO).launch {
            var arr = throwable.stackTrace
            errorReport.append("---------------- Device Info ----------------\n")
            errorReport.append("Model: ${Helpers.deviceName}\n")
            errorReport.append("Android SDK: ${Build.VERSION.SDK_INT}\n")
            errorReport.append("\n---------------- Main Crash ----------------\n")
            errorReport.append(throwable)
            errorReport.append("\n\n")
            errorReport.append("---------------- Stack Strace ----------------\n\n")
            for (i in arr) {
                errorReport.append(i)
                errorReport.append("\n")
            }
            errorReport.append("\n---------------- end of crash details ----------------\n\n")

            /** If the exception was thrown in a background thread inside
            then the actual exception can be found with getCause*/
            errorReport.append("- background thread Crash Log ----------------\n")
            val cause: Throwable? = throwable.cause
            if (cause != null) {
                errorReport.append("Main Crash Name - $cause".trimIndent())

                arr = cause.stackTrace
                for (i in arr) {
                    errorReport.append(i)
                    errorReport.append("\n")
                }
            }
            errorReport.append("\n- end of background thread Crash Log ----------------\n\n")

            val intent = Intent(this@handleUncaughtException, CrashActivity::class.java).apply {
                putExtra("errorDetails", errorReport.toString())
                putExtra("isShownLogs", showLogs.toString())
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intent)

            Process.killProcess(Process.myPid())
            exitProcess(2)

        }
    }
}

fun Context.copyToClipBoard(errorData: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("label", errorData)
    clipboardManager.setPrimaryClip(clipData)
}