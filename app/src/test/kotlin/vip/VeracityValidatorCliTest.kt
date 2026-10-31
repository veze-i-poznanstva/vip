package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The command-line entry point of the veracity validator, run in-process with stub network checks. */
class VeracityValidatorCliTest {

    @TempDir
    lateinit var dir: File

    private class Run(val code: Int, val out: String, val err: String)

    private val allReachable = VeracityValidation(urlChecker = { true to "OK" }, archiveLookup = { null })

    private fun run(validation: VeracityValidation, vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runVeracityValidator(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"), validation)
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    private fun run(vararg args: String) = run(allReachable, *args)

    private fun write(path: String, body: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(
        "@prefix ex: <https://w3id.org/vip/rs/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
            "@prefix : <http://poderopedia.com/vocab/> .\n@prefix bio: <http://purl.org/vocab/bio/0.1/> .\n$body\n"
    )

    private fun documented(vararg urls: String) =
        urls.joinToString(", ") { "[ a :Documentation ; :hasDocumentURL <$it> ]" }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val run = run()
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
        assertEquals("", run.out)
    }

    @Test
    fun `an entity with one URL fails with a numbered error`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; :hasDocumentation ${documented("https://a.example/1")} .")
        val run = run(dir.path, "schema.ttl")
        assertEquals(1, run.code, run.out + run.err)
        assertTrue(run.out.contains("✗ Validation FAILED"), run.out)
        assertTrue(run.out.contains("  1. "), run.out)
        assertTrue(run.out.contains("Total errors: 1"), run.out)
    }

    @Test
    fun `a blocked URL with a genuine archive record passes with a Warnings section`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; :hasDocumentation ${documented("https://a.example/1", "https://b.example/2")} .")
        val archives = File(dir, "archives.ttl")
        archives.writeText(
            "<https://a.example/1> <https://w3id.org/vip/vocab#hasArchiveURL> <https://web.archive.org/web/20260101000000/https://a.example/1> .\n"
        )
        val blockedA = VeracityValidation(
            urlChecker = { url -> if (url == "https://a.example/1") false to "HTTP 403" else true to "OK" },
            archiveLookup = { null }
        )
        val run = run(blockedA, File(dir, "entities").path, "schema.ttl", archives.path)
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("Warnings (1):"), run.out)
        assertTrue(run.out.contains("Validation PASSED"), run.out)
    }

    @Test
    fun `a missing archives file is an error on stderr with exit 1`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        val run = run(dir.path, "schema.ttl", File(dir, "nope.ttl").path)
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
        assertTrue(run.err.contains("nope.ttl"), run.err)
    }

    @Test
    fun `a control character in a label cannot start a line of output`() {
        write("cases/case.ttl", "[] a :Connection ; rdfs:label \"x\\n::error injected\" ; bio:participant ex:Ana, ex:Ivan .")
        val run = run(dir.path, "schema.ttl")
        assertEquals(1, run.code, run.out + run.err)
        assertFalse(run.out.lines().any { it.startsWith("::") }, run.out)
        assertTrue(run.out.contains("x ::error injected"), run.out)
    }
}
