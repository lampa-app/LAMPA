package top.rootu.lampa

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import top.rootu.lampa.MainActivity.Companion.VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
import top.rootu.lampa.MainActivity.Companion.keepPlayerConnection
import top.rootu.lampa.MainActivity.Companion.playerTimeCode
import top.rootu.lampa.helpers.Helpers.encodeUrlIfNeeded
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.models.LAMPA_CARD_KEY
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.receivers.PlayerReportReceiver
import top.rootu.lampa.tmdb.TMDB
import java.util.Locale

/**
 * Just+ Player's nested playlist API: the launch intent and the report the player sends back.
 * https://github.com/just-plus-player/just-plus-player/blob/master/docs/PLAYLIST_API.md
 */
object JustPlusPlayer {
    // Players on the Just+ nested playlist contract; beta builds add a suffix to the package
    private val PLAYER_PREFIXES = listOf("com.justplus.player", "com.lampaua.player", "com.syntex.player")

    fun matches(name: String) = PLAYER_PREFIXES.any { name.startsWith(it) }

    // Report callback extras: the launch's activity and card
    private const val REPORT_ACTIVITY_EXTRA = "lampa_activity"
    private const val REPORT_CARD_EXTRA = "lampa_card"
    // The window of the playlist the player got: its first item, its size and the whole playlist's size
    private const val REPORT_OFFSET_EXTRA = "lampa_offset"
    private const val REPORT_COUNT_EXTRA = "lampa_count"
    private const val REPORT_TOTAL_EXTRA = "lampa_total"
    // Bytes of items one launch may carry: the intent reaches the player in a one-way Binder call, which
    // gets half of the process's 1 MB buffer; past that the system kills the player before it starts (a
    // long anime with every episode's links). A launch of 517 KB failed on Android 16 and one of 512 KB
    // passed; the rest of the launch adds about 8 KB, and the half is shared with whatever else is in flight
    private const val ITEMS_BUDGET = 400_000

    /** com.justplus.player.result as JSON: everything handleJustPlusReport needs, nothing else. */
    fun report(intent: Intent): JSONObject {
        val window = intent.getIntArrayExtra("positions_sec") ?: IntArray(0)
        // Back from the window the player got to the whole playlist: -1 = never opened. A report of
        // another window (an old session through a callback a newer launch has updated) stays as it
        // came, so handleJustPlusReport drops it by its size
        val ours = window.size == intent.getIntExtra(REPORT_COUNT_EXTRA, window.size)
        val offset = if (ours) intent.getIntExtra(REPORT_OFFSET_EXTRA, 0) else 0
        val total = if (ours) intent.getIntExtra(REPORT_TOTAL_EXTRA, window.size) else window.size
        val positions = IntArray(total) { window.getOrElse(it - offset) { -1 } }
        // Durations come per visit in history; the last known one of each item wins.
        val durations = IntArray(positions.size) { -1 }
        @Suppress("DEPRECATION")
        intent.getParcelableArrayExtra("history")?.forEach { visit ->
            val v = visit as? Bundle ?: return@forEach
            val i = v.getInt("index", -1).let { if (it < 0) it else it + offset }
            val d = v.getInt("duration_sec", -1)
            if (i in durations.indices && d > 0) durations[i] = d
        }
        val index = intent.getIntExtra("index", -1).let { if (it < 0) it else it + offset }
        val duration = intent.getIntExtra("duration_sec", -1)
        if (index in durations.indices && duration > 0) durations[index] = duration
        return JSONObject().apply {
            put("key", intent.getStringExtra(REPORT_ACTIVITY_EXTRA) ?: MainActivity.lampaActivity)
            put("card", intent.getStringExtra(REPORT_CARD_EXTRA))
            put("uri", intent.getStringExtra("uri") ?: intent.dataString)
            put("index", index)
            put("end_by", intent.getStringExtra("end_by"))
            put("error", intent.getStringExtra("error_message"))
            put("positions", JSONArray(positions.toList()))
            put("durations", JSONArray(durations.toList()))
        }
    }

