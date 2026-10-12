package top.rootu.lampa.channels

import top.rootu.lampa.models.LampaCard
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.net.URI
import java.util.concurrent.Executor

internal data class PluginChannelRequest(val name: String, val title: String?, val items: List<LampaCard>) {
    companion object {
        private val idPattern = Regex("[a-z0-9._-]{1,64}")
        private val cardIdPattern = Regex("[A-Za-z0-9._:-]{1,256}")
        private val sourcePattern = Regex("[A-Za-z0-9._-]{1,64}")
        private val gson = Gson()
        private val stringFields = listOf("source", "type", "name", "title", "original_name", "original_title",
            "overview", "img", "poster_path", "backdrop_path", "background_image", "original_language",
            "release_year", "release_date", "first_air_date")
        private val integerFields = listOf("runtime", "number_of_seasons", "number_of_episodes")

        fun clear(id: String?): PluginChannelRequest? = id?.takeIf { idPattern.matches(it) }
            ?.let { PluginChannelRequest("plugin:$it", null, emptyList()) }

        fun parse(json: String?): PluginChannelRequest? {
            if (json == null || json.length > 1_048_576 || !hasSafeDepth(json)) return null
            return try {
                val reader = JsonReader(StringReader(json)).apply { isLenient = false }
                val root = reader.use {
                    val value = gson.getAdapter(JsonElement::class.java).read(it)
                    require(it.peek() == JsonToken.END_DOCUMENT)
                    require(value.isJsonObject)
                    value.asJsonObject
                }
                val id = string(root, "id") ?: return null
                val name = clear(id)?.name ?: return null
                val title = string(root, "title")?.takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
                    ?: return null
                val input = root.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
                val cards = input.mapNotNull { item ->
                    require(item.isJsonObject)
                    val source = item.asJsonObject
                    val safe = JsonObject()
                    stringFields.forEach { field -> string(source, field)?.let { safe.addProperty(field, it) } }
                    integerFields.forEach { field ->
                        source.get(field)?.takeUnless { it.isJsonNull }?.let {
                            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                            val number = it.asBigDecimal.intValueExact()
                            require(number >= 0 && (field != "runtime" || number <= Int.MAX_VALUE / 60000))
                            safe.addProperty(field, number)
                        }
                    }
                    source.get("vote_average")?.takeUnless { it.isJsonNull }?.let {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                        require(it.asDouble.isFinite() && it.asDouble in 0.0..10.0)
                        safe.add("vote_average", it)
                    }
                    val cardId = source.get("id")?.takeUnless { it.isJsonNull }?.let {
                        require(it.isJsonPrimitive && (it.asJsonPrimitive.isString || it.asJsonPrimitive.isNumber))
                        it.asString
                    }
                    require(string(source, "type") in listOf("movie", "tv"))
                    listOf("img", "background_image").forEach { field ->
                        string(source, field)?.takeIf { it.isNotBlank() }?.let {
                            require(URI(it).scheme?.lowercase() in listOf("http", "https"))
                        }
                    }
                    if (cardId.isNullOrBlank() || (string(source, "name").isNullOrBlank() &&
                            string(source, "title").isNullOrBlank())) return@mapNotNull null
                    require(cardIdPattern.matches(cardId))
                    string(source, "source")?.let { require(sourcePattern.matches(it)) }
                    safe.addProperty("id", cardId)
                    if (string(source, "name").isNullOrBlank()) safe.remove("name")
                    gson.fromJson(safe, LampaCard::class.java)
                }.distinctBy { it.id }
                if (!input.isEmpty && cards.isEmpty()) return null
                PluginChannelRequest(name, title, cards)
            } catch (_: Exception) {
                null
            }
        }

        // Bound recursion before Gson reads even unknown extension fields.
        private fun hasSafeDepth(json: String): Boolean {
            var depth = 0
            var quoted = false
            var escaped = false
            json.forEach { char ->
                if (quoted) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> if (++depth > 64) return false
                    '}', ']' -> if (--depth < 0) return false
                }
            }
            return depth == 0 && !quoted
        }

        private fun string(obj: JsonObject, field: String): String? {
            val value = obj.get(field)?.takeUnless { it.isJsonNull } ?: return null
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
    }
}

internal class PluginChannelPublisher(
    private val executor: Executor,
    private val available: () -> Boolean,
    private val write: (PluginChannelRequest) -> Unit,
    private val onFailure: (Exception) -> Unit
) {
    @Synchronized
    fun publish(json: String?): Boolean = PluginChannelRequest.parse(json)?.let(::submit) ?: false

    @Synchronized
    fun clear(id: String?): Boolean = PluginChannelRequest.clear(id)?.let(::submit) ?: false

    private fun submit(request: PluginChannelRequest): Boolean {
        return try {
            if (!available()) return false
            executor.execute {
                try {
                    check(available())
                    write(request)
                } catch (error: Exception) {
                    onFailure(error)
                }
            }
            true
        } catch (error: Exception) {
            onFailure(error)
            false
        }
    }
}
