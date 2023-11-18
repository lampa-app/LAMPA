package top.rootu.lampa.tmdb.models.entity

data class ProductionCompany(
    val id: Int,
    var logo_path: String,
    val name: String,
    val origin_country: String
)