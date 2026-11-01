package vip

import org.apache.jena.rdf.model.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import com.google.gson.GsonBuilder
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

data class CytoscapeElement(
    val data: ElementData,
    val classes: String? = null
)

data class ElementData(
    val id: String,
    val label: String? = null,
    val source: String? = null,
    val target: String? = null,
    val type: String? = null,
    val nodeType: String? = null,
    val uri: String? = null
)

fun main(args: Array<String>) {
    exitProcess(runTtlToCytoscape(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runTtlToCytoscape(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.size < 2 || args.size > 3) {
        err.println("Usage: ttlToCytoscape <data-dir-or-file> <output.json> [schema.ttl]")
        err.println("Example: ttlToCytoscape data web/public/test-data-cytoscape.json schema.ttl")
        return 1
    }

    val inputFile = File(args[0])
    val outputFile = File(args[1])
    val schemaFile = File(args.getOrElse(2) { "schema.ttl" })

    for (f in listOf(inputFile, schemaFile)) {
        if (!f.exists()) {
            err.println("Error: file '${f.path.printable()}' not found")
            return 1
        }
    }

    try {
        outputFile.parentFile?.mkdirs()
        out.println("Converting ${inputFile.path.printable()} to Cytoscape format using vocabulary ${schemaFile.path.printable()}...")

        val data = loadDataSet(inputFile).merged
        val schema = RDFDataMgr.loadModel(schemaFile.path)
        val elements = convertModelToCytoscape(data, schema)

        val gson = GsonBuilder().setPrettyPrinting().create()
        outputFile.writeText(gson.toJson(elements))

        val nodeCount = elements.count { it.data.source == null }
        val edgeCount = elements.count { it.data.source != null }
        out.println("Successfully converted to ${outputFile.path.printable()}")
        out.println("Created $nodeCount nodes and $edgeCount edges")
        return 0
    } catch (e: Exception) {
        err.println("Error converting file: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}

/**
 * Turns instance data into a graph for the viewer, guided by the vocabulary.
 *
 * Nodes are the agents (people and organizations). Edges come from two places: every
 * connection resource becomes an edge between each pair of its participants, labelled by its own
 * label, its work roles or its type; and every direct object property between two agents becomes
 * an edge labelled by the property. Connections, documentation and plain URLs never become nodes.
 */
fun convertModelToCytoscape(data: Model, schema: Model): List<CytoscapeElement> =
    GraphBuilder(data, schema).build()

private class GraphBuilder(private val data: Model, private val schema: Model) {
    private val inferred: InfModel = ModelFactory.createRDFSModel(schema, data)
    // Only declared resources become nodes: a participant that the data never types is not an
    // agent just because the vocabulary's range axiom would make it one.
    private val agents: List<Resource> = instancesOf(Vocab.AGENT).filter { it.isURIResource && isDeclared(it) }
    // Ids are derived from the URI's local name; when two URIs would collide, the later ones get a
    // hash suffix so a look-alike URI can never graft its edges onto a real person's node.
    private val nodeIds: Map<String, String> = run {
        val taken = mutableSetOf<String>()
        agents.map { it.uri }.sorted().associateWith { uri ->
            val plain = createNodeId(uri)
            (if (plain in taken) plain + "_" + Integer.toHexString(uri.hashCode()) else plain).also { taken.add(it) }
        }
    }
    private fun nodeId(resource: Resource): String = nodeIds.getValue(resource.uri)
    private var edgeCounter = 0

    fun build(): List<CytoscapeElement> {
        val nodes = agents.map(::nodeElement)
        val edges = connectionEdges() + directEdges()
        return nodes + dedupe(edges)
    }

    private fun instancesOf(classUri: String): List<Resource> =
        inferred.listSubjectsWithProperty(RDF.type, inferred.createResource(classUri)).toList()
            .filter { data.containsResource(it) }

    private fun isDeclared(resource: Resource): Boolean = data.contains(resource, RDF.type, null as RDFNode?)

    private fun isA(resource: Resource, classUri: String): Boolean =
        inferred.contains(resource, RDF.type, inferred.createResource(classUri))

    private fun nodeElement(agent: Resource): CytoscapeElement = CytoscapeElement(
        data = ElementData(
            id = nodeId(agent),
            label = labelOf(agent) ?: extractLabelFromUri(agent.uri),
            type = "instance",
            nodeType = nodeTypeOf(agent),
            uri = agent.uri
        )
    )

    private fun nodeTypeOf(agent: Resource): String = when {
        isA(agent, Vocab.PERSON) -> "person"
        isA(agent, Vocab.POLITICAL_ORGANIZATION) -> "party"
        isA(agent, Vocab.ACADEMIC_ORGANIZATION) -> "academic"
        isA(agent, Vocab.ORGANIZATION) -> "organization"
        else -> "default"
    }

    private fun connectionEdges(): List<CytoscapeElement> =
        instancesOf(Vocab.CONNECTION).flatMap { connection ->
            val participants = inferred.listObjectsOfProperty(connection, inferred.createProperty(Vocab.PARTICIPANT))
                .toList()
                .filter { it.isURIResource }
                .map { it.asResource() }
                .distinct()
                .filter { it.uri in nodeIds }
                .sortedWith(compareBy({ !isOwnerIn(connection, it) }, { !isA(it, Vocab.PERSON) }, { it.uri }))
            val label = connectionLabel(connection)
            participants.indices.flatMap { i ->
                (i + 1 until participants.size).map { j -> edge(participants[i], participants[j], label) }
            }
        }

    /** An ownership edge always leaves the owner, whatever the two URIs sort to. */
    private fun isOwnerIn(connection: Resource, participant: Resource): Boolean =
        data.contains(connection, data.createProperty(Vocab.OWNER), participant)

    /** Only properties the vocabulary declares become edges: a made-up predicate is not a fact. */
    private fun directEdges(): List<CytoscapeElement> =
        data.listStatements().toList()
            .filter { it.predicate != RDF.type && it.subject.isURIResource && it.`object`.isURIResource }
            .filter { it.subject.uri in nodeIds && it.`object`.asResource().uri in nodeIds }
            .filter { schema.containsResource(it.predicate) }
            .map { edge(it.subject, it.`object`.asResource(), propertyLabel(it.predicate)) }

    private fun edge(from: Resource, to: Resource, label: String) = CytoscapeElement(
        data = ElementData(
            id = "edge${edgeCounter++}",
            source = nodeId(from),
            target = nodeId(to),
            label = label
        )
    )

    /** One edge per unordered pair and label, so symmetric properties are not drawn twice. */
    private fun dedupe(edges: List<CytoscapeElement>): List<CytoscapeElement> =
        edges.distinctBy { setOf(it.data.source, it.data.target) to it.data.label }

    /** Own label, else its work roles, else the label of its most specific declared type. */
    private fun connectionLabel(connection: Resource): String {
        labelOf(connection)?.let { return it }
        val roles = data.listObjectsOfProperty(connection, data.createProperty(Vocab.HAS_WORK_ROLE)).toList()
            .filter { it.isResource }
            .mapNotNull { schemaLabel(it.asResource()) }
            .sorted()
        if (roles.isNotEmpty()) return roles.joinToString(", ").lowercase()
        val declaredTypes = data.listObjectsOfProperty(connection, RDF.type).toList().filter { it.isURIResource }
        val typeLabel = declaredTypes.firstNotNullOfOrNull { schemaLabel(it.asResource()) }
            ?: declaredTypes.firstOrNull()?.let { extractLabelFromUri(it.asResource().uri) }
        return (typeLabel ?: "connected to").lowercase()
    }

    private fun propertyLabel(property: Property): String =
        (schemaLabel(property) ?: extractLabelFromUri(property.uri)).lowercase()

    /** Languages in order of preference for a node's own name; a literal in no listed language ranks last. */
    private val preferredLanguages = listOf("sr", "en")

    /**
     * The entity's own name, chosen the same way on every run: by the first property that has one,
     * then by language preference, then alphabetically, so the export never flips between names.
     */
    private fun labelOf(resource: Resource): String? =
        listOf(Vocab.NAME, RDFS.label.uri, "http://purl.org/dc/terms/title")
            .firstNotNullOfOrNull { p ->
                data.listStatements(resource, data.createProperty(p), null as RDFNode?).toList()
                    .map { it.`object` }.filter { it.isLiteral }.map { it.asLiteral() }
                    .minWithOrNull(compareBy({ preferredLanguages.indexOf(it.language).let { i -> if (i < 0) preferredLanguages.size else i } }, { it.lexicalForm }))
                    ?.lexicalForm
            }

    private fun schemaLabel(resource: Resource, language: String = "en"): String? {
        val labels = schema.listStatements(resource, RDFS.label, null as RDFNode?).toList().map { it.`object`.asLiteral() }
        return (labels.firstOrNull { it.language == language } ?: labels.firstOrNull())?.lexicalForm
    }
}

fun createNodeId(uri: String): String {
    val id = uri
        .substringAfterLast("/")
        .substringAfterLast("#")
        .replace(Regex("[^a-zA-Z0-9_]"), "_")
        .lowercase()

    // If ID is empty or just underscores, use a hash of the full URI
    return if (id.isBlank() || id.all { it == '_' }) {
        "node_" + uri.hashCode().toString().replace("-", "n")
    } else {
        id
    }
}

fun extractLabelFromUri(uri: String): String {
    val label = uri
        .substringAfterLast("/")
        .substringAfterLast("#")
        .replace("_", " ")
        .split(Regex("(?<=[a-z0-9])(?=[A-Z])"))
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return label.ifEmpty { uri }
}
