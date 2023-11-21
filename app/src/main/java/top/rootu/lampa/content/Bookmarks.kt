package top.rootu.lampa.content

import android.util.Log
import top.rootu.lampa.AndroidJS
import top.rootu.lampa.App
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.helpers.Prefs.useWatchNext
import top.rootu.lampa.models.TmdbID
import top.rootu.lampa.models.getEntity
import top.rootu.lampa.tmdb.models.entity.Entity

class Bookmarks : LampaProviderI() {

    override fun get(): ReleaseID {
        return ReleaseID(Bookmarks.get())
    }

//    fun add(ent: Entity) {
//        Bookmarks.add(ent)
//        if (App.context.useWatchNext)
//            WatchNext.add(ent)
//    }

//    fun rem(ent: Entity) {
//        Bookmarks.rem(ent)
//        if (App.context.useWatchNext)
//            WatchNext.rem(ent)
//    }

//    fun isInFavorite(ent: Entity): Boolean {
//        return get().items?.find { it.id == ent.id } != null
//    }

    fun add(tmdbID: TmdbID) {
        Bookmarks.add(tmdbID)
        if (App.context.useWatchNext)
            tmdbID.getEntity()?.let { WatchNext.add(it) }
    }

    fun rem(tmdbID: TmdbID) {
        Bookmarks.rem(tmdbID)
        if (App.context.useWatchNext)
            WatchNext.rem(tmdbID.id)
    }

    fun isBookmarked(tmdbID: TmdbID): Boolean {
        return get().items?.find { it.id == tmdbID.id } != null
    }
    companion object {
        fun get(): List<TmdbID> {
            val bookmarks = AndroidJS.FAV.book
            val cards = AndroidJS.FAV.card
            bookmarks?.let {
                Log.d("*****","Bookmarks.get() list: $bookmarks")
            }
            val found = cards?.filter { bookmarks?.contains(it.id) == true }
            //Log.d("*****", "Bookmarks cards found: ${found?.toString()}")

            return emptyList()
        }

        fun add(tmdbID: TmdbID) {
            // TODO
        }

        fun rem(tmdbID: TmdbID) {
            // TODO
        }
    }
}