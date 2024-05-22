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
    const val RECS = "recs"
    const val BOOK = "book"
    const val LATE = "wath"
    const val LIKE = "like"
    const val HIST = "history"
    const val LOOK = "look"
    const val VIEW = "viewed"
    const val SCHD = "scheduled"
    const val CONT = "continued"
    const val THRW = "thrown"

    private val providers = mapOf(
        RECS to Recs(),
        BOOK to Bookmarks(),
        LIKE to Like(),
        HIST to History(),
        LOOK to Look(),
        VIEW to Viewed(),
        SCHD to Scheduled(),
        CONT to Continued(),
        THRW to Thrown(),
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