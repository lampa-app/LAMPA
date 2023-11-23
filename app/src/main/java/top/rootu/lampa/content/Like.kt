package top.rootu.lampa.content

import top.rootu.lampa.AndroidJS
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.models.TmdbID

class Like : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Like.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val found = App.context.FAV?.card?.filter { App.context.FAV?.like?.contains(it.id) == true }
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }
    }
}