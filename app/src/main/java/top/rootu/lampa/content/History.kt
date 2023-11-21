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
            val history = AndroidJS.FAV.history
            val cards = AndroidJS.FAV.card
            history?.let {
                Log.d("*****","History.get() list: $history")
            }
            val found = cards?.filter { history?.contains(it.id) == true }
            //Log.d("*****", "History cards found: ${found?.toString()}")
            // TODO
            return emptyList()
        }

        fun add(ent: Entity) {
            // TODO
        }
    }
}