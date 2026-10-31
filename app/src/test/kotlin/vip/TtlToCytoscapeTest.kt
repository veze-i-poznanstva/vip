package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.StringReader

class TtlToCytoscapeTest {

    private val schema: Model by lazy { RDFDataMgr.loadModel("schema.ttl") }

    private val prefixes = """
        @prefix ex: <https://w3id.org/vip/rs/> .
        @prefix : <http://poderopedia.com/vocab/> .
        @prefix vip: <https://w3id.org/vip/vocab#> .
        @prefix foaf: <http://xmlns.com/foaf/0.1/> .
        @prefix bio: <http://purl.org/vocab/bio/0.1/> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
    """.trimIndent()

    private fun convert(ttl: String): List<CytoscapeElement> {
        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(prefixes + "\n" + ttl.trimIndent()), null, "TTL")
        return convertModelToCytoscape(model, schema)
    }

    private fun List<CytoscapeElement>.nodes() = filter { it.data.source == null }
    private fun List<CytoscapeElement>.edges() = filter { it.data.source != null }
    private fun List<CytoscapeElement>.edgeBetween(a: String, b: String) =
        edges().find { setOf(it.data.source, it.data.target) == setOf(a, b) }

    @Test
    fun `should create valid node IDs from URIs`() {
        assertEquals("person", createNodeId("http://example.org/Person"))
        assertEquals("john_doe", createNodeId("http://example.org/John_Doe"))
        assertTrue(createNodeId("https://example.com/").startsWith("node_")) // hash-based for empty
    }

    @Test
    fun `should type nodes through the vocabulary hierarchy`() {
        val elements = convert("""
            ex:John a foaf:Person ; foaf:name "John" .
            ex:Acme a :Company ; foaf:name "Acme" .
            ex:Harvard a :AcademicOrganization ; foaf:name "Harvard" .
            ex:NALED a :NonProfitOrganization ; foaf:name "NALED" .
            ex:SNS a :PoliticalOrganization ; foaf:name "SNS" .
        """)
        val types = elements.nodes().associate { it.data.id to it.data.nodeType }
        assertEquals("person", types["john"])
        assertEquals("organization", types["acme"])
        assertEquals("academic", types["harvard"])
        assertEquals("organization", types["naled"])
        assertEquals("party", types["sns"])
    }

    @Test
    fun `should label nodes by name and fall back to the URI`() {
        val elements = convert("""
            ex:Ana_Brnabic a foaf:Person ; foaf:name "Ana Brnabić"@sr .
            ex:Ivan_Bosnjak a foaf:Person .
        """)
        val labels = elements.nodes().associate { it.data.id to it.data.label }
        assertEquals("Ana Brnabić", labels["ana_brnabic"])
        assertEquals("Ivan Bosnjak", labels["ivan_bosnjak"])
    }

    @Test
    fun `a node with names in several languages always gets the Serbian one, then English, then any`() {
        val both = "ex:A a foaf:Person ; foaf:name \"Serbian Progressive Party\"@en, \"Srpska napredna stranka\"@sr ."
        repeat(3) { assertEquals("Srpska napredna stranka", convert(both).nodes().single().data.label) }
        val english = "ex:A a foaf:Person ; foaf:name \"Serbian Progressive Party\"@en, \"Partido Progresista Serbio\"@es ."
        repeat(3) { assertEquals("Serbian Progressive Party", convert(english).nodes().single().data.label) }
        val untagged = "ex:A a foaf:Person ; foaf:name \"Zeta\", \"Alpha\" ."
        repeat(3) { assertEquals("Alpha", convert(untagged).nodes().single().data.label) }
    }

    @Test
    fun `should not turn documentation, document URLs or connections into nodes`() {
        val elements = convert("""
            ex:Ana a foaf:Person ;
                :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://sr.wikipedia.org/wiki/Ana> ] .
            ex:Ivan a foaf:Person .
            ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan ;
                :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://www.krik.rs/tag/ana/> ] .
        """)
        assertEquals(setOf("ana", "ivan"), elements.nodes().map { it.data.id }.toSet())
    }

    @Test
    fun `should draw a political connection as an edge from the person to the party`() {
        val elements = convert("""
            ex:Sinisa_Mali a foaf:Person .
            ex:SNS a :PoliticalOrganization .
            ex:SinisaMali_SNS a :PoliticalConnection ;
                :personParticipant ex:Sinisa_Mali ;
                :politicalParticipant ex:SNS .
        """)
        val edge = elements.edgeBetween("sinisa_mali", "sns")
        assertNotNull(edge, "membership edge should exist")
        assertEquals("sinisa_mali", edge!!.data.source)
        assertEquals("sns", edge.data.target)
        assertEquals("political connection", edge.data.label)
        assertNull(elements.nodes().find { it.data.id == "sinisamali_sns" })
    }

    @Test
    fun `should draw an ownership connection from the owner to the company`() {
        val elements = convert("""
            ex:Ivan a foaf:Person .
            ex:Milenijum_Tim a :Company .
            _:own a vip:OwnershipConnection ; vip:owner ex:Ivan ; vip:ownedCompany ex:Milenijum_Tim ; vip:ownershipShare 50.0 .
        """)
        val edge = elements.edgeBetween("ivan", "milenijum_tim")
        assertNotNull(edge)
        assertEquals("ivan", edge!!.data.source)
        assertEquals("milenijum_tim", edge.data.target)
        assertEquals("ownership connection", edge.data.label)
        assertEquals(1, elements.edges().size)
    }

    @Test
    fun `should label a work connection by its roles`() {
        val elements = convert("""
            ex:Ana a foaf:Person .
            ex:NALED a :NonProfitOrganization .
            _:work a :WorkConnection ;
                bio:participant ex:Ana ;
                :hasEconomicOrganizationParticipant ex:NALED ;
                :hasWorkRole vip:founder, vip:boardChair .
        """)
        val edge = elements.edgeBetween("ana", "naled")
        assertNotNull(edge)
        assertEquals("ana", edge!!.data.source)
        assertEquals("chair of the board, founder", edge.data.label)
    }

    @Test
    fun `should prefer the connection's own label over its type`() {
        val elements = convert("""
            ex:Ana a foaf:Person .
            ex:Ivan a foaf:Person .
            ex:AnaIvan a :Connection ; bio:participant ex:Ana, ex:Ivan ;
                rdfs:label "poslovni partneri"@sr .
        """)
        assertEquals("poslovni partneri", elements.edgeBetween("ana", "ivan")!!.data.label)
    }

    @Test
    fun `should label an educational connection by its type`() {
        val elements = convert("""
            ex:Ana a foaf:Person .
            ex:Gimnazija a :AcademicOrganization .
            _:edu a :AlumniEducationalConnection ; :hasStudent ex:Ana ; :hasAcademicOrganization ex:Gimnazija .
        """)
        val edge = elements.edgeBetween("ana", "gimnazija")
        assertNotNull(edge)
        assertEquals("ana", edge!!.data.source)
        assertEquals("alumni", edge.data.label)
    }

    @Test
    fun `should connect every pair of participants of a connection with many participants`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person . ex:C a foaf:Person .
            _:c a :SocialConnection ; bio:participant ex:A, ex:B, ex:C .
        """)
        assertEquals(3, elements.edges().size)
        assertTrue(elements.edges().all { it.data.label == "social connection" })
    }

    @Test
    fun `should ignore participants that are not agents in the data`() {
        val elements = convert("""
            ex:A a foaf:Person .
            _:c a :Connection ; bio:participant ex:A, ex:Ghost .
        """)
        assertEquals(0, elements.edges().size)
    }

    @Test
    fun `should draw direct properties between agents labelled by the vocabulary`() {
        val elements = convert("""
            ex:Parent a :Company . ex:Child a :Company .
            ex:Child :subsidiaryCompanyOf ex:Parent .
        """)
        val edge = elements.edgeBetween("child", "parent")
        assertNotNull(edge)
        assertEquals("child", edge!!.data.source)
        assertEquals("subsidiary company of", edge.data.label)
    }

    @Test
    fun `should draw a symmetric property stated in both directions only once`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person .
            ex:A :hasBusinessPartner ex:B .
            ex:B :hasBusinessPartner ex:A .
        """)
        assertEquals(1, elements.edges().size)
        assertEquals("has business partner", elements.edges().first().data.label)
    }

    @Test
    fun `should not draw edges to literals, types or documentation`() {
        val elements = convert("""
            ex:A a foaf:Person ; foaf:name "A" ;
                :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://example.com/a> ] .
        """)
        assertEquals(0, elements.edges().size)
    }

    @Test
    fun `a direct property the vocabulary does not declare is not drawn, so an unsourced claim cannot slip into the graph`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:Acme a :Company .
            ex:A ex:tookBribesFrom ex:Acme .
        """)
        assertEquals(0, elements.edges().size)
    }

    @Test
    fun `two different URIs never share a node id`() {
        val elements = convert("""
            ex:Ana_B a foaf:Person . ex:Ana-B a foaf:Person . <https://w3id.org/vip/rs/ana_b> a foaf:Person .
        """)
        assertEquals(3, elements.nodes().map { it.data.id }.toSet().size, elements.nodes().map { it.data.id }.toString())
    }

    @Test
    fun `every edge has a distinct id`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person . ex:C a foaf:Person .
            _:c a :SocialConnection ; bio:participant ex:A, ex:B, ex:C .
        """)
        val ids = elements.edges().map { it.data.id }
        assertEquals(3, ids.size)
        assertEquals(3, ids.toSet().size, ids.toString())
    }

    @Test
    fun `nodes carry their URI and the instance type`() {
        val node = convert("ex:Ana a foaf:Person .").nodes().single()
        assertEquals("instance", node.data.type)
        assertEquals("https://w3id.org/vip/rs/Ana", node.data.uri)
    }

    @Test
    fun `an agent that is neither person nor organisation is typed default`() {
        assertEquals("default", convert("ex:Thing a foaf:Agent .").nodes().single().data.nodeType)
    }

    @Test
    fun `a node label prefers foaf name over rdfs label over dcterms title`() {
        val elements = convert("""
            @prefix dcterms: <http://purl.org/dc/terms/> .
            ex:A a foaf:Person ; foaf:name "Name" ; rdfs:label "Label" ; dcterms:title "Title" .
            ex:B a foaf:Person ; rdfs:label "Label" ; dcterms:title "Title" .
            ex:C a foaf:Person ; dcterms:title "Title" .
        """)
        val labels = elements.nodes().associate { it.data.id to it.data.label }
        assertEquals("Name", labels["a"])
        assertEquals("Label", labels["b"])
        assertEquals("Title", labels["c"])
    }

    @Test
    fun `a connection between two people points from the lower URI to the higher`() {
        val elements = convert("""
            ex:Zed a foaf:Person . ex:Abe a foaf:Person .
            _:c a :SocialConnection ; bio:participant ex:Zed, ex:Abe .
        """)
        val edge = elements.edges().single()
        assertEquals("abe", edge.data.source)
        assertEquals("zed", edge.data.target)
    }

    @Test
    fun `a participant stated through two properties is one participant`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person .
            _:c a :SocialConnection ; bio:participant ex:A, ex:B ; :personParticipant ex:A .
        """)
        assertEquals(1, elements.edges().size)
        assertTrue(elements.edges().none { it.data.source == it.data.target })
    }

    @Test
    fun `two different relations between the same pair are two edges`() {
        val elements = convert("""
            ex:A a :Company . ex:B a :Company .
            ex:A :subsidiaryCompanyOf ex:B .
            _:c a :SocialConnection ; bio:participant ex:A, ex:B .
        """)
        assertEquals(2, elements.edges().size)
        assertEquals(setOf("subsidiary company of", "social connection"), elements.edges().map { it.data.label }.toSet())
    }

    @Test
    fun `two connections with the same label between the same pair draw one edge`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person .
            _:c1 a :SocialConnection ; bio:participant ex:A, ex:B .
            _:c2 a :SocialConnection ; bio:participant ex:B, ex:A .
        """)
        assertEquals(1, elements.edges().size)
    }

    @Test
    fun `a blank-node person is not a node and not a participant`() {
        val elements = convert("""
            ex:A a foaf:Person .
            _:p a foaf:Person .
            _:c a :SocialConnection ; bio:participant ex:A, _:p .
        """)
        assertEquals(1, elements.nodes().size)
        assertEquals(0, elements.edges().size)
    }

    @Test
    fun `a connection without a declared type and without roles is labelled connected to`() {
        val elements = convert("""
            ex:Ana a foaf:Person . ex:NALED a :NonProfitOrganization .
            _:c :hasEconomicOrganizationParticipant ex:NALED ; bio:participant ex:Ana .
        """)
        assertEquals("connected to", elements.edges().single().data.label)
    }

    @Test
    fun `a work role the vocabulary does not label is dropped and the type label is used`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a :Company .
            _:c a :WorkConnection ; bio:participant ex:A, ex:B ; :hasWorkRole ex:Imaginary .
        """)
        assertEquals("work connection", elements.edges().single().data.label)
    }

    @Test
    fun `a connection's own label keeps its case while type labels are lowercased`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person .
            _:c a :SocialConnection ; rdfs:label "Poslovni Partneri" ; bio:participant ex:A, ex:B .
        """)
        assertEquals("Poslovni Partneri", elements.edges().single().data.label)
    }

    @Test
    fun `a local name of only punctuation gets a hash id`() {
        assertTrue(createNodeId("https://x.example/___").startsWith("node_"))
        assertTrue(createNodeId("https://x.example/--").startsWith("node_"))
        assertEquals("node_" + "https://x.example/___".hashCode().toString().replace("-", "n"), createNodeId("https://x.example/___"))
    }

    @Test
    fun `extractLabelFromUri splits camel case and underscores`() {
        assertEquals("Ana Brnabic", extractLabelFromUri("https://w3id.org/vip/rs/Ana_Brnabic"))
        assertEquals("Work Connection", extractLabelFromUri("http://poderopedia.com/vocab/WorkConnection"))
        assertEquals("Bar", extractLabelFromUri("http://example.org/vocab#Bar"))
    }

    @Test
    fun `an empty local name falls back to the URI`() {
        assertEquals("https://w3id.org/vip/rs/", extractLabelFromUri("https://w3id.org/vip/rs/"))
    }

    @Test
    fun `an acronym is kept whole and camel case still splits`() {
        assertEquals("NALED", extractLabelFromUri("https://w3id.org/vip/rs/NALED"))
        assertEquals("MT Gastel", extractLabelFromUri("https://w3id.org/vip/rs/MT_Gastel"))
        assertEquals("Sinisa Mali", extractLabelFromUri("https://w3id.org/vip/rs/SinisaMali"))
    }
}
