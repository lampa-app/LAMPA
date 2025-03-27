package top.rootu.lampa

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import top.rootu.lampa.MainActivity.Companion.VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.isNotEmpty

class PlayerStateManager(context: Context) {
    private companion object {
        // const val PREFS_NAME = "player_state_prefs"
        const val MAX_CACHED_STATES = 5
        const val TAG = "PlayerStateManager"
    }

    private val prefs = App.context.lastPlayedPrefs // getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateCache = ConcurrentHashMap<String, PlaybackState>()

    data class PlaybackState(
        val activityKey: String, // lampaActivity
        val playlist: List<PlaylistItem>,
        val currentIndex: Int, // in playlist
        val currentUrl: String? = null,
        val currentPosition: Long = 0,
        val lastUpdated: Long = System.currentTimeMillis(),
        val extras: Map<String, Any> = emptyMap()
    ) {
        val currentItem: PlaylistItem? get() = playlist.getOrNull(currentIndex)
        val isEnded: Boolean
            get() {
                val item = currentItem ?: return false
                val timeline = item.timeline ?: return false

                return when {
                    // Case 1: Explicitly marked as completed (100%)
                    timeline.percent >= 100 -> true
                    // Case 2: Reached near end of content (96% threshold)
                    timeline.duration > 0 && timeline.time >= timeline.duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE -> true
                    // Case 3: Position reset to start but marked complete
                    currentPosition <= 0 && timeline.percent >= VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE * 100 -> true
                    // Default case: Not ended
                    else -> false
                }
            }
    }

    data class PlaylistItem(
        val url: String,
        val title: String? = null,
        val timeline: Timeline? = null,
        val quality: Map<String, String>? = null,
        val subtitles: List<Subtitle>? = null
    ) {
        data class Timeline(
            val hash: String,
            val time: Double,
            val duration: Double,
            val percent: Int,
            val profile: Int? = null
        )

        data class Subtitle(
            val url: String,
            val label: String,
            val language: String? = null
        )
    }

    /**
     * Saves or updates playback state for the given activity
     */
    fun saveState(
        activityJson: String,
        playlist: List<PlaylistItem>,
        currentIndex: Int = 0,
        currentUrl: String? = null,
        currentPosition: Long = 0,
        extras: Map<String, Any> = emptyMap()
    ): PlaybackState {
        val key = generateActivityKey(activityJson)
        val state = PlaybackState(
            activityKey = key,
            playlist = playlist,
            currentIndex = currentIndex,
            currentUrl = currentUrl ?: playlist.getOrNull(currentIndex)?.url,
            currentPosition = currentPosition,
            extras = extras
        )

        synchronized(this) {
            stateCache[key] = state
            pruneCache()
            persistState(state)
        }

        return state
    }

    /**
     * Gets the current playback state for an activity
     */
    fun getState(activityJson: String): PlaybackState {
        val key = generateActivityKey(activityJson)

        return stateCache[key] ?: run {
            val loadedState = loadPersistedState(key) ?: createDefaultState(key)
            stateCache[key] = loadedState
            loadedState
        }
    }

    /**
     * Updates just the playback position for the current state
     */
    fun updatePosition(activityJson: String, position: Long): PlaybackState? {
        val currentState = getState(activityJson)
        if (currentState.playlist.isEmpty()) return null

        return saveState(
            activityJson = activityJson,
            playlist = currentState.playlist,
            currentIndex = currentState.currentIndex,
            currentUrl = currentState.currentUrl,
            currentPosition = position,
            extras = currentState.extras
        )
    }

    /**
     * Clears the playback state for an activity
     */
    fun clearState(activityJson: String) {
        val key = generateActivityKey(activityJson)
        synchronized(this) {
            stateCache.remove(key)
            prefs.edit().remove("state_$key").apply()
        }
    }

    /**
     * Clears all playback states
     */
    fun clearAll() {
        synchronized(this) {
            stateCache.clear()
            prefs.edit().clear().apply()
        }
    }

    /**
     * Public method to convert JSONArray to Playlist
     */
    fun convertJsonToPlaylist(jsonArray: JSONArray): List<PlaylistItem> {
        return jsonArray.toPlaylist() // Uses internal extension
    }

    /**
     * Public method to convert single JSONObject to Timeline
     */
    fun convertJsonToTimeline(json: JSONObject): PlaylistItem.Timeline {
        return json.toTimeline() // Uses internal extension
    }

    /**
     * Public method to convert single JSONObject to QualityMap
     */
    fun convertJsonToQualityMap(json: JSONObject): Map<String, String> {
        return json.toQualityMap() // Uses internal extension
    }

    /**
     * Public method to convert single JSONObject to PlaylistItem
     */
    fun convertJsonToPlaylistItem(json: JSONObject): PlaylistItem {
        return json.toPlaylistItem() // Uses internal extension
    }

    /**
     * Public method to convert single PlaylistItem.Timeline to JSON String
     */
    fun convertTimelineToJsonString(timeline: PlaylistItem.Timeline): String {
        return timeline.toJson().toString() // Uses internal extension
    }

    // Private helper methods

