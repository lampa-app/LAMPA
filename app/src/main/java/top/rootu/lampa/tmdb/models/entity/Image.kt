package top.rootu.lampa.tmdb.models.entity

data class Image(
    var aspect_ratio: Double,
    var file_path: String?, // Gson leaves a missing field null whatever is declared
    var height: Int,
    var iso_639_1: String?, // TMDB: null for images without lettering
    var vote_average: Double,
    var vote_count: Int,
    var width: Int
)