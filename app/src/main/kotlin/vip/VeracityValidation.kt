package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.net.URL
import java.net.HttpURLConnection

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    /** Things worth knowing that do not make the data invalid, such as a page served only from an archive. */
    val warnings: List<String> = emptyList()
)

/** Returns (isAccessible, status) for a URL. Injected so unit tests never touch the network. */
typealias UrlChecker = (String) -> Pair<Boolean, String>

/**
 * The sourcing rule of the project: every agent and every connection must be substantiated by
 * at least two documents hosted on two different domains, and each document must be reachable.
 *
 * Evidence is read through the vocabulary's Documentation nodes: any property of the entity
 * whose object is a `:Documentation` contributes that node's `:hasDocumentURL` values.
 */
/** Finds an archived copy of a URL, or null. Injected so unit tests never touch the network. */
typealias ArchiveLookup = (String) -> ArchiveSnapshot?

class VeracityValidation(
    private val urlChecker: UrlChecker = { url -> checkUrlOverHttp(url) },
    private val archiveLookup: ArchiveLookup = { url -> WaybackClient().lookup(url) }
) {

    /** Each URL is checked once per validator, however many entities cite it. */
    private val checked = mutableMapOf<String, Pair<Boolean, String>>()

    /** When set, only these URLs go to the network; the rest were checked when they were cited. */
    var liveCheckOnly: Set<String>? = null

    /** The host that counts for the two-domains rule: lowercase, without a leading www or a trailing dot. */
    fun extractDomain(urlString: String): String {
        val host = try { URL(urlString).host } catch (e: Exception) { return "" }
        return host.lowercase().removeSuffix(".").removePrefix("www.")
    }

    fun checkUrlAccessibility(urlString: String): Pair<Boolean, String> {
        if (liveCheckOnly?.contains(urlString) == false) return Pair(true, "not rechecked")
        return checked.getOrPut(urlString) { urlChecker(urlString) }
    }

    /**
     * URLs of every Documentation node attached to the resource, through any property.
     * Documents whose status is `:removed` stay on record but are no longer evidence.
     */
    fun documentationUrls(resource: Resource): List<String> {
        val model = resource.model
        val documentation = model.createResource(Vocab.DOCUMENTATION)
        val hasDocumentURL = model.createProperty(Vocab.HAS_DOCUMENT_URL)
        val hasStatus = model.createProperty(Vocab.HAS_DOCUMENT_STATUS)
        val removed = model.createResource(Vocab.REMOVED)
        return resource.listProperties().toList()
            .map { it.`object` }
            .filter { it.isResource && it.asResource().hasProperty(RDF.type, documentation) }
            .filterNot { it.asResource().hasProperty(hasStatus, removed) }
            .flatMap { doc -> doc.asResource().listProperties(hasDocumentURL).toList() }
            .map { it.`object` }
            .filter { it.isURIResource }
            .map { it.asResource().uri }
            .distinct()
    }

    fun validateItem(resource: Resource): ValidationResult {
        val name = describe(resource)
        val urls = documentationUrls(resource)
        if (urls.isEmpty()) {
            return ValidationResult(false, listOf("$name has no documentation"))
        }

        val errors = mutableListOf<String>()

        // Rule 1: at least 2 URLs from at least 2 different domains.
        // A single URL is reported once; the domain complaint would be redundant.
        val domains = urls.map { extractDomain(it) }.toSet()
        if (urls.size < 2) {
            errors.add("$name has only ${urls.size} URL")
        } else if (domains.size < 2) {
            errors.add("$name has URLs from only ${domains.size} domain(s): ${domains.joinToString(", ")}")
        }

        // Rule 2: every URL is reachable, live or through an archived copy.
        val warnings = mutableListOf<String>()
        val inaccessible = mutableListOf<String>()
        for (url in urls) {
            val (ok, status) = checkUrlAccessibility(url)
            if (ok) continue
            val recorded = recordedArchiveValue(resource.model, url)
            val archive = if (recorded != null) snapshotFromRecord(recorded) else lookupArchiveSafely(url)
            when {
                archive != null -> warnings.add("$name: $url not reachable live ($status); using archived copy from ${archive.date} at ${archive.url}")
                recorded != null -> inaccessible.add("$url ($status; its archive record $recorded is not a Wayback Machine snapshot)")
                else -> inaccessible.add("$url ($status, no archived copy)")
            }
        }
        if (inaccessible.isNotEmpty()) {
            errors.add("$name has inaccessible URLs: ${inaccessible.joinToString(", ")}")
        }

        return ValidationResult(errors.isEmpty(), errors, warnings)
    }

    /** The archive snapshot recorded in the data for this document URL, if any and if genuine. */
    fun recordedArchive(model: Model, url: String): ArchiveSnapshot? =
        recordedArchiveValue(model, url)?.let { snapshotFromRecord(it) }

    /** Whatever the data records as the archive URL of this document, as text, or null. */
    private fun recordedArchiveValue(model: Model, url: String): String? =
        model.getResource(url).getProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL))?.`object`?.let {
            if (it.isURIResource) it.asResource().uri else it.asLiteral().lexicalForm
        }

    /**
     * Only a Wayback Machine snapshot address counts as an archived copy. archives.ttl is contributed
     * like any other file, so a record pointing anywhere else is an error, never evidence.
     */
    private fun snapshotFromRecord(recorded: String): ArchiveSnapshot? =
        WAYBACK_SNAPSHOT.find(recorded)?.let { ArchiveSnapshot(recorded, it.groupValues[1]) }

    private fun lookupArchiveSafely(url: String): ArchiveSnapshot? =
        try { archiveLookup(url) } catch (e: Exception) { null }

    /**
     * Validates every entity in the model.
     *
     * With a [schema], the entities are everything the vocabulary entails to be a `foaf:Agent`
     * or a `:Connection`, so an entity with no documentation at all is reported. Without a
     * schema only resources that already carry documentation can be found.
     */
    fun validate(data: Model, schema: Model? = null): ValidationResult {
        val entities = if (schema == null) subjectsWithDocumentation(data) else entitiesEntailedBy(schema, data)
        val results = entities.map { validateItem(it) }
        val errors = results.flatMap { it.errors }
        return ValidationResult(errors.isEmpty(), errors, results.flatMap { it.warnings })
    }

    private fun subjectsWithDocumentation(data: Model): List<Resource> {
        val documentation = data.createResource(Vocab.DOCUMENTATION)
        return data.listStatements().toList()
            .filter { it.`object`.isResource && it.`object`.asResource().hasProperty(RDF.type, documentation) }
            .map { it.subject }
            .distinct()
    }

    private fun entitiesEntailedBy(schema: Model, data: Model): List<Resource> {
        val inferred = ModelFactory.createRDFSModel(schema, data)
        val agents = inferred.listSubjectsWithProperty(RDF.type, inferred.createResource(Vocab.AGENT)).toList()
        val connections = inferred.listSubjectsWithProperty(RDF.type, inferred.createResource(Vocab.CONNECTION)).toList()
        // Only things the data itself talks about; the vocabulary's own resources are not entities.
        return (agents + connections).distinct().filter { data.containsResource(it) }
    }

    private fun describe(resource: Resource): String {
        if (resource.isURIResource) return resource.uri
        val label = resource.getProperty(RDFS.label)?.`object`?.asLiteral()?.lexicalForm
        return if (label != null) "Blank node \"$label\"" else "Blank node"
    }
}

