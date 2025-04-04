package top.rootu.lampa.helpers

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.defPrefs
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.xml.parsers.DocumentBuilderFactory

object Backup {
    private val isOperationInProgress = AtomicBoolean(false)
    private const val MAX_BACKUPS = 5

    val DIR: File by lazy { // Compatible directory selection for all APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .resolve("LAMPA").apply { mkdirs() }
        } else {
            File(Environment.getExternalStorageDirectory(), "LAMPA").apply { mkdirs() }
        }
    }

    fun Context.saveSettings(which: String? = ""): Boolean {
        if (!PermHelpers.hasStoragePermissions(this)) {
            PermHelpers.verifyStoragePermissions(this)
            return false
        }

        if (!isOperationInProgress.compareAndSet(false, true)) {
            App.toast("Backup operation in progress")
            return false
        }

        return try {
            val prefsFile = getPrefsFile(which)
            val content = try {
                prefsFile.readTextSafely()
            } catch (_: Exception) {
                App.toast("Error reading settings")
                return false
            }

            if (content.isNotBlank()) {
                val baseName = if (which.isNullOrEmpty()) "prefs.backup" else "$which.backup"
                rotateBackups(baseName)
                writeFileSafely(baseName, content)
            } else false
        } finally {
            isOperationInProgress.set(false)
        }
    }

    fun Context.loadFromBackup(which: String? = ""): Boolean {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            App.toast("Restore operation in progress")
            return false
        }

        return try {
            val content = loadFileSafely(if (which.isNullOrEmpty()) "prefs.backup" else "$which.backup")
            if (content.isBlank()) return false
            parseAndApplyPreferences(which, content)
        } finally {
            isOperationInProgress.set(false)
        }
    }

    private fun Context.getPrefsFile(which: String?): File {
        val fileName = if (which.isNullOrEmpty()) {
            "${packageName}_preferences.xml"
        } else {
            "$which.xml"
        }
        return File(filesDir.parentFile, "shared_prefs/$fileName")
    }

    private fun rotateBackups(baseName: String) {
        try {
            // 1. Initialize date format with minute precision
            val minuteFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

            // 2. Get existing backups sorted newest first
            val backups = DIR.listFiles { file ->
                file.name.startsWith(baseName) && file.name != baseName
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            // 3. Archive current backup (if exists) with minute timestamp
            File(DIR, baseName).takeIf { it.exists() }?.let { current ->
                val timestamp = minuteFormat.format(Date(current.lastModified()))
                var uniqueName = "$baseName.$timestamp"

                // Handle duplicates (same minute)
                var counter = 1
                while (File(DIR, uniqueName).exists()) {
                    uniqueName = "$baseName.$timestamp.$counter"
                    counter++
                }

                if (!current.renameTo(File(DIR, uniqueName))) {
                    Log.w("Backup", "Failed to archive $baseName")
                }
            }

            // 4. Clean up excess backups (keeps newest MAX_BACKUPS)
            backups.drop(MAX_BACKUPS).forEach {
                if (!it.deleteSafely()) {
                    Log.w("Backup", "Failed to delete old backup: ${it.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("Backup", "Rotation failed", e)
            App.toast("Backup rotation error")
        }
    }

    private fun Context.parseAndApplyPreferences(which: String?, xmlContent: String): Boolean {
        return try {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xmlContent)))

            val pref = if (which.isNullOrEmpty()) defPrefs
            else getSharedPreferences(which, Context.MODE_PRIVATE)

            val edit = pref.edit()
            var child = doc.documentElement.firstChild

            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val element = child as Element
                    when (element.nodeName) {
                        "int" -> element.getAttribute("value").toIntOrNull()?.let {
                            edit.putInt(element.getAttribute("name"), it)
                        }
                        "long" -> element.getAttribute("value").toLongOrNull()?.let {
                            edit.putLong(element.getAttribute("name"), it)
                        }
                        "float" -> element.getAttribute("value").toFloatOrNull()?.let {
                            edit.putFloat(element.getAttribute("name"), it)
                        }
                        "string" -> element.textContent.takeIf { it.isNotBlank() }?.let {
                            edit.putString(element.getAttribute("name"), it)
                        }
                        "boolean" -> edit.putBoolean(
                            element.getAttribute("name"),
                            element.getAttribute("value") == "true"
                        )
                        "set" -> {
                            val values = mutableListOf<String>().apply {
                                var ch = element.firstChild
                                while (ch != null) {
                                    if (ch.nodeType == Node.ELEMENT_NODE) {
                                        (ch as Element).textContent.takeIf { it.isNotBlank() }?.let { add(it) }
                                    }
                                    ch = ch.nextSibling
                                }
                            }.takeIf { it.isNotEmpty() }?.toSet()
                            values?.let { edit.putStringSet(element.getAttribute("name"), it) }
                        }
                    }
                }
                child = child.nextSibling
            }
            edit.apply()
            true
        } catch (_: Exception) {
            App.toast("Failed to parse backup")
            false
        }
    }

    fun writeFileSafely(fileName: String, content: String): Boolean {
        val tempFile = File(DIR, "$fileName.tmp") // Atomic write
        return try {
            tempFile.writer(Charsets.UTF_8).use { it.write(content) }
            tempFile.renameTo(File(DIR, fileName)) // Atomic commit
            true
        } catch (_: Exception) {
            tempFile.deleteSafely()
            false
        }
    }

    private fun loadFileSafely(fileName: String): String {
        return try {
            File(DIR, fileName).reader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun File.readTextSafely(): String {
        return try {
            reader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun File.deleteSafely() = try { delete() } catch (_: Exception) { false }
}