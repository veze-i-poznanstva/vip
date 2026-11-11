package vip

import com.google.gson.JsonParser
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command line that publishes the data, the vocabulary and the rules for the browser's query engine. */
class QueryExportCliTest {

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runQueryExport(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"))
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val run = run()
        assertEquals(1, run.code)
        assertTrue(run.err.contains("Usage"), run.err)
    }

    @Test
    fun `over the repository it writes the entailed data, the vocabulary and the rules into the output directory`() {
        val dir = Files.createTempDirectory("query-export").toFile()
        val run = run("data", "schema.ttl", "rules", dir.path)
        assertEquals(0, run.code, run.err)

        val data = RDFDataMgr.loadModel(File(dir, "data.ttl").path)
        val braca = data.createResource(Vocab.RS + "PredragMali_SinisaMali_Braca")
        assertTrue(data.contains(braca, RDF.type, data.createResource(Vocab.VIP + "FamilyConnection")))
        assertTrue(data.contains(braca, data.createProperty(Vocab.PARTICIPANT), data.createResource(Vocab.RS + "Sinisa_Mali")))

        assertEquals(File("schema.ttl").readText(), File(dir, "schema.ttl").readText())

        val rules = JsonParser.parseString(File(dir, "rules.json").readText()).asJsonArray
        assertEquals(3, rules.size())
        assertTrue(run.out.contains("rules.json"), run.out)
    }

    @Test
    fun `it also writes the leads the build found as red-flags json, for the viewer's equivalence test`() {
        val dir = Files.createTempDirectory("query-export").toFile()
        val run = run("data", "schema.ttl", "rules", dir.path)
        assertEquals(0, run.code, run.err)

        val leads = JsonParser.parseString(File(dir, "red-flags.json").readText()).asJsonArray.map { it.asJsonObject }
        assertEquals(listOf("nepotism", "owner-in-office", "schoolmates-in-power"), leads.map { it["name"].asString })
        val ownerInOffice = leads.single { it["name"].asString == "owner-in-office" }
        assertTrue(ownerInOffice["matches"].asJsonArray.any { it.asJsonObject["person"].asString == Vocab.RS + "Sinisa_Mali" }, ownerInOffice.toString())
        assertTrue(run.out.contains("red-flags.json"), run.out)
    }

    @Test
    fun `the public offices on record make Mali and Jovanović a schoolmates-in-power lead through the Government`() {
        val dir = Files.createTempDirectory("query-export").toFile()
        assertEquals(0, run("data", "schema.ttl", "rules", dir.path).code)

        val leads = JsonParser.parseString(File(dir, "red-flags.json").readText()).asJsonArray.map { it.asJsonObject }
        val schoolmates = leads.single { it["name"].asString == "schoolmates-in-power" }["matches"].asJsonArray.map { it.asJsonObject }
        val pair = schoolmates.filter { it["personA"].asString == Vocab.RS + "Mihailo_Jovanovic" && it["personB"].asString == Vocab.RS + "Sinisa_Mali" }
        assertEquals(listOf(Vocab.RS + "Vlada_Srbije"), pair.map { it["organization"].asString }, schoolmates.toString())
        assertEquals("overlapping", pair.single()["schoolOverlap"].asString)
    }

    @Test
    fun `an unreadable path is reported on stderr with exit 1`() {
        val run = run(File("does-not-exist").path, "schema.ttl", "rules", Files.createTempDirectory("query-export").toFile().path)
        assertEquals(1, run.code)
        assertTrue(run.err.startsWith("Error: "), run.err)
    }
}
