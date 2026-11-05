package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command-line entry point of the Cytoscape export, run in-process with captured output. */
class TtlToCytoscapeCliTest {

    @TempDir
    lateinit var dir: File

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runTtlToCytoscape(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    private fun write(path: String, body: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(
        "@prefix ex: <https://w3id.org/vip/rs/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix : <http://poderopedia.com/vocab/> .\n@prefix bio: <http://purl.org/vocab/bio/0.1/> .\n$body\n"
    )

    @Test
    fun `a wrong argument count prints usage on stderr and exits 1`() {
        val run = run("only-one")
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
        assertEquals("", run.out)
    }

    @Test
    fun `a missing input file exits 1 and names it on stderr`() {
        val run = run(File(dir, "missing").path, File(dir, "out.json").path, "schema.ttl")
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
        assertTrue(run.err.contains("missing"), run.err)
    }

    @Test
    fun `a valid conversion writes the json into new directories and prints the counts`() {
        write("data/entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("data/entities/ivan.ttl", "ex:Ivan a foaf:Person .")
        write("data/cases/case.ttl", "ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan .")
        val output = File(dir, "a/b/out.json")
        val run = run(File(dir, "data").path, output.path, "schema.ttl")
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(output.exists())
        assertTrue(run.out.contains("Created 2 nodes and 1 edges"), run.out)
    }

    @Test
    fun `a broken data file exits 1 with Error on stderr and no stack trace`() {
        write("data/entities/bad.ttl", "ex:Ana a foaf:Person . this is not turtle")
        val run = run(File(dir, "data").path, File(dir, "out.json").path, "schema.ttl")
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Error"), run.err)
        assertTrue(!run.err.contains("\tat "), run.err)
    }

    @Test
    fun `a run with an archives file puts the snapshot on a source`() {
        write("data/entities/ana.ttl", "ex:Ana a foaf:Person ; :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/x> ] .")
        val archives = File(dir, "archives.ttl")
        archives.writeText("@prefix vip: <https://w3id.org/vip/vocab#> .\n<https://a.example/x> vip:hasArchiveURL <http://web.archive.org/web/20260521145618/https://a.example/x> .\n")
        val output = File(dir, "out.json")
        val run = run(File(dir, "data").path, output.path, "schema.ttl", archives.path)
        assertEquals(0, run.code, run.out + run.err)
        val json = output.readText()
        assertTrue(json.contains("\"archive\": \"http://web.archive.org/web/20260521145618/https://a.example/x\""), json)
        assertTrue(json.contains("\"file\": \"" + File(dir, "data").path + "/entities/ana.ttl\""), json)
    }

    @Test
    fun `a named archives file that does not exist exits 1 and names it`() {
        write("data/entities/ana.ttl", "ex:Ana a foaf:Person .")
        val run = run(File(dir, "data").path, File(dir, "out.json").path, "schema.ttl", File(dir, "nope.ttl").path)
        assertEquals(1, run.code)
        assertTrue(run.err.contains("nope.ttl"), run.err)
    }
}
