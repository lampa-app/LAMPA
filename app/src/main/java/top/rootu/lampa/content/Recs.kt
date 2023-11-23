package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.AndroidJS
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.helpers.Prefs.RCS
import top.rootu.lampa.models.LampaRec
import top.rootu.lampa.models.TmdbID

class Recs : LampaProviderI() {
    override fun get(): ReleaseID {
        return ReleaseID(Recs.get())
    }

    companion object {
        fun get(): List<TmdbID> {
            val lst = mutableListOf<TmdbID>()
            val filtered = App.context.RCS?.filterAll(generateFilters())
                ?.distinctBy { it.id } // make unique
                ?.shuffled() // randomize order

            if (BuildConfig.DEBUG)
                Log.d("*****", "Recs cards total: ${App.context.RCS?.size} | filtered: ${filtered?.size}")
            if (!filtered.isNullOrEmpty()) {
                filtered.forEach { r ->
                    lst.add(r.toTmdbID())
                }
            }
            return lst
        }
        private fun generateFilters() = listOf<(LampaRec) -> Boolean>(
            { it.id != 0 },
            { it.genre_ids?.let { gid -> !gid.contains(16) } ?:  true }, // exclude Animation
            { it.vote_average?.let { r -> r > 6 } ?: true }, // rating > 6
            { it.popularity?.let { p -> p > 10 } ?: true } // popularity > 10
        )

        private fun <T> List<T>.filterAll(filters: List<(T) -> Boolean>) =
            filter { item -> filters.all { filter -> filter(item) } }
    }
}