    // Number of scan lines a quality label denotes (e.g. "1080p" -> 1080, "4K"/"UHD" -> 2160),
    // used to sort quality variants from highest to lowest before they are sent to the player.
    private fun qualityLines(label: String?): Int {
        if (label == null) return 0
        val normalized = label.lowercase(Locale.getDefault())
        if (normalized.contains("4k") || normalized.contains("uhd")) return 2160
        return label.filter { it.isDigit() }.toIntOrNull() ?: 0
    }

    // The nested `playlist` contract: one Bundle with an item Bundle per episode.
    // The player answers through the result_callback (see MainActivity.handleJustPlusReport).
    fun configureIntent(
        context: Context,
        intent: Intent,
        playerPackage: String,
        state: PlayerStateManager.PlaybackState,
        videoTitle: String,
        position: Long,
        headers: Array<String>?,
        activity: String
    ) {
        val card = (state.extras[LAMPA_CARD_KEY] as? String)
            ?.let { getJson(it, LampaCard::class.java) }
        val cardImdbId: String? = card?.imdb_id?.takeIf { it.isNotEmpty() }
        val cardId = card?.tmdb_id?.takeIf { it.isNotEmpty() }
            ?: card?.id?.takeIf { it.isNotEmpty() }
        // A series: the root title is the show, each item's own name goes under it as episode_title
        val isSeries = card?.type == "tv" ||
                state.playlist.any { it.season != null || it.episode != null }
        // The root title is the card's name (a show's is `name`, a film's `title`), else what LAMPA sent
        val cardTitle = (if (isSeries) listOf(card?.name, card?.title) else listOf(card?.title, card?.name))
            .firstOrNull { !it.isNullOrEmpty() }
        val title = cardTitle ?: videoTitle
        // The widest logo in the app language, else in English, else none
        val logos = card?.images?.logos.orEmpty().filter { !it.file_path.isNullOrEmpty() }
        val logo = (logos.filter { it.iso_639_1 == context.appLang.substringBefore('-') }.takeIf { it.isNotEmpty() }
            ?: logos.filter { it.iso_639_1 == "en" })
            .maxByOrNull { it.aspect_ratio }
            ?.let { TMDB.imageUrl(it.file_path).replace("original", "w500") }
        // The best-voted backdrop without lettering, else the card's own backdrop
        val backdrop = card?.images?.backdrops.orEmpty()
            .filter { !it.file_path.isNullOrEmpty() && it.iso_639_1.isNullOrEmpty() }
            .maxByOrNull { it.vote_average }?.file_path
        val background = (backdrop ?: card?.backdrop_path)?.takeIf { it.isNotEmpty() }
            ?.let { TMDB.imageUrl(it).replace("original", "w1280") }
            ?: card?.background_image?.takeIf { it.isNotEmpty() }

        val items = state.playlist.mapIndexed { index, item ->
            Bundle().apply {
                // Encoded as the launch's data is (createBaseIntent): spaces, Cyrillic, brackets
                putString("uri", encodeUrlIfNeeded(item.url))
                item.title?.takeIf { it.isNotEmpty() && (isSeries || it != videoTitle) }
                    ?.let { putString("episode_title", it) }
                item.thumbnail?.takeIf { it.isNotEmpty() }?.let { putString("thumbnail", it) }
                (item.imdbId ?: cardImdbId)?.let { putString("imdb_id", it) }
                cardId?.let { putString("tmdb_id", it) }
                item.season?.let { putInt("season", it) }
                item.episode?.let { putInt("episode", it) }
                item.segments?.takeIf { it.isNotEmpty() }?.let { putString("segments", it) }
                // The start item from the resolved position; the others from their own timeline, so a
                // jump from the player's playlist resumes a half-watched episode (a finished one starts over)
                // "again": explicit 0, else the player resumes from its own saved position
                if (index == state.currentIndex) {
                    if (playerTimeCode == "again")
                        putInt("position_sec", 0)
                    else if (position > 0)
                        putInt("position_sec", (position / 1000).toInt())
                } else if (playerTimeCode != "again") {
                    item.timeline?.takeIf {
                        it.time > 0 && it.percent < VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE
                    }?.let { putInt("position_sec", it.time.toInt()) }
                }
                item.quality?.takeIf { it.isNotEmpty() }?.let { q ->
                    putParcelableArray("qualities", q.keys.sortedByDescending { qualityLines(it) }
                        .map { label ->
                            Bundle().apply {
                                putString("label", label)
                                putString("uri", encodeUrlIfNeeded(q.getValue(label)))
                            }
                        }.toTypedArray())
                }
                item.subtitles?.takeIf { it.isNotEmpty() }?.let { subs ->
                    putParcelableArray("subtitles", subs.map { sub ->
                        Bundle().apply {
                            putString("uri", encodeUrlIfNeeded(sub.url))
                            putString("label", sub.label.ifEmpty { "Sub" })
                            sub.language?.let { putString("language", it) }
                        }
                    }.toTypedArray())
                }
            }
        }

        val start = state.currentIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        val window = window(items, start)

        intent.apply {
            setPackage(playerPackage)
            putExtra("playlist", Bundle().apply {
                putString("title", title)
                logo?.let { putString("logo", it) }
                background?.let { putString("background", it) }
                putInt("start_index", start - window.first)
                headers?.let { putStringArray("headers", it) }
                // Lampa's "ask": the player asks Resume / Start over, only when there is something to resume
                putBoolean("ask_resume", playerTimeCode == "ask" && position > 0)
                putParcelableArray("items", items.slice(window).toTypedArray())
                // A report every 2 minutes while playing, the cadence of Lampa's own player: the one on
                // leaving can come too late to be kept (a task swiped from recents dies a second after it)
                if (keepPlayerConnection) putInt("report_interval_sec", 120)
                // Reports on every leave (Home included). Mutable, or the player's extras are dropped:
                // the default below Android 12 and for targetSdk < 31, explicit from 31 on. One per activity
                // and card: a new launch into the running player first reports the old session to the
                // old callback, and a shared one would by then carry the new activity's key. And per window:
                // an old session's report must not be mapped back by a newer window.
                putParcelable(
                    "result_callback", PendingIntent.getBroadcast(
                        context, (activity + card?.id + window.first).hashCode(),
                        Intent(context, PlayerReportReceiver::class.java)
                            .putExtra(REPORT_ACTIVITY_EXTRA, activity)
                            .putExtra(REPORT_CARD_EXTRA, card?.id)
                            .putExtra(REPORT_OFFSET_EXTRA, window.first)
                        .putExtra(REPORT_COUNT_EXTRA, window.count())
                            .putExtra(REPORT_TOTAL_EXTRA, items.size),
                        PendingIntent.FLAG_UPDATE_CURRENT or
                                (if (VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    )
                )
            })
        }
    }

    /**
     * The items around [start] that fit [ITEMS_BUDGET]: the whole playlist when it fits, else the next
     * ones first (up to two thirds, as auto-next goes forward), then next and previous in turn.
     */
    private fun window(items: List<Bundle>, start: Int): IntRange {
        if (items.isEmpty()) return 0 until 0
        val sizes = items.map { item ->
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(item)
                parcel.dataSize()
            } finally {
                parcel.recycle()
            }
        }
        var first = start
        var last = start
        var used = sizes[start]
        while (last < items.lastIndex && used + sizes[last + 1] <= ITEMS_BUDGET * 2 / 3) used += sizes[++last]
        var grew = true
        while (grew) {
            grew = false
            if (last < items.lastIndex && used + sizes[last + 1] <= ITEMS_BUDGET) {
                used += sizes[++last]; grew = true
            }
            if (first > 0 && used + sizes[first - 1] <= ITEMS_BUDGET) {
                used += sizes[--first]; grew = true
            }
        }
        if (first > 0 || last < items.lastIndex)
            Log.i("JustPlusPlayer", "Playlist cut to $first..$last of ${items.size} [$used bytes]")
        return first..last
    }
}
