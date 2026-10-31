package vip

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF

class VeracityValidationTest {

    private val EX_NS = "https://w3id.org/vip/rs/"
    private val PODER = Vocab.PODER
    private val FOAF = Vocab.FOAF

    /** Stub URL checker: every URL counts as reachable, so tests stay offline and deterministic. */
    private val allReachable: UrlChecker = { Pair(true, "OK") }

    /** Stub archive: nothing is archived unless a test says otherwise. */
    private val noArchive: ArchiveLookup = { null }

    private fun validator(checker: UrlChecker = allReachable, archive: ArchiveLookup = noArchive) =
        VeracityValidation(checker, archive)

    private fun blockedExcept(vararg fine: String): UrlChecker = { url -> if (url in fine) Pair(true, "OK") else Pair(false, "HTTP 403") }

    private val schema: Model by lazy { RDFDataMgr.loadModel("schema.ttl") }

    /** Attaches one Documentation node per URL through the given property. */
    private fun Resource.withDocumentation(vararg urls: String, via: String = PODER + "hasDocumentation"): Resource {
        val m = model
        for (url in urls) {
            val doc = m.createResource()
                .addProperty(RDF.type, m.createResource(PODER + "Documentation"))
                .addProperty(m.createProperty(PODER + "hasDocumentURL"), m.createResource(url))
            addProperty(m.createProperty(via), doc)
        }
        return this
    }

    private fun Model.person(localName: String): Resource =
        createResource(EX_NS + localName)
            .addProperty(RDF.type, createResource(FOAF + "Person"))
            .addProperty(createProperty(FOAF + "firstName"), localName)
            .addProperty(createProperty(PODER + "firstLastName"), "Test")

    @Test
    fun `extractDomain should extract domain from URL correctly`() {
        val validator = VeracityValidation()

        assertEquals("example.com", validator.extractDomain("https://example.com/page"))
        assertEquals("example.com", validator.extractDomain("http://example.com/page"))
        assertEquals("example.com", validator.extractDomain("https://www.example.com/page"))
        assertEquals("subdomain.example.com", validator.extractDomain("https://subdomain.example.com/page"))
        assertEquals("example.co.uk", validator.extractDomain("https://www.example.co.uk/page"))
        assertEquals("localhost", validator.extractDomain("http://localhost:8080/page"))
        assertEquals("sr.wikipedia.org", validator.extractDomain("https://sr.wikipedia.org/wiki/page"))
        assertEquals("example.com", validator.extractDomain("https://WWW.Example.COM./page"), "case and a trailing dot do not make a new domain")
        assertEquals("", validator.extractDomain("not a url"), "no host, no domain")
        assertEquals("istinomer.rs", validator.extractDomain("https://www.istinomer.rs/akter/someone"))
    }

    @Test
    fun `validateItem should pass with two documents from different domains`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("TestPerson").withDocumentation("https://wikipedia.org/page1", "https://example.com/page2")

