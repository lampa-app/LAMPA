package top.rootu.lampa.tmdb.models.trailers

data class Trailer(
    var id: String?,
    var iso_3166_1: String?,
    var iso_639_1: String?,
    var key: String?,
    var name: String?,
    var site: String?,
    var size: Int?,
    var type: String?,

    var poster: String?,
    var link: String?
)