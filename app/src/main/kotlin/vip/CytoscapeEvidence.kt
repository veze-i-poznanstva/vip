package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import java.io.File

/** One cited document with the passage that supports the record and its archive snapshot, if any. */
data class Source(
    val url: String,
    val domain: String,
    val type: String? = null,
    val status: String? = null,
    val quote: String? = null,
    val archive: String? = null,
    val archivedOn: String? = null
)

/** One documented state of a relationship: the record's own share, roles, dates, comment, location and sources. */
data class ConnectionState(
    val uri: String? = null,
    val share: Double? = null,
    val roles: List<String>? = null,
    val start: String? = null,
    val end: String? = null,
    val comment: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val sources: List<Source>? = null
)

/** A literal-valued statement about an agent, with the vocabulary's label for its property. */
data class Fact(val property: String, val label: String, val value: String)

/** Where a record lives in the repository: the file and the 1-based line its subject starts on. */
data class RecordLocation(val file: String, val line: Int?)

/** The last path segment of a URI, after `#` or `/`. */
fun localName(uri: String): String = uri.substringAfterLast('#').substringAfterLast('/')

/** Host lowercased, without a leading `www.`; empty when the address has no host. */
fun sourceDomain(url: String): String =
    (try { java.net.URI(url).host } catch (e: Exception) { null } ?: "").lowercase().removePrefix("www.")

/** Archive records keyed by document URL: snapshot address and the date it was taken. */
class ArchiveIndex(private val archives: Model) {
    fun snapshotOf(url: String): String? =
        archives.listObjectsOfProperty(archives.createResource(url), archives.createProperty(Vocab.HAS_ARCHIVE_URL))
            .toList().filter { it.isURIResource }.map { it.asResource().uri }.minOrNull()

    fun dateOf(url: String): String? =
        archives.listObjectsOfProperty(archives.createResource(url), archives.createProperty(Vocab.ARCHIVED_ON))
            .toList().filter { it.isLiteral }.map { it.asLiteral().lexicalForm }.minOrNull()
}

/**
 * Finds the file that declares a resource and the line its subject starts on.
 * Paths are the input path as given plus the file's path under the data root.
 */
class RecordLocator(private val dataSet: DataSet, inputPath: String) {
    private val base = inputPath.replace('\\', '/').trimEnd('/')
    private val lines = mutableMapOf<File, List<String>>()

    private fun pathOf(file: File): String = if (dataSet.root == null) base else base + "/" + dataSet.relativePath(file)

    /** The file whose model holds the resource's `rdf:type`. */
    fun declaration(resource: Resource): RecordLocation? {
        if (!resource.isURIResource) return null
        val (file, model) = dataSet.models.entries.firstOrNull { (_, m) ->
            m.contains(m.createResource(resource.uri), RDF.type, null as org.apache.jena.rdf.model.RDFNode?)
        } ?: return null
        return RecordLocation(pathOf(file), lineOf(file, model, resource.uri))
    }

    /** The file holding this very statement. */
    fun statement(statement: org.apache.jena.rdf.model.Statement): String? =
        dataSet.models.entries.firstOrNull { (_, m) -> m.contains(statement) }?.key?.let(::pathOf)

    private fun lineOf(file: File, model: Model, uri: String): Int? {
        val forms = model.nsPrefixMap.filter { (_, ns) -> uri.startsWith(ns) && uri.length > ns.length }
            .map { (p, ns) -> "$p:${uri.removePrefix(ns)}" } + "<$uri>"
        val index = lines.getOrPut(file) { file.readLines() }.indexOfFirst { line ->
            forms.any { form -> line.startsWith(form) && line.getOrNull(form.length)?.isWhitespace() == true }
        }
        return if (index < 0) null else index + 1
    }
}
