package vip

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** An archived copy of a web page. [timestamp] is the archive's 14-digit form, yyyyMMddHHmmss. */
data class ArchiveSnapshot(val url: String, val timestamp: String) {
    val date: LocalDate get() {
        if (!timestamp.matches(Regex("\\d{14}"))) throw ArchiveException("snapshot timestamp '$timestamp' is not yyyyMMddHHmmss")
        return LocalDate.parse(timestamp.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
    }
}

/** What the project needs from a web archive. Implemented by [WaybackClient], replaced by stubs in tests. */
interface Wayback {
    /** The most recent snapshot of [url], or null if the archive has never seen it. */
    fun lookup(url: String): ArchiveSnapshot?

    /** Asks the archive to capture [url] now and returns the new snapshot. */
    fun save(url: String): ArchiveSnapshot
}

class ArchiveException(message: String) : RuntimeException(message)

/**
 * Internet Archive client. [webBase] serves snapshots and Save Page Now, [availabilityBase] is the
 * availability API. Both are parameters so tests can point them at a local server.
 *
 * [timeoutMs] bounds a lookup; [saveTimeoutMs] bounds a capture, which the archive performs
 * synchronously and which routinely takes a minute for a slow or heavy page.
 */
class WaybackClient(
    private val webBase: String = "https://web.archive.org",
    private val availabilityBase: String = "https://archive.org/wayback/available",
    private val timeoutMs: Int = 30000,
    private val saveTimeoutMs: Int = 120000
) : Wayback {

    override fun lookup(url: String): ArchiveSnapshot? {
        val connection = open("$availabilityBase?url=${URLEncoder.encode(url, "UTF-8")}")
        try {
            val code = connection.responseCode
            failIfRateLimited(code)
            if (code != 200) throw ArchiveException("availability API answered HTTP $code")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val answer = try { JsonParser.parseString(json).asJsonObject } catch (e: Exception) {
                throw ArchiveException("availability API answered with something that is not JSON: ${json.take(80)}")
            }
            val closest = answer.getAsJsonObject("archived_snapshots")?.getAsJsonObject("closest") ?: return null
            if (closest["status"]?.asString?.startsWith("2") == false) return null
            val snapshotUrl = closest["url"]?.takeIf { it.isJsonPrimitive }?.asString
                ?: throw ArchiveException("availability API listed a snapshot without a url for $url")
            return snapshotOf(url, snapshotUrl)
        } finally {
            connection.disconnect()
        }
    }

    override fun save(url: String): ArchiveSnapshot {
        val connection = open("$webBase/save/$url", readTimeoutMs = saveTimeoutMs)
        try {
            val code = connection.responseCode
            failIfRateLimited(code)
            if (code >= 400) throw ArchiveException("Save Page Now answered HTTP $code for $url")
            val location = connection.getHeaderField("Content-Location") ?: connection.getHeaderField("Location")
                ?: throw ArchiveException("Save Page Now gave no snapshot location for $url")
            return snapshotOf(url, URL(URL(webBase), location).toString())
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The snapshot at [snapshotUrl], provided it really captured [requested]. The archive follows
     * redirects, so a page that moved to a new host name is accepted as long as its path is the
     * same, but a redirect onto the site's home page or an error page is not a copy of the page.
     */
    private fun snapshotOf(requested: String, snapshotUrl: String): ArchiveSnapshot {
        val match = Regex("/web/(\\d{14})/(.*)").find(snapshotUrl)
            ?: throw ArchiveException("Unexpected snapshot address $snapshotUrl")
        val (timestamp, capturedPage) = match.destructured
        if (!captures(requested, capturedPage)) {
            throw ArchiveException("the archive captured a different page for $requested: $capturedPage (the site probably redirected it, e.g. to an error page)")
        }
        return ArchiveSnapshot(snapshotUrl, timestamp)
    }

    private fun captures(requested: String, captured: String): Boolean {
        val r = normalise(requested)
        val c = normalise(captured)
        if (r == c) return true
        val requestedPath = r.substringAfter('/', "")
        val capturedPath = c.substringAfter('/', "")
        if (capturedPath.isEmpty() || ERROR_PAGE_PATH.containsMatchIn(capturedPath)) return false
        return requestedPath == capturedPath
    }

    /** Same page regardless of scheme, a leading www, percent-encoding, a fragment or a trailing slash. */
    private fun normalise(url: String): String = runCatching { java.net.URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
        .substringBefore('#').removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private fun failIfRateLimited(code: Int) {
        if (code == 429) throw ArchiveException("the archive is rate limiting us (HTTP 429); wait a few minutes and retry")
    }

    private fun open(address: String, readTimeoutMs: Int = timeoutMs): HttpURLConnection {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = timeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, text/html;q=0.8, */*;q=0.5")
        return connection
    }
}
