package top.rootu.lampa.models

import top.rootu.lampa.tmdb.TMDB
import top.rootu.lampa.tmdb.models.entity.Entity

data class TmdbId(
    var id: Int,
    var media_type: String,
    var genre_ids: List<Int?>?,
    var vote_average: Double?,
    var vote_count: Int?,
    var countries: List<String>?,
    var release_date: String?,
)

fun TmdbId.getEntity(): Entity? {
    return TMDB.video("$media_type/$id")
}