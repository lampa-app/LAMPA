package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.models.TmdbID

class Like : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Like.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val likeCards = App.context.FAV?.card?.filter { App.context.FAV?.like?.contains(it.id) == true }
            val excludePending = likeCards?.filter {
                !App.context.likeToRemove.contains(it.id)
            } // skip pending to remove
            excludePending?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }
    }
}