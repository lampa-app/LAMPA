package top.rootu.lampa

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import java.util.concurrent.TimeUnit


/**
 * Manages playback history for multiple videos with:
 * - Max storage limit (100 items by default)
 * - Automatic expiration (30 days by default)
 * - Thread-safe operations
 */
object PlayHistoryManager {
    private const val PREFS_KEY = "video_playback_history"
    private const val MAX_ENTRIES = 100
    private val EXPIRATION_DAYS = TimeUnit.DAYS.toMillis(30)

    /**
     * Saves playback state for a video
     * @param videoUrl Unique video identifier
     * @param position Current playback position in ms
     * @param duration Total video duration in ms
     * @param ended Whether playback was completed
     */
    fun saveState(
        videoUrl: String,
        position: Int,
        duration: Int,
        ended: Boolean
    ) {
        val history = loadHistory()

        // Update or add entry
        history[videoUrl] = PlaybackState(
            position = position,
            duration = duration,
            ended = ended,
            lastUpdated = System.currentTimeMillis()
        )

        // Maintain size limit
        if (history.size > MAX_ENTRIES) {
            val oldestEntry = history.minByOrNull { it.value.lastUpdated }
            oldestEntry?.key?.let { history.remove(it) }
        }

        saveHistory(history)
    }

    /**
     * Gets playback state for a video
     * @return PlaybackState or null if not found/expired
     */
    fun getState(videoUrl: String): PlaybackState? {
        val state = loadHistory()[videoUrl]
        return state?.takeIf {
            System.currentTimeMillis() - it.lastUpdated < EXPIRATION_DAYS
        }
    }

    /**
     * Clears specific video's history
     */
    fun clearState(videoUrl: String) {
        val history = loadHistory().apply { remove(videoUrl) }
        saveHistory(history)
    }

    /**
     * Clears all playback history
     */
    fun clearAll() {
        getSharedPrefs().edit().remove(PREFS_KEY).apply()
    }

    // --- Internal Implementation ---

    data class PlaybackState(
        val position: Int,
        val duration: Int,
        val ended: Boolean,
        val lastUpdated: Long
    )

    private fun getSharedPrefs(): SharedPreferences {
        return App.context.lastPlayedPrefs
    }

    private fun loadHistory(): MutableMap<String, PlaybackState> {
        return try {
            val json = getSharedPrefs().getString(PREFS_KEY, "{}") ?: "{}"
            val type = object : TypeToken<Map<String, PlaybackState>>() {}.type
            Gson().fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e("PlaybackHistory", "Error loading history", e)
            mutableMapOf()
        }
    }

    private fun saveHistory(history: Map<String, PlaybackState>) {
        try {
            val json = Gson().toJson(history)
            getSharedPrefs().edit().putString(PREFS_KEY, json).apply()
        } catch (e: Exception) {
            Log.e("PlaybackHistory", "Error saving history", e)
        }
    }
}