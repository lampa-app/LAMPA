package top.rootu.lampa.models

import top.rootu.lampa.tmdb.TMDB
import java.util.Locale

const val LAMPA_CARD_KEY = "lampaCardJSON" // Used in Intents and PlayStateManager

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
//    val data: String?, // "{\"id\":212344,\"source\":\"ivi\",\"title\"..."
    val data: LampaCard?, // Object
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
    var source: String?, // "KP","tmdb","cub","ivi","okko" etc
    var type: String?, // "movie","tv","Scripted" etc
    val id: String?, // "KP_1227897","84958","0a88d69f-6f33-49aa-91db-ee6e0c3fdff1"
    val name: String?, // "Топ Гир: Лучшее",
    val original_name: String?, // "Top Gear: Best of",
    val title: String?, // "Топ Гир: Лучшее",
    val original_title: String?, // "Top Gear: Best of",
    val original_language: String?, // "en",
    val overview: String?,
    val poster_path: String?, // "/MSc1kcaHXvvyGgoyjDa60jxdqq.jpg",
    val backdrop_path: String?, // "/4XM8DUTQb3lhLemJC51Jx4a2EuA.jpg",
    var img: String?, // "https://kinopoiskapiunofficial.tech/images/posters/kp_small/1227897.jpg",
    var background_image: String?,
    val genre_ids: List<String>?, // [28,80,53],
    var genres: List<Genre?>?,
    val popularity: Double?,
    val origin_country: List<String>?, // [ "GB" ]
    val production_companies: List<ProductionCompany>?,
    val production_countries: List<ProductionCountry>?,
    val vote_average: Double?, // 9.1,
    val vote_count: Int?, // 7217,
    val kinopoisk_id: String?, // 1227897,
    val kp_rating: Double?, // 9,
    val imdb_id: String?, // "",
    val imdb_rating: Double?, // 0,
    val release_year: String?, // "2006", "2023"
    val release_date: String?, // "2006", "2023-07-19"
    val first_air_date: String?, // 2006, 1989-12-17
    val last_air_date: String?, // 2014, 2023-11-19
    val number_of_seasons: Int?, // 1,
    val number_of_episodes: Int?, // 12,
    // val next_episode_to_air: String?, // "" | { "id": 5220956, "overview": "", "name": "Эпизод 5", "vote_average": 7.5, "vote_count": 2, "air_date": "2024-06-25", "episode_number": 5, "episode_type": "standard", "production_code": "", "runtime": null, "season_number": 1, "show_id": 114479, "still_path": "/3ZPtAcaLnzJ6dAPw97hB9bKc19o.jpg" }
    val persons: Persons?,
    val simular: Simular?,
    val status: String?, // "released", "Ended", "returning series", ...
    val release_quality: String?, // "4K",
    val runtime: Int?, // 0,
    val adult: Boolean?, // false
    // "seasons": [{...}],
) {
    /**
     * Normalizes and fixes data inconsistencies in the LampaCard.
     */
    fun fixCard() {
        // internalid = id?.toIntOrNull() ?: id.hashCode() // fix ID
        fixSource() // fix source
        fixType() // fix media_type
        fixGenres() // fix genres
        fixPosters() // fix posters
    }

    private fun fixSource() {
        source = source?.lowercase(Locale.ROOT) ?: "lampa"
    }

    private fun fixType() {
        type = type?.lowercase(Locale.ROOT) ?: ""
        when {
            type == "scripted" -> type =
                if (release_date.isNullOrEmpty() || !name.isNullOrEmpty()) "tv" else "movie"

            type?.contains("miniseries", true) == true || type?.contains(
                "news",
                true
            ) == true -> type = "tv"

            type.isNullOrEmpty() -> type =
                if (release_date.isNullOrEmpty() || !name.isNullOrEmpty()) "tv" else "movie"
        }
    }

    private fun fixGenres() {
        if (!genre_ids.isNullOrEmpty() && genres.isNullOrEmpty()) {
            genres = genre_ids.mapNotNull { id ->
                id.toIntOrNull()?.let { genreId ->
                    Genre(genreId.toString(), TMDB.genres[genreId] ?: "", "")
                }
            }
        }
    }

    private fun fixPosters() {
        if (!poster_path.isNullOrEmpty() && img.isNullOrEmpty()) {
            img = TMDB.imageUrl(poster_path).replace("original", "w342")
        }
        if (!backdrop_path.isNullOrEmpty() && background_image.isNullOrEmpty()) {
            background_image = TMDB.imageUrl(backdrop_path).replace("original", "w1280")
        }
    }

    override fun toString(): String {
        val displayName = when {
            !name.isNullOrEmpty() -> name
            !title.isNullOrEmpty() -> title
            else -> "-"
        }
        return "LampaCard(source:$source id:$id type:$type $displayName $img)"
    }
}

data class LampaRec(
    val id: String, // "84958"
    val name: String?,
    val original_name: String?,
    val title: String?,
    val original_title: String?,
    val original_language: String?, // "en"
    val overview: String?,
    val poster_path: String?, // "/82HaUMIagdh5PLflUOVrHn5GsI9.jpg"
    val backdrop_path: String?, // "/aRKQdF6AGbhnF9IAyJbte5epH5R.jpg"
    val media_type: String?, // "movie" | "tv"
    val genre_ids: List<String>?, // [28,80,53],
    val popularity: Double?, // 126.38
    val origin_country: List<String>?, // [ "GB" ]
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
        val genres = genre_ids?.mapNotNull { id ->
            val genreId = id.toIntOrNull()
            if (genreId != null) Genre(id, TMDB.genres[genreId] ?: "", "") else null
        }
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
            original_language,
            overview,
            poster_path,
            backdrop_path,
            img,
            backdrop,
            genre_ids,
            genres,
            popularity,
            origin_country,
            null,
            null,
            vote_average,
            vote_count,
            null,
            null,
            null,
            null,
            null,
            release_date,
            first_air_date,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
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