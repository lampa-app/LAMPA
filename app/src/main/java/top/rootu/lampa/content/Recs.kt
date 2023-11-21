package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.AndroidJS
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.models.TmdbID

class Recs : LampaProviderI() {
    override fun get(): ReleaseID {
        return ReleaseID(Recs.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val cards = AndroidJS.RCS
            val lst = mutableListOf<TmdbID>()
            val filter = cards.filter { it.media_type.isNotEmpty() }
                .distinctBy { it.id }
                .shuffled()
            if (BuildConfig.DEBUG) Log.d("*****", "Recs cards total: ${cards.size} | filter: ${filter.size}")
            if (filter.isNotEmpty()) {
                filter.forEach { r ->
                    lst.add(r.toTmdbID())
                }
            }
            return lst
        }
    }
}