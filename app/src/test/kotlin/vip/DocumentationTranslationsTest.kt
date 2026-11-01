package vip

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every document a reader may open has an English and a Serbian version that stay in step.
 * The README pair is named README.md / PROCITAJME.md; every other document in docs/ is
 * `<name>.md` with its translation `<name>.sr.md`. Tests run from the repository root.
 */
class DocumentationTranslationsTest {

    private val pairs: List<Pair<File, File>> =
        listOf(File("README.md") to File("PROCITAJME.md")) +
            (File("docs").listFiles { f -> f.name.endsWith(".md") && !f.name.endsWith(".sr.md") } ?: emptyArray())
                .sorted()
                .map { it to File(it.parentFile, it.name.removeSuffix(".md") + ".sr.md") }

    @Test
    fun `every document has a Serbian translation beside it`() {
        val missing = pairs.filterNot { (_, serbian) -> serbian.isFile }.map { (english, serbian) -> "${serbian.path} (translation of ${english.path})" }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun `a translation has the same heading outline as its original`() {
        for ((english, serbian) in pairs.filter { (_, sr) -> sr.isFile }) {
            assertEquals(outline(english), outline(serbian), "${serbian.path} does not follow the headings of ${english.path}")
        }
    }

    /** The heading levels in order, so a dropped or added section is caught but wording is free. */
    private fun outline(file: File): List<Int> =
        file.readLines().mapNotNull { line -> Regex("^(#{1,6}) ").find(line)?.groupValues?.get(1)?.length }

    @Test
    fun `the agent prompt box is the same English text on both language pages`() {
        fun box(file: File): String = file.readText().substringAfter("````text\n").substringBefore("\n````")
        assertEquals(box(File("docs/agent-prompt.md")), box(File("docs/agent-prompt.sr.md")))
    }
}
