package top.rootu.lampa.content

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
            val found = App.context.FAV?.card?.filter { App.context.FAV?.book?.contains(it.id) == true }
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }

        fun addToLampaWatchNext(tmdbID: TmdbID) {
            tmdbID.id.let {
                if (!App.context.isInLampaWatchNext(it.toString())) {
                    Helpers.manageFavorite("add", "wath", it.toString())
                }
            }
        }

        fun remFromLampaWatchNext(tmdbID: String) {
            if (App.context.isInLampaWatchNext(tmdbID)) {
                Helpers.manageFavorite("rem", "wath", tmdbID)
            }
        }
    }
}