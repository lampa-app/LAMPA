package top.rootu.lampa.content

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.AndroidJS
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.helpers.Helpers.manageFavorite
import top.rootu.lampa.models.TmdbID
import top.rootu.lampa.models.getEntity

class Bookmarks : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Bookmarks.get())
    }

    fun isBookmarked(tmdbID: String?): Boolean {
        return get().items?.find { it.id.toString() == tmdbID } != null
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val bookmarks = AndroidJS.FAV?.book
            val cards = AndroidJS.FAV?.card
            Log.d("*****","Bookmarks.get() list: $bookmarks")
            val found = cards?.filter { bookmarks?.contains(it.id) == true }
            Log.d("*****", "Bookmarks cards found: ${found?.toString()}")
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }

        fun add(tmdbID: String?) {
            manageFavorite("add", "book", tmdbID.toString())
        }

        fun rem(tmdbID: String?) {
            manageFavorite("rem", "book", tmdbID.toString())
        }

        fun addToWatchNext(tmdbID: TmdbID) {
            if (!isInLampaWatchNext(tmdbID.id.toString())) {
                CoroutineScope(Dispatchers.IO).launch {
                    val ent = tmdbID.getEntity()
                    ent?.let {
                        WatchNext.add(it)
                    }
                }
            }
            manageFavorite("add", "wath", tmdbID.id.toString())
        }

        fun remFromWatchNext(tmdbID: String) {
            if (isInLampaWatchNext(tmdbID)) {
                WatchNext.rem(tmdbID)
            }
            manageFavorite("rem", "wath", tmdbID)
        }

        fun isInLampaWatchNext(tmdbID: String?): Boolean {
            val nxt = AndroidJS.FAV?.wath
            return nxt?.contains(tmdbID) == true
        }
    }
}