package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.AndroidJS
import top.rootu.lampa.models.TmdbID
import top.rootu.lampa.tmdb.models.entity.Entity

class History : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(History.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val history = AndroidJS.FAV.history
            val cards = AndroidJS.FAV.card
            Log.d("*****","History.get() list: $history")
            val found = cards?.filter { history?.contains(it.id) == true }
            Log.d("*****", "History cards found: ${found?.toString()}")
            found?.forEach { card ->
                if (card.id !== "0")
                    lst.add(card.toTmdbID())
            }
            return lst
        }

        fun add(ent: Entity) {
            // TODO
        }
    }
}