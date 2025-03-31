package top.rootu.lampa

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import top.rootu.lampa.MainActivity.Companion.VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import top.rootu.lampa.models.LAMPA_CARD_KEY
import top.rootu.lampa.models.LampaCard
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.text.isNotEmpty

/**
 * Manages playback states for media items, including current position, playlist, and other metadata.
 * Handles both in-memory caching and persistent storage of playback states.
 */
class PlayerStateManager(context: Context) {
    private companion object {
        // const val PREFS_NAME = "player_state_prefs"
        const val MAX_CACHED_STATES = 5
        const val MAX_DAYS_CACHE = 30  // Days to store states
        const val TAG = "PlayerStateManager"
    }

    private val prefs =
        context.lastPlayedPrefs // getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stateCache = ConcurrentHashMap<String, PlaybackState>()

    /**
     * Data class representing the playback state of a media item.
     *
     * @property activityKey Unique identifier for the activity
     * @property playlist List of items in the current playlist
     * @property currentIndex Current position in the playlist
     * @property currentUrl Currently playing URL
     * @property currentPosition Current playback position in milliseconds
     * @property startIndex Initial playback position in playlist
     * @property lastUpdated Timestamp of last update
     * @property extras Additional metadata associated with the state
     */
    data class PlaybackState(
        val activityKey: String, // lampaActivity
        // val rawActivityJson: String? = null, // original JSON
        val playlist: List<PlaylistItem>,
        val currentIndex: Int, // in playlist
        val currentUrl: String? = null,
        val currentPosition: Long = 0,
        val startIndex: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis(),
        val extras: Map<String, Any> = emptyMap()
    ) {
        /**
         * Gets the current playlist item if available.
         */
        val currentItem: PlaylistItem? get() = playlist.getOrNull(currentIndex)

        /**
         * Determines if playback has ended based on timeline and position.
         */
        val isEnded: Boolean
            get() {
                val item = currentItem ?: return false
                val timeline = item.timeline ?: return false

                return when {
                    // Case 1: Explicitly marked as completed (100%)
                    timeline.percent >= 100 -> true
                    // Case 2: Reached near end of content (96% threshold)
                    timeline.duration > 0 && timeline.time >= timeline.duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE / 100 -> true
                    // Case 3: Position reset to start but marked complete
                    currentPosition <= 0 && timeline.percent >= VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE -> true
                    // Default case: Not ended
                    else -> false
                }
            }
    }

    /**
     * Data class representing an item in the playback playlist.
     *
     * @property url Media URL
     * @property title Display title
     * @property timeline Playback timeline information
     * @property quality Available quality variants
     * @property subtitles Available subtitle tracks
     */
    data class PlaylistItem(
        val url: String,
        val title: String? = null,
        val timeline: Timeline? = null,
        val quality: Map<String, String>? = null,
        val subtitles: List<Subtitle>? = null
    ) {
        /**
         * Data class representing playback timeline information.
         *
         * @property hash Unique identifier for the timeline
         * @property time Current playback position in seconds
         * @property duration Total duration in seconds
         * @property percent Completion percentage
         * @property profile Quality profile ID
         */
        data class Timeline(
            val hash: String,
            val time: Double,
            val duration: Double,
            val percent: Int,
            val profile: Int? = null
        )

        /**
         * Data class representing a subtitle track.
         *
         * @property url Subtitle file URL
         * @property label Display label
         * @property language Language code
         */
        data class Subtitle(
            val url: String,
            val label: String,
            val language: String? = null
        )
    }

    /**
     * Saves or updates playback state for the given activity.
     *
     * @param activityJson JSON string representing the activity
     * @param playlist List of playlist items
     * @param currentIndex Current position in playlist
     * @param currentUrl Currently playing URL
     * @param currentPosition Current playback position in milliseconds
     * @param startIndex Initial playback position in playlist
     * @param extras Additional metadata to store
     * @param card Associated LampaCard if available
     * @return The saved PlaybackState
     */
    fun saveState(
        activityJson: String,
        playlist: List<PlaylistItem>,
        currentIndex: Int = 0,
        currentUrl: String? = null,
        currentPosition: Long = 0,
        startIndex: Int = 0,
        extras: Map<String, Any> = emptyMap(),
        card: LampaCard? = null
    ): PlaybackState {
        val key = generateActivityKey(activityJson)
        val validatedStartIndex = startIndex.coerceIn(0, playlist.size - 1)
        val updatedExtras = extras.toMutableMap().apply {
            card?.let { put(LAMPA_CARD_KEY, Gson().toJson(it)) }
        }
        val state = PlaybackState(
            activityKey = key,
            // rawActivityJson = activityJson, // Store original JSON
            playlist = playlist,
            currentIndex = currentIndex,
            currentUrl = currentUrl ?: playlist.getOrNull(currentIndex)?.url,
            currentPosition = currentPosition,
            startIndex = validatedStartIndex,
            extras = updatedExtras
        )

        synchronized(this) {
            stateCache[key] = state
            pruneCache()
            persistState(state)
        }

        return state
    }

