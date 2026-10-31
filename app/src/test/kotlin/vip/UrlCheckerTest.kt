package vip

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Drives the live URL checker against a local HTTP server, so the behaviour is tested without the internet. */
class UrlCheckerTest {

    private lateinit var server: HttpServer
    private val seenUserAgents = mutableListOf<String>()
    private val seenMethods = mutableListOf<String>()

    private fun base() = "http://localhost:${server.address.port}"
    private fun check(url: String) = checkUrlOverHttp(url, HostPolicy.AnyHost)

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/ok") { ex -> record(ex); respond(ex, 200) }
        server.createContext("/missing") { ex -> record(ex); respond(ex, 404) }
        server.createContext("/head-forbidden") { ex ->
            record(ex)
            if (ex.requestMethod == "HEAD") respond(ex, 403) else respond(ex, 200)
        }
        server.createContext("/moved") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "${base()}/ok")
            respond(ex, 302)
        }
        server.createContext("/moved-relative") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "/ok")
            respond(ex, 301)
        }
        server.createContext("/404.php") { ex -> record(ex); respond(ex, 200) }
        server.createContext("/gone-article") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "/404.php")
            respond(ex, 302)
        }
        server.createContext("/to-home") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "/")
            respond(ex, 302)
        }
        server.createContext("/") { ex -> record(ex); respond(ex, 200) }
        server.createContext("/to-private") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "http://127.0.0.1:1/internal")
            respond(ex, 302)
        }
        for (i in 1..5) server.createContext("/hop$i") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", if (i == 5) "/ok" else "/hop${i + 1}")
            respond(ex, 302)
        }
        server.createContext("/loop") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", "${base()}/loop")
            respond(ex, 302)
        }
        server.createContext("/no-content") { ex -> record(ex); respond(ex, 204) }
        server.createContext("/redir") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", ex.requestURI.rawQuery.removePrefix("to="))
            respond(ex, 302)
        }
        for (i in 1..6) server.createContext("/six$i") { ex ->
            record(ex)
            ex.responseHeaders.add("Location", if (i == 6) "/ok" else "/six${i + 1}")
            respond(ex, 302)
        }
        server.createContext("/both-fail") { ex ->
            record(ex)
            respond(ex, if (ex.requestMethod == "HEAD") 405 else 410)
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun record(ex: HttpExchange) {
        ex.requestHeaders.getFirst("User-Agent")?.let { seenUserAgents.add(it) }
        seenMethods.add(ex.requestMethod)
    }

    private fun respond(ex: HttpExchange, status: Int) {
        if (ex.requestMethod == "HEAD") ex.sendResponseHeaders(status, -1) else {
            val body = "x".toByteArray()
            ex.sendResponseHeaders(status, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
    }

    @Test
    fun `a page answering 200 is accessible`() {
        val (ok, status) = check("${base()}/ok")
        assertTrue(ok, status)
        assertEquals("OK", status)
    }

    @Test
    fun `a page answering 404 is not accessible and reports the code`() {
        val (ok, status) = check("${base()}/missing")
        assertFalse(ok)
        assertEquals("HTTP 404", status)
    }

    @Test
    fun `a site that refuses HEAD but serves GET is accessible`() {
        val (ok, status) = check("${base()}/head-forbidden")
        assertTrue(ok, status)
    }

    @Test
    fun `a redirect to an absolute location is followed`() {
        val (ok, status) = check("${base()}/moved")
        assertTrue(ok, status)
    }

    @Test
    fun `a redirect to a relative location is followed`() {
        val (ok, status) = check("${base()}/moved-relative")
        assertTrue(ok, status)
    }

    @Test
    fun `a redirect onto the site's error page is not accessible even if that page answers 200`() {
        val (ok, status) = check("${base()}/gone-article")
        assertFalse(ok)
        assertTrue(status.contains("404.php"), status)
    }

    @Test
    fun `a redirect loop gives up and is not accessible`() {
        val (ok, status) = check("${base()}/loop")
        assertFalse(ok)
        assertTrue(status.contains("redirect"), status)
    }

    @Test
    fun `an unreachable host is not accessible and reports the error`() {
        val (ok, status) = check("http://localhost:1/nothing")
        assertFalse(ok)
        assertTrue(status.startsWith("Error"), status)
    }

    @Test
    fun `requests identify the validator in the User-Agent header`() {
        check("${base()}/ok")
        assertTrue(seenUserAgents.isNotEmpty())
        assertTrue(seenUserAgents.all { it.startsWith("VIP-VeracityValidator/") }, seenUserAgents.toString())
    }

    @Test
    fun `a redirect from a deep page onto the site's home page is not accessible`() {
        val (ok, status) = check("${base()}/to-home/news/2019/article")
        assertFalse(ok)
        assertTrue(status.contains("home page"), status)
    }

    @Test
    fun `five redirects are followed`() {
        val (ok, status) = check("${base()}/hop1")
        assertTrue(ok, status)
    }

    @Test
    fun `a URL with a scheme other than http or https is not accessible and says so`() {
        val (ok, status) = check("file:///etc/passwd")
        assertFalse(ok)
        assertTrue(status.contains("scheme"), status)
    }

    @Test
    fun `a 204 answer is accessible`() {
        val (ok, status) = check("${base()}/no-content")
        assertTrue(ok, status)
    }

    @Test
    fun `six redirects are too many`() {
        val (ok, status) = check("${base()}/six1")
        assertFalse(ok)
        assertEquals("too many redirects", status)
    }

    @Test
    fun `redirects onto not-found and error pages are refused`() {
        for (target in listOf("/not-found", "/NotFound/", "/Error.aspx", "/en/not_found.html")) {
            val (ok, status) = check("${base()}/redir?to=$target")
            assertFalse(ok, target)
            assertTrue(status.contains("error page"), "$target: $status")
        }
    }

    @Test
    fun `a redirect onto an article whose path merely contains 404 or error is followed`() {
        for (target in listOf("/404-ways-to-save/", "/news/errors-of-2024/")) {
            val (ok, status) = check("${base()}/redir?to=$target")
            assertTrue(ok, "$target: $status")
        }
    }

    @Test
    fun `a page accepting HEAD is not fetched twice`() {
        check("${base()}/ok")
        assertEquals(listOf("HEAD"), seenMethods)
    }

    @Test
    fun `when HEAD and GET both fail the GET status is reported`() {
        val (ok, status) = check("${base()}/both-fail")
        assertFalse(ok)
        assertEquals("HTTP 410", status)
        assertEquals(listOf("HEAD", "GET"), seenMethods)
    }

    @Test
    fun `under the public host policy a loopback or private address is refused without connecting`() {
        val (ok, status) = checkUrlOverHttp("${base()}/ok", HostPolicy.PublicHostsOnly)
        assertFalse(ok)
        assertTrue(status.contains("not a public host"), status)
        assertTrue(seenUserAgents.isEmpty(), "no request must be made")
    }

    @Test
    fun `under the public host policy a redirect to a private address is refused`() {
        val (ok, status) = checkUrlOverHttp("${base()}/to-private", HostPolicy.PublicHostsOnly.butAllowing("localhost"))
        assertFalse(ok)
        assertTrue(status.contains("not a public host"), status)
    }
}

/** Addresses with non-ASCII hosts or paths must reach the wire the way browsers send them. */
class AsciiFormTest {
    @Test
    fun `an internationalised host becomes punycode and non-ASCII path and query characters are percent-encoded`() {
        assertEquals(
            "https://xn--e1afmkfd.xn--90a3ac/%D0%BF%D1%83%D1%82/a?q=%D1%9B#x",
            asciiForm("https://пример.срб/пут/a?q=ћ#x")
        )
    }

    @Test
    fun `an ASCII address is returned unchanged, existing percent-encoding included`() {
        assertEquals("https://www.istinomer.rs/akter/sini%C5%A1a-mali/?a=1", asciiForm("https://www.istinomer.rs/akter/sini%C5%A1a-mali/?a=1"))
    }
}
