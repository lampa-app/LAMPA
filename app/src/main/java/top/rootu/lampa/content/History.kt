package top.rootu.lampa.content

import top.rootu.lampa.AndroidJS
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.models.TmdbID

class History : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(History.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val history = App.context.FAV?.history
            val cards = App.context.FAV?.card
            val found = cards?.filter { history?.contains(it.id) == true }
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }
    }
}