    /**
     * Gets the current playback state for an activity.
     *
     * @param activityJson JSON string representing the activity
     * @return The PlaybackState, creating a default one if none exists
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
     * Finds a playback state by matching LampaCard.
     *
     * @param card The LampaCard to search for
     * @return Most recent matching PlaybackState or null if not found
     */
    fun findStateByCard(card: LampaCard): PlaybackState? {
        fun matchesCard(state: PlaybackState): Boolean {
            return (state.extras[LAMPA_CARD_KEY] as? String)?.let { json ->
                getJson(json, LampaCard::class.java)?.let { storedCard ->
                    storedCard.id == card.id ||
                            (storedCard.title == card.title && storedCard.release_year == card.release_year)
                }
            } == true
        }

        return (stateCache.values.filter(::matchesCard) +
                prefs.all.keys
                    .filter { it.startsWith("state_") }
                    .mapNotNull { loadPersistedState(it.removePrefix("state_")) }
                    .filter(::matchesCard))
            .maxByOrNull { it.lastUpdated }
    }

    /**
     * Updates just the playback position for the current state.
     *
     * @param activityJson JSON string representing the activity
     * @param position New playback position in milliseconds
     * @return Updated PlaybackState or null if update failed
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
            startIndex = currentState.startIndex,
            extras = currentState.extras
        )
    }

    /**
     * Clears the playback state for an activity.
     *
     * @param activityJson JSON string representing the activity to clear
     */
    fun clearState(activityJson: String) {
        val key = generateActivityKey(activityJson)
        synchronized(this) {
            stateCache.remove(key)
            prefs.edit().remove("state_$key").apply()
        }
    }

    /**
     * Clears all playback states from both memory and persistent storage.
     */
    fun clearAll() {
        synchronized(this) {
            stateCache.clear()
            prefs.edit().clear().apply()
        }
    }

    /**
     * Converts a JSONArray to a list of PlaylistItems.
     *
     * @param jsonArray JSON array containing playlist items
     * @return List of PlaylistItem objects
     */
    fun convertJsonToPlaylist(jsonArray: JSONArray): List<PlaylistItem> {
        return jsonArray.toPlaylist() // Uses internal extension
    }

    /**
     * Converts a single JSONObject to a PlaylistItem.
     *
     * @param json JSON object representing a playlist item
     * @return PlaylistItem object
     */
    fun convertJsonToPlaylistItem(json: JSONObject): PlaylistItem {
        return json.toPlaylistItem() // Uses internal extension
    }

    /**
     * Converts a JSONObject to a Timeline object.
     *
     * @param json JSON object representing timeline data
     * @return Timeline object
     */
    fun convertJsonToTimeline(json: JSONObject): PlaylistItem.Timeline {
        return json.toTimeline() // Uses internal extension
    }

    /**
     * Converts a JSONObject to a quality map.
     *
     * @param json JSON object representing quality variants
     * @return Map of quality names to URLs
     */
    fun convertJsonToQualityMap(json: JSONObject): Map<String, String> {
        return json.toQualityMap() // Uses internal extension
    }

    /**
     * Converts a Timeline object to a JSON string.
     *
     * @param timeline Timeline object to convert
     * @return JSON string representation
     */
    fun convertTimelineToJsonString(timeline: PlaylistItem.Timeline): String {
        return timeline.toJson().toString() // Uses internal extension
    }

