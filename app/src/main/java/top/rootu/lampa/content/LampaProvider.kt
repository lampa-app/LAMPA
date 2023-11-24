package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.models.TmdbID

abstract class LampaProviderI : Any() {
    abstract fun get(): ReleaseID?
}

data class ReleaseID(
    val items: List<TmdbID>?
)

object LampaProvider {
    // this is channel internal id
    const val Recs = "recs"
    const val Like = "like"
    const val Hist = "history"
    const val Book = "book"
    const val Late = "wath"

    private val providers = mapOf(
        Recs to Recs(),
        Like to Like(),
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
            App.context.FAV?.viewed?.contains(ent.id.toString()) != true && App.context.FAV?.history?.contains(ent.id.toString()) != true
        }
    }
}