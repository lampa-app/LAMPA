package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.helpers.Prefs.historyItems
import top.rootu.lampa.helpers.Prefs.viewedItems
import top.rootu.lampa.models.TmdbID

abstract class LampaProviderI : Any() {
    abstract fun get(): ReleaseID?
}

data class ReleaseID(
    val items: List<TmdbID>?
)

object LampaProvider {
    const val Recs = "recs"
    const val Hist = "history"
    const val Book = "book"

    private val providers = mapOf(
        Recs to Recs(),
        Hist to History(),
        Book to Bookmarks(),
    )

    fun get(name: String, filter: Boolean): ReleaseID? {
        providers[name]?.let { provider ->
            synchronized(provider) {
                try {
                    provider.get()?.let { rls ->
                        if (filter)
                            return ReleaseID(filterViewed(rls.items.orEmpty()))
                        return rls
                    }
                    return null
                } catch (e: Exception) {
                    return null
                }
            }
        } ?: let {
            return null
        }
    }

    private fun filterViewed(lst: List<TmdbID>): List<TmdbID> {
        return lst.filter { ent ->
            !App.context.viewedItems.contains(ent.id.toString()) && !App.context.historyItems.contains(ent.id.toString())
        }
    }
}