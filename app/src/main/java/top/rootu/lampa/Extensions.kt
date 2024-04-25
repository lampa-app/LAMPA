package top.rootu.lampa

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.rootu.lampa.helpers.Backup
import top.rootu.lampa.helpers.PermHelpers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess


fun Application.handleUncaughtException(showLogs:Boolean?=null) {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        /**
        here you can report the throwable exception to Sentry or Crashlytics or whatever crash reporting service you're using,
        otherwise you may set the throwable variable to _ if it'll remain unused
         */
        val errorReport = StringBuilder()
        Log.d("*****", "handleUncaughtException(showLogs:$showLogs)")
        CoroutineScope(Dispatchers.IO).launch {
            var arr = throwable.stackTrace
            errorReport.append("---------------- Main Crash ----------------\n")
            errorReport.append(throwable)
            errorReport.append("\n\n")
            errorReport.append("---------------- Stack Strace ----------------\n\n")
            for (i in arr) {
                errorReport.append(i)
                errorReport.append("\n")
            }
            errorReport.append("\n---------------- end of crash deatils ----------------\n\n")

            /** If the exception was thrown in a background thread inside
            then the actual exception can be found with getCause*/
            errorReport.append("background thread Crash Log ----------------\n")
            val cause: Throwable? = throwable.cause
            if (cause != null) {
                errorReport.append("Main Crash Name - $cause".trimIndent())

                arr = cause.stackTrace
                for (i in arr) {
                    errorReport.append(i)
                    errorReport.append("\n")
                }
            }
            errorReport.append("end of background thread Crash Log ----------------\n\n")
            withContext(Dispatchers.Main) {
                val intent = Intent(this@handleUncaughtException, CrashActivity::class.java).apply {
                    putExtra("errorDetails", errorReport.toString())
                    putExtra("isShownLogs",showLogs.toString())
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                //finish()
                Process.killProcess(Process.myPid())
                exitProcess(2)
            }
        }
    }
}

fun Context.copyToClipBoard(errorData:String){
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("label",errorData)
    clipboardManager.setPrimaryClip(clipData)
}
fun Context.saveCrashLog(errorData:String): Boolean {
    if (!PermHelpers.hasStoragePermissions(this)) {
        PermHelpers.verifyStoragePermissions(this)
    }
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val current = LocalDateTime.now().format(formatter)
    return Backup.writeFile("${current}.crash.txt", errorData)
}