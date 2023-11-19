package top.rootu.lampa.tmdb.models.titles

data class AlternativeTitles(
    val id: Int?, // optional
    val titles: List<Result>?, // movie titles
    val results: List<Result>? // tv titles
)