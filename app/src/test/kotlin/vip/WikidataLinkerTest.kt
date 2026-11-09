package vip

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikidataLinkerTest {

    @TempDir
    lateinit var dir: File

    private class Stub(
        val articles: Map<String, ArticleItem> = emptyMap(),
        val existing: Set<String> = emptySet(),
        val failing: Set<String> = emptySet()
    ) : ItemLookup {
        override fun itemOfArticle(articleUrl: String): ArticleItem {
            if (articleUrl in failing) throw WikidataException("Wikipedia API answered HTTP 503")
            return articles[articleUrl] ?: ArticleItem.MissingPage
        }
        override fun itemExists(id: String) = id in existing
    }

    private val prefixes = "@prefix rs: <https://w3id.org/vip/rs/> .\n@prefix vip: <https://w3id.org/vip/vocab#> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n"
    private val sm = "https://sr.wikipedia.org/wiki/Синиша_Мали"
    private val sm2 = "https://en.wikipedia.org/wiki/Siniša_Mali"

    private fun entity(slug: String, body: String): File =
        File(dir, "data/entities/$slug.ttl").apply { parentFile.mkdirs(); writeText(prefixes + body) }

    private fun data() = loadDataSet(File(dir, "data"))
    private fun linker(stub: Stub, log: MutableList<String> = mutableListOf()) = WikidataLinker(stub, log::add)
    private fun item(q: String) = "<http://www.wikidata.org/entity/$q>"

    @Test
    fun `entities lists subjects with an article or an item sorted by uri`() {
        entity("b", "rs:B a foaf:Person ; vip:wikidataItem ${item("Q2")} .\n")
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> , <$sm2> .\n")
        entity("c", "rs:C a foaf:Person ; foaf:name \"C\" .\n")
        val found = linker(Stub()).entities(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A", "https://w3id.org/vip/rs/B"), found.map { it.uri })
        assertEquals(listOf(sm2, sm).sorted(), found[0].articles)
        assertEquals(null, found[0].item)
        assertEquals("http://www.wikidata.org/entity/Q2", found[1].item)
        assertEquals("a.ttl", found[0].file.name)
    }

    @Test
    fun `matching item is fine`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> ; vip:wikidataItem ${item("Q1")} .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.Found("Q1")))).check(data())
        assertEquals(emptyList(), r.errors + r.warnings + r.notes)
        assertTrue(r.linkable.isEmpty())
    }

    @Test
    fun `a different item is an error`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> ; vip:wikidataItem ${item("Q1")} .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.Found("Q2")))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A records Q1 but its Wikipedia article has item Q2"), r.errors)
    }

    @Test
    fun `a recorded item whose article has none is an error`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> ; vip:wikidataItem ${item("Q1")} .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.NoItem))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A records Q1 but its Wikipedia article has no Wikidata item; remove the item or link the article on Wikidata"), r.errors)
    }

    @Test
    fun `a missing article warns and falls back to the item`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> ; vip:wikidataItem ${item("Q1")} .\n")
        val ok = linker(Stub(existing = setOf("Q1"))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A: Wikipedia article $sm does not exist"), ok.warnings)
        assertTrue(ok.errors.isEmpty())
        val gone = linker(Stub()).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A records Q1, which does not exist on Wikidata"), gone.errors)
    }

    @Test
    fun `an unrecorded item is linkable and an error in check mode`() {
        val f = entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.Found("Q7")))).check(data())
        assertEquals(listOf("Q7"), r.linkable.values.toList())
        assertEquals(f, r.linkable.keys.single().file)
        assertEquals(listOf("https://w3id.org/vip/rs/A: its Wikipedia article has Wikidata item Q7; run ./gradlew :app:linkWikidata and commit entities/a.ttl"), r.errors)
    }

    @Test
    fun `an article without item and no record is only a note`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.NoItem))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A: no Wikidata item yet for $sm"), r.notes)
        assertTrue(r.errors.isEmpty() && r.linkable.isEmpty())
    }

    @Test
    fun `a missing article without a record is only a warning`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> .\n")
        val r = linker(Stub()).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A: Wikipedia article $sm does not exist"), r.warnings)
        assertTrue(r.errors.isEmpty())
    }

    @Test
    fun `articles that disagree are an error`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> , <$sm2> .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.Found("Q2"), sm2 to ArticleItem.Found("Q1")))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A has Wikipedia articles that belong to different Wikidata items: Q1, Q2"), r.errors)
        assertTrue(r.linkable.isEmpty())
    }

    @Test
    fun `two articles with the same item resolve once`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> , <$sm2> .\n")
        val r = linker(Stub(mapOf(sm to ArticleItem.Found("Q1"), sm2 to ArticleItem.Found("Q1")))).link(data())
        assertEquals(1, r.linked.size)
    }

    @Test
    fun `an item without article must exist`() {
        entity("a", "rs:A a foaf:Person ; vip:wikidataItem ${item("Q5")} .\n")
        assertTrue(linker(Stub(existing = setOf("Q5"))).check(data()).let { it.errors.isEmpty() && it.warnings.isEmpty() })
        assertEquals(listOf("https://w3id.org/vip/rs/A records Q5, which does not exist on Wikidata"), linker(Stub()).check(data()).errors)
    }

    @Test
    fun `a failing lookup is only a warning`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> .\n")
        entity("b", "rs:B a foaf:Person ; vip:wikipediaArticle <$sm2> .\n")
        val r = linker(Stub(mapOf(sm2 to ArticleItem.Found("Q3")), failing = setOf(sm))).check(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A: could not ask Wikipedia/Wikidata: Wikipedia API answered HTTP 503"), r.warnings)
        assertEquals(1, r.errors.size)
        assertEquals(1, r.linkable.size)
    }

    private val found = Stub(mapOf(sm to ArticleItem.Found("Q9")))

    @Test
    fun `link inserts the item after an article line ending with a semicolon and keeps everything else byte for byte`() {
        val body = "# keep me\nrs:A a foaf:Person ;\n    foaf:name \"A\"@sr ;\n\t  vip:wikipediaArticle <$sm> ;   \n    foaf:firstName \"S\" . # tail\n"
        val f = entity("a", body)
        val log = mutableListOf<String>()
        val outcome = linker(found, log).link(data())
        assertEquals(listOf("https://w3id.org/vip/rs/A"), outcome.linked)
        assertEquals(prefixes + "# keep me\nrs:A a foaf:Person ;\n    foaf:name \"A\"@sr ;\n\t  vip:wikipediaArticle <$sm> ;   \n\t  vip:wikidataItem ${item("Q9")} ;\n    foaf:firstName \"S\" . # tail\n", f.readText())
        assertEquals(listOf("linked https://w3id.org/vip/rs/A -> http://www.wikidata.org/entity/Q9 (entities/a.ttl)"), log)
    }

    @Test
    fun `link handles an article line that ends the statement`() {
        val f = entity("a", "rs:A a foaf:Person ;\n    vip:wikipediaArticle <$sm> .\n")
        val outcome = linker(found).link(data())
        assertEquals(emptyMap(), outcome.failed)
        assertEquals(prefixes + "rs:A a foaf:Person ;\n    vip:wikipediaArticle <$sm> ;\n    vip:wikidataItem ${item("Q9")} .\n", f.readText())
        // running again changes nothing and finds nothing to do
        assertEquals(emptyList(), linker(found).link(data()).linked)
    }

    @Test
    fun `link does not report the run-link-again error`() {
        entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle <$sm> .\n")
        assertTrue(linker(found).link(data()).report.errors.isEmpty())
    }

    @Test
    fun `link leaves a file untouched when the article line ends neither way`() {
        val f = entity("a", "rs:A a foaf:Person ; vip:wikipediaArticle\n    <$sm> .\n")
        val before = f.readText()
        val outcome = linker(found).link(data())
        assertEquals(before, f.readText())
        assertEquals(mapOf("https://w3id.org/vip/rs/A" to "entities/a.ttl: could not find the vip:wikipediaArticle line to put the item after"), outcome.failed)
        assertTrue(outcome.linked.isEmpty())
    }

    @Test
    fun `link restores the file when it no longer parses`() {
        val f = entity("a", "rs:A a foaf:Person ;\n    # vip:wikipediaArticle <x> .\n    vip:wikipediaArticle <$sm> ;\n    foaf:name \"A\" .\n")
        val before = f.readText()
        val outcome = linker(found).link(data())
        assertEquals(before, f.readText())
        assertTrue(outcome.failed.getValue("https://w3id.org/vip/rs/A").contains("left unchanged"), outcome.failed.toString())
    }
}
