package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The command-line entry point of the SHACL validator, run in-process with captured output. */
class ShaclValidatorCliTest {

    @TempDir
    lateinit var dir: File

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runShaclValidator(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    private fun write(path: String, body: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(
        "@prefix ex: <https://w3id.org/vip/rs/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix : <http://poderopedia.com/vocab/> .\n@prefix bio: <http://purl.org/vocab/bio/0.1/> .\n@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n$body\n"
    )

    private val docs = "[ a :Documentation ; :hasDocumentURL <https://a.example/1> ], [ a :Documentation ; :hasDocumentURL <https://b.example/2> ]"

    private val quotedDocs = "[ a :Documentation ; :hasDocumentURL <https://a.example/1> ; rdfs:comment \"Quoted\" ], [ a :Documentation ; :hasDocumentURL <https://b.example/2> ; rdfs:comment \"Quoted\" ]"

    @Test
    fun `conforming data exits 0 and says so`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; foaf:firstName \"Ana\" ; :firstLastName \"A\" ; :hasDocumentation $docs .")
        write("entities/ivan.ttl", "ex:Ivan a foaf:Person ; foaf:firstName \"Ivan\" ; :firstLastName \"I\" ; :hasDocumentation $docs .")
        write("cases/case.ttl", "ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan ; :hasDocumentation $quotedDocs .")
        val run = run("schema.ttl", dir.path)
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("conforms"), run.out)
    }

    @Test
    fun `a connection with one participant exits 1 and the report names bio participant`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/case.ttl", "ex:Alone a :Connection ; bio:participant ex:Ana .")
        val run = run("schema.ttl", dir.path)
        assertEquals(1, run.code, run.out + run.err)
        assertTrue(run.out.contains("participant"), run.out)
        assertTrue(run.out.contains("✗ Validation failures found"), run.out)
    }

    @Test
    fun `with one argument the schema is validated against its own shapes without throwing`() {
        val run = run("schema.ttl")
        assertTrue(run.code == 0 || run.code == 1)
        assertTrue(run.out.contains("Validating schema against its own shapes"), run.out)
        assertEquals("", run.err)
    }

    @Test
    fun `a missing shapes file exits 1 with Error on stderr and no stack trace`() {
        val run = run(File(dir, "missing.ttl").path)
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error"), run.err)
        assertFalse(run.err.contains("\tat "), run.err)
    }
}
