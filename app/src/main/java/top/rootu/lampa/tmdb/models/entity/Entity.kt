package top.rootu.lampa.tmdb.models.entity

import top.rootu.lampa.tmdb.models.titles.AlternativeTitles
import top.rootu.lampa.tmdb.models.trailers.Trailers

data class Entities(
    var page: Int,
    var total_results: Int,
    var total_pages: Int,
    var results: List<Entity>
)

//fun Entity.toTmdbID(): TmdbId {
//    var g = genres?.map { it?.id }
//    if (g.isNullOrEmpty())
//        g = genre_ids
//    val c = production_countries?.map { it.iso_3166_1 } ?: this.origin_country
//    var d = release_date
//    if (d?.isEmpty() == true)
//        d = first_air_date
//    if (d?.isEmpty() == true)
//        d = null
//    return TmdbId(id ?: 0, media_type ?: "", g, vote_average, vote_count, c, d)
//}

data class Entity(
    var adult: Boolean?,
    var alternative_titles: AlternativeTitles?,
    var backdrop_path: String?,
    var belongs_to_collection: BelongsToCollection?,
    var budget: Int?,
    var certification: String?,
    var character: String?,
//    var created_by: Any?,
    var credit_id: String?,
    var episode_run_time: List<Int>?,
    var first_air_date: String?,
    var genre_ids: List<Int?>?,
    var genres: List<Genre?>?,
    var homepage: String?,
    var id: Int?,
    var images: Images?,
    var imdb_id: String?,
    var in_production: Boolean?,
    var languages: Any?,
    var last_air_date: String?,
    var media_type: String?,
    var name: String?,
    var networks: List<ProductionCompany>?,
    var number_of_episodes: Int?,
    var number_of_seasons: Int?,
    var origin_country: List<String>?,
    var original_language: String?,
    var original_name: String?,
    var original_title: String?,
    var overview: String?,
    var popularity: Double?,
    var poster_path: String?,
    var production_companies: List<ProductionCompany>?,
    var production_countries: List<ProductionCountry>?,
    var release_date: String?,
    var revenue: Long?,
    var runtime: Int?,
    var seasons: List<Season>?,
    var spoken_languages: List<SpokenLanguage>?,
    var status: String?,
    var tagline: String?,
    var title: String?,
    var type: String?,
    var video: Boolean?,
    var vote_average: Double?,
    var vote_count: Int?,
    var year: String?,
    var videos: Trailers?,
) {
    override fun toString(): String = (id?.toString() ?: "-1") + ": " + (title ?: "") + " " + (original_title ?: "") + " " + (year ?: "")
}