    /**
     * Converts a PlaybackState to a JSONObject.
     *
     * @param state PlaybackState to convert
     * @return JSONObject representation
     */
    fun getStateJson(state: PlaybackState): JSONObject {
        return JSONObject().apply {
            put("activity_key", state.activityKey)
            put("playlist", JSONArray().apply {
                state.playlist.forEach { item ->
                    put(JSONObject().apply {
                        put("url", item.url)
                        item.title?.let { put("title", it) }
                        item.timeline?.let { timeline ->
                            put("timeline", JSONObject().apply {
                                put("hash", timeline.hash)
                                put("time", timeline.time)
                                put("duration", timeline.duration)
                                put("percent", timeline.percent)
                                timeline.profile?.let { put("profile", it) }
                            })
                        }
                        item.quality?.let { quality ->
                            put("quality", JSONObject(quality))
                        }
                        item.subtitles?.let { subs ->
                            put("subtitles", JSONArray().apply {
                                subs.forEach { sub ->
                                    put(JSONObject().apply {
                                        put("url", sub.url)
                                        put("label", sub.label)
                                        sub.language?.let { put("language", it) }
                                    })
                                }
                            })
                        }
                    })
                }
            })
            put("current_index", state.currentIndex)
            put("url", state.currentUrl)
            put("position", state.currentPosition)
            put("start_index", state.startIndex)
            put("last_updated", state.lastUpdated)
            put("extras", JSONObject(state.extras))
        }
    }

