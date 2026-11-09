package vip

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** What the tools need from Wikipedia and Wikidata. Implemented by [WikimediaClient], replaced by stubs in tests. */
interface ItemLookup {
    /** The Wikidata item of the Wikipedia article at [articleUrl]. */
    fun itemOfArticle(articleUrl: String): ArticleItem

    /** Whether item [id] (Q…) exists on Wikidata. */
    fun itemExists(id: String): Boolean
}

sealed class ArticleItem {
    data class Found(val id: String) : ArticleItem()
    /** The article exists but nobody linked it to Wikidata. */
    object NoItem : ArticleItem()
    /** There is no such article. */
    object MissingPage : ArticleItem()
}

class WikidataException(message: String) : RuntimeException(message)

/** Wikipedia and Wikidata over their MediaWiki APIs. The endpoints are parameters so tests can use a local server. */
class WikimediaClient(
    private val wikipediaEndpoint: (String) -> String = { lang -> "https://$lang.wikipedia.org/w/api.php" },
    private val wikidataEndpoint: String = "https://www.wikidata.org/w/api.php",
    private val timeoutMs: Int = 30000,
    private val userAgent: String = "VIP/1.0 (https://github.com/veze-i-poznanstva/vip)"
) : ItemLookup {

    override fun itemOfArticle(articleUrl: String): ArticleItem {
        val match = ARTICLE.matchEntire(articleUrl)
            ?: throw WikidataException("$articleUrl is not a Wikipedia article address")
        val (lang, rawTitle) = match.destructured
        val title = try { URLDecoder.decode(rawTitle.replace("+", "%2B"), "UTF-8") } catch (e: IllegalArgumentException) {
            throw WikidataException("$articleUrl is not a Wikipedia article address")
        }
        val answer = get("${wikipediaEndpoint(lang)}?action=query&titles=${URLEncoder.encode(title, "UTF-8")}" +
            "&prop=pageprops&ppprop=wikibase_item&redirects=1&converttitles=1&format=json&formatversion=2")
        val page = answer.getAsJsonObject("query")?.getAsJsonArray("pages")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw WikidataException("Wikipedia answered without a page for $articleUrl")
        if (page["missing"]?.takeIf { it.isJsonPrimitive }?.asBoolean == true) return ArticleItem.MissingPage
        val id = page.getAsJsonObject("pageprops")?.get("wikibase_item")?.takeIf { it.isJsonPrimitive }?.asString
        return if (id != null && ITEM_ID.matches(id)) ArticleItem.Found(id) else ArticleItem.NoItem
    }

    override fun itemExists(id: String): Boolean {
        if (!ITEM_ID.matches(id)) throw WikidataException("$id is not a Wikidata item id")
        val answer = get("$wikidataEndpoint?action=wbgetentities&ids=$id&props=info&format=json")
        val entity = answer.getAsJsonObject("entities")?.getAsJsonObject(id)
            ?: throw WikidataException("Wikidata answered without an entity for $id")
        return when {
            entity.has("missing") -> false
            entity.has("id") -> true
            else -> throw WikidataException("Wikidata answered unexpectedly for $id")
        }
    }

    private fun get(address: String): JsonObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code == 429) throw WikidataException("Wikipedia API is rate limiting us (HTTP 429); wait a few minutes and retry")
            if (code != 200) throw WikidataException("Wikipedia API answered HTTP $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return try { JsonParser.parseString(body).asJsonObject } catch (e: Exception) {
                throw WikidataException("Wikipedia API answered with something that is not JSON: ${body.take(80)}")
            }
        } catch (e: java.io.IOException) {
            throw WikidataException("could not reach the Wikipedia API: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val ARTICLE = Regex("^https://([a-z-]+)\\.wikipedia\\.org/wiki/(.+)$")
        val ITEM_ID = Regex("^Q[1-9][0-9]*$")
    }
}
