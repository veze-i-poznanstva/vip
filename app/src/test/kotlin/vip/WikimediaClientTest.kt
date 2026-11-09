package vip

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WikimediaClientTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()
    private val userAgents = mutableListOf<String?>()
    private var status = 200
    private var body = ""

    private fun base() = "http://localhost:${server.address.port}"
    private fun client() = WikimediaClient(wikipediaEndpoint = { lang -> "${base()}/$lang/api" }, wikidataEndpoint = "${base()}/wikidata/api")

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { ex ->
            requests.add(ex.requestURI.toString())
            userAgents.add(ex.requestHeaders.getFirst("User-Agent"))
            respond(ex, status, body)
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun respond(ex: HttpExchange, code: Int, text: String) {
        val bytes = text.toByteArray()
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
    }

    private val found = """{"batchcomplete":true,"query":{"normalized":[{"fromencoded":false,"from":"Синиша_Мали","to":"Синиша Мали"}],"pages":[{"pageid":123,"ns":0,"title":"Синиша Мали","pageprops":{"wikibase_item":"Q16935101"}}]}}"""
    private val noItem = """{"batchcomplete":true,"query":{"pages":[{"pageid":456,"ns":0,"title":"Ivan Bošnjak (privrednik)"}]}}"""
    private val missing = """{"batchcomplete":true,"query":{"pages":[{"ns":0,"title":"Nema","missing":true}]}}"""

    @Test
    fun `an article with a Wikidata item is found (also when the title was normalised)`() {
        body = found
        assertEquals(ArticleItem.Found("Q16935101"), client().itemOfArticle("https://sr.wikipedia.org/wiki/Синиша_Мали"))
    }

    @Test
    fun `an article nobody linked to Wikidata has no item`() {
        body = noItem
        assertEquals(ArticleItem.NoItem, client().itemOfArticle("https://sr.wikipedia.org/wiki/Ivan_Bošnjak_(privrednik)"))
    }

    @Test
    fun `a missing article is reported as missing`() {
        body = missing
        assertEquals(ArticleItem.MissingPage, client().itemOfArticle("https://sr.wikipedia.org/wiki/Nema"))
    }

    @Test
    fun `a wikibase item that is not a Q number counts as no item`() {
        body = """{"query":{"pages":[{"pageid":1,"pageprops":{"wikibase_item":"Q0"}}]}}"""
        assertEquals(ArticleItem.NoItem, client().itemOfArticle("https://sr.wikipedia.org/wiki/X"))
    }

    @Test
    fun `an answer without pages throws`() {
        body = """{"query":{"pages":[]}}"""
        assertFailsWith<WikidataException> { client().itemOfArticle("https://sr.wikipedia.org/wiki/X") }
    }

    @Test
    fun `HTTP 500 throws`() {
        status = 500; body = "boom"
        val e = assertFailsWith<WikidataException> { client().itemOfArticle("https://sr.wikipedia.org/wiki/X") }
        assertTrue(e.message!!.contains("HTTP 500"), e.message)
    }

    @Test
    fun `HTTP 429 mentions the rate limit`() {
        status = 429; body = "slow down"
        val e = assertFailsWith<WikidataException> { client().itemOfArticle("https://sr.wikipedia.org/wiki/X") }
        assertTrue(e.message!!.contains("rate limit"), e.message)
    }

    @Test
    fun `an answer that is not JSON throws`() {
        body = "<html>maintenance</html>"
        val e = assertFailsWith<WikidataException> { client().itemOfArticle("https://sr.wikipedia.org/wiki/X") }
        assertTrue(e.message!!.contains("not JSON: <html>maintenance"), e.message)
    }

    @Test
    fun `an address that is not a Wikipedia article throws without any request`() {
        for (bad in listOf("https://example.org/wiki/X", "http://sr.wikipedia.org/wiki/X", "https://sr.wikipedia.org/w/index.php", "https://sr.wikipedia.org/wiki/")) {
            val e = assertFailsWith<WikidataException> { client().itemOfArticle(bad) }
            assertTrue(e.message!!.contains("is not a Wikipedia article address"), e.message)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `every request sends a User-Agent and the language picks the endpoint`() {
        body = found
        client().itemOfArticle("https://sr.wikipedia.org/wiki/X")
        assertTrue(requests[0].startsWith("/sr/api?action=query"), requests[0])
        body = """{"entities":{"Q42":{"id":"Q42"}}}"""
        client().itemExists("Q42")
        assertEquals(2, userAgents.size)
        userAgents.forEach { assertTrue(it!!.startsWith("VIP/1.0"), it) }
    }

    @Test
    fun `a Cyrillic title is sent percent-encoded once`() {
        body = found
        client().itemOfArticle("https://sr.wikipedia.org/wiki/Михаило_Јовановић_(политичар)")
        val expected = java.net.URLEncoder.encode("Михаило_Јовановић_(политичар)", "UTF-8")
        assertTrue(requests[0].contains("titles=$expected&"), requests[0])
        assertTrue(!requests[0].contains("%25"), requests[0])
    }

    @Test
    fun `an already percent-encoded title is decoded and encoded once`() {
        body = noItem
        client().itemOfArticle("https://sr.wikipedia.org/wiki/Ivan_Bo%C5%A1njak_(privrednik)")
        assertTrue(requests[0].contains("titles=Ivan_Bo%C5%A1njak_%28privrednik%29&"), requests[0])
    }

    @Test
    fun `a plus sign in a title stays a plus sign`() {
        body = noItem
        client().itemOfArticle("https://en.wikipedia.org/wiki/C++")
        assertTrue(requests[0].contains("titles=C%2B%2B&"), requests[0])
    }

    @Test
    fun `an existing item exists`() {
        body = """{"entities":{"Q42":{"type":"item","id":"Q42","pageid":138}},"success":1}"""
        assertTrue(client().itemExists("Q42"))
        assertTrue(requests[0].contains("action=wbgetentities&ids=Q42"), requests[0])
    }

    @Test
    fun `a missing item does not exist`() {
        body = """{"entities":{"Q999999999999":{"id":"Q999999999999","missing":""}},"success":1}"""
        assertEquals(false, client().itemExists("Q999999999999"))
    }

    @Test
    fun `an unexpected entity answer throws`() {
        body = """{"entities":{"Q42":{}}}"""
        assertFailsWith<WikidataException> { client().itemExists("Q42") }
        body = """{"success":0}"""
        assertFailsWith<WikidataException> { client().itemExists("Q42") }
    }

    @Test
    fun `a bad item id throws without any request`() {
        for (bad in listOf("42", "Q0", "Q01", "q42", "Q42&x=1", "")) {
            assertFailsWith<WikidataException> { client().itemExists(bad) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an unreachable server throws a WikidataException`() {
        val c = WikimediaClient(wikidataEndpoint = "http://localhost:1/api", timeoutMs = 500)
        assertFailsWith<WikidataException> { c.itemExists("Q42") }
    }
}