    /**
     * Finds all states that match the given activity JSON.
     *
     * @param activityJson JSON string to match against
     * @return List of matching PlaybackStates, sorted by most recent first
     */
    fun findMatchingStates(activityJson: String): List<PlaybackState> {
        val targetKey = generateActivityKey(activityJson)
        val normalizedInput = normalizeJson(activityJson)

        return stateCache.values.filter { state ->
            // Match by either:
            // Exact key match
            state.activityKey == targetKey ||
                    // Normalized JSON match
                    // (state.rawActivityJson?.let { normalizeJson(it) == normalizedInput } == true) ||
                    // Partial content match (fallback)
                    (state.extras["lampaActivity"] as? String)?.let { normalizeJson(it) == normalizedInput } == true
        }.ifEmpty {
            // Check persisted states if cache has no matches
            prefs.all.keys
                .filter { it.startsWith("state_") }
                .mapNotNull { key ->
                    try {
                        loadPersistedState(key.removePrefix("state_"))?.takeIf {
                            it.activityKey == targetKey // ||
                            // it.rawActivityJson?.let { normalizeJson(it) == normalizedInput } == true
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
        }.sortedByDescending { it.lastUpdated } // Most recent first
    }

    /**
     * Logs debug information about key matching for the given activity JSON.
     *
     * @param activityJson JSON string to debug
     */
    fun debugKeyMatching(activityJson: String) {
        val targetKey = generateActivityKey(activityJson)
        val normalizedJson = normalizeJson(activityJson)

        Log.d(TAG, "===== KEY MATCHING DEBUG =====")
        Log.d(TAG, "Input JSON (${activityJson.length} chars):")
        Log.d(TAG, activityJson.take(200) + if (activityJson.length > 200) "..." else "")
        Log.d(TAG, "Generated Key: $targetKey")
        Log.d(TAG, "Normalized JSON: ${normalizedJson.take(200)}...")

        Log.d(TAG, "Potential matches in cache:")
        stateCache.values.forEach { state ->
            val matchQuality = when {
                state.activityKey == targetKey -> "EXACT_KEY"
                // normalizeJson(state.rawActivityJson ?: "") == normalizedJson -> "EXACT_JSON"
                (state.extras["lampaActivity"] as? String)?.let { normalizeJson(it) == normalizedJson } == true -> "EXTRA_JSON"

                else -> "NO_MATCH"
            }

            Log.d(TAG, "• Key: ${state.activityKey} ($matchQuality)")
            // Log.d(TAG, "  Raw JSON: ${state.rawActivityJson?.take(50)}...")
            Log.d(TAG, "  Extras JSON: ${(state.extras["lampaActivity"] as? String)?.take(50)}...")
        }
        Log.d(TAG, "===== END DEBUG =====")
    }

    /**
     * Logs detailed information about all cached and persisted states.
     */
    fun debugLogAllStates() {
        Log.d(TAG, "===== DEBUGGING PLAYER STATES =====")

        // Log cached states
        Log.d(TAG, "CACHED STATES (${stateCache.size}):")
        stateCache.values.forEach { state ->
            Log.d(TAG, "• State Key: ${state.activityKey}")
            Log.d(TAG, "  - Current Index: ${state.currentIndex}")
            Log.d(TAG, "  - Current Position: ${state.currentPosition}")
            Log.d(TAG, "  - Last Updated: ${state.lastUpdated}")

            // Log extras
            Log.d(TAG, "  - Extras:")
            state.extras.forEach { (key, value) ->
                Log.d(TAG, "    - $key: $value")
            }

            // Log playlist
            Log.d(TAG, "  - Playlist (${state.playlist.size} items):")
            state.playlist.forEachIndexed { index, item ->
                Log.d(TAG, "    ${index + 1}. ${item.title ?: "Untitled"}")
                Log.d(TAG, "      - URL: ${item.url}")
                Log.d(TAG, "      - Timeline: ${item.timeline?.hash ?: "No timeline"}")
                Log.d(
                    TAG,
                    "      - Quality Variants: ${item.quality?.keys?.joinToString() ?: "None"}"
                )
                Log.d(TAG, "      - Subtitles: ${item.subtitles?.size ?: 0} available")
            }
        }

        // Log persisted states
        Log.d(TAG, "PERSISTED STATES:")
        prefs.all.keys
            .filter { it.startsWith("state_") }
            .forEach { key ->
                Log.d(TAG, "• $key")
                try {
                    prefs.getString(key, null)?.let {
                        Log.d(TAG, "  ${it.take(100)}...") // First 100 chars
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading pref $key", e)
                }
            }

        Log.d(TAG, "===== END DEBUG OUTPUT =====")
    }

    /**
     * Automatically purges persisted playback states older than [MAX_DAYS_CACHE] days.
     */
    fun purgeOldStates() {
        val cutoffTime =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_DAYS_CACHE.toLong())
        val editor = prefs.edit()
        var purgedCount = 0

        prefs.all.keys
            .filter { it.startsWith("state_") }
            .forEach { key ->
                try {
                    val jsonString = prefs.getString(key, null) ?: return@forEach
                    val lastUpdated = JSONObject(jsonString).optLong("last_updated", 0)

                    if (lastUpdated < cutoffTime) {
                        editor.remove(key)
                        purgedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse state for purging: $key", e)
                }
            }

        editor.apply()
        if (purgedCount > 0) {
            Log.d(TAG, "Purged $purgedCount stale states (older than $MAX_DAYS_CACHE days)")
        }
    }

    // Private helper methods

    /**
     * Generates a unique key for an activity based on its JSON representation.
     *
     * @param activityJson JSON string representing the activity
     * @return SHA-256 hash of the normalized activity key components
     */
    private fun generateActivityKey(activityJson: String): String {
        return try {
            JSONObject(activityJson).let { json ->
                buildString {
                    // Always include these core fields if they exist
                    json.optString("id").takeIf { it.isNotEmpty() }?.let { append("id:$it") }
                    json.optString("component").takeIf { it.isNotEmpty() }
                        ?.let { append("|comp:$it") }
                    json.optString("source").takeIf { it.isNotEmpty() }?.let { append("|src:$it") }
                    // Include additional important fields
                    json.optString("search").takeIf { it.isNotEmpty() }
                        ?.let { append("|search:$it") }
                    json.optString("url").takeIf { it.isNotEmpty() }?.let { append("|url:$it") }
                    json.optString("title").takeIf { it.isNotEmpty() }?.let { append("|title:$it") }
                    if (isEmpty()) append("default") // fallback
                }.sha256Hash() // Use hash instead of hashCode()
            }
        } catch (_: Exception) {
            "invalid_${activityJson.sha256Hash()}"
        }
    }

    /**
     * Computes SHA-256 hash of a string.
     *
     * @return Hexadecimal string representation of the hash
     */
    private fun String.sha256Hash(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates a default playback state for a given key.
     *
     * @param key Activity key
     * @return Default PlaybackState with empty playlist
     */
    private fun createDefaultState(key: String): PlaybackState {
        return PlaybackState(
            activityKey = key,
            playlist = emptyList(),
            currentIndex = 0,
            startIndex = 0
        )
    }

    /**
     * Persists a playback state to shared preferences.
     *
     * @param state PlaybackState to save
     */
    private fun persistState(state: PlaybackState) {
        try {
            prefs.edit().putString("state_${state.activityKey}", state.toJson().toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist state", e)
        }
    }

    /**
     * Loads a persisted playback state from shared preferences.
     *
     * @param key Activity key to load
     * @return PlaybackState or null if not found or error occurred
     */
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

    /**
     * Prunes the state cache to maintain the maximum size limit.
     */
    private fun pruneCache() {
        if (stateCache.size > MAX_CACHED_STATES) {
            val oldest = stateCache.values.sortedBy { it.lastUpdated }
                .take(stateCache.size - MAX_CACHED_STATES)
            oldest.forEach { stateCache.remove(it.activityKey) }
        }
    }

    // JSON conversion extensions

    /**
     * Converts a PlaybackState to a JSONObject.
     *
     * @return JSONObject representation of the state
     */
    private fun PlaybackState.toJson(): JSONObject {
        return JSONObject().apply {
            put("activity_key", activityKey)
            put("playlist", JSONArray().apply {
                playlist.forEach { put(it.toJson()) }
            })
            put("current_index", currentIndex)
            put("url", currentUrl) // was "current_url"
            put("position", currentPosition) // was "current_position"
            put("start_index", startIndex)
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

    /**
     * Converts a JSONObject to a PlaybackState.
     *
     * @return PlaybackState object
     */
    private fun JSONObject.toPlaybackState(): PlaybackState {
        return PlaybackState(
            activityKey = getString("activity_key"),
            playlist = getJSONArray("playlist").toPlaylist(),
            currentIndex = getInt("current_index"),
            currentUrl = optString("url").takeIf { it.isNotEmpty() }, // was "current_url"
            currentPosition = getLong("position"), // was "current_position"
            startIndex = optInt("start_index"),
            lastUpdated = getLong("last_updated"),
            extras = optJSONObject("extras")?.toMap() ?: emptyMap()
        )
    }

    /**
     * Converts a JSONObject to a Map.
     *
     * @return Map containing all key-value pairs from the JSONObject
     */
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

    /**
     * Converts a JSONArray to a list of PlaylistItems.
     *
     * @return List of PlaylistItem objects
     */
    private fun JSONArray.toPlaylist(): List<PlaylistItem> {
        return List(length()) { i ->
            getJSONObject(i).toPlaylistItem()
        }
    }

    /**
     * Converts a JSONObject to a PlaylistItem.
     *
     * @return PlaylistItem object
     * @throws IllegalArgumentException if required fields are missing
     */
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

    /**
     * Converts a JSONObject to a Timeline.
     *
     * @return Timeline object
     */
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
            } catch (_: JSONException) {
                null  // Fallback if profile exists but isn't an Int
            }
        )
    }

    /**
     * Converts a JSONObject to a quality map.
     *
     * @return Map of quality names to URLs
     */
    private fun JSONObject.toQualityMap(): Map<String, String> {
        return keys().asSequence().associateWith { getString(it) }
    }

    /**
     * Converts a JSONArray to a list of Subtitles.
     *
     * @return List of Subtitle objects
     */
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

    /**
     * Converts a PlaylistItem to a JSONObject.
     *
     * @return JSONObject representation of the item
     */
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

    /**
     * Converts a Timeline to a JSONObject.
     *
     * @return JSONObject representation of the timeline
     */
    private fun PlaylistItem.Timeline.toJson(): JSONObject {
        return JSONObject().apply {
            put("hash", hash)
            put("time", time)
            put("duration", duration)
            put("percent", percent)
            profile?.let { put("profile", profile) }
        }
    }

    /**
     * Converts a Subtitle to a JSONObject.
     *
     * @return JSONObject representation of the subtitle
     */
    private fun PlaylistItem.Subtitle.toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("label", label)
            language?.let { put("language", it) }
        }
    }

    /**
     * Normalizes a JSON string by sorting keys and removing whitespace.
     *
     * @param json JSON string to normalize
     * @return Normalized JSON string
     */
    private fun normalizeJson(json: String): String {
        return try {
            val obj = JSONObject(json)
            val keys = mutableListOf<String>()
            val iterator = obj.keys()
            while (iterator.hasNext()) {
                keys.add(iterator.next())
            }

            val sorted = JSONObject()
            keys.sorted().forEach { key ->
                sorted.put(key, obj.get(key))
            }
            sorted.toString()
        } catch (_: Exception) {
            // Fallback: simple whitespace removal
            json.replace("\\s".toRegex(), "")
        }
    }
}
