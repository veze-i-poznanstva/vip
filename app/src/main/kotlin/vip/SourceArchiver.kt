package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.vocabulary.RDF
import org.apache.jena.datatypes.xsd.XSDDatatype
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Makes sure every cited document has an archived copy.
 *
 * The archive records live in their own file, keyed by the document URL, so the hand-written data
 * is never rewritten by a machine. For each live document URL without a record the archiver asks
 * the archive for an existing snapshot first and only then requests a new capture.
 */
class SourceArchiver(
    private val wayback: Wayback,
    private val pauseBetweenSavesMs: Long = 6000,
    private val log: (String) -> Unit = ::println,
    /** New captures requested in one run; the rest wait for the next run, so one branch cannot flood the archive. */
    private val maxCapturesPerRun: Int = DEFAULT_MAX_CAPTURES_PER_RUN,
    private val pause: (Long) -> Unit = Thread::sleep
) {
    companion object { const val DEFAULT_MAX_CAPTURES_PER_RUN = 25 }

    data class Outcome(
        val alreadyArchived: Int,
        val foundExisting: List<String>,
        val captured: List<String>,
        val failed: Map<String, String>,
        /** Documents without a snapshot that were not captured because the per-run cap was reached. */
        val deferred: List<String> = emptyList()
    )

    /** Document URLs that need an archive record: live (not removed) documents without one. */
    fun unarchivedUrls(data: Model, archives: Model): List<String> {
        val hasArchive = archives.createProperty(Vocab.HAS_ARCHIVE_URL)
        return citedUrls(data).filterNot { archives.getResource(it).hasProperty(hasArchive) }
    }

    /** Live (not removed) document URLs cited anywhere in the data, each once, in a stable order. */
    private fun citedUrls(data: Model): List<String> {
        val documentation = data.createResource(Vocab.DOCUMENTATION)
        val hasUrl = data.createProperty(Vocab.HAS_DOCUMENT_URL)
        val hasStatus = data.createProperty(Vocab.HAS_DOCUMENT_STATUS)
        val removed = data.createResource(Vocab.REMOVED)
        return data.listSubjectsWithProperty(RDF.type, documentation).toList()
            .filterNot { it.hasProperty(hasStatus, removed) }
            .flatMap { doc -> doc.listProperties(hasUrl).toList().map { it.`object` } }
            .filter { it.isURIResource }
            .map { it.asResource().uri }
            .distinct()
            .sorted()
    }

    /** Adds a record for every unarchived URL to [archives]. Returns what happened. */
    fun archive(data: Model, archives: Model): Outcome {
        val todo = unarchivedUrls(data, archives)
        val alreadyArchived = citedUrls(data).size - todo.size
        val existing = mutableListOf<String>()
        val captured = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        val deferred = mutableListOf<String>()
        for (url in todo) {
            try {
                val found = wayback.lookup(url)
                val snapshot = if (found != null) {
                    existing.add(url); found
                } else {
                    if (captured.size >= maxCapturesPerRun) { deferred.add(url); continue }
                    if (captured.isNotEmpty()) pause(pauseBetweenSavesMs)
                    wayback.save(url).also { captured.add(url) }
                }
                record(archives, url, snapshot)
                log("archived $url -> ${snapshot.url}")
            } catch (e: Exception) {
                failed[url] = e.message ?: e.toString()
                log("FAILED  $url: ${e.message}")
            }
        }
        return Outcome(alreadyArchived = alreadyArchived, foundExisting = existing, captured = captured, failed = failed, deferred = deferred)
    }

    fun record(archives: Model, url: String, snapshot: ArchiveSnapshot): Resource =
        archives.getResource(url)
            .addProperty(archives.createProperty(Vocab.HAS_ARCHIVE_URL), archives.createResource(snapshot.url))
            .addProperty(archives.createProperty(Vocab.ARCHIVED_ON), archives.createTypedLiteral(snapshot.date.toString(), XSDDatatype.XSDdate))
}

fun loadOrEmpty(file: File): Model =
    if (file.exists()) RDFDataMgr.loadModel(file.path) else ModelFactory.createDefaultModel()

fun main(args: Array<String>) {
    exitProcess(runSourceArchiver(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runSourceArchiver(args: Array<String>, out: PrintStream, err: PrintStream, wayback: Wayback = WaybackClient()): Int {
    if (args.size < 2) {
        err.println("Usage: SourceArchiver <data-dir-or-file> <archives.ttl> [--check]")
        err.println("  --check  only report documents without an archive record; do not contact the archive")
        return 1
    }
    try {
        val dataFile = File(args[0])
        val archivesFile = File(args[1])
        val checkOnly = args.contains("--check")

        out.println("=== Source Archiving ===\n")
        val data = loadDataSet(dataFile).merged
        val archives = loadOrEmpty(archivesFile)
        archives.setNsPrefix("vip", Vocab.VIP)
        archives.setNsPrefix("xsd", XSDDatatype.XSD + "#")
        val archiver = SourceArchiver(wayback, log = { out.println(it.printable()) })
        val todo = archiver.unarchivedUrls(data, archives)

        if (todo.isEmpty()) {
            out.println("✓ Every cited document has an archive record (${archivesFile.path.printable()})")
            return 0
        }
        if (checkOnly) {
            out.println("✗ ${todo.size} cited document(s) have no archive record:")
            todo.forEach { out.println("  - ${it.printable()}") }
            out.println("\nRun ./gradlew :app:archiveSources locally and commit ${archivesFile.path.printable()}")
            return 1
        }

        out.println("Archiving ${todo.size} document(s) through the Internet Archive...\n")
        val outcome = archiver.archive(data, archives)
        archivesFile.outputStream().use { RDFDataMgr.write(it, archives, RDFFormat.TURTLE_PRETTY) }
        out.println("\nAlready on record:       ${outcome.alreadyArchived}")
        out.println("Existing snapshots used: ${outcome.foundExisting.size}")
        out.println("New captures requested:  ${outcome.captured.size}")
        out.println("Failed:                  ${outcome.failed.size}")
        out.println("Records written to ${archivesFile.path.printable()}")
        if (outcome.deferred.isNotEmpty()) {
            out.println("\n${outcome.deferred.size} document(s) left for a later run (at most ${SourceArchiver.DEFAULT_MAX_CAPTURES_PER_RUN} new captures per run):")
            outcome.deferred.forEach { out.println("  - ${it.printable()}") }
            out.println("Run ./gradlew :app:archiveSources again to continue")
        }
        return if (outcome.failed.isNotEmpty() || outcome.deferred.isNotEmpty()) 1 else 0
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}
