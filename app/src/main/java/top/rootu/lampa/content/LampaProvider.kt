package top.rootu.lampa.content

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
    // TODO: filter viewed
    fun get(name: String, filter: Boolean): ReleaseID? {
        providers[name]?.let { provider ->
            synchronized(provider) {
                try {
                    provider.get()?.let { rls ->
//                        if (filter)
//                            return ReleaseID(rls.date, rls.time, filterViewed(rls.items.orEmpty()))
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

//    private fun filterViewed(lst: List<TmdbId>): List<TmdbId> {
//        if (Prefs.getViewedType() == "2")
//            clearExpiredViewedItems()
//        val viewed = getViewedItems()
//        return lst.filter { ent -> !viewed.map { it.first }.contains(ent.id.toString()) }
//    }
}