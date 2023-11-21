package top.rootu.lampa.models

import android.util.Log

data class Favorite (
    val card: List<LampaCard>?,
    val like: List<String>?,
    val wath: List<String>?,
    val book: List<String>?,
    val history: List<String>?, //["KP_573840", 345887, 156022],
    val look: List<String>?,
    val viewed: List<String>?,
    val scheduled: List<String>?,
    val continued: List<String>?,
    val thrown: List<String>?
)

data class LampaCard (
    val source: String, //"KP","TMDB","cub" etc
    val type: String?, //"movie","tv","Scripted" etc
    val adult: Boolean?,
    val id: String, // "KP_1227897", "84958"
    val name: String?, //"Топ Гир: Лучшее",
    val original_name: String?, //"Top Gear: Best of",
    val title: String?, //"Топ Гир: Лучшее",
    val original_title: String?, //"Top Gear: Best of",
    val overview: String?,
    val img: String?, //"https://kinopoiskapiunofficial.tech/images/posters/kp_small/1227897.jpg",
    val background_image: String?,
    val genres: List<Genre?>?,
    // val genres_ids: List<Int?>?,
    val popularity: Float?,
    val production_companies: List<ProductionCompany>?,
    val production_countries: List<ProductionCountry>?,
    val vote_average: Double?, //9.1,
    val vote_count: Int?, //7217,
    val kinopoisk_id: String?, //1227897,
    val kp_rating: Double?, //9,
    val imdb_id: String, //"",
    val imdb_rating: Double?, //0,
    val first_air_date: String?, //2006, 1989-12-17
    val last_air_date: String?, //2014, 2023-11-19
    val number_of_seasons: Int?, //1,
//    "seasons": [{...}],
    val number_of_episodes: Int?, //12,
    val persons: Persons?,
    val simular: Simular?,
    val runtime: Int?, //0,
    val release_date: String?, //"2006", "2023-07-19"
    val release_year: String?, //"2006", "2023"
)
// {
//    override fun toString(): String =
//        (id ?: "-1") + ": " + (name ?: "") + " " + (original_name ?: "") + " " + (type ?: "")
// }


data class LampaRec(
    val adult: Boolean,
    val backdrop_path: String, // "/aRKQdF6AGbhnF9IAyJbte5epH5R.jpg"
    val id: Int, // "84958"
    val name: String?,
    val original_name: String?,
    val original_language: String?, //"en"
    val poster_path: String, // "/82HaUMIagdh5PLflUOVrHn5GsI9.jpg"
    val title: String?,
    val original_title: String?,
    val media_type: String, // "movie" | "tv"
    val genre_ids: List<Int?>?,
    val overview: String?,
    val popularity: Double?, // 126.38
    val release_date: String?, // "2023-06-09"
    val first_air_date: String?, // "2022-05-05"
    val vote_average: Double?, // 8.239
    val vote_count: Int?, // 427
    val video: Boolean?, // false
) {
    fun toTmdbID(): TmdbID {
        val g = genre_ids
//        val c = production_countries?.map { it.iso_3166_1 } ?:  this.origin_country
        var d = release_date
        if (d.isNullOrEmpty())
            d = first_air_date
        if (d.isNullOrEmpty())
            d = null
        Log.d("*****", "LampaRec.toTmdbID() id: $id, media_type: $media_type genres: $g date: $d")
        return TmdbID(id, media_type, g, vote_average, vote_count, d)
    }
}
data class Genre(
    val id: Int,
    val name: String?,
    val url: String?,
)

data class ProductionCompany(
    val name: String
)

data class ProductionCountry(
    val iso_3166_1: String?,
    val name: String
)

data class Simular(
    val results: List<Any>?
)

data class Person(
    val id: Int, //261639,
    val name: String, //Клара Румянова",
    val url: String, //"",
    val img: String, //https://kinopoiskapiunofficial.tech/images/actor_posters/kp/261639.jpg",
    val character: String, //Заяц, озвучка",
    val job: String, //Actor"
)
data class Persons (
    val cast: List<Person>,
    val crew: List<Person>
)