package netfix.content

import android.util.Log
import netfix.App
import netfix.app.BuildConfig
import netfix.helpers.Prefs.REC
import netfix.models.NetfixCard
import netfix.models.NetfixRec

class Recs : NetfixProviderI() {
    override fun get(): ReleaseID {
        return ReleaseID(Recs.get())
    }

    companion object {
        fun get(): List<NetfixCard> {
            val lst = mutableListOf<NetfixCard>()
            val filtered = App.context.REC?.filterAll(generateFilters())
                ?.distinctBy { it.id } // make unique
                ?.shuffled() // randomize order
            if (BuildConfig.DEBUG)
                Log.d("*****", "Recs cards total: ${App.context.REC?.size} | filtered: ${filtered?.size}")
            if (!filtered.isNullOrEmpty()) {
                filtered.forEach { r ->
                    lst.add(r.toNetfixCard())
                }
            }
            return lst
        }
        private fun generateFilters() = listOf<(NetfixRec) -> Boolean>(
//            { it.id != "0" },
            { it.genre_ids?.let { gid -> !gid.contains("16") } != false }, // exclude Animation
            { it.vote_average?.let { r -> r > 6 } != false }, // rating > 6
            { it.popularity?.let { p -> p > 10 } != false } // popularity > 10
        )

        private fun <T> List<T>.filterAll(filters: List<(T) -> Boolean>) =
            filter { item -> filters.all { filter -> filter(item) } }
    }
}