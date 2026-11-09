package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/** One entity's public identifiers as the data records them. [file] is its home file. */
data class EntityIdentifiers(val uri: String, val file: File, val articles: List<String>, val item: String?)

/**
 * Keeps `vip:wikidataItem` in step with `vip:wikipediaArticle`: [link] writes the item of each
 * entity's Wikipedia article into its file, [check] reports what would be written and anything inconsistent.
 * A Wikipedia outage is only ever a warning, so CI never fails because of it.
 */
class WikidataLinker(private val lookup: ItemLookup, private val log: (String) -> Unit = ::println) {
    /** [linkable] maps an entity to the item id (Q…) its article has and the data lacks. */
    data class Report(val errors: List<String>, val warnings: List<String>, val notes: List<String>, val linkable: Map<EntityIdentifiers, String>)
    data class Outcome(val report: Report, val linked: List<String>, val failed: Map<String, String>)

    /** Every URI subject with an article or an item, sorted by uri; checkDataLayout guarantees one entity per file. */
    fun entities(dataSet: DataSet): List<EntityIdentifiers> {
        val result = mutableListOf<EntityIdentifiers>()
        for ((file, model) in dataSet.models) {
            val article = model.createProperty(Vocab.WIKIPEDIA_ARTICLE)
            val item = model.createProperty(Vocab.WIKIDATA_ITEM)
            val subjects = (model.listSubjectsWithProperty(article).toList() + model.listSubjectsWithProperty(item).toList())
                .filter { it.isURIResource }.distinctBy { it.uri }
            for (s in subjects) {
                result.add(EntityIdentifiers(
                    uri = s.uri,
                    file = file,
                    articles = model.listObjectsOfProperty(s, article).toList().map(::text).sorted(),
                    item = model.listObjectsOfProperty(s, item).toList().map(::text).minOrNull()
                ))
            }
        }
        return result.sortedBy { it.uri }
    }

    fun check(dataSet: DataSet): Report = examine(dataSet, asCheck = true)

    /** [check], then writes every linkable item into its file. */
    fun link(dataSet: DataSet): Outcome {
        val report = examine(dataSet, asCheck = false)
        val linked = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        for ((entity, id) in report.linkable.entries.sortedBy { it.key.uri }) {
            val name = dataSet.relativePath(entity.file)
            val problem = insert(entity, ITEM_NAMESPACE + id, name)
            if (problem == null) {
                linked.add(entity.uri)
                log("linked ${entity.uri} -> $ITEM_NAMESPACE$id ($name)")
            } else {
                failed[entity.uri] = problem
                log("FAILED  ${entity.uri}: $problem")
            }
        }
        return Outcome(report, linked, failed)
    }

    private fun examine(dataSet: DataSet, asCheck: Boolean): Report {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val linkable = linkedMapOf<EntityIdentifiers, String>()
        for (entity in entities(dataSet)) {
            try {
                examine(entity, dataSet.relativePath(entity.file), asCheck, errors, warnings, notes, linkable)
            } catch (e: WikidataException) {
                warnings.add("${entity.uri}: could not ask Wikipedia/Wikidata: ${e.message}")
            }
        }
        return Report(errors, warnings, notes, linkable)
    }

    private fun examine(
        entity: EntityIdentifiers, file: String, asCheck: Boolean,
        errors: MutableList<String>, warnings: MutableList<String>, notes: MutableList<String>,
        linkable: MutableMap<EntityIdentifiers, String>
    ) {
        val uri = entity.uri
        val item = entity.item?.removePrefix(ITEM_NAMESPACE)
        if (entity.articles.isEmpty()) {
            if (item != null && !lookup.itemExists(item)) errors.add("$uri records $item, which does not exist on Wikidata")
            return
        }
        val answers = entity.articles.associateWith { lookup.itemOfArticle(it) }
        val ids = answers.values.filterIsInstance<ArticleItem.Found>().map { it.id }.distinct().sorted()
        val missing = answers.filterValues { it is ArticleItem.MissingPage }.keys
        val withoutItem = answers.filterValues { it is ArticleItem.NoItem }.keys
        missing.forEach { warnings.add("$uri: Wikipedia article $it does not exist") }
        if (ids.size > 1) {
            errors.add("$uri has Wikipedia articles that belong to different Wikidata items: ${ids.joinToString(", ")}")
            return
        }
        val resolved = ids.singleOrNull()
        when {
            resolved != null && item == null -> {
                linkable[entity] = resolved
                if (asCheck) errors.add("$uri: its Wikipedia article has Wikidata item $resolved; run ./gradlew :app:linkWikidata and commit $file")
            }
            resolved != null && item != resolved -> errors.add("$uri records $item but its Wikipedia article has item $resolved")
            resolved != null -> Unit
            withoutItem.isNotEmpty() && item != null ->
                errors.add("$uri records $item but its Wikipedia article has no Wikidata item; remove the item or link the article on Wikidata")
            withoutItem.isNotEmpty() -> withoutItem.forEach { notes.add("$uri: no Wikidata item yet for $it") }
            item != null && !lookup.itemExists(item) -> errors.add("$uri records $item, which does not exist on Wikidata")
        }
    }