        val result = validator().validateItem(person)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validateItem should pass with www and non-www URLs from different domains`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("TestPerson").withDocumentation("https://www.wikipedia.org/page1", "https://example.com/page2")

        val result = validator().validateItem(person)
        assertTrue(result.isValid)
    }

    @Test
    fun `validateItem should fail with a single document`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("Sinisa_Mali").withDocumentation("https://sr.wikipedia.org/wiki/Синиша_Мали")

        val result = validator().validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("only 1 URL"))
    }

    @Test
    fun `validateItem should fail when all documents come from one domain`() {
        val model = ModelFactory.createDefaultModel()
        val connection = model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalConnection"))
            .withDocumentation("https://www.istinomer.rs/akter/ana-brnabic/", "https://www.istinomer.rs/akter/ivan-bosnjak/")

        val result = validator().validateItem(connection)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("from only 1 domain"))
    }

    @Test
    fun `validateItem should treat www and non-www as the same domain`() {
        val model = ModelFactory.createDefaultModel()
        val item = model.createResource(EX_NS + "TestItem").withDocumentation("https://www.example.com/page1", "https://example.com/page2")

        val result = validator().validateItem(item)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("from only 1 domain"))
    }

    @Test
    fun `validateItem should fail when there is no documentation at all`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("PersonWithoutDocs")
        person.addProperty(model.createProperty(FOAF + "name"), "Test Person")

        val result = validator().validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("has no documentation"))
    }

    @Test
    fun `validateItem should ignore URLs that are not wrapped in a Documentation node`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("Bare")
        person.addProperty(model.createProperty(FOAF + "page"), model.createResource("https://example.com/profile"))

        val result = validator().validateItem(person)
        assertFalse(result.isValid)
        assertTrue(result.errors[0].contains("has no documentation"))
    }

    @Test
    fun `validateItem should count documentation attached through a specialised property`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("Sanctioned")
            .withDocumentation("https://court.example.org/ruling", via = PODER + "hasSanctionDocumentation")
            .withDocumentation("https://news.example.com/story", via = PODER + "hasOtherDocumentation")

        val result = validator().validateItem(person)
        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun `validateItem should fail when a URL is not accessible`() {
        val unreachable = "https://example.com/missing"
        val checker: UrlChecker = { url -> if (url == unreachable) Pair(false, "HTTP 404") else Pair(true, "OK") }
        val model = ModelFactory.createDefaultModel()
        val person = model.person("TestPerson").withDocumentation("https://wikipedia.org/page1", unreachable)

        val result = validator(checker).validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("inaccessible URLs"))
        assertTrue(result.errors[0].contains("$unreachable (HTTP 404, no archived copy)"))
    }

    @Test
    fun `validate without schema should report every documented resource that breaks the rule`() {
        val model = ModelFactory.createDefaultModel()
        model.person("Valid").withDocumentation("https://wikipedia.org/person", "https://example.com/person")
        model.person("OneUrl").withDocumentation("https://wikipedia.org/person1")
        model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalConnection"))
            .withDocumentation("https://istinomer.rs/page1", "https://www.istinomer.rs/page2")

        val result = validator().validate(model)
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validate without schema should pass for a model whose documented resources all comply`() {
        val model = ModelFactory.createDefaultModel()
        model.person("Valid").withDocumentation("https://wikipedia.org/person", "https://example.com/person")
        model.createResource(EX_NS + "ValidCompany")
            .addProperty(RDF.type, model.createResource(PODER + "Company"))
            .withDocumentation("https://sr.wikipedia.org/wiki/Company", "https://www.companywall.rs/firma/company")

        val result = validator().validate(model)
        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun `validate with schema should report an agent that has no documentation`() {
        val model = ModelFactory.createDefaultModel()
        model.person("Documented").withDocumentation("https://wikipedia.org/a", "https://example.com/a")
        model.createResource(EX_NS + "Silent_School")
            .addProperty(RDF.type, model.createResource(PODER + "AcademicOrganization"))
            .addProperty(model.createProperty(FOAF + "name"), "Silent School")

        val result = validator().validate(model, schema)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("Silent_School"))
        assertTrue(result.errors[0].contains("has no documentation"))
    }

    @Test
    fun `validate with schema should report a connection that has no documentation`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("Member").withDocumentation("https://wikipedia.org/m", "https://example.com/m")
        val party = model.createResource(EX_NS + "Party")
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalOrganization"))
            .withDocumentation("https://wikipedia.org/p", "https://example.com/p")
        model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalConnection"))
            .addProperty(model.createProperty(PODER + "personParticipant"), person)
            .addProperty(model.createProperty(PODER + "politicalParticipant"), party)

