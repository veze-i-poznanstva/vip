package vip

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules that keep a dataset split into one file per case coherent. */
class DataLayoutCheckTest {

    @TempDir
    lateinit var dir: File

    private fun write(path: String, body: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(
        "@prefix ex: <http://example.org/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n@prefix : <http://poderopedia.com/vocab/> .\n@prefix vip: <https://w3id.org/vip/vocab#> .\n@prefix bio: <http://purl.org/vocab/bio/0.1/> .\n$body\n"
    )

    private val schema by lazy { org.apache.jena.riot.RDFDataMgr.loadModel("schema.ttl") }
    private fun problems(): List<String> = checkDataLayout(loadDataSet(dir), schema)

    @Test
    fun `an entity declared in two files is reported with both files`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; foaf:name \"Ana\" .")
        write("entities/ivan.ttl", "ex:Ivan a foaf:Person . ex:Ana a foaf:Person ; foaf:name \"Ana B.\" .")
        val found = problems().filter { it.contains("home") }
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("http://example.org/Ana"), found[0])
        assertTrue(found[0].contains("ana.ttl") && found[0].contains("ivan.ttl"), found[0])
    }

    @Test
    fun `a file may add connections to an entity declared in another file`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/ivan.ttl", "ex:Ivan a foaf:Person .")
        write("cases/case-b.ttl", "ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `a case that cuts across investigations may be a folder under cases with one file per public body`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/vlada.ttl", "ex:Vlada a :GovernmentOrganization .")
        write("entities/grad.ttl", "ex:Grad a :GovernmentOrganization .")
        write("cases/javne-funkcije/vlada.ttl", "ex:AnaVlada a :WorkConnection ; bio:participant ex:Ana, ex:Vlada .")
        write("cases/javne-funkcije/grad.ttl", "ex:AnaGrad a :WorkConnection ; bio:participant ex:Ana, ex:Grad .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `a person declared in a nested case file is still reported, nesting does not escape the rules`() {
        write("cases/javne-funkcije/vlada.ttl", "ex:Ana a foaf:Person .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found.single().contains("cases/javne-funkcije/vlada.ttl") && found.single().contains("entities/"), found.toString())
    }

    @Test
    fun `blank nodes such as documentation are not entities and may appear in every file`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/> ] .")
        write("entities/ivan.ttl", "ex:Ivan a foaf:Person ; :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://b.example/> ] .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `an entity referenced by a connection but declared in no file is reported with the referencing file`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/case-b.ttl", "ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("http://example.org/Ivan") && found[0].contains("case-b.ttl"), found[0])
    }

    @Test
    fun `vocabulary terms such as work roles and document types are not missing entities`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/naled.ttl", "ex:NALED a :NonProfitOrganization .")
        write("cases/case-a.ttl", "_:w a :WorkConnection ; bio:participant ex:Ana, ex:NALED ; :hasWorkRole vip:founder ; :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/> ; :hasDocumentType vip:newsArticle ; :hasDocumentStatus vip:published ] .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `a person or organisation declared in a case file is reported, entities belong under entities`() {
        write("cases/case-a.ttl", "ex:Ana a foaf:Person .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("http://example.org/Ana") && found[0].contains("cases/case-a.ttl") && found[0].contains("entities/"), found[0])
    }

    @Test
    fun `a connection declared in an entity file is reported, connections belong under cases`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person . ex:AnaAna a :SocialConnection ; bio:participant ex:Ana .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("http://example.org/AnaAna") && found[0].contains("entities/ana.ttl") && found[0].contains("cases/"), found[0])
    }

    @Test
    fun `an entity file is named after the URI of its entity, lowercase with hyphens`() {
        write("entities/sinisa-mali.ttl", "ex:Sinisa_Mali a foaf:Person .")
        write("entities/brnabic.ttl", "ex:Ana_Brnabic a foaf:Person .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("entities/brnabic.ttl") && found[0].contains("ana-brnabic.ttl"), found[0])
    }

    @Test
    fun `an entity file holds exactly one entity`() {
        write("entities/ana-brnabic.ttl", "ex:Ana_Brnabic a foaf:Person . ex:Ivan_Bosnjak a foaf:Person .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("entities/ana-brnabic.ttl") && found[0].contains("ivan-bosnjak.ttl"), found[0])
    }

    @Test
    fun `a turtle file outside entities and cases is reported`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("stray.ttl", "ex:Ivan a foaf:Person .")
        write("drafts/case-x.ttl", "ex:C a :Connection ; bio:participant ex:Ana .")
        val found = problems()
        assertEquals(2, found.size, found.toString())
        assertTrue(found.any { it.contains("stray.ttl") && it.contains("entities/") }, found.toString())
        assertTrue(found.any { it.contains("drafts/case-x.ttl") && it.contains("cases/") }, found.toString())
    }

    @Test
    fun `a type the vocabulary does not know is reported, so a misspelt class is not silently ignored`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Persno .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("foaf/0.1/Persno") && found[0].contains("entities/ana.ttl"), found[0])
    }

    @Test
    fun `vocabulary statements in a data file are reported, data may not reshape the schema`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person . foaf:Person <http://www.w3.org/2000/01/rdf-schema#subClassOf> :Connection .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("subClassOf") && found[0].contains("entities/ana.ttl"), found[0])
    }

    @Test
    fun `a direct property between two entities that the vocabulary does not declare is reported`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/acme.ttl", "ex:Acme a :Company .")
        write("cases/case-a.ttl", "ex:Ana ex:tookBribesFrom ex:Acme .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("tookBribesFrom") && found[0].contains("cases/case-a.ttl"), found[0])
    }

    @Test
    fun `typed resources that are neither agents nor connections, such as financial information, are left alone`() {
        write("entities/acme.ttl", "ex:Acme a :Company . ex:Acme_2024 a :FinancialInformation ; :hasFinancialInformationYear 2024 .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `problems name files by their path under the data root`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/ana.ttl", "ex:Ana a foaf:Person .")
        val found = problems()
        assertTrue(found.any { it.contains("cases/ana.ttl") && it.contains("entities/ana.ttl") }, found.toString())
    }

    @Test
    fun `duplicate declarations are reported with their relative paths`() {
        write("entities/x.ttl", "ex:X a foaf:Person .")
        write("cases/x.ttl", "ex:X a foaf:Person .")
        val found = problems().filter { it.contains("home") }
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("(cases/x.ttl, entities/x.ttl)"), found[0])
    }

    @Test
    fun `a missing entity referenced from two files names both`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/a.ttl", "ex:C1 a :Connection ; bio:participant ex:Ana, ex:Ghost .")
        write("cases/b.ttl", "ex:C2 a :Connection ; bio:participant ex:Ana, ex:Ghost .")
        val found = problems()
        assertEquals(1, found.size, found.toString())
        assertTrue(found[0].contains("cases/a.ttl, cases/b.ttl"), found[0])
    }

    @Test
    fun `an entity redeclared in a case file breaks two rules`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/c.ttl", "ex:Ana a foaf:Person .")
        val found = problems()
        assertEquals(2, found.size, found.toString())
        assertTrue(found.any { it.contains("one home file") })
        assertTrue(found.any { it.contains("belong in entities/") })
    }

    @Test
    fun `a URI-named documentation node in a case file is not reported, only agents are misplaced`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("cases/c.ttl", "ex:Doc a :Documentation ; :hasDocumentURL <https://a.example/> . ex:C a :Connection ; bio:participant ex:Ana .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `an ownership connection in a case file is not a misplaced entity`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person .")
        write("entities/firma.ttl", "ex:Firma a foaf:Organization .")
        write("cases/c.ttl", "ex:Own a vip:OwnershipConnection ; bio:participant ex:Ana, ex:Firma .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `entityFileName lowercases, hyphenates and handles hash URIs`() {
        assertEquals("sinisa-mali.ttl", entityFileName("https://w3id.org/vip/rs/Sinisa_Mali"))
        assertEquals("bar.ttl", entityFileName("http://example.org/vocab#Bar"))
        assertEquals("firma-d.o.o..ttl", entityFileName("https://w3id.org/vip/rs/Firma_D.O.O."))
    }

    @Test
    fun `an entity URI ending in a slash gets an empty name, just dot ttl`() {
        assertEquals(".ttl", entityFileName("https://w3id.org/vip/rs/"))
    }

    @Test
    fun `a Wikipedia article is a web address, not a missing entity`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; vip:wikipediaArticle <https://sr.wikipedia.org/wiki/Ana> .")
        assertEquals(emptyList(), problems())
    }

    @Test
    fun `a Wikidata item is a web address, not a missing entity`() {
        write("entities/ana.ttl", "ex:Ana a foaf:Person ; vip:wikidataItem <http://www.wikidata.org/entity/Q42> .")
        assertEquals(emptyList(), problems())
    }
}