    /** Puts the item after the entity's first article line; null on success, else why not. The file is untouched on failure. */
    private fun insert(entity: EntityIdentifiers, itemIri: String, name: String): String? {
        val original = entity.file.readText()
        val lines = original.split("\n").toMutableList()
        val index = lines.indexOfFirst { it.contains("vip:wikipediaArticle") }
        val noPlace = "$name: could not find the vip:wikipediaArticle line to put the item after"
        if (index < 0) return noPlace
        val line = lines[index]
        val trimmed = line.trimEnd()
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        when {
            trimmed.endsWith(";") -> lines.add(index + 1, "${indent}vip:wikidataItem <$itemIri> ;")
            trimmed.endsWith(".") -> {
                lines[index] = trimmed.dropLast(1) + ";" + line.substring(trimmed.length)
                lines.add(index + 1, "${indent}vip:wikidataItem <$itemIri> .")
            }
            else -> return noPlace
        }
        entity.file.writeText(lines.joinToString("\n"))
        val recorded = try {
            val model = RDFDataMgr.loadModel(entity.file.path)
            model.contains(model.createResource(entity.uri), model.createProperty(Vocab.WIKIDATA_ITEM), model.createResource(itemIri))
        } catch (e: Exception) { false }
        if (!recorded) {
            entity.file.writeText(original)
            return "$name: the file did not parse with the item added, or lacks the triple; left unchanged"
        }
        return null
    }

    private fun text(node: RDFNode): String = if (node.isURIResource) node.asResource().uri else node.asLiteral().lexicalForm

    private companion object { const val ITEM_NAMESPACE = "http://www.wikidata.org/entity/" }
}

fun main(args: Array<String>) {
    exitProcess(runWikidataLinker(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runWikidataLinker(args: Array<String>, out: PrintStream, err: PrintStream, lookup: ItemLookup = WikimediaClient()): Int {
    if (args.isEmpty()) {
        err.println("Usage: WikidataLinker <data-dir-or-file> [--check]")
        err.println("  --check  only report items that are missing or wrong; write nothing")
        return 1
    }
    try {
        val checkOnly = args.contains("--check")
        val dataSet = loadDataSet(File(args.first { it != "--check" }))
        out.println("=== Wikidata items ===\n")
        val linker = WikidataLinker(lookup, log = { out.println(it.printable()) })
        val report: WikidataLinker.Report
        var failed = emptyMap<String, String>()
        var linkedCount = 0
        if (checkOnly) {
            report = linker.check(dataSet)
        } else {
            val outcome = linker.link(dataSet)
            report = outcome.report; failed = outcome.failed; linkedCount = outcome.linked.size
        }
        report.errors.forEach { out.println("✗ ${it.printable()}") }
        report.warnings.forEach { out.println("! ${it.printable()}") }
        report.notes.forEach { out.println("- ${it.printable()}") }
        if (checkOnly) {
            if (report.errors.isEmpty() && report.warnings.isEmpty() && report.notes.isEmpty()) {
                out.println("✓ Every Wikipedia article's Wikidata item is recorded")
            }
            out.println("\nErrors: ${report.errors.size}, warnings: ${report.warnings.size}, notes: ${report.notes.size}")
            return if (report.errors.isEmpty()) 0 else 1
        }
        out.println("\nLinked: $linkedCount")
        out.println("Failed: ${failed.size}")
        out.println("Errors: ${report.errors.size}, warnings: ${report.warnings.size}, notes: ${report.notes.size}")
        if (report.warnings.isNotEmpty()) out.println("Run ./gradlew :app:linkWikidata again")
        return if (report.errors.isNotEmpty() || failed.isNotEmpty()) 1 else 0
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}
