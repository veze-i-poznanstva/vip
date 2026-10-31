package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The command-line entry point of the RDF syntax validator, run in-process with captured output. */
class RdfValidatorCliTest {

    @TempDir
    lateinit var dir: File

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runRdfValidator(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val run = run()
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
        assertEquals("", run.out)
    }

    @Test
    fun `a run over the schema exits 0`() {
        val run = run("schema.ttl")
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("Valid RDF file"), run.out)
        assertEquals("", run.err)
    }

    @Test
    fun `a syntactically broken file exits 1 with Validation failed on stderr and no stack trace`() {
        val broken = File(dir, "broken.ttl").apply { writeText("<a> <b> <c> . this is not turtle") }
        val run = run(broken.path)
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Validation failed"), run.err)
        assertFalse(run.err.contains("\tat "), run.err)
    }

    @Test
    fun `a missing file exits 1 with a message on stderr`() {
        val run = run(File(dir, "missing.ttl").path)
        assertEquals(1, run.code)
        assertTrue(run.err.isNotBlank(), run.err)
        assertFalse(run.err.contains("\tat "), run.err)
    }
}