/** A genuine Wayback Machine snapshot address, with its 14-digit capture time. */
val WAYBACK_SNAPSHOT = Regex("^https?://web\\.archive\\.org/web/(\\d{14})/")

/** Identifies the validator honestly, as Wikimedia and others require of automated clients. */
const val USER_AGENT = "VIP-VeracityValidator/1.0 (source verification for an open civic dataset)"
private const val MAX_REDIRECTS = 5
private const val TIMEOUT_MS = 10000
/** Paths sites send a visitor to when the page is gone, while still answering 200. */
internal val ERROR_PAGE_PATH = Regex("(?i)(^|/)(404|not[-_]?found|error)(\\.\\w+)?/?$")

/**
 * Which hosts the checker may connect to. Cited URLs come from contributed data and are fetched
 * by CI and by maintainers, so by default only public hosts are allowed: no loopback, private,
 * link-local or multicast addresses, at any hop of a redirect chain.
 */
class HostPolicy private constructor(private val allowAll: Boolean, private val exceptions: Set<String>) {
    fun allows(host: String): Boolean {
        if (allowAll || host.lowercase() in exceptions) return true
        if (host.isEmpty() || host.equals("localhost", ignoreCase = true)) return false
        val address = try { java.net.InetAddress.getByName(host) } catch (e: Exception) { return false }
        return !(address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isAnyLocalAddress || address.isMulticastAddress || isUniqueLocal(address))
    }

