package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.models.TmdbID

class History : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(History.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val historyCards = App.context.FAV?.card?.filter { App.context.FAV?.history?.contains(it.id) == true }
            val excludePending = historyCards?.filter {
                !App.context.histToRemove.contains(it.id)
            } // skip pending to remove
            excludePending?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst.reversed()
        }
    }
}