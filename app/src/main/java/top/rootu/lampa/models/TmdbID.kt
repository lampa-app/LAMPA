package top.rootu.lampa.models

import top.rootu.lampa.tmdb.TMDB
import top.rootu.lampa.tmdb.models.entity.Entity

data class TmdbID(
    var id: Int, // required
    var media_type: String, // required
    var genre_ids: List<Int?>?,
    var vote_average: Double?,
    var vote_count: Int?,
    var release_date: String?,
)

fun TmdbID.getEntity(): Entity? {
    return TMDB.video("$media_type/$id")
}