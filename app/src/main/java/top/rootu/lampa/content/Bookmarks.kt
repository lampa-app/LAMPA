package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.models.TmdbID

class Bookmarks : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Bookmarks.get())
    }

//    fun isBookmarked(tmdbID: String?): Boolean {
//        return get().items?.find { it.id.toString() == tmdbID } != null
//    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val bookCards = App.context.FAV?.card?.filter { App.context.FAV?.book?.contains(it.id) == true }
            val excludePending = bookCards?.filter {
                !App.context.bookToRemove.contains(it.id)
            } // skip pending to remove
            excludePending?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }
    }
}