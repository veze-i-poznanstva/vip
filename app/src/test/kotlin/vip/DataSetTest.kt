package vip

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DataSetTest {

    @TempDir
    lateinit var dir: File

    private fun write(name: String, body: String) = File(dir, name).apply {
        writeText("@prefix ex: <http://example.org/> .\n@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n$body\n")
    }

    @Test
    fun `loads every turtle file in a directory into one model`() {
        write("a.ttl", "ex:A a foaf:Person .")
        write("b.ttl", "ex:B a foaf:Person .")
        val dataSet = loadDataSet(dir)
        assertEquals(2, dataSet.files.size)
        assertEquals(2, dataSet.merged.listSubjects().toList().size)
    }

    @Test
    fun `a directory with no turtle files is an error, not an empty dataset`() {
        File(dir, "notes.md").writeText("nothing here")
        val e = kotlin.test.assertFailsWith<IllegalArgumentException> { loadDataSet(dir) }
        kotlin.test.assertTrue(e.message!!.contains(dir.name), e.message)
    }

    @Test
    fun `a single file path loads just that file`() {
        val file = write("only.ttl", "ex:A a foaf:Person .")
        write("other.ttl", "ex:B a foaf:Person .")
        val dataSet = loadDataSet(file)
        assertEquals(listOf(file), dataSet.files)
        assertEquals(1, dataSet.merged.listSubjects().toList().size)
    }

    @Test
    fun `files other than turtle are ignored and turtle files come in name order`() {
        write("b.ttl", "ex:B a foaf:Person .")
        write("a.ttl", "ex:A a foaf:Person .")
        File(dir, "README.md").writeText("about this case")
        assertEquals(listOf("a.ttl", "b.ttl"), loadDataSet(dir).files.map { it.name })
    }

    @Test
    fun `turtle files in subdirectories are loaded too, ordered by their path`() {
        File(dir, "entities").mkdirs(); File(dir, "cases").mkdirs()
        File(dir, "entities/ana.ttl").writeText("@prefix ex: <http://example.org/> . ex:Ana a <http://xmlns.com/foaf/0.1/Person> .")
        File(dir, "cases/case-a.ttl").writeText("@prefix ex: <http://example.org/> . ex:C a <http://poderopedia.com/vocab/Connection> .")
        val dataSet = loadDataSet(dir)
        assertEquals(listOf("cases/case-a.ttl", "entities/ana.ttl"), dataSet.files.map { it.relativeTo(dir).path })
        assertEquals(2, dataSet.merged.listSubjects().toList().size)
    }

    @Test
    fun `a syntax error names the file it is in`() {
        write("good.ttl", "ex:A a foaf:Person .")
        File(dir, "bad.ttl").writeText("this is not turtle")
        val e = kotlin.test.assertFailsWith<IllegalArgumentException> { loadDataSet(dir) }
        kotlin.test.assertTrue(e.message!!.contains("bad.ttl"), e.message)
    }

    @Test
    fun `symbolic links are not followed`() {
        write("a.ttl", "ex:A a foaf:Person .")
        val outside = File(dir.parentFile, dir.name + "-outside.ttl").apply { writeText("not turtle at all") }
        java.nio.file.Files.createSymbolicLink(File(dir, "link.ttl").toPath(), outside.toPath())
        try {
            assertEquals(listOf("a.ttl"), loadDataSet(dir).files.map { it.name })
        } finally { outside.delete() }
    }

    @Test
    fun `only files ending in lowercase dot ttl are loaded`() {
        write("a.ttl", "ex:A a foaf:Person .")
        write("notes.ttl.bak", "ex:B a foaf:Person .")
        write("upper.TTL", "ex:C a foaf:Person .")
        assertEquals(listOf("a.ttl"), loadDataSet(dir).files.map { it.name })
    }

    @Test
    fun `a file nested below a top-level folder belongs to that folder`() {
        File(dir, "cases/2025").mkdirs()
        val nested = File(dir, "cases/2025/x.ttl").apply { writeText("@prefix ex: <http://example.org/> . ex:C ex:p ex:D .") }
        val dataSet = loadDataSet(dir)
        assertEquals("cases/2025/x.ttl", dataSet.relativePath(nested))
        assertEquals("cases", dataSet.folderOf(nested))
    }

    @Test
    fun `folder names are case sensitive and a root file has an empty folder`() {
        File(dir, "Entities").mkdirs()
        val upper = File(dir, "Entities/a.ttl").apply { writeText("@prefix ex: <http://example.org/> . ex:A ex:p ex:B .") }
        val top = write("top.ttl", "ex:A ex:p ex:B .")
        val dataSet = loadDataSet(dir)
        assertEquals("Entities", dataSet.folderOf(upper))
        assertEquals("", dataSet.folderOf(top))
    }
}
