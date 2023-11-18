package top.rootu.lampa.tmdb.models.release_dates

data class ReleaseDate(
    val certification: String,
    val iso_639_1: String,
    val note: String,
    val release_date: String,
    val type: Int
)