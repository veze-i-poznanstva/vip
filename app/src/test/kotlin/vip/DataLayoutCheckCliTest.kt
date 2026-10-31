package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command-line entry point of the layout check, run in-process with captured output. */
class DataLayoutCheckCliTest {

    @TempDir
    lateinit var dir: File

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runDataLayoutCheck(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val run = run()
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
        assertEquals("", run.out)
    }

    private fun write(path: String, body: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(
        "@prefix ex: <http://example.org/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix : <http://poderopedia.com/vocab/> .\n@prefix bio: <http://purl.org/vocab/bio/0.1/> .\n$body\n"
    )

    @Test
    fun `a coherent layout exits 0 and lists the files`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/ivan.ttl", "ex:Ivan a foaf:Person .")
        write("cases/case.ttl", "ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan .")
        val run = run(dir.path, "schema.ttl")
        assertEquals(0, run.code, run.out + run.err)
        assertTrue(run.out.contains("Files (3)"), run.out)
        assertTrue(run.out.contains("✓"), run.out)
    }

    @Test
    fun `a broken layout exits 1 and lists each problem`() {
        write("cases/case.ttl", "ex:Ana a foaf:Person .")
        val run = run(dir.path, "schema.ttl")
        assertEquals(1, run.code)
        assertTrue(run.out.contains("✗ 1 problem(s):"), run.out)
        assertTrue(run.out.contains("http://example.org/Ana"), run.out)
    }

    @Test
    fun `an unreadable data path is reported on stderr with exit 1, not a stack trace`() {
        val run = run(File(dir, "missing").path)
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
    }
}
