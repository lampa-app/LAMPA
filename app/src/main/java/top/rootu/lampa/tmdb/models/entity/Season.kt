package top.rootu.lampa.tmdb.models.entity

data class Season(
    var air_date: String?,
    val episode_count: Int,
    val id: Int,
    val name: String?,
    val overview: String?,
    var poster_path: String?,
    val season_number: Int
)