package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.helpers.Prefs.REC
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.models.LampaRec

class Recs : LampaProviderI() {
    override fun get(): LampaContent {
        return LampaContent(Recs.get())
    }

    companion object {
        fun get(): List<LampaCard> {
            val lst = mutableListOf<LampaCard>()
            val recommendations = App.context.REC ?: return lst // Handle null case
            // Apply filters, ensure uniqueness, and shuffle
            val filtered = recommendations
                .filterAll(generateFilters())
                .distinctBy { it.id }
                .shuffled()
            // Log results in debug mode
            if (BuildConfig.DEBUG) {
                Log.d(
                    "Recs",
                    "Total recommendations: ${recommendations.size} | Filtered: ${filtered.size}"
                )
            }
            // Convert filtered recommendations to LampaCard and add to the list
            filtered.forEach { recommendation ->
                lst.add(recommendation.toLampaCard())
            }
            return lst
        }

        private fun generateFilters(): List<(LampaRec) -> Boolean> = listOf(
            // { it.genre_ids?.contains("16") != true }, // Exclude Animation
            { it.vote_average?.let { rating -> rating > 6 } == true }, // Rating > 6
            { it.popularity?.let { pop -> pop > 4 } == true } // Popularity > 4
        )

        private fun <T> List<T>.filterAll(filters: List<(T) -> Boolean>): List<T> =
            filter { item -> filters.all { filter -> filter(item) } }
    }
}