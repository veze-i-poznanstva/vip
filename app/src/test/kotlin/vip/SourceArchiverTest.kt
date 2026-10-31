package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceArchiverTest {

    private val PODER = Vocab.PODER

    private class FakeWayback(val known: Map<String, ArchiveSnapshot> = emptyMap(), val failing: Set<String> = emptySet()) : Wayback {
        val saved = mutableListOf<String>()
        val lookedUp = mutableListOf<String>()
        override fun lookup(url: String): ArchiveSnapshot? { lookedUp.add(url); return known[url] }
        override fun save(url: String): ArchiveSnapshot {
            if (url in failing) throw ArchiveException("Save Page Now answered HTTP 429")
            saved.add(url)
            return ArchiveSnapshot("http://web.archive.org/web/20261030120000/$url", "20261030120000")
        }
    }

    private fun Model.documented(localName: String, vararg urls: String, status: String = "published"): Resource {
        val entity = createResource("https://w3id.org/vip/rs/$localName").addProperty(RDF.type, createResource(Vocab.PERSON))
        for (url in urls) {
            val doc = createResource()
                .addProperty(RDF.type, createResource(PODER + "Documentation"))
                .addProperty(createProperty(Vocab.HAS_DOCUMENT_URL), createResource(url))
                .addProperty(createProperty(Vocab.HAS_DOCUMENT_STATUS), createResource(Vocab.VIP + status))
            entity.addProperty(createProperty(PODER + "hasDocumentation"), doc)
        }
        return entity
    }

    private fun archiver(wayback: Wayback) = SourceArchiver(wayback, pauseBetweenSavesMs = 0, log = {})

    @Test
    fun `every live document URL without a record is listed once`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://a.example/1", "https://shared.example/x")
        data.documented("B", "https://shared.example/x", "https://b.example/2")
        val archives = ModelFactory.createDefaultModel()

        val todo = archiver(FakeWayback()).unarchivedUrls(data, archives)
        assertEquals(setOf("https://a.example/1", "https://shared.example/x", "https://b.example/2"), todo.toSet())
        assertEquals(3, todo.size)
    }

    @Test
    fun `removed documents are not archived`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://dead.example/1", status = "removed")
        assertTrue(archiver(FakeWayback()).unarchivedUrls(data, ModelFactory.createDefaultModel()).isEmpty())
    }

    @Test
    fun `documents that already have a record are skipped`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://a.example/1", "https://b.example/2")
        val archives = ModelFactory.createDefaultModel()
        archiver(FakeWayback()).record(archives, "https://a.example/1", ArchiveSnapshot("http://web.archive.org/web/20200101000000/https://a.example/1", "20200101000000"))

        assertEquals(listOf("https://b.example/2"), archiver(FakeWayback()).unarchivedUrls(data, archives))
    }

    @Test
    fun `an existing snapshot is recorded without requesting a new capture`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://a.example/1")
        val archives = ModelFactory.createDefaultModel()
        val fake = FakeWayback(known = mapOf("https://a.example/1" to ArchiveSnapshot("http://web.archive.org/web/20261020063300/https://a.example/1", "20261020063300")))

        val outcome = archiver(fake).archive(data, archives)
        assertEquals(listOf("https://a.example/1"), outcome.foundExisting)
        assertTrue(fake.saved.isEmpty())
        val record = archives.getResource("https://a.example/1")
        assertEquals("http://web.archive.org/web/20261020063300/https://a.example/1", record.getProperty(archives.createProperty(Vocab.HAS_ARCHIVE_URL)).resource.uri)
        assertEquals("2026-10-20", record.getProperty(archives.createProperty(Vocab.ARCHIVED_ON)).literal.lexicalForm)
    }

    @Test
    fun `a never-archived page is captured and recorded`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://new.example/1")
        val archives = ModelFactory.createDefaultModel()
        val fake = FakeWayback()

        val outcome = archiver(fake).archive(data, archives)
        assertEquals(listOf("https://new.example/1"), fake.saved)
        assertEquals(listOf("https://new.example/1"), outcome.captured)
        assertEquals("2026-10-30", archives.getResource("https://new.example/1").getProperty(archives.createProperty(Vocab.ARCHIVED_ON)).literal.lexicalForm)
    }

    @Test
    fun `alreadyArchived counts the records that existed before the run for documents still cited`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://old.example/1", "https://new.example/2")
        val archives = ModelFactory.createDefaultModel()
        val snapshot = ArchiveSnapshot("http://web.archive.org/web/20200101000000/x", "20200101000000")
        archiver(FakeWayback()).record(archives, "https://old.example/1", snapshot)
        archiver(FakeWayback()).record(archives, "https://stale.example/no-longer-cited", snapshot)

        val outcome = archiver(FakeWayback()).archive(data, archives)
        assertEquals(1, outcome.alreadyArchived)
        assertEquals(listOf("https://new.example/2"), outcome.captured)
    }

    @Test
    fun `the archiver pauses between captures but not before the first and not after lookups`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://known.example/1", "https://new.example/2", "https://new.example/3")
        val fake = FakeWayback(known = mapOf("https://known.example/1" to ArchiveSnapshot("http://web.archive.org/web/20200101000000/x", "20200101000000")))
        val pauses = mutableListOf<Long>()

        SourceArchiver(fake, pauseBetweenSavesMs = 6000, log = {}, pause = { pauses.add(it) }).archive(data, ModelFactory.createDefaultModel())
        assertEquals(listOf(6000L), pauses)
    }

    @Test
    fun `captures beyond the per-run cap are left for a later run and reported`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://new.example/1", "https://new.example/2", "https://new.example/3")
        val fake = FakeWayback()

        val outcome = SourceArchiver(fake, pauseBetweenSavesMs = 0, log = {}, maxCapturesPerRun = 2).archive(data, ModelFactory.createDefaultModel())
        assertEquals(listOf("https://new.example/1", "https://new.example/2"), outcome.captured)
        assertEquals(listOf("https://new.example/3"), outcome.deferred)
        assertEquals(2, fake.saved.size)
    }

    @Test
    fun `a failed capture is reported and the others still succeed`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://ok.example/1", "https://busy.example/2")
        val archives = ModelFactory.createDefaultModel()
        val fake = FakeWayback(failing = setOf("https://busy.example/2"))

        val outcome = archiver(fake).archive(data, archives)
        assertEquals(listOf("https://ok.example/1"), outcome.captured)
        assertEquals(setOf("https://busy.example/2"), outcome.failed.keys)
        assertTrue(outcome.failed["https://busy.example/2"]!!.contains("429"))
        assertTrue(archives.getResource("https://busy.example/2").listProperties().toList().isEmpty())
    }

    @Test
    fun `the validator accepts a snapshot recorded by the archiver for a blocked page`() {
        val data = ModelFactory.createDefaultModel()
        val blocked = "https://blocked.example/p"
        val person = data.documented("A", "https://fine.example/1", blocked)
        archiver(FakeWayback()).archive(data, data)   // records straight into the data model, as the validator would see after loading both files

        val checker: UrlChecker = { url -> if (url == blocked) Pair(false, "HTTP 403") else Pair(true, "OK") }
        val result = VeracityValidation(checker, { null }).validateItem(person)
        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(1, result.warnings.size)
    }

    private val snapshot = ArchiveSnapshot("http://web.archive.org/web/20200101000000/x", "20200101000000")

    @Test
    fun `a failed lookup is reported and the others still proceed`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://busy.example/1", "https://ok.example/2")
        val inner = FakeWayback()
        val wayback = object : Wayback {
            override fun lookup(url: String): ArchiveSnapshot? =
                if (url.contains("busy")) throw ArchiveException("the archive is rate limiting us (HTTP 429)") else inner.lookup(url)
            override fun save(url: String) = inner.save(url)
        }

        val outcome = archiver(wayback).archive(data, ModelFactory.createDefaultModel())
        assertEquals(setOf("https://busy.example/1"), outcome.failed.keys)
        assertEquals(listOf("https://ok.example/2"), inner.saved)
    }

    @Test
    fun `the log names each archived and failed URL`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://ok.example/1", "https://busy.example/2")
        val lines = mutableListOf<String>()

        SourceArchiver(FakeWayback(failing = setOf("https://busy.example/2")), pauseBetweenSavesMs = 0, log = { lines.add(it) })
            .archive(data, ModelFactory.createDefaultModel())
        assertTrue(lines.any { it.startsWith("archived https://ok.example/1 -> http://web.archive.org/web/20261030120000/https://ok.example/1") }, lines.toString())
        assertTrue(lines.any { it.startsWith("FAILED  https://busy.example/2: ") && it.contains("429") }, lines.toString())
    }

    @Test
    fun `an exception without a message is still reported`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://x.example/1")
        val wayback = object : Wayback {
            override fun lookup(url: String): ArchiveSnapshot? = throw IllegalStateException()
            override fun save(url: String): ArchiveSnapshot = throw IllegalStateException()
        }

        val outcome = archiver(wayback).archive(data, ModelFactory.createDefaultModel())
        assertEquals("java.lang.IllegalStateException", outcome.failed["https://x.example/1"])
    }

    @Test
    fun `a recorded archive date is an xsd date`() {
        val archives = ModelFactory.createDefaultModel()
        archiver(FakeWayback()).record(archives, "https://a.example/1", snapshot)
        val literal = archives.getResource("https://a.example/1").getProperty(archives.createProperty(Vocab.ARCHIVED_ON)).literal
        assertEquals(org.apache.jena.datatypes.xsd.XSDDatatype.XSDdate.uri, literal.datatypeURI)
    }

    @Test
    fun `recording a URL twice keeps both archive URLs, record never replaces`() {
        val archives = ModelFactory.createDefaultModel()
        val second = ArchiveSnapshot("http://web.archive.org/web/20210101000000/x", "20210101000000")
        archiver(FakeWayback()).record(archives, "https://a.example/1", snapshot)
        archiver(FakeWayback()).record(archives, "https://a.example/1", second)

        val urls = archives.getResource("https://a.example/1").listProperties(archives.createProperty(Vocab.HAS_ARCHIVE_URL)).toList()
        assertEquals(2, urls.size)
    }

    @Test
    fun `loadOrEmpty gives an empty model for a missing file and the parsed model otherwise`() {
        val dir = java.nio.file.Files.createTempDirectory("loadOrEmpty").toFile()
        try {
            assertTrue(loadOrEmpty(java.io.File(dir, "missing.ttl")).isEmpty)
            val file = java.io.File(dir, "a.ttl")
            file.writeText("<https://a.example/1> <https://w3id.org/vip/vocab#archivedOn> \"x\" .\n")
            assertEquals(1L, loadOrEmpty(file).size())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a URL still published elsewhere is archived even if one citation is removed`() {
        val data = ModelFactory.createDefaultModel()
        data.documented("A", "https://shared.example/1", status = "removed")
        data.documented("B", "https://shared.example/1", status = "published")

        assertEquals(listOf("https://shared.example/1"), archiver(FakeWayback()).unarchivedUrls(data, ModelFactory.createDefaultModel()))
    }
}
