package vip

import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command-line entry point of the archiver, run in-process against a fake archive. */
class SourceArchiverCliTest {

    @TempDir
    lateinit var dir: File

    private class FakeWayback(val failing: Set<String> = emptySet()) : Wayback {
        val saved = mutableListOf<String>()
        val lookedUp = mutableListOf<String>()
        override fun lookup(url: String): ArchiveSnapshot? { lookedUp.add(url); return null }
        override fun save(url: String): ArchiveSnapshot {
            if (url in failing) throw ArchiveException("Save Page Now answered HTTP 429")
            saved.add(url)
            return ArchiveSnapshot("https://web.archive.org/web/20261030120000/$url", "20261030120000")
        }
    }

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(wayback: Wayback, vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runSourceArchiver(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"), wayback)
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    private fun data(vararg urls: String) = File(dir, "data/entities/ana.ttl").apply { parentFile.mkdirs() }.also {
        it.writeText(
            "@prefix ex: <https://w3id.org/vip/rs/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix : <http://poderopedia.com/vocab/> .\n" +
                "ex:Ana a foaf:Person ; :hasDocumentation " + urls.joinToString(", ") { "[ a :Documentation ; :hasDocumentURL <$it> ]" } + " .\n"
        )
    }.parentFile.parentFile

    private val archives get() = File(dir, "archives.ttl")

    @Test
    fun `without enough arguments it prints usage on stderr and exits 1`() {
        val run = run(FakeWayback(), "only-one")
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
        assertEquals("", run.out)
    }

    @Test
    fun `check lists unarchived documents, exits 1 and never contacts the archive`() {
        val fake = FakeWayback()
        val run = run(fake, data("https://a.example/1").path, archives.path, "--check")
        assertEquals(1, run.code, run.out + run.err)
        assertTrue(run.out.contains("1 cited document(s) have no archive record"), run.out)
        assertTrue(run.out.contains("  - https://a.example/1"), run.out)
        assertTrue(fake.lookedUp.isEmpty() && fake.saved.isEmpty())
    }

    @Test
    fun `check exits 0 when every document has a record`() {
        archives.writeText("<https://a.example/1> <https://w3id.org/vip/vocab#hasArchiveURL> <https://web.archive.org/web/20260101000000/https://a.example/1> .\n")
        val fake = FakeWayback()
        val run = run(fake, data("https://a.example/1").path, archives.path, "--check")
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("Every cited document has an archive record"), run.out)
        assertTrue(fake.lookedUp.isEmpty())
    }

    @Test
    fun `archive mode writes the records with the vip prefix and prints the counts`() {
        val run = run(FakeWayback(), data("https://a.example/1").path, archives.path)
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("Already on record:       0"), run.out)
        assertTrue(run.out.contains("New captures requested:  1"), run.out)
        val model = RDFDataMgr.loadModel(archives.path)
        assertTrue(model.getResource("https://a.example/1").hasProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL)))
        assertEquals(Vocab.VIP, model.getNsPrefixURI("vip"))
        assertTrue(archives.readText().contains("@prefix vip:"))
    }

    @Test
    fun `archive mode exits 1 when a capture failed`() {
        val run = run(FakeWayback(failing = setOf("https://a.example/1")), data("https://a.example/1").path, archives.path)
        assertEquals(1, run.code)
        assertTrue(run.out.contains("Failed:                  1"), run.out)
    }

    @Test
    fun `an unreadable data path is reported on stderr with exit 1`() {
        val run = run(FakeWayback(), File(dir, "missing").path, archives.path)
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
    }
}
