package top.rootu.lampa.tmdb.models.entity
data class Entities(
    var page: Int,
    var total_results: Int,
    var total_pages: Int,
    var results: List<Entity>
)