    private fun generateActivityKey(activityJson: String): String {
        return try {
            JSONObject(activityJson).let { json ->
                buildString {
                    when {
                        json.has("id") -> append("id_${json.getString("id")}")
                        json.has("component") -> append("comp_${json.getString("component")}")
                        else -> append("default")
                    }
                    if (json.has("source")) {
                        append("_src_${json.getString("source")}")
                    }
                }.hashCode().toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate activity key", e)
            "invalid_${activityJson.hashCode()}"
        }
    }

    private fun createDefaultState(key: String): PlaybackState {
        return PlaybackState(
            activityKey = key,
            playlist = emptyList(),
            currentIndex = 0
        )
    }

    private fun persistState(state: PlaybackState) {
        try {
            prefs.edit().putString("state_${state.activityKey}", state.toJson().toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist state", e)
        }
    }

    private fun loadPersistedState(key: String): PlaybackState? {
        return try {
            prefs.getString("state_$key", null)?.let { jsonString ->
                JSONObject(jsonString).toPlaybackState()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted state", e)
            null
        }
    }

    private fun pruneCache() {
        if (stateCache.size > MAX_CACHED_STATES) {
            val oldest = stateCache.values.sortedBy { it.lastUpdated }
                .take(stateCache.size - MAX_CACHED_STATES)
            oldest.forEach { stateCache.remove(it.activityKey) }
        }
    }

    // JSON conversion extensions

    private fun PlaybackState.toJson(): JSONObject {
        return JSONObject().apply {
            put("activity_key", activityKey)
            put("playlist", JSONArray().apply {
                playlist.forEach { put(it.toJson()) }
            })
            put("current_index", currentIndex)
            put("current_url", currentUrl)
            put("current_position", currentPosition)
            put("last_updated", lastUpdated)
            put("extras", JSONObject().apply {
                extras.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                        is Double -> put(key, value)
                        is Boolean -> put(key, value)
                    }
                }
            })
        }
    }

    private fun JSONObject.toPlaybackState(): PlaybackState {
        return PlaybackState(
            activityKey = getString("activity_key"),
            playlist = getJSONArray("playlist").toPlaylist(),
            currentIndex = getInt("current_index"),
            currentUrl = optString("current_url").takeIf { it.isNotEmpty() },
            currentPosition = getLong("current_position"),
            lastUpdated = getLong("last_updated"),
            extras = optJSONObject("extras")?.toMap() ?: emptyMap()
        )
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        keys().forEach { key ->
            map[key] = when (val value = get(key)) {
                is String -> value
                is Int -> value
                is Long -> value
                is Double -> value
                is Boolean -> value
                else -> value.toString()
            }
        }
        return map
    }

    private fun JSONArray.toPlaylist(): List<PlaylistItem> {
        return List(length()) { i ->
            getJSONObject(i).toPlaylistItem()
        }
    }

    private fun JSONObject.toPlaylistItem(): PlaylistItem {
        return PlaylistItem(
            url = try { // Required field
                getString("url").takeIf { it.isNotBlank() }
                    ?: throw JSONException("Missing or empty URL")
            } catch (e: JSONException) {
                throw IllegalArgumentException("Invalid playlist item: URL required", e)
            },
            title = optString("title").takeIf { it.isNotEmpty() },
            timeline = optJSONObject("timeline")?.toTimeline(),
            quality = optJSONObject("quality")?.toQualityMap(),
            subtitles = optJSONArray("subtitles")?.toSubtitles()
        )
    }

    private fun JSONObject.toTimeline(): PlaylistItem.Timeline {
        return PlaylistItem.Timeline(
            hash = optString("hash", "0"),  // Safer string access with default
            time = getDouble("time").coerceAtLeast(0.0),  // Ensure non-negative
            duration = getDouble("duration").coerceAtLeast(0.0),  // Ensure non-negative
            percent = getInt("percent").coerceIn(0, 100),  // Clamp to valid percentage
            profile = try {
                // More robust profile handling
                when {
                    has("profile") -> getInt("profile").takeIf { it > 0 }
                    else -> null
                }
            } catch (e: JSONException) {
                null  // Fallback if profile exists but isn't an Int
            }
        )
    }

    private fun JSONObject.toQualityMap(): Map<String, String> {
//        val map = mutableMapOf<String, String>()
//        keys().forEach { key -> map[key] = getString(key) }
//        return map
        return keys().asSequence().associateWith { getString(it) }
    }

    private fun JSONArray.toSubtitles(): List<PlaylistItem.Subtitle> {
        return List(length()) { i ->
            getJSONObject(i).let { sub ->
                PlaylistItem.Subtitle(
                    url = sub.getString("url"),
                    label = sub.getString("label"),
                    language = sub.optString("language").takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    private fun PlaylistItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            title?.let { put("title", it) }
            timeline?.let { put("timeline", it.toJson()) }
            quality?.let { qual ->
                put("quality", JSONObject().apply {
                    qual.forEach { (key, value) -> put(key, value) }
                })
            }
            subtitles?.let { subs ->
                put("subtitles", JSONArray().apply {
                    subs.forEach { put(it.toJson()) }
                })
            }
        }
    }

    private fun PlaylistItem.Timeline.toJson(): JSONObject {
        return JSONObject().apply {
            put("hash", hash)
            put("time", time)
            put("duration", duration)
            put("percent", percent)
            profile?.let { put ("profile", profile) }
        }
    }

    private fun PlaylistItem.Subtitle.toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("label", label)
            language?.let { put("language", it) }
        }
    }
}