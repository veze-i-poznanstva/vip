package vip

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The command-line entry point of the Wikidata linker, run in-process against a stub lookup. */
class WikidataLinkerCliTest {

    @TempDir
    lateinit var dir: File

    private val article = "https://sr.wikipedia.org/wiki/Ana"

    private class Stub(val answer: () -> ArticleItem) : ItemLookup {
        override fun itemOfArticle(articleUrl: String) = answer()
        override fun itemExists(id: String) = true
    }

    private class Run(val code: Int, val out: String, val err: String)

    private fun run(lookup: ItemLookup, vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val code = runWikidataLinker(arrayOf(*args), PrintStream(out, true, "UTF-8"), PrintStream(err, true, "UTF-8"), lookup)
        return Run(code, out.toString("UTF-8"), err.toString("UTF-8"))
    }

    private fun data(articleLine: String = "    vip:wikipediaArticle <$article> ;\n", item: String = ""): File {
        val f = File(dir, "data/entities/ana.ttl").apply { parentFile.mkdirs() }
        f.writeText("@prefix rs: <https://w3id.org/vip/rs/> .\n@prefix vip: <https://w3id.org/vip/vocab#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n" +
            "rs:Ana a foaf:Person ;\n$articleLine$item    foaf:name \"Ana\" .\n")
        return f
    }

    private val q1 = Stub { ArticleItem.Found("Q1") }

    @Test
    fun `without arguments it prints usage on stderr and exits 1`() {
        val r = run(q1)
        assertEquals(1, r.code)
        assertTrue(r.err.contains("Usage"), r.err)
        assertEquals("", r.out)
    }

    @Test
    fun `check exits 0 when everything is recorded`() {
        data(item = "    vip:wikidataItem <http://www.wikidata.org/entity/Q1> ;\n")
        val r = run(q1, File(dir, "data").path, "--check")
        assertEquals(0, r.code, r.out + r.err)
        assertTrue(r.out.contains("=== Wikidata items ==="), r.out)
        assertTrue(r.out.contains("✓ Every Wikipedia article's Wikidata item is recorded"), r.out)
    }

    @Test
    fun `check exits 1 on an unrecorded item and says what to run`() {
        val f = data()
        val before = f.readText()
        val r = run(q1, File(dir, "data").path, "--check")
        assertEquals(1, r.code, r.out)
        assertTrue(r.out.contains("✗ https://w3id.org/vip/rs/Ana: its Wikipedia article has Wikidata item Q1; run ./gradlew :app:linkWikidata"), r.out)
        assertEquals(before, f.readText())
    }

    @Test
    fun `check exits 0 with a warning when Wikipedia cannot be asked`() {
        data()
        val r = run(Stub { throw WikidataException("Wikipedia API answered HTTP 503") }, File(dir, "data").path, "--check")
        assertEquals(0, r.code, r.out + r.err)
        assertTrue(r.out.contains("! https://w3id.org/vip/rs/Ana: could not ask Wikipedia/Wikidata"), r.out)
    }

    @Test
    fun `check prints notes`() {
        data()
        val r = run(Stub { ArticleItem.NoItem }, File(dir, "data").path, "--check")
        assertEquals(0, r.code)
        assertTrue(r.out.contains("- https://w3id.org/vip/rs/Ana: no Wikidata item yet"), r.out)
    }

    @Test
    fun `link writes the file and prints linked`() {
        val f = data()
        val r = run(q1, File(dir, "data").path)
        assertEquals(0, r.code, r.out + r.err)
        assertTrue(r.out.contains("linked https://w3id.org/vip/rs/Ana -> http://www.wikidata.org/entity/Q1 (entities/ana.ttl)"), r.out)
        assertTrue(r.out.contains("Linked: 1") && r.out.contains("Failed: 0"), r.out)
        assertTrue(f.readText().contains("    vip:wikidataItem <http://www.wikidata.org/entity/Q1> ;\n"))
    }

    @Test
    fun `link exits 1 on a placement failure`() {
        val f = data(articleLine = "    vip:wikipediaArticle\n        <$article> ;\n")
        val before = f.readText()
        val r = run(q1, File(dir, "data").path)
        assertEquals(1, r.code, r.out)
        assertTrue(r.out.contains("FAILED"), r.out)
        assertTrue(r.out.contains("Failed: 1"), r.out)
        assertEquals(before, f.readText())
    }

    @Test
    fun `link says to run again after a warning`() {
        data()
        val r = run(Stub { throw WikidataException("down") }, File(dir, "data").path)
        assertEquals(0, r.code)
        assertTrue(r.out.contains("Run ./gradlew :app:linkWikidata again"), r.out)
    }

    @Test
    fun `an unreadable data path prints Error and exits 1`() {
        val r = run(q1, File(dir, "nowhere").path)
        assertEquals(1, r.code)
        assertTrue(r.err.startsWith("Error: "), r.err)
    }
}
