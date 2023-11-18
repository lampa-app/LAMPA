package top.rootu.lampa.tmdb.models.release_dates
data class Result(
    val iso_3166_1: String,
    val release_dates: List<ReleaseDate>
)