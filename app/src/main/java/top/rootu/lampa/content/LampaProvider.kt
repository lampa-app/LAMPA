package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.App
import top.rootu.lampa.helpers.Prefs.CUB
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.syncEnabled
import top.rootu.lampa.models.LampaCard

abstract class LampaProviderI {
    abstract fun get(): LampaContent?
}

data class LampaContent(
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

    // Map of provider names to provider instances
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

    /**
     * Retrieves content from the specified provider.
     *
     * @param name The name of the provider.
     * @param filter Whether to filter out viewed or historical content.
     * @return A LampaContent object containing the content, or null if an error occurs.
     */
    fun get(name: String, filter: Boolean): LampaContent? {
        val provider = providers[name] ?: return null
        return synchronized(provider) {
            try {
                val release = provider.get() ?: return@synchronized null
                if (filter) {
                    LampaContent(filterViewed(release.items.orEmpty()))
                } else {
                    release
                }
            } catch (e: Exception) {
                Log.e("LampaProvider", "Error retrieving content from provider $name", e)
                null
            }
        }
    }

    /**
     * Filters out content that has been viewed or is in the history.
     *
     * @param lst The list of LampaCard objects to filter.
     * @return A filtered list of LampaCard objects.
     */
    private fun filterViewed(lst: List<LampaCard>): List<LampaCard> {
        val fav = App.context.FAV
        val cub = App.context.CUB
        val syncEnabled = App.context.syncEnabled

        return lst.filter { card ->
            val isInFavHistory = !syncEnabled && fav?.history?.contains(card.id) == true
            val isInFavViewed = !syncEnabled && fav?.viewed?.contains(card.id) == true
            val isInCubHistory =
                syncEnabled && cub?.filter { it.type == HIST }?.mapNotNull { it.card_id }
                    ?.contains(card.id) == true
            val isInCubViewed =
                syncEnabled && cub?.filter { it.type == VIEW }?.mapNotNull { it.card_id }
                    ?.contains(card.id) == true

            !isInFavHistory && !isInFavViewed && !isInCubHistory && !isInCubViewed
        }
    }
}