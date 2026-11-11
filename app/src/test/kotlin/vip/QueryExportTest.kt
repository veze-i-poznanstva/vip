package vip

import com.google.gson.JsonParser
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.File
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The data published for the browser's query engine: the records plus what the vocabulary entails of them. */
class QueryExportTest {

    private val schema: Model by lazy { RDFDataMgr.loadModel("schema.ttl") }

    private fun data(ttl: String): Model = ModelFactory.createDefaultModel().also {
        it.read(StringReader("""
            @prefix rs: <https://w3id.org/vip/rs/> .
            @prefix : <http://poderopedia.com/vocab/> .
            @prefix vip: <https://w3id.org/vip/vocab#> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            @prefix bio: <http://purl.org/vocab/bio/0.1/> .
            $ttl
        """.trimIndent()), null, "TTL")
    }

    private fun Model.has(s: String, p: String, o: String) =
        contains(createResource(s), createProperty(p), createResource(o))

    @Test
    fun `a sibling connection is also typed as a family connection and as a connection, and the data itself is kept`() {
        val out = entail(data("""
            rs:A a foaf:Person . rs:B a foaf:Person .
            rs:AB a vip:SiblingConnection ; bio:participant rs:A, rs:B .
        """), schema)
        assertTrue(out.has("https://w3id.org/vip/rs/AB", RDF.type.uri, Vocab.VIP + "SiblingConnection"))
        assertTrue(out.has("https://w3id.org/vip/rs/AB", RDF.type.uri, Vocab.VIP + "FamilyConnection"))
        assertTrue(out.has("https://w3id.org/vip/rs/AB", RDF.type.uri, Vocab.CONNECTION))
        assertTrue(out.has("https://w3id.org/vip/rs/AB", Vocab.PARTICIPANT, "https://w3id.org/vip/rs/A"))
    }

    @Test
    fun `a parent is also a participant, along the subproperty chain`() {
        val out = entail(data("""
            rs:P a foaf:Person . rs:C a foaf:Person .
            rs:PC a vip:ParentChildConnection ; vip:parent rs:P ; vip:child rs:C .
        """), schema)
        assertTrue(out.has("https://w3id.org/vip/rs/PC", Vocab.PARTICIPANT, "https://w3id.org/vip/rs/P"))
        assertTrue(out.has("https://w3id.org/vip/rs/PC", Vocab.PARTICIPANT, "https://w3id.org/vip/rs/C"))
    }

    @Test
    fun `neither the vocabulary nor RDFS housekeeping is exported`() {
        val out = entail(data("""rs:A a foaf:Person ."""), schema)
        assertFalse(out.has("https://w3id.org/vip/rs/A", RDF.type.uri, RDFS.Resource.uri))
        assertFalse(out.containsResource(out.createResource(Vocab.VIP + "FamilyConnection")))
        assertEquals(1, out.listSubjects().toList().size)
    }

    @Test
    fun `the exported Turtle is the same text on every run, so the published file changes only when the records do`() {
        val once = dataTurtle(entail(loadDataSet(java.io.File("data")).merged, schema))
        val again = dataTurtle(entail(loadDataSet(java.io.File("data")).merged, schema))
        assertEquals(once, again)
    }

    @Test
    fun `an entailed type that is only an anonymous class of the schema is left out, a bare label says nothing`() {
        val restriction = ModelFactory.createDefaultModel().read(("""
            @prefix owl: <http://www.w3.org/2002/07/owl#> . @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix rs: <https://w3id.org/vip/rs/> .
            rs:C rdfs:subClassOf [ a owl:Restriction ; owl:onProperty rs:p ; owl:someValuesFrom rs:D ] .
        """).byteInputStream(), null, "TTL")
        val turtle = dataTurtle(entail(data("rs:x a rs:C ."), restriction))
        val written = ModelFactory.createDefaultModel().read(turtle.byteInputStream(), null, "TTL")
        assertTrue(written.listObjects().toList().none { it.isAnon }, turtle)
        assertTrue(written.has("https://w3id.org/vip/rs/x", RDF.type.uri, "https://w3id.org/vip/rs/C"), turtle)
    }

    @Test
    fun `rules are exported as JSON with the title and the explanation split by language, and the query text`() {
        val rule = Rule("nepotism", "Nepotism / Nepotizam\nA spouse works there.\nTime: years meet.\n\nSupružnik radi tamo.\nVreme: godine se dodiruju.", "# Nepotism / Nepotizam\nSELECT * WHERE { ?s ?p ?o }")
        val exported = JsonParser.parseString(rulesJson(listOf(rule))).asJsonArray.single().asJsonObject
        assertEquals("nepotism", exported["name"].asString)
        assertEquals("Nepotism", exported["title"].asJsonObject["en"].asString)
        assertEquals("Nepotizam", exported["title"].asJsonObject["sr"].asString)
        assertEquals("A spouse works there. Time: years meet.", exported["explanation"].asJsonObject["en"].asString)
        assertEquals("Supružnik radi tamo. Vreme: godine se dodiruju.", exported["explanation"].asJsonObject["sr"].asString)
        assertTrue(exported["query"].asString.contains("SELECT * WHERE"))
    }

    @Test
    fun `every repository rule explains itself in both languages`() {
        val exported = JsonParser.parseString(rulesJson(loadRules(File("rules")))).asJsonArray
        assertEquals(listOf("nepotism", "owner-in-office", "schoolmates-in-power"), exported.map { it.asJsonObject["name"].asString })
        for (rule in exported.map { it.asJsonObject }) {
            for (language in listOf("en", "sr")) {
                assertTrue(rule["title"].asJsonObject[language].asString.isNotBlank(), rule.toString())
                assertTrue(rule["explanation"].asJsonObject[language].asString.length > 40, rule.toString())
            }
        }
    }

    @Test
    fun `the leads of each rule are exported as JSON, so the browser engine can be checked against the build`() {
        val rule = Rule("owner-in-office", "Owner in office / Vlasnik na funkciji\nA.\n\nB.", """
            PREFIX : <http://poderopedia.com/vocab/>
            PREFIX vip: <https://w3id.org/vip/vocab#>
            SELECT ?person ?company ?role WHERE { ?o vip:owner ?person ; vip:ownedCompany ?company OPTIONAL { ?o :hasWorkRole ?role } }
        """.trimIndent())
        val report = findRedFlags(data("""rs:OC a vip:OwnershipConnection ; vip:owner rs:A ; vip:ownedCompany rs:C ."""), schema, listOf(rule))
        val exported = JsonParser.parseString(leadsJson(report)).asJsonArray.single().asJsonObject
        assertEquals("owner-in-office", exported["name"].asString)
        assertEquals(listOf("person", "company", "role"), exported["variables"].asJsonArray.map { it.asString })
        val match = exported["matches"].asJsonArray.single().asJsonObject
        assertEquals("https://w3id.org/vip/rs/A", match["person"].asString)
        assertEquals("https://w3id.org/vip/rs/C", match["company"].asString)
        assertFalse(match.has("role"))
    }
}
