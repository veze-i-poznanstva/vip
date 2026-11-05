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

    private fun convert(ttl: String, archivesTtl: String = ""): List<CytoscapeElement> {
        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(prefixes + "\n" + ttl.trimIndent()), null, "TTL")
        val archives = ModelFactory.createDefaultModel()
        archives.read(StringReader(
            "@prefix vip: <https://w3id.org/vip/vocab#> .\n@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" + archivesTtl), null, "TTL")
        return convertModelToCytoscape(model, schema, archives)
    }

    private fun doc(url: String, extra: String = "") =
        "[ a :Documentation ; :hasDocumentURL <$url> ${if (extra.isEmpty()) "" else "; $extra"} ]"

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
    fun `two connections with the same label between the same pair draw two edges, one per connection`() {
        val elements = convert("""
            ex:A a foaf:Person . ex:B a foaf:Person .
            _:c1 a :SocialConnection ; bio:participant ex:A, ex:B .
            _:c2 a :SocialConnection ; bio:participant ex:B, ex:A .
        """)
        assertEquals(2, elements.edges().size)
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

    @Test
    fun `an ownership edge runs from owner to owned even when the owner sorts later`() {
        val elements = convert("""
            ex:Z_Owner a :Company ; foaf:name "Z" .
            ex:A_Owned a :Company ; foaf:name "A" .
            ex:Ownership a vip:OwnershipConnection ; vip:owner ex:Z_Owner ; vip:ownedCompany ex:A_Owned .
        """)
        val edge = elements.edges().single()
        assertEquals("z_owner", edge.data.source)
        assertEquals("a_owned", edge.data.target)
    }

    @Test
    fun `a node carries the entity's own comment, Serbian first, and none when it has no comment`() {
        val elements = convert("""
            ex:Ana a foaf:Person ; rdfs:comment "An official"@en, "Funkcionerka"@sr .
            ex:Ivan a foaf:Person .
        """)
        val comments = elements.nodes().associate { it.data.id to it.data.comment }
        assertEquals("Funkcionerka", comments["ana"])
        assertNull(comments["ivan"])
    }

    @Test
    fun `a node links its declared Wikipedia article, Serbian first, and only an https Wikipedia address`() {
        val elements = convert("""
            ex:Both a foaf:Person ; vip:wikipediaArticle <https://en.wikipedia.org/wiki/A>, <https://sr.wikipedia.org/wiki/A> .
            ex:One a foaf:Person ; vip:wikipediaArticle <https://en.wikipedia.org/wiki/B> .
            ex:Fake a foaf:Person ; vip:wikipediaArticle <https://wikipedia.org.example/wiki/C>, <http://en.wikipedia.org/wiki/C> .
        """)
        val links = elements.nodes().associate { it.data.id to it.data.wikipedia }
        assertEquals("https://sr.wikipedia.org/wiki/A", links["both"])
        assertEquals("https://en.wikipedia.org/wiki/B", links["one"])
        assertNull(links["fake"])
    }

    @Test
    fun `a Wikipedia page that is only cited as a source is not linked as the entity's article`() {
        val elements = convert("""
            ex:Person a foaf:Person ; :hasDocumentation [ a :Documentation ; :hasDocumentURL <https://en.wikipedia.org/wiki/Company> ] .
        """)
        assertNull(elements.nodes().single().data.wikipedia)
    }

    @Test
    fun `a node carries its sources with url, domain, type, status and quote`() {
        val node = convert("""
            ex:Ana a foaf:Person ; :hasDocumentation [ a :Documentation ;
                :hasDocumentURL <https://www.Krik.rs/tag/ana/> ; :hasDocumentType vip:newsArticle ;
                :hasDocumentStatus vip:published ; rdfs:comment "Ana je ministarka"@sr, "Ana is a minister"@en ] .
        """).nodes().single()
        assertEquals(
            listOf(Source("https://www.Krik.rs/tag/ana/", "krik.rs", "newsArticle", "published", "Ana je ministarka")),
            node.data.sources
        )
    }

    @Test
    fun `a removed source is listed last and keeps its status`() {
        val sources = convert("""
            ex:Ana a foaf:Person ; :hasDocumentation
                ${doc("https://a.example/gone", ":hasDocumentStatus vip:removed")},
                ${doc("https://z.example/ok", ":hasDocumentStatus vip:published")},
                ${doc("https://b.example/archived", ":hasDocumentStatus vip:archived")} .
        """).nodes().single().data.sources!!
        assertEquals(listOf("published", "archived", "removed"), sources.map { it.status })
        assertEquals("https://a.example/gone", sources.last().url)
    }

    @Test
    fun `a source gets its archive snapshot and date from the archives model`() {
        val elements = convert("""
            ex:Ana a foaf:Person ; :hasDocumentation ${doc("https://a.example/x")}, ${doc("https://b.example/y")} .
        """, """
            <https://a.example/x> vip:archivedOn "2026-05-21"^^xsd:date ; vip:hasArchiveURL <http://web.archive.org/web/20260521145618/https://a.example/x> .
        """)
        val (archived, plain) = elements.nodes().single().data.sources!!
        assertEquals("http://web.archive.org/web/20260521145618/https://a.example/x", archived.archive)
        assertEquals("2026-05-21", archived.archivedOn)
        assertNull(plain.archive)
        assertNull(plain.archivedOn)
    }

    @Test
    fun `a node without documentation has no sources`() {
        assertNull(convert("ex:Ana a foaf:Person .").nodes().single().data.sources)
    }

    @Test
    fun `aliases collect the other names and the vip aliases, sorted`() {
        val node = convert("""
            ex:Ana a foaf:Person ; foaf:name "Ana Brnabić"@sr, "Ana Brnabic"@en ; :alias "Zlatna Ana", "Anči" .
            ex:Ivan a foaf:Person ; foaf:name "Ivan" .
        """).nodes()
        assertEquals(listOf("Ana Brnabic", "Anči", "Zlatna Ana"), node.first { it.data.id == "ana" }.data.aliases)
        assertNull(node.first { it.data.id == "ivan" }.data.aliases)
    }

    @Test
    fun `facts carry birth date and place with vocabulary labels and skip names, comments and types`() {
        val facts = convert("""
            @prefix dbo: <http://dbpedia.org/ontology/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            ex:Ana a foaf:Person ; foaf:name "Ana" ; foaf:firstName "Ana" ; :firstLastName "B" ; :alias "A" ;
                rdfs:label "L" ; rdfs:comment "C" ;
                dbo:birthPlace "Beograd"@sr ; dbo:birthDate "1975-09-28"^^xsd:date ; :hasTickerSymbol "ANA" .
        """).nodes().single().data.facts
        assertEquals(
            listOf(Fact("birthDate", "birth date", "1975-09-28"), Fact("birthPlace", "birth place", "Beograd"),
                Fact("hasTickerSymbol", "Ticker symbol", "ANA")),
            facts
        )
        assertNull(convert("ex:Ana a foaf:Person ; foaf:name \"Ana\" .").nodes().single().data.facts)
    }

    @Test
    fun `an edge carries the connection uri, kind, type label, start and end exactly as written`() {
        val data = convert("""
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            ex:A a foaf:Person . ex:B a :Company .
            ex:Own a vip:OwnershipConnection ; vip:owner ex:A ; bio:participant ex:A, ex:B ;
                :connectionStartDate "2016-04"^^xsd:gYearMonth ; :connectionEndDate "2018"^^xsd:gYear ;
                rdfs:comment "Vlasnik"@sr ; :hasDocumentation ${doc("https://a.example/x")} .
        """).edges().single().data
        assertEquals("https://w3id.org/vip/rs/Own", data.uri)
        assertEquals("OwnershipConnection", data.kind)
        assertEquals("Ownership connection", data.typeLabel)
        assertEquals("2016-04", data.start)
        assertEquals("2018", data.end)
        assertEquals("Vlasnik", data.comment)
        assertEquals("a.example", data.sources!!.single().domain)
    }

    @Test
    fun `an ownership edge carries its share`() {
        val data = convert("""
            ex:A a foaf:Person . ex:B a :Company .
            ex:Own a vip:OwnershipConnection ; vip:owner ex:A ; vip:ownedCompany ex:B ; vip:ownershipShare 50.0 .
            ex:Soc a :SocialConnection ; bio:participant ex:A, ex:B .
        """).edges().associateBy { it.data.kind }
        assertEquals(50.0, data["OwnershipConnection"]!!.data.share)
        assertNull(data["SocialConnection"]!!.data.share)
        assertNull(data["SocialConnection"]!!.data.roles)
    }

    @Test
    fun `a work edge carries its role labels`() {
        val data = convert("""
            ex:A a foaf:Person . ex:N a :NonProfitOrganization .
            _:w a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:N ;
                :hasWorkRole vip:founder, vip:boardChair .
        """).edges().single().data
        assertEquals(listOf("Chair of the board", "Founder"), data.roles)
        assertNull(data.uri)
    }

    @Test
    fun `two connections between the same pair are two edges with their own dates`() {
        val edges = convert("""
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            ex:A a foaf:Person . ex:B a :Company .
            ex:Own1 a vip:OwnershipConnection ; vip:owner ex:A ; vip:ownedCompany ex:B ; :connectionStartDate "2003"^^xsd:gYear .
            ex:Own2 a vip:OwnershipConnection ; vip:owner ex:A ; vip:ownedCompany ex:B ; :connectionStartDate "2010"^^xsd:gYear .
        """).edges()
        assertEquals(listOf("2003", "2010"), edges.map { it.data.start })
    }

    @Test
    fun `a direct property edge has no connection fields`() {
        val data = convert("""
            ex:P a :Company . ex:C a :Company .
            ex:C :subsidiaryCompanyOf ex:P .
        """).edges().single().data
        assertNull(data.uri)
        assertNull(data.kind)
        assertNull(data.sources)
        assertNull(data.file)
    }

    @Test
    fun `the most specific declared type wins when two are declared`() {
        val data = convert("""
            ex:A a foaf:Person . ex:B a :AcademicOrganization .
            ex:E a :Connection, :AlumniEducationalConnection, :EducationalConnection ; :hasStudent ex:A ; :hasAcademicOrganization ex:B .
        """).edges().single().data
        assertEquals("AlumniEducationalConnection", data.kind)
        assertEquals("Alumni", data.typeLabel)
    }

    private val tmp: java.io.File by lazy { java.nio.file.Files.createTempDirectory("cyto").toFile() }

    private fun convertFiles(files: Map<String, String>, input: (java.io.File) -> java.io.File = { it }, inputPath: String = "data"): List<CytoscapeElement> {
        val root = java.io.File(tmp, "d" + System.nanoTime()).apply { mkdirs() }
        files.forEach { (name, body) -> java.io.File(root, name).apply { parentFile.mkdirs() }.writeText(prefixes + "\n" + body.trimIndent() + "\n") }
        return convertDataSetToCytoscape(loadDataSet(input(root)), schema, ModelFactory.createDefaultModel(), inputPath)
    }

    @Test
    fun `file and line point at the declaring file and its line`() {
        val elements = convertFiles(mapOf(
            "entities/ana.ttl" to "# c\nex:Ana a foaf:Person .",
            "entities/acme.ttl" to "ex:Acme a :Company .",
            "cases/c.ttl" to "# first\n<https://w3id.org/vip/rs/Own> a vip:OwnershipConnection ; vip:owner ex:Ana ; vip:ownedCompany ex:Acme .\nex:Aff :subsidiaryCompanyOf ex:Acme ."
        ))
        val ana = elements.nodes().first { it.data.id == "ana" }.data
        assertEquals("data/entities/ana.ttl", ana.file)
        assertEquals(8, ana.line)
        val own = elements.edges().single().data
        assertEquals("data/cases/c.ttl", own.file)
        assertEquals(8, own.line)
    }

    @Test
    fun `line is null when the subject is written in a way the search does not find`() {
        val elements = convertFiles(mapOf("entities/two.ttl" to "ex:Ana a foaf:Person . ex:Ivan a foaf:Person ."))
        val byId = elements.nodes().associate { it.data.id to it.data }
        assertEquals(7, byId["ana"]!!.line)
        assertNull(byId["ivan"]!!.line)
        assertEquals("data/entities/two.ttl", byId["ivan"]!!.file)
    }

    @Test
    fun `a single input file is its own path`() {
        val elements = convertFiles(mapOf("one.ttl" to "ex:Ana a foaf:Person ."),
            input = { it.resolve("one.ttl") }, inputPath = "data/entities/one.ttl")
        assertEquals("data/entities/one.ttl", elements.nodes().single().data.file)
    }
}
