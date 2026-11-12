package vip

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaybackClientTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()

    private fun base() = "http://localhost:${server.address.port}"
    private fun client() = WaybackClient(webBase = base(), availabilityBase = "${base()}/available")

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/available") { ex ->
            requests.add(ex.requestURI.toString())
            val query = ex.requestURI.rawQuery ?: ""
            val requested = java.net.URLDecoder.decode(query.removePrefix("url="), "UTF-8")
            val body = when {
                query.contains("known.example") -> """{"url":"$requested","archived_snapshots":{"closest":{"status":"200","available":true,"url":"http://web.archive.org/web/20261020063300/$requested","timestamp":"20261020063300"}}}"""
                query.contains("redirected.example") -> """{"url":"https://redirected.example/news/a","archived_snapshots":{"closest":{"status":"200","available":true,"url":"http://web.archive.org/web/20261020063300/https://redirected.example/404.php","timestamp":"20261020063300"}}}"""
                query.contains("moved.example") -> """{"url":"https://moved.example/firm/1","archived_snapshots":{"closest":{"status":"200","available":true,"url":"http://web.archive.org/web/20261020063300/https://newname.example/firm/1","timestamp":"20261020063300"}}}"""
                query.contains("lowered.example") -> """{"url":"https://lowered.example/vesti/a1-Nova-Vlada/","archived_snapshots":{"closest":{"status":"200","available":true,"url":"http://web.archive.org/web/20261020063300/https://lowered.example/vesti/a1-nova-vlada/","timestamp":"20261020063300"}}}"""
                query.contains("captured404.example") -> """{"url":"https://captured404.example/x","archived_snapshots":{"closest":{"status":"404","available":false,"url":"http://web.archive.org/web/20261020063300/https://captured404.example/x","timestamp":"20261020063300"}}}"""
                query.contains("nokey.example") -> """{"url":"x"}"""
                query.contains("maintenance.example") -> "<html>archive.org is down for maintenance</html>"
                query.contains("notime.example") -> """{"archived_snapshots":{"closest":{"status":"200","available":true}}}"""
                query.contains("slowapi.example") -> { Thread.sleep(400); """{"archived_snapshots":{}}""" }
                query.contains("broken.example") -> null
                query.contains("busy.example") -> "429"
                else -> """{"url":"x","archived_snapshots":{}}"""
            }
            when (body) {
                null -> respond(ex, 500, "boom")
                "429" -> respond(ex, 429, "Too Many Requests")
                else -> respond(ex, 200, body)
            }
        }
        server.createContext("/save/") { ex ->
            requests.add(ex.requestURI.toString())
            val target = ex.requestURI.toString().removePrefix("/save/")
            when {
                target.contains("busy.example") -> respond(ex, 429, "slow down")
                target.contains("fail500.example") -> respond(ex, 500, "boom")
                target.contains("fail403.example") -> respond(ex, 403, "no")
                target.contains("job.example") -> { ex.responseHeaders.add("Location", "/save/status/abc"); respond(ex, 302, "") }
                target.contains("relative.example") -> { ex.responseHeaders.add("Location", "/web/20261030101010/$target"); respond(ex, 302, "") }
                target.contains("nowhere.example") -> respond(ex, 200, "ok but no header")
                target.contains("gone.example") -> { ex.responseHeaders.add("Content-Location", "/web/20261030133033/https://gone.example/404.php"); respond(ex, 200, "") }
                target.contains("www.plain.example") -> { ex.responseHeaders.add("Content-Location", "/web/20261030133033/http://plain.example/p"); respond(ex, 200, "") }
                target.contains("home.example") -> { ex.responseHeaders.add("Content-Location", "/web/20261030133033/https://home.example/"); respond(ex, 200, "") }
                target.contains("slow.example") -> { Thread.sleep(400); ex.responseHeaders.add("Content-Location", "/web/20261030120000/$target"); respond(ex, 200, "") }
                else -> { ex.responseHeaders.add("Content-Location", "/web/20261030090000/$target"); respond(ex, 200, "") }
            }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun respond(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `lookup returns the closest snapshot with its date`() {
        val snapshot = client().lookup("https://known.example/p")
        assertNotNull(snapshot)
        assertEquals("http://web.archive.org/web/20261020063300/https://known.example/p", snapshot.url)
        assertEquals("2026-10-20", snapshot.date.toString())
    }

    @Test
    fun `lookup returns null when the archive has never seen the page`() {
        assertNull(client().lookup("https://nothing.example/q"))
    }

    @Test
    fun `lookup encodes the page address as a query parameter`() {
        client().lookup("https://known.example/a b?c=d&e")
        assertTrue(requests.any { it.contains("url=https%3A%2F%2Fknown.example%2Fa+b%3Fc%3Dd%26e") }, requests.toString())
    }

    @Test
    fun `lookup fails loudly when the API is down`() {
        val e = assertFailsWith<ArchiveException> { client().lookup("https://broken.example/x") }
        assertTrue(e.message!!.contains("500"))
    }

    @Test
    fun `lookup reports rate limiting as a distinct error`() {
        val e = assertFailsWith<ArchiveException> { client().lookup("https://busy.example/x") }
        assertTrue(e.message!!.contains("rate limit"), e.message)
    }

    @Test
    fun `save returns the snapshot named by Content-Location`() {
        val snapshot = client().save("https://site.example/page")
        assertEquals("${base()}/web/20261030090000/https://site.example/page", snapshot.url)
        assertEquals("20261030090000", snapshot.timestamp)
    }

    @Test
    fun `save follows a redirect Location to the snapshot`() {
        val snapshot = client().save("https://relative.example/page")
        assertEquals("20261030101010", snapshot.timestamp)
        assertTrue(snapshot.url.endsWith("/web/20261030101010/https://relative.example/page"))
    }

    @Test
    fun `save reports rate limiting as a distinct error`() {
        val e = assertFailsWith<ArchiveException> { client().save("https://busy.example/page") }
        assertTrue(e.message!!.contains("rate limit"), e.message)
    }

    @Test
    fun `save waits longer for the archive than a lookup does`() {
        // Save Page Now routinely takes a minute; the availability API answers in a second.
        val client = WaybackClient(webBase = base(), availabilityBase = "${base()}/available", timeoutMs = 100, saveTimeoutMs = 5000)
        assertEquals("20261030120000", client.save("https://slow.example/page").timestamp)
    }

    @Test
    fun `save refuses a snapshot of a different page, such as a site's 404 page`() {
        val e = assertFailsWith<ArchiveException> { client().save("https://gone.example/news/article/") }
        assertTrue(e.message!!.contains("404.php"), e.message)
    }

    @Test
    fun `save accepts a snapshot that differs only in scheme or www`() {
        assertEquals("20261030133033", client().save("https://www.plain.example/p").timestamp)
    }

    @Test
    fun `save fails when the archive gives no snapshot location`() {
        assertFailsWith<ArchiveException> { client().save("https://nowhere.example/page") }
    }

    @Test
    fun `lookup refuses a snapshot of a different page, such as a site's 404 page`() {
        val e = assertFailsWith<ArchiveException> { client().lookup("https://redirected.example/news/a") }
        assertTrue(e.message!!.contains("404.php"), e.message)
    }

    @Test
    fun `lookup accepts a snapshot captured under a new host name when the path is unchanged`() {
        val snapshot = client().lookup("https://moved.example/firm/1")
        assertNotNull(snapshot)
        assertTrue(snapshot.url.endsWith("https://newname.example/firm/1"))
    }

    @Test
    fun `lookup accepts a snapshot whose address differs only in letter case, as the archive indexes it`() {
        val snapshot = client().lookup("https://lowered.example/vesti/a1-Nova-Vlada/")
        assertNotNull(snapshot)
        assertTrue(snapshot.url.endsWith("https://lowered.example/vesti/a1-nova-vlada/"))
    }

    @Test
    fun `lookup treats a snapshot whose status is not 2xx as no snapshot`() {
        assertNull(client().lookup("https://captured404.example/x"))
    }

    @Test
    fun `save refuses a snapshot of the site's home page when a deeper page was requested`() {
        val e = assertFailsWith<ArchiveException> { client().save("https://home.example/news/article-1") }
        assertTrue(e.message!!.contains("different page"), e.message)
    }

    @Test
    fun `save ignores a fragment when comparing the captured page with the requested one`() {
        assertEquals("20261030090000", client().save("https://site.example/page#section-2").timestamp)
    }

    @Test
    fun `save fails loudly when Save Page Now answers 4xx or 5xx`() {
        val e500 = assertFailsWith<ArchiveException> { client().save("https://fail500.example/p") }
        assertTrue(e500.message!!.contains("500"), e500.message)
        val e403 = assertFailsWith<ArchiveException> { client().save("https://fail403.example/p") }
        assertTrue(e403.message!!.contains("403"), e403.message)
    }

    @Test
    fun `save rejects a location that is not a snapshot address`() {
        val e = assertFailsWith<ArchiveException> { client().save("https://job.example/p") }
        assertTrue(e.message!!.contains("Unexpected snapshot address"), e.message)
    }

    @Test
    fun `lookup returns null when the API omits archived_snapshots`() {
        assertNull(client().lookup("https://nokey.example/p"))
    }

    @Test
    fun `lookup on a non-JSON answer fails as an ArchiveException naming the problem`() {
        val e = assertFailsWith<ArchiveException> { client().lookup("https://maintenance.example/p") }
        assertTrue(e.message!!.contains("not JSON"), e.message)
    }

    @Test
    fun `lookup on a snapshot without a url fails as an ArchiveException`() {
        val e = assertFailsWith<ArchiveException> { client().lookup("https://notime.example/p") }
        assertTrue(e.message!!.contains("without a url"), e.message)
    }

    @Test
    fun `lookup gives up on a slow API after timeoutMs`() {
        val client = WaybackClient(webBase = base(), availabilityBase = "${base()}/available", timeoutMs = 100, saveTimeoutMs = 5000)
        assertFailsWith<java.net.SocketTimeoutException> { client.lookup("https://slowapi.example/p") }
    }

    @Test
    fun `a snapshot timestamp that is not fourteen digits is rejected as an ArchiveException`() {
        assertFailsWith<ArchiveException> { ArchiveSnapshot("u", "2026").date }
        assertFailsWith<ArchiveException> { ArchiveSnapshot("u", "2026-10-30T00:00").date }
    }
}
