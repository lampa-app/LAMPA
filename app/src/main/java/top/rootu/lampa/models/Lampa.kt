package top.rootu.lampa.models

import top.rootu.lampa.tmdb.TMDB
import java.util.Locale

data class Favorite(
    val card: List<LampaCard>?,
    val like: List<String>?,
    val wath: List<String>?,
    val book: List<String>?,
    val history: List<String>?, //["KP_573840", 345887, 156022]
    val look: List<String>?,
    val viewed: List<String>?,
    val scheduled: List<String>?,
    val continued: List<String>?,
    val thrown: List<String>?
)

data class CubBookmark(
    val id: Int?,
    val cid: Int?,
    val card_id: String?, // "92830", "0a88d69f-6f33-49aa-91db-ee6e0c3fdff1"
    val type: String?, // "history", "book", "wath"
    val data: String?, // "{\"id\":212344,\"source\":\"ivi\",\"title\"..."
    val profile: Int?,
    val time: Long? // 0, 1650748577390
)

//data class TimeTable(
//    val id: Int?,
//    val season: Int?,
//    val episodes: Episodes?,
//    val scanned: Int?,
//    val scanned_time: Long?,
//)
//
//data class Episodes(
//    val air_date: String?,
//    val season_number: Int?,
//    val episode_number: Int?,
//    val name: String?,
//    val still_path: String?
//)

// TODO: implement type adapters for fields like id
// https://stackoverflow.com/questions/27626355/gson-deserializing-with-changing-field-types
// https://proandroiddev.com/safe-parsing-kotlin-data-classes-with-gson-4d560fe3cdd2
data class LampaCard(
    var source: String?, //"KP","tmdb","cub","ivi","okko" etc
    var type: String?, //"movie","tv","Scripted" etc
    val id: String?, //"KP_1227897","84958","0a88d69f-6f33-49aa-91db-ee6e0c3fdff1"
    val name: String?, //"Топ Гир: Лучшее",
    val original_name: String?, //"Top Gear: Best of",
    val title: String?, //"Топ Гир: Лучшее",
    val original_title: String?, //"Top Gear: Best of",
    val overview: String?,
    val img: String?, //"https://kinopoiskapiunofficial.tech/images/posters/kp_small/1227897.jpg",
    val background_image: String?,
    val genres: List<Genre?>?,
    val popularity: Double?,
    val production_companies: List<ProductionCompany>?,
    val production_countries: List<ProductionCountry>?,
    val vote_average: Double?, //9.1,
    val vote_count: Int?, //7217,
    val kinopoisk_id: String?, //1227897,
    val kp_rating: Double?, //9,
    val imdb_id: String?, //"",
    val imdb_rating: Double?, //0,
    val first_air_date: String?, //2006, 1989-12-17
    val last_air_date: String?, //2014, 2023-11-19
    val number_of_seasons: Int?, //1,
    val number_of_episodes: Int?, //12,
    val persons: Persons?,
    val simular: Simular?,
    val runtime: Int?, //0,
    val release_date: String?, //"2006", "2023-07-19"
    val release_year: String?, //"2006", "2023"
    val adult: Boolean?, // false
    // "seasons": [{...}],
) {
    fun fixCard() {
        // fix ID
        // internalid = id?.toIntOrNull() ?: id.hashCode()
        // fix source
        source = source?.lowercase(Locale.ROOT) ?: "lampa"
        // fix media_type
        type = type?.lowercase(Locale.ROOT) ?: ""
        if (type == "scripted")
            type = if (release_date.isNullOrEmpty() || !name.isNullOrEmpty())
                "tv"
            else
                "movie"
        if (type?.contains("miniseries", true) == true)
            type = "tv"
        if (type.isNullOrEmpty())
            type = if (release_date.isNullOrEmpty() || !name.isNullOrEmpty())
                "tv"
            else
                "movie"
//        if (!poster_path.isNullOrEmpty() && img.isNullOrEmpty())
//            img = TMDB.imageUrl(poster_path)
//        if (!backdrop_path.isNullOrEmpty() && background_image.isNullOrEmpty())
//            background_image = TMDB.imageUrl(backdrop_path)
    }

    override fun toString(): String {
        val tt = if (!name.isNullOrEmpty()) name else title
        return "LampaCard(source:$source id:$id type:$type $tt $img)"
    }
}

data class LampaRec(
    val id: String, // "84958"
    val name: String?,
    val title: String?,
    val original_name: String?,
    val original_language: String?, // "en"
    val original_title: String?,
    val overview: String?,
    val poster_path: String?, // "/82HaUMIagdh5PLflUOVrHn5GsI9.jpg"
    val backdrop_path: String?, // "/aRKQdF6AGbhnF9IAyJbte5epH5R.jpg"
    val media_type: String?, // "movie" | "tv"
    val genre_ids: List<String?>?,
    val popularity: Double?, // 126.38
    val release_date: String?, // "2023-06-09"
    val first_air_date: String?, // "2022-05-05"
    val vote_average: Double?, // 8.239
    val vote_count: Int?, // 427
    val video: Boolean?, // false
    val adult: Boolean?, // false
) {
    fun toLampaCard(): LampaCard {
        // fix media_type
        val mt = media_type ?: "movie"
        // fix genres
        val genres = genre_ids?.map { Genre(it, TMDB.genres[it?.toIntOrNull()], "") }
        // fix images
        val img =
            if (!poster_path.isNullOrEmpty() && poster_path.startsWith("/")) TMDB.imageUrl(
                poster_path
            )
                .replace("original", "w342") else "" // TODO fetch TMDB.Images.get
        val backdrop =
            if (!backdrop_path.isNullOrEmpty() && backdrop_path.startsWith("/")) TMDB.imageUrl(
                backdrop_path
            )
                .replace("original", "w1280") else "" // TODO fetch TMDB.Images.get
        return LampaCard(
            "tmdb",
            mt,
            id,
            name,
            original_name,
            title,
            original_title,
            overview,
            img,
            backdrop,
            genres,
            popularity,
            null,
            null,
            vote_average,
            vote_count,
            null,
            null,
            null,
            null,
            first_air_date,
            null,
            null,
            null,
            null,
            null,
            null,
            release_date,
            null,
            adult,
        )
    }
}

data class Genre(
    val id: String?, // can be int ID or String like Drama in CUB bookmarks
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

data class Persons(
    val cast: List<Person>,
    val crew: List<Person>
)

data class WatchNextToAdd(
    val id: String,
    var card: LampaCard?
)