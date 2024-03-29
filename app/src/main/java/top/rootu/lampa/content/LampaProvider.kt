package top.rootu.lampa.content

import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.models.LampaCard

abstract class LampaProviderI : Any() {
    abstract fun get(): ReleaseID?
}

data class ReleaseID(
    val items: List<LampaCard>?
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

    private fun filterViewed(lst: List<LampaCard>): List<LampaCard> {
        return lst.filter { card ->
            App.context.FAV?.viewed?.contains(card.id) != true &&
                    App.context.FAV?.history?.contains(card.id) != true
        }
    }
}