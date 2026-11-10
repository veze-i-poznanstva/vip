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
    /** A connection's own label by language code, when it has one. */
    val labels: Map<String, String>? = null,
    val source: String? = null,
    val target: String? = null,
    val type: String? = null,
    val nodeType: String? = null,
    val uri: String? = null,
    val comment: String? = null,
    val wikipedia: String? = null,
    val wikidata: String? = null,
    val aliases: List<String>? = null,
    val facts: List<Fact>? = null,
    val kind: String? = null,
    val typeLabel: String? = null,
    val roles: List<Map<String, String>>? = null,
    val share: Double? = null,
    val start: String? = null,
    val end: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val sources: List<Source>? = null,
    val history: List<ConnectionState>? = null
)

fun main(args: Array<String>) {
    exitProcess(runTtlToCytoscape(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runTtlToCytoscape(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.size < 2 || args.size > 4) {
        err.println("Usage: ttlToCytoscape <data-dir-or-file> <output.json> [schema.ttl] [archives.ttl]")
        err.println("Example: ttlToCytoscape data web/public/test-data-cytoscape.json schema.ttl archives.ttl")
        return 1
    }

    val inputFile = File(args[0])
    val outputFile = File(args[1])
    val schemaFile = File(args.getOrElse(2) { "schema.ttl" })

    // An archives file that is named must exist; the default one is optional.
    val archivesFile = if (args.size > 3) File(args[3]) else File("archives.ttl").takeIf { it.exists() }

    for (f in listOfNotNull(inputFile, schemaFile, archivesFile)) {
        if (!f.exists()) {
            err.println("Error: file '${f.path.printable()}' not found")
            return 1
        }
    }

    try {
        outputFile.parentFile?.mkdirs()
        out.println("Converting ${inputFile.path.printable()} to Cytoscape format using vocabulary ${schemaFile.path.printable()}...")

        val dataSet = loadDataSet(inputFile)
        val schema = RDFDataMgr.loadModel(schemaFile.path)
        val archives = archivesFile?.let { RDFDataMgr.loadModel(it.path) } ?: ModelFactory.createDefaultModel()
        val elements = convertDataSetToCytoscape(dataSet, schema, archives, args[0])

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
fun convertModelToCytoscape(
    data: Model, schema: Model, archives: Model = ModelFactory.createDefaultModel()
): List<CytoscapeElement> = GraphBuilder(data, schema, archives, null).build()

/** As [convertModelToCytoscape], and records which file and line each record lives at ([inputPath] as given on the command line). */
fun convertDataSetToCytoscape(dataSet: DataSet, schema: Model, archives: Model, inputPath: String): List<CytoscapeElement> =
    GraphBuilder(dataSet.merged, schema, archives, RecordLocator(dataSet, inputPath)).build()

private class GraphBuilder(
    private val data: Model, private val schema: Model, archives: Model, private val locator: RecordLocator?
) {
    private val archiveIndex = ArchiveIndex(archives)
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

    fun build(): List<CytoscapeElement> {
        val nodes = agents.map(::nodeElement)
        val edges = (mergeRelationships(connectionEdges()) + dedupe(directEdges()))
            .mapIndexed { i, e -> e.copy(data = e.data.copy(id = "edge$i")) }
        return nodes + edges
    }

    private fun instancesOf(classUri: String): List<Resource> =
        inferred.listSubjectsWithProperty(RDF.type, inferred.createResource(classUri)).toList()
            .filter { data.containsResource(it) }

    private fun isDeclared(resource: Resource): Boolean = data.contains(resource, RDF.type, null as RDFNode?)

    private fun isA(resource: Resource, classUri: String): Boolean =
        inferred.contains(resource, RDF.type, inferred.createResource(classUri))

    private fun nodeElement(agent: Resource): CytoscapeElement {
        val label = labelOf(agent) ?: extractLabelFromUri(agent.uri)
        val where = locator?.declaration(agent)
        return CytoscapeElement(
            data = ElementData(
                id = nodeId(agent),
                label = label,
                type = "instance",
                nodeType = nodeTypeOf(agent),
                uri = agent.uri,
                comment = commentOf(agent),
                wikipedia = wikipediaOf(agent),
                wikidata = wikidataOf(agent),
                aliases = aliasesOf(agent, label),
                facts = factsOf(agent),
                file = where?.file,
                line = where?.line,
                sources = sourcesOf(agent)
            )
        )
    }

    /** Every other name of the agent: its further foaf:name values and its aliases. */
    private fun aliasesOf(agent: Resource, label: String): List<String>? =
        (literals(agent, Vocab.NAME) + literals(agent, Vocab.ALIAS)).filter { it != label }.distinct().sorted().ifEmpty { null }

    private fun literals(resource: Resource, property: String): List<String> =
        data.listObjectsOfProperty(resource, data.createProperty(property)).toList()
            .filter { it.isLiteral }.map { it.asLiteral().lexicalForm }

    private val notFacts = setOf(
        Vocab.NAME, Vocab.FIRST_NAME, Vocab.FIRST_LAST_NAME, Vocab.ALIAS, RDFS.label.uri,
        Vocab.DCTERMS_TITLE, RDFS.comment.uri, RDF.type.uri
    )

    /** Literal-valued statements other than names, comments and types, labelled by the vocabulary. */
    private fun factsOf(agent: Resource): List<Fact>? =
        data.listStatements(agent, null, null as RDFNode?).toList()
            .filter { it.`object`.isLiteral && it.predicate.uri !in notFacts }
            .map { Fact(localName(it.predicate.uri), englishLabel(it.predicate) ?: extractLabelFromUri(it.predicate.uri).lowercase(), it.`object`.asLiteral().lexicalForm) }
            .distinct().sortedWith(compareBy({ it.property }, { it.value })).ifEmpty { null }

    private fun sourcesOf(resource: Resource): List<Source>? =
        data.listObjectsOfProperty(resource, data.createProperty(Vocab.HAS_DOCUMENTATION)).toList()
            .filter { it.isResource }.map { it.asResource() }
            .mapNotNull { doc ->
                val url = data.listObjectsOfProperty(doc, data.createProperty(Vocab.HAS_DOCUMENT_URL)).toList()
                    .filter { it.isURIResource }.map { it.asResource().uri }.minOrNull() ?: return@mapNotNull null
                Source(
                    url = url,
                    domain = sourceDomain(url),
                    type = localNameOf(doc, Vocab.HAS_DOCUMENT_TYPE),
                    status = localNameOf(doc, Vocab.HAS_DOCUMENT_STATUS),
                    quote = preferredLiteral(doc, RDFS.comment),
                    archive = archiveIndex.snapshotOf(url),
                    archivedOn = archiveIndex.dateOf(url)
                )
            }
            .sortedWith(compareBy({ statusRank(it.status) }, { it.domain }, { it.url })).ifEmpty { null }

    private fun statusRank(status: String?): Int = when (status) {
        "published" -> 0
        "archived" -> 1
        "removed" -> 3
        else -> 2
    }

    private fun localNameOf(resource: Resource, property: String): String? =
        data.listObjectsOfProperty(resource, data.createProperty(property)).toList()
            .filter { it.isURIResource }.map { localName(it.asResource().uri) }.minOrNull()

    private fun nodeTypeOf(agent: Resource): String = when {
        isA(agent, Vocab.PERSON) -> "person"
        isA(agent, Vocab.POLITICAL_ORGANIZATION) -> "party"
        isA(agent, Vocab.ACADEMIC_ORGANIZATION) -> "academic"
        isA(agent, Vocab.ORGANIZATION) -> "organization"
        else -> "default"
    }

    private fun connectionEdges(): List<CytoscapeElement> =
        instancesOf(Vocab.CONNECTION).sortedWith(compareBy({ it.uri ?: "" }, { commentOf(it) ?: "" })).flatMap { connection ->
            val participants = inferred.listObjectsOfProperty(connection, inferred.createProperty(Vocab.PARTICIPANT))
                .toList()
                .filter { it.isURIResource }
                .map { it.asResource() }
                .distinct()
                .filter { it.uri in nodeIds }
                .sortedWith(compareBy({ !isOriginIn(connection, it) }, { !isA(it, Vocab.PERSON) }, { it.uri }))
            val label = connectionLabel(connection)
            participants.indices.flatMap { i ->
                (i + 1 until participants.size).map { j -> connectionEdge(connection, participants[i], participants[j], label) }
            }
        }

    private fun connectionEdge(connection: Resource, from: Resource, to: Resource, label: String): CytoscapeElement {
        val kind = kindOf(connection)
        val where = locator?.declaration(connection)
        val roles = rolesOf(connection)
        return edge(from, to, label).let { e ->
            e.copy(data = e.data.copy(
                uri = connection.takeIf { it.isURIResource }?.uri,
                labels = labelsOf(data, connection).ifEmpty { null },
                kind = kind?.let { localName(it.uri) },
                typeLabel = kind?.let { englishLabel(it) },
                roles = roles,
                share = literals(connection, Vocab.OWNERSHIP_SHARE).mapNotNull { it.toDoubleOrNull() }.minOrNull(),
                start = literals(connection, Vocab.CONNECTION_START_DATE).minOrNull(),
                end = literals(connection, Vocab.CONNECTION_END_DATE).minOrNull(),
                comment = commentOf(connection),
                file = where?.file,
                line = where?.line,
                sources = sourcesOf(connection)
            ))
        }
    }

    /**
     * One edge per relationship (kind, label, source, target, roles): the latest documented state,
     * with every state oldest first as its history when there are several. Dates compare as written;
     * a missing start sorts first, a missing end last. The edge keeps the position of its first record.
     */
    private fun mergeRelationships(edges: List<CytoscapeElement>): List<CytoscapeElement> =
        edges.groupBy { listOf(it.data.kind, it.data.label, it.data.source, it.data.target, it.data.roles.orEmpty()) }
            .values.map { group ->
                val states = group.sortedWith(
                    compareBy<CytoscapeElement, String?>(nullsFirst(), { it.data.start })
                        .thenBy(nullsLast()) { it.data.end }
                        .thenBy { it.data.uri ?: "" }
                ).map { it.data }
                val latest = states.last()
                CytoscapeElement(latest.copy(history = if (states.size < 2) null else states.map {
                    ConnectionState(it.uri, it.share, it.roles, it.start, it.end, it.comment, it.file, it.line, it.sources)
                }))
            }

    /** The work roles as the vocabulary names them in each language, ordered by the English name. */
    private fun rolesOf(connection: Resource): List<Map<String, String>>? =
        data.listObjectsOfProperty(connection, data.createProperty(Vocab.HAS_WORK_ROLE)).toList()
            .filter { it.isResource }.map { labelsOf(schema, it.asResource()) }.filter { it.isNotEmpty() }
            .sortedBy { it["en"] ?: it.values.first() }.ifEmpty { null }

    /** The `rdfs:label`s of a resource in [model] by language; an untagged label counts as English. */
    private fun labelsOf(model: Model, resource: Resource): Map<String, String> =
        model.listStatements(resource, RDFS.label, null as RDFNode?).toList().map { it.`object`.asLiteral() }
            .groupBy({ it.language.ifEmpty { "en" } }, { it.lexicalForm }).mapValues { (_, forms) -> forms.min() }.toSortedMap()

    /** The most specific declared type: the one that is a subclass of all the others, else the first by name. */
    private fun kindOf(connection: Resource): Resource? {
        val types = data.listObjectsOfProperty(connection, RDF.type).toList()
            .filter { it.isURIResource }.map { it.asResource() }.sortedBy { localName(it.uri) }
        return types.firstOrNull { t -> types.all { o -> o == t || inferred.contains(t, RDFS.subClassOf, o) } } ?: types.firstOrNull()
    }

    /** A directed tie leaves its origin, the owner or the parent, whatever the two URIs sort to. */
    private fun isOriginIn(connection: Resource, participant: Resource): Boolean =
        listOf(Vocab.OWNER, Vocab.PARENT).any { data.contains(connection, data.createProperty(it), participant) }

    /** Only properties the vocabulary declares become edges: a made-up predicate is not a fact. */
    private fun directEdges(): List<CytoscapeElement> =
        data.listStatements().toList()
            .filter { it.predicate != RDF.type && it.subject.isURIResource && it.`object`.isURIResource }
            .filter { it.subject.uri in nodeIds && it.`object`.asResource().uri in nodeIds }
            .filter { schema.containsResource(it.predicate) }
            .sortedWith(compareBy({ it.subject.uri }, { it.predicate.uri }, { it.`object`.asResource().uri }))
            .map { st ->
                val e = edge(st.subject, st.`object`.asResource(), propertyLabel(st.predicate))
                e.copy(data = e.data.copy(file = locator?.statement(st)))
            }

    private fun edge(from: Resource, to: Resource, label: String) = CytoscapeElement(
        data = ElementData(
            id = "",
            source = nodeId(from),
            target = nodeId(to),
            label = label
        )
    )

    /** One edge per unordered pair and label, so symmetric direct properties are not drawn twice. Connections are never merged. */
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
            .firstNotNullOfOrNull { p -> preferredLiteral(resource, data.createProperty(p)) }

    /** The entity's own description, chosen like its name. */
    private fun commentOf(resource: Resource): String? = preferredLiteral(resource, RDFS.comment)

    /** The article about this entity, as the data declares it; a page merely cited as a source does not count. */
    private fun wikipediaOf(resource: Resource): String? =
        data.listObjectsOfProperty(resource, data.createProperty(Vocab.WIKIPEDIA_ARTICLE)).toList()
            .filter { it.isURIResource }.map { it.asResource().uri }
            .filter { WIKIPEDIA_ARTICLE_URL.containsMatchIn(it) }
            .minWithOrNull(compareBy({ url -> preferredLanguages.indexOf(url.substringAfter("//").substringBefore('.')).let { i -> if (i < 0) preferredLanguages.size else i } }, { it }))

    /** The entity's Wikidata item as its entity IRI; the shapes allow one, so the lowest wins if the data has more. */
    private fun wikidataOf(resource: Resource): String? =
        data.listObjectsOfProperty(resource, data.createProperty(Vocab.WIKIDATA_ITEM)).toList()
            .filter { it.isURIResource }.map { it.asResource().uri }
            .filter { WIKIDATA_ENTITY_IRI.matches(it) }
            .minOrNull()

    private fun preferredLiteral(resource: Resource, property: Property): String? =
        data.listStatements(resource, property, null as RDFNode?).toList()
            .map { it.`object` }.filter { it.isLiteral }.map { it.asLiteral() }
            .minWithOrNull(compareBy({ preferredLanguages.indexOf(it.language).let { i -> if (i < 0) preferredLanguages.size else i } }, { it.lexicalForm }))
            ?.lexicalForm

    /** The vocabulary's English (or untagged) label, if it has one. */
    private fun englishLabel(resource: Resource): String? =
        schema.listStatements(resource, RDFS.label, null as RDFNode?).toList().map { it.`object`.asLiteral() }
            .filter { it.language == "en" || it.language == "" }.map { it.lexicalForm }.minOrNull()

    private fun schemaLabel(resource: Resource, language: String = "en"): String? {
        val labels = schema.listStatements(resource, RDFS.label, null as RDFNode?).toList().map { it.`object`.asLiteral() }
        return (labels.firstOrNull { it.language == language } ?: labels.firstOrNull())?.lexicalForm
    }
}

private val WIKIPEDIA_ARTICLE_URL = Regex("^https://[a-z-]+\\.wikipedia\\.org/wiki/")
private val WIKIDATA_ENTITY_IRI = Regex("^http://www\\.wikidata\\.org/entity/Q[1-9][0-9]*$")

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
