package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * Rules for a dataset kept as entity files and case files:
 *  1. Every entity has one home: the file that declares its type. Other files may add
 *     connections to it but never redeclare it, so facts about a person cannot drift apart.
 *  2. Everything a file refers to is declared somewhere: participants in some file, types,
 *     predicates between entities and vocabulary terms in the schema. A typo therefore cannot
 *     create a ghost participant, an unknown class or a made-up relationship that nothing checks.
 *  3. People and organisations are declared under `entities/`, connections under `cases/`, so a
 *     case file reads as a story about entities that each have their own home.
 *  4. An entity file holds one entity and is named after it, so `ex:Sinisa_Mali` is found in
 *     `sinisa-mali.ttl` without searching.
 *  5. There is nowhere else: a Turtle file outside those two folders is reported.
 *  6. Data files hold data: a statement that would extend the vocabulary (subclass, domain,
 *     range, OWL or SHACL) is reported, because it would change what every other check means.
 * Rules 2 to 4 need the [schema] to tell vocabulary terms from entities and agents from connections.
 */
const val ENTITIES_FOLDER = "entities"
const val CASES_FOLDER = "cases"

private val VOCABULARY_NAMESPACES = listOf(RDFS.getURI(), OWL.getURI(), "http://www.w3.org/ns/shacl#")
private val ANNOTATIONS_ALLOWED_IN_DATA = setOf(RDFS.label.uri, RDFS.comment.uri, RDFS.seeAlso.uri)

fun checkDataLayout(dataSet: DataSet, schema: Model? = null): List<String> {
    val homes = linkedMapOf<String, MutableList<String>>()
    for ((file, model) in dataSet.models) {
        model.listSubjectsWithProperty(RDF.type).toList()
            .filter { it.isURIResource }
            .forEach { homes.getOrPut(it.uri) { mutableListOf() }.add(dataSet.relativePath(file)) }
    }
    val duplicates = homes.filterValues { it.size > 1 }
        .map { (uri, files) -> "$uri is declared in ${files.size} files (${files.sorted().joinToString(", ")}); an entity has exactly one home file" }
    if (schema == null) return duplicates

    fun inSchema(uri: String) = schema.containsResource(ResourceFactory.createResource(uri))
    val referenceProperties = setOf(Vocab.HAS_DOCUMENT_URL, Vocab.HAS_ARCHIVE_URL, Vocab.WIKIPEDIA_ARTICLE, Vocab.WIKIDATA_ITEM)
    val dangling = linkedMapOf<String, MutableSet<String>>()
    val unknownTypes = mutableListOf<String>()
    val undeclaredPredicates = mutableListOf<String>()
    val vocabularyStatements = mutableListOf<String>()
    for ((file, model) in dataSet.models) {
        val path = dataSet.relativePath(file)
        for (statement in model.listStatements().toList()) {
            val predicate = statement.predicate.uri
            val obj = statement.`object`
            if (predicate == RDF.type.uri) {
                if (obj.isURIResource && !inSchema(obj.asResource().uri)) unknownTypes.add("${obj.asResource().uri} is not a type the vocabulary knows (used in $path)")
                continue
            }
            if (predicate !in ANNOTATIONS_ALLOWED_IN_DATA && VOCABULARY_NAMESPACES.any { predicate.startsWith(it) }) {
                vocabularyStatements.add("$path states $predicate; data files may not extend the vocabulary")
                continue
            }
            if (predicate in referenceProperties || !obj.isURIResource) continue
            val target = obj.asResource().uri
            if (target !in homes && !inSchema(target)) dangling.getOrPut(target) { sortedSetOf() }.add(path)
            if (statement.subject.isURIResource && statement.subject.uri in homes && target in homes && !inSchema(predicate)) {
                undeclaredPredicates.add("$path relates ${statement.subject.uri} to $target with $predicate, which the vocabulary does not declare; use a connection with evidence")
            }
        }
    }

    val misplaced = dataSet.models.flatMap { (file, model) ->
        // Infer over the data alone: a vocabulary statement smuggled into a data file is reported
        // above and must not change what counts as an agent or a connection here.
        val dataOnly = ModelFactory.createDefaultModel().add(model.listStatements().toList().filter { st ->
            st.predicate.uri in ANNOTATIONS_ALLOWED_IN_DATA || VOCABULARY_NAMESPACES.none { st.predicate.uri.startsWith(it) }
        })
        val inferred = ModelFactory.createRDFSModel(schema, dataOnly)
        val connection = inferred.createResource(Vocab.CONNECTION)
        val agent = inferred.createResource(Vocab.AGENT)
        val declared = model.listSubjectsWithProperty(RDF.type).toList().filter { it.isURIResource }
        val connections = declared.filter { inferred.contains(it, RDF.type, connection) }
        val agents = declared.filter { inferred.contains(it, RDF.type, agent) && it !in connections }
        when (dataSet.folderOf(file)) {
            CASES_FOLDER -> agents.map { "${it.uri} is declared in ${dataSet.relativePath(file)}; people and organisations belong in $ENTITIES_FOLDER/, a case file holds only connections" }
            ENTITIES_FOLDER -> connections.map { "${it.uri} is declared in ${dataSet.relativePath(file)}; connections belong in $CASES_FOLDER/, an entity file holds only the entity itself" } +
                entityFileNaming(dataSet.relativePath(file), file.name, agents.map { it.uri })
            else -> if (dataSet.root == null) emptyList() else listOf(
                "${dataSet.relativePath(file)} is outside the data layout; entities belong in $ENTITIES_FOLDER/, connections in $CASES_FOLDER/"
            )
        }
    }
    return duplicates + dangling.map { (uri, files) -> "$uri is referenced in ${files.joinToString(", ")} but declared in no file" } +
        unknownTypes + undeclaredPredicates + vocabularyStatements + misplaced
}

private fun entityFileNaming(path: String, fileName: String, entityUris: List<String>): List<String> = when {
    entityUris.size > 1 -> listOf("$path declares ${entityUris.size} entities; one entity per file: ${entityUris.map { entityFileName(it) }.sorted().joinToString(", ")}")
    entityUris.size == 1 && fileName != entityFileName(entityUris[0]) -> listOf("$path declares ${entityUris[0]} and should be named ${entityFileName(entityUris[0])}")
    else -> emptyList()
}

/** The file an entity lives in: its URI's local name, lowercase, underscores as hyphens. */
fun entityFileName(uri: String): String =
    uri.substringAfterLast('/').substringAfterLast('#').lowercase().replace('_', '-') + ".ttl"

fun main(args: Array<String>) {
    exitProcess(runDataLayoutCheck(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runDataLayoutCheck(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.isEmpty()) {
        err.println("Usage: DataLayoutCheck <data-dir-or-file> [schema-file]")
        return 1
    }
    return try {
        out.println("=== Data Layout Check ===\n")
        val dataSet = loadDataSet(File(args[0]))
        val schema = args.getOrNull(1)?.let { org.apache.jena.riot.RDFDataMgr.loadModel(it) }
        out.println("Files (${dataSet.files.size}): ${dataSet.files.joinToString(", ") { dataSet.relativePath(it) }}")
        val problems = checkDataLayout(dataSet, schema)
        if (problems.isEmpty()) {
            out.println("✓ Entities under $ENTITIES_FOLDER/, connections under $CASES_FOLDER/, one home file per entity, every reference resolves")
            0
        } else {
            out.println("✗ ${problems.size} problem(s):")
            problems.forEach { out.println("  - ${it.printable()}") }
            1
        }
    } catch (e: Exception) {
        err.println("Error: ${e.message?.printable()}")
        1
    }
}
