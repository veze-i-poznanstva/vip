package vip

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The red-flag command line, run in-process over the repository's own data and rules. */
class RedFlagsCliTest {

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runRedFlags(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val run = run()
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
    }

    @Test
    fun `over the repository data it reports every rule and exits 0 whether or not anything matched`() {
        val run = run("data", "schema.ttl", "rules")
        assertEquals(0, run.code, run.err)
        for (rule in listOf("nepotism", "owner-in-office", "schoolmates-in-power")) assertTrue(run.out.contains("$rule: "), run.out)
        assertTrue(run.out.contains("Total: "), run.out)
    }

    @Test
    fun `an unreadable path is reported on stderr with exit 1`() {
        val run = run(File("does-not-exist").path, "schema.ttl", "rules")
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
    }
}