    /** The same policy, with named hosts allowed regardless; for tests that run a local server. */
    fun butAllowing(vararg hosts: String) = HostPolicy(allowAll, exceptions + hosts.map { it.lowercase() })

    private fun isUniqueLocal(address: java.net.InetAddress) =
        address is java.net.Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc

    companion object {
        val PublicHostsOnly = HostPolicy(allowAll = false, exceptions = emptySet())
        val AnyHost = HostPolicy(allowAll = true, exceptions = emptySet())
    }
}

/**
 * Checks that a URL is reachable the way a reader would reach it: HEAD first, and if the site
 * refuses that, GET; redirects are followed by hand so relative and cross-protocol targets work.
 * A redirect onto an error page or onto the site's home page means the page is gone.
 */
fun checkUrlOverHttp(urlString: String, hostPolicy: HostPolicy = HostPolicy.PublicHostsOnly): Pair<Boolean, String> {
    return try {
        val head = requestFollowingRedirects(urlString, "HEAD", hostPolicy)
        if (head.first) head else requestFollowingRedirects(urlString, "GET", hostPolicy)
    } catch (e: Exception) {
        Pair(false, "Error: ${e.message}")
    }
}

/**
 * The address as it goes on the wire: an internationalised host in punycode and every non-ASCII
 * character of the path, query and fragment percent-encoded as UTF-8. ASCII, including existing
 * percent sequences, is left alone, so a URL copied from a browser is never encoded twice.
 */
fun asciiForm(urlString: String): String {
    val url = URL(urlString)
    val host = java.net.IDN.toASCII(url.host)
    val rest = urlString.substring(urlString.indexOf(url.host, urlString.indexOf("//") + 2) + url.host.length)
    val encoded = StringBuilder()
    for (byte in rest.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xff
        if (c < 0x80) encoded.append(c.toChar()) else encoded.append('%').append("%02X".format(c))
    }
    val userInfo = url.userInfo?.let { "$it@" } ?: ""
    return "${url.protocol}://$userInfo$host$encoded"
}

private fun requestFollowingRedirects(start: String, method: String, hostPolicy: HostPolicy): Pair<Boolean, String> {
    var url = URL(asciiForm(start))
    val startPath = url.path.trimEnd('/')
    repeat(MAX_REDIRECTS + 1) {
        if (url.protocol != "http" && url.protocol != "https") return Pair(false, "unsupported scheme ${url.protocol}")
        if (!hostPolicy.allows(url.host)) return Pair(false, "${url.host} is not a public host")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
        val code = connection.responseCode
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        when {
            code in 200..299 -> return Pair(true, "OK")
            code in 300..399 && location != null -> {
                url = URL(asciiForm(URL(url, location).toString()))
                if (ERROR_PAGE_PATH.containsMatchIn(url.path)) return Pair(false, "redirected to error page $url")
                if (startPath.isNotEmpty() && url.path.trimEnd('/').isEmpty()) return Pair(false, "redirected to home page $url")
            }
            else -> return Pair(false, "HTTP $code")
        }
    }
    return Pair(false, "too many redirects")
}
