package top.rootu.lampa.content

import top.rootu.lampa.AndroidJS
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.isInLampaWatchNext
import top.rootu.lampa.models.TmdbID

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
            val bookmarks = App.context.FAV?.book
            val cards = App.context.FAV?.card
            val found = cards?.filter { bookmarks?.contains(it.id) == true }
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }

        fun addToWatchNext(tmdbID: TmdbID) {
            tmdbID.id.let {
                if (!App.context.isInLampaWatchNext(it.toString())) {
                    Helpers.manageFavorite("add", "wath", it.toString())
                }
            }
// addToWatchNext called from HomeWatch so already added by user action
//            CoroutineScope(Dispatchers.IO).launch {
//                val ent = tmdbID.getEntity()
//                ent?.let {
//                    withContext(Dispatchers.Default) {
//                        WatchNext.add(it)
//                    }
//                }
//            }
        }

        fun remFromWatchNext(tmdbID: String) {
            if (App.context.isInLampaWatchNext(tmdbID)) {
                Helpers.manageFavorite("rem", "wath", tmdbID)
            }
// remFromWatchNext called from HomeWatch so already removed by user action
//            WatchNext.rem(tmdbID)
        }
    }
}