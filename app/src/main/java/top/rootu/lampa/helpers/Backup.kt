package top.rootu.lampa.helpers

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.helpers.Prefs.defPrefs
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.xml.parsers.DocumentBuilderFactory
import androidx.core.content.edit

object Backup {
    private val isOperationInProgress = AtomicBoolean(false)
    private const val MAX_BACKUPS = 3 // Except current
    private const val TAG = "Backup"

    val DIR: File by lazy { // Compatible directory selection for all APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                .resolve("LAMPA").apply { mkdirs() }
        } else {
            File(Environment.getExternalStorageDirectory(), "LAMPA").apply { mkdirs() }
        }
    }

    fun Context.backupSettings(which: String? = ""): Boolean {
        if (!PermHelpers.hasStoragePermissions(this)) {
            PermHelpers.verifyStoragePermissions(this)
            return false
        }

        if (!isOperationInProgress.compareAndSet(false, true)) {
            App.toast("Backup operation in progress")
            return false
        }
        // Delete any .tmp files from failed writes
        // DIR.listFiles { file -> file.name.endsWith(".tmp") }?.forEach { it.deleteSafely() }
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
            val content =
                loadFileSafely(if (which.isNullOrEmpty()) "prefs.backup" else "$which.backup")
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
            val minuteFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

            // Get ALL matching files (including active backup)
            val allBackups = DIR.listFiles { file ->
                file.name.startsWith(baseName) // Include active backup
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            // Separate active backup from archives
            val (activeBackup, _) = allBackups.partition { it.name == baseName }

            // Archive current backup if exists
            activeBackup.firstOrNull()?.let { current ->
                val timestamp = minuteFormat.format(Date(current.lastModified()))
                var uniqueName = "$baseName.$timestamp"
                var counter = 1
                while (File(DIR, uniqueName).exists()) {
                    uniqueName = "$baseName.$timestamp.$counter"
                    counter++
                }
                current.renameTo(File(DIR, uniqueName))
            }

            // Clean up - keep only MAX_BACKUPS total (active + archives)
            allBackups.drop(MAX_BACKUPS).forEach {
                it.deleteSafely()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Rotation failed", e)
        }
    }

    private fun Context.parseAndApplyPreferences(which: String?, xmlContent: String): Boolean {
        return try {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xmlContent)))

            val pref = if (which.isNullOrEmpty()) defPrefs
            else getSharedPreferences(which, Context.MODE_PRIVATE)

            pref.edit {
                var child = doc.documentElement.firstChild

                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        val element = child as Element
                        when (element.nodeName) {
                            "int" -> element.getAttribute("value").toIntOrNull()?.let {
                                putInt(element.getAttribute("name"), it)
                            }

                            "long" -> element.getAttribute("value").toLongOrNull()?.let {
                                putLong(element.getAttribute("name"), it)
                            }

                            "float" -> element.getAttribute("value").toFloatOrNull()?.let {
                                putFloat(element.getAttribute("name"), it)
                            }

                            "string" -> element.textContent.takeIf { it.isNotBlank() }?.let {
                                putString(element.getAttribute("name"), it)
                            }

                            "boolean" -> putBoolean(
                                element.getAttribute("name"),
                                element.getAttribute("value") == "true"
                            )

                            "set" -> {
                                val values = mutableListOf<String>().apply {
                                    var ch = element.firstChild
                                    while (ch != null) {
                                        if (ch.nodeType == Node.ELEMENT_NODE) {
                                            (ch as Element).textContent.takeIf { it.isNotBlank() }
                                                ?.let { add(it) }
                                        }
                                        ch = ch.nextSibling
                                    }
                                }.takeIf { it.isNotEmpty() }?.toSet()
                                values?.let { putStringSet(element.getAttribute("name"), it) }
                            }
                        }
                    }
                    child = child.nextSibling
                }
            }
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

    private fun File.deleteSafely() = try {
        delete()
    } catch (_: Exception) {
        false
    }

    fun countItemsInBackup(backupFile: File): Int {
        return try {
            val xmlFactory = DocumentBuilderFactory.newInstance()
            val builder = xmlFactory.newDocumentBuilder()
            val doc = builder.parse(backupFile)
            val prefs = doc.getElementsByTagName("map").item(0)?.childNodes
            var count = 0
            for (i in 0 until (prefs?.length ?: 0)) {
                val node = prefs?.item(i)
                if (node?.nodeType == Node.ELEMENT_NODE) {
                    count++
                }
            }
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backup", e)
            0
        }
    }

    fun validateStorageBackup(expected: Int): Boolean {
        return try {
            val backupFile = File(DIR, "${Prefs.STORAGE_PREFERENCES}.backup")
            if (!backupFile.exists()) return false
            // Validate and delete if corrupt
            val actual = countItemsInBackup(backupFile)
            if (actual != expected) {
                Log.e(TAG, "Validation failed: expected $expected, got $actual")
                if (BuildConfig.DEBUG) debugBackupContents(backupFile)
                if (backupFile.delete()) {
                    Log.w(TAG, "Deleted invalid backup file")
                    // Optional: Create empty backup to prevent crashes
                    // File(DIR, "${Prefs.STORAGE_PREFERENCES}.backup").createNewFile()
                } else {
                    Log.e(TAG, "Failed to delete invalid backup")
                }
                false
            } else true
        } catch (_: Exception) {
            false
        }
    }

    fun debugBackupContents(backupFile: File) {
        try {
            val content = backupFile.readText()
            Log.d(TAG, "File size: ${content.length} chars")
            Log.d(TAG, "First 500 chars:\n${content.take(500)}")
            // Count occurrences of <string>, <int> etc.
            val types = listOf("string", "int", "long", "boolean", "float", "set")
            types.forEach { type ->
                val count = content.split("<$type ").size - 1
                Log.d(TAG, "Found $count <$type> elements")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup", e)
        }
    }
}