        val result = validator().validate(model, schema)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].startsWith("Blank node"))
        assertTrue(result.errors[0].contains("has no documentation"))
    }

    @Test
    fun `validate with schema should not treat Documentation nodes or the vocabulary itself as entities`() {
        val model = ModelFactory.createDefaultModel()
        model.person("Only").withDocumentation("https://wikipedia.org/a", "https://example.com/a")

        val result = validator().validate(model, schema)
        assertTrue(result.isValid, result.errors.joinToString())
    }

    @Test
    fun `validateItem should not count a document whose status is removed`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("Stale").withDocumentation("https://wikipedia.org/alive")
        val dead = model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "Documentation"))
            .addProperty(model.createProperty(PODER + "hasDocumentURL"), model.createResource("https://gone.example.com/page"))
            .addProperty(model.createProperty(PODER + "hasDocumentStatus"), model.createResource(Vocab.REMOVED))
        person.addProperty(model.createProperty(PODER + "hasDocumentation"), dead)

        val result = validator().validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("only 1 URL"))
    }

    @Test
    fun `a blocked URL with a recorded archive snapshot is accepted with a warning`() {
        val model = ModelFactory.createDefaultModel()
        val blocked = "https://www.istinomer.rs/akter/ana-brnabic/"
        val person = model.person("Ana").withDocumentation("https://sr.wikipedia.org/wiki/Ana", blocked)
        model.getResource(blocked)
            .addProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL), model.createResource("http://web.archive.org/web/20261020063300/$blocked"))
            .addProperty(model.createProperty(Vocab.ARCHIVED_ON), model.createTypedLiteral("2026-10-20", "http://www.w3.org/2001/XMLSchema#date"))

        val result = validator(blockedExcept("https://sr.wikipedia.org/wiki/Ana")).validateItem(person)
        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("HTTP 403"))
        assertTrue(result.warnings[0].contains("2026-10-20"))
        assertTrue(result.warnings[0].contains("web.archive.org/web/20261020063300"))
    }

    @Test
    fun `an archive record that is not a Wayback Machine snapshot is an error, not evidence`() {
        val model = ModelFactory.createDefaultModel()
        val dead = "https://gone.example/article"
        val person = model.person("Ana").withDocumentation("https://sr.wikipedia.org/wiki/Ana", dead)
        model.getResource(dead).addProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL), model.createResource("https://attacker.example/copy-of-article"))

        val result = validator(blockedExcept("https://sr.wikipedia.org/wiki/Ana")).validateItem(person)
        assertFalse(result.isValid)
        assertTrue(result.errors.single().contains("attacker.example/copy-of-article"), result.errors.toString())
        assertTrue(result.errors.single().contains("not a Wayback Machine snapshot"), result.errors.toString())
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `an archive record without a capture timestamp is an error, not a crash`() {
        val model = ModelFactory.createDefaultModel()
        val dead = "https://gone.example/article"
        val person = model.person("Ana").withDocumentation("https://sr.wikipedia.org/wiki/Ana", dead)
        model.getResource(dead)
            .addProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL), model.createResource("https://web.archive.org/web/$dead"))
            .addProperty(model.createProperty(Vocab.ARCHIVED_ON), "sometime")

        val result = validator(blockedExcept("https://sr.wikipedia.org/wiki/Ana")).validateItem(person)
        assertFalse(result.isValid)
        assertTrue(result.errors.single().contains("not a Wayback Machine snapshot"), result.errors.toString())
    }

    @Test
    fun `a recorded archive is used without asking the archive, so CI does not depend on it`() {
        val model = ModelFactory.createDefaultModel()
        val blocked = "https://www.istinomer.rs/akter/ana-brnabic/"
        val person = model.person("Ana").withDocumentation("https://sr.wikipedia.org/wiki/Ana", blocked)
        model.getResource(blocked).addProperty(model.createProperty(Vocab.HAS_ARCHIVE_URL), model.createResource("http://web.archive.org/web/20261020063300/$blocked"))
        var lookups = 0
        val archive: ArchiveLookup = { lookups++; null }

        val result = validator(blockedExcept("https://sr.wikipedia.org/wiki/Ana"), archive).validateItem(person)
        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(0, lookups)
    }

    @Test
    fun `a blocked URL without a record falls back to looking the archive up`() {
        val model = ModelFactory.createDefaultModel()
        val blocked = "https://www.sns.org.rs/"
        val person = model.person("Party").withDocumentation("https://sr.wikipedia.org/wiki/SNS", blocked)
        val lookedUp = mutableListOf<String>()
        val archive: ArchiveLookup = { url -> lookedUp.add(url); ArchiveSnapshot("http://web.archive.org/web/20251023082323/$url", "20251023082323") }

        val result = validator(blockedExcept("https://sr.wikipedia.org/wiki/SNS"), archive).validateItem(person)
        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(listOf(blocked), lookedUp)
        assertTrue(result.warnings[0].contains("2025-10-23"))
    }

    @Test
    fun `a dead URL that no archive has ever seen is an error`() {
        val model = ModelFactory.createDefaultModel()
        val dead = "https://www.krik.rs/tag/roberts-milenijum/"
        val person = model.person("Co").withDocumentation("https://www.danas.rs/x", dead)

        val result = validator(blockedExcept("https://www.danas.rs/x")).validateItem(person)
        assertFalse(result.isValid)
        assertTrue(result.errors[0].contains("$dead (HTTP 403, no archived copy)"))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `an archive that is itself unreachable does not break validation`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://a.example/1", "https://b.example/2")
        val failing: ArchiveLookup = { throw ArchiveException("archive down") }

        val result = validator(blockedExcept("https://a.example/1"), failing).validateItem(person)
        assertFalse(result.isValid)
        assertTrue(result.errors[0].contains("no archived copy"))
    }

    @Test
    fun `live URLs are never looked up in the archive`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://a.example/1", "https://b.example/2")
        var lookups = 0
        val counting: ArchiveLookup = { lookups++; null }

        val result = validator(allReachable, counting).validateItem(person)
        assertTrue(result.isValid)
        assertEquals(0, lookups)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `a URL cited by several entities is checked once per run`() {
        val model = ModelFactory.createDefaultModel()
        val shared = "https://www.istinomer.rs/akter/ana-brnabic/"
        model.person("Ana").withDocumentation("https://sr.wikipedia.org/wiki/Ana", shared)
        model.person("Ivan").withDocumentation("https://sr.wikipedia.org/wiki/Ivan", shared)
        val checked = mutableListOf<String>()
        val counting: UrlChecker = { url -> checked.add(url); Pair(true, "OK") }

        validator(counting).validate(model, schema)
        assertEquals(1, checked.count { it == shared }, checked.toString())
    }

    @Test
    fun `validate collects warnings from every entity`() {
        val model = ModelFactory.createDefaultModel()
        model.person("A").withDocumentation("https://a.example/1", "https://blocked.example/a")
        model.person("B").withDocumentation("https://b.example/1", "https://blocked.example/b")
        val archive: ArchiveLookup = { url -> ArchiveSnapshot("http://web.archive.org/web/20260101000000/$url", "20260101000000") }

        val result = validator(blockedExcept("https://a.example/1", "https://b.example/1"), archive).validate(model)
        assertTrue(result.isValid)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `a single dead URL is reported for both rules`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://dead.example/1")

        val result = validator(blockedExcept()).validateItem(person)
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size, result.errors.toString())
        assertTrue(result.errors.any { it.contains("only 1 URL") })
        assertTrue(result.errors.any { it.contains("inaccessible URLs") })
    }

    @Test
    fun `one archived and one dead URL give one warning and one error`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://archived.example/1", "https://dead.example/2")
        val archive: ArchiveLookup = { url ->
            if (url.contains("archived.example")) ArchiveSnapshot("http://web.archive.org/web/20260101000000/$url", "20260101000000") else null
        }

        val result = validator(blockedExcept(), archive).validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.warnings.size)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("https://dead.example/2"))
        assertFalse(result.errors[0].contains("https://archived.example/1"))
    }

    @Test
    fun `every inaccessible URL is listed in the error`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://a.example/1", "https://b.example/2")

        val result = validator(blockedExcept()).validateItem(person)
        val error = result.errors.single()
        assertTrue(error.contains("https://a.example/1 (HTTP 403, no archived copy)"), error)
        assertTrue(error.contains("https://b.example/2 (HTTP 403, no archived copy)"), error)
        assertTrue(error.contains("), https://"), "entries are comma separated: $error")
    }

    @Test
    fun `the same URL cited twice counts once`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://a.example/1", "https://a.example/1")

        val result = validator().validateItem(person)
        assertEquals(listOf("https://a.example/1"), validator().documentationUrls(person))
        assertTrue(result.errors.single().contains("only 1 URL"), result.errors.toString())
    }

    @Test
    fun `a documentation node without a URL is no evidence`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P").withDocumentation("https://a.example/1")
        person.addProperty(model.createProperty(PODER + "hasDocumentation"),
            model.createResource().addProperty(RDF.type, model.createResource(PODER + "Documentation")))

        val result = validator().validateItem(person)
        assertEquals(listOf("https://a.example/1"), validator().documentationUrls(person))
        assertTrue(result.errors.single().contains("only 1 URL"))
    }

    @Test
    fun `documents with status published or archived remain evidence`() {
        val model = ModelFactory.createDefaultModel()
        val person = model.person("P")
        for ((url, status) in listOf("https://a.example/1" to "published", "https://b.example/2" to "archived")) {
            val doc = model.createResource()
                .addProperty(RDF.type, model.createResource(PODER + "Documentation"))
                .addProperty(model.createProperty(PODER + "hasDocumentURL"), model.createResource(url))
                .addProperty(model.createProperty(Vocab.HAS_DOCUMENT_STATUS), model.createResource(Vocab.VIP + status))
            person.addProperty(model.createProperty(PODER + "hasDocumentation"), doc)
        }

        assertEquals(setOf("https://a.example/1", "https://b.example/2"), validator().documentationUrls(person).toSet())
        assertTrue(validator().validateItem(person).isValid)
    }

    @Test
    fun `an empty dataset is valid and reports nothing`() {
        for (result in listOf(validator().validate(ModelFactory.createDefaultModel()), validator().validate(ModelFactory.createDefaultModel(), schema))) {
            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
            assertTrue(result.warnings.isEmpty())
        }
    }

    @Test
    fun `a labelled blank-node connection is named by its label in errors`() {
        val model = ModelFactory.createDefaultModel()
        val connection = model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalConnection"))
            .addProperty(org.apache.jena.vocabulary.RDFS.label, "poslovni partneri")
            .withDocumentation("https://a.example/1")

        val result = validator().validateItem(connection)
        assertTrue(result.errors.single().startsWith("Blank node \"poslovni partneri\""), result.errors.toString())
    }

    @Test
    fun `an untyped participant referenced by a connection is an agent that needs documentation`() {
        val model = ModelFactory.createDefaultModel()
        val untyped = model.createResource(EX_NS + "Untyped")
        model.createResource()
            .addProperty(RDF.type, model.createResource(PODER + "PoliticalConnection"))
            .addProperty(model.createProperty(Vocab.PARTICIPANT), untyped)
            .withDocumentation("https://a.example/1", "https://b.example/2")

        val result = validator().validate(model, schema)
        assertFalse(result.isValid)
        assertEquals(listOf("${EX_NS}Untyped has no documentation"), result.errors)
    }
}
