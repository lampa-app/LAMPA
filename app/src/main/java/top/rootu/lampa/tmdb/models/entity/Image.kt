package top.rootu.lampa.tmdb.models.entity

data class Image(
    var aspect_ratio: Double,
    var file_path: String,
    var height: Int,
    var iso_639_1: String,
    var vote_average: Double,
    var vote_count: Int,
    var width: Int
)