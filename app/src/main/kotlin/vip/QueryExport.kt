package vip

import com.google.gson.GsonBuilder
import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.RDF
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.RDFS
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/**
 * The data with what the vocabulary entails of it, materialised: the browser's query engine does no
 * reasoning, so the types along `rdfs:subClassOf`, the participants along `rdfs:subPropertyOf` and the
 * domain and range typings that the rules see in the build are written out as plain statements.
 * Only statements about the data's own subjects are kept, so the vocabulary itself and RDFS
 * housekeeping such as `rdf:type rdfs:Resource` stay out, and so does a type that is only an
 * anonymous class of the schema, which the export could write only as a bare label.
 */
fun entail(data: Model, schema: Model): Model {
    val subjects = data.listSubjects().toSet()
    val inferred = ModelFactory.createRDFSModel(schema, data)
    val out = ModelFactory.createDefaultModel()
    out.setNsPrefixes(schema.nsPrefixMap)
    out.setNsPrefixes(data.nsPrefixMap)
    inferred.listStatements().forEach { st ->
        val housekeeping = st.predicate == RDF.type && st.`object`.isURIResource && st.`object`.asResource().nameSpace == RDFS.uri
        // A type that is only an anonymous class of the schema (an OWL restriction) would be a bare label in the export.
        val anonymousClass = st.`object`.isAnon && !data.containsResource(st.`object`)
        if (st.subject in subjects && !housekeeping && !anonymousClass) out.add(st)
    }
    return out
}

/**
 * The entailed data as Turtle that is the same text on every build. Jena labels blank nodes at
 * random and its writer orders by those labels, so a documentation node would move from build to
 * build; here every blank node is relabelled from where it hangs and what it says, and the
 * statements are added in sorted order, which fixes the text.
 */
fun dataTurtle(entailed: Model): String {
    val stable = StableBlankNodes(entailed).copy()
    return java.io.ByteArrayOutputStream().also { RDFDataMgr.write(it, stable, Lang.TURTLE) }.toString(Charsets.UTF_8)
}

private class StableBlankNodes(private val model: Model) {
    private val labels = HashMap<Resource, Resource>()
    private val taken = HashSet<String>()
    private val out: Model = ModelFactory.createDefaultModel().setNsPrefixes(model.nsPrefixMap)

    fun copy(): Model {
        val statements = model.listStatements().toList().map { st ->
            val obj = st.`object`
            out.createStatement(stable(st.subject), st.predicate, if (obj.isAnon) stable(obj.asResource()) else obj)
        }
        statements.sortedBy { it.toString() }.forEach { out.add(it) }
        return out
    }

    private fun stable(node: Resource): Resource = if (!node.isAnon) node else labels.getOrPut(node) {
        val hangingOn = model.listStatements(null, null, node).toList().map { key(it.subject, setOf(node)) + " " + it.predicate.uri }.sorted()
        val base = sha1(hangingOn.joinToString("\n") + "\n\n" + description(node, setOf(node)))
        var label = base
        var n = 2
        while (!taken.add(label)) label = "$base-${n++}"
        out.createResource(AnonId.create(label))
    }

    /** What a blank node says, in a fixed order, nested blank nodes included. */
    private fun description(node: Resource, seen: Set<Resource>): String =
        model.listStatements(node, null, null as RDFNode?).toList().map { it.predicate.uri + " " + key(it.`object`, seen) }.sorted().joinToString("\n")

    private fun key(node: RDFNode, seen: Set<Resource>): String = when {
        !node.isAnon -> node.asNode().toString()
        node.asResource() in seen -> "_:cycle"
        else -> "[" + description(node.asResource(), seen + node.asResource()) + "]"
    }
}

private fun sha1(text: String) = java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * The rules for the browser as JSON: name, title and explanation by language, and the query text.
 * A rule's leading comment is the title line "English / Serbian", the English paragraph, a blank
 * comment line, then the Serbian paragraph.
 */
fun rulesJson(rules: List<Rule>): String {
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    return gson.toJson(rules.map { rule ->
        val lines = rule.explanation.lines()
        val titles = lines.first().split(" / ", limit = 2)
        val paragraphs = lines.drop(1).joinToString("\n").trim().split("\n\n", limit = 2)
        val paragraph = { i: Int -> paragraphs.getOrElse(i) { "" }.lines().joinToString(" ").trim() }
        mapOf(
            "name" to rule.name,
            "title" to mapOf("en" to titles[0].trim(), "sr" to titles.getOrElse(1) { "" }.trim()),
            "explanation" to mapOf("en" to paragraph(0), "sr" to paragraph(1)),
            "query" to rule.query
        )
    })
}

/**
 * The leads the build found, rule by rule, as JSON: the projected variables and each match as
 * variable to value (an IRI, or a literal's lexical form; an unbound optional is absent). The viewer's
 * tests run the same rules in the browser engine and compare.
 */
fun leadsJson(report: List<RuleMatches>): String {
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    return gson.toJson(report.map { found ->
        mapOf("name" to found.rule.name, "variables" to found.variables, "matches" to found.matches)
    })
}

fun main(args: Array<String>) {
    exitProcess(runQueryExport(args, System.out, System.err))
}

/**
 * Writes what the browser's query engine needs into the output directory, from the same data as
 * the graph: `data.ttl` (the records with their entailments), `schema.ttl` (the vocabulary as it
 * is), `rules.json` (the rules with their explanations by language) and `red-flags.json` (the leads
 * the build finds, for the viewer's equivalence test).
 */
fun runQueryExport(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.size != 4) {
        err.println("Usage: QueryExport <data-dir-or-file> <schema-file> <rules-dir> <output-dir>")
        err.println("Example: QueryExport data schema.ttl rules web/public")
        return 1
    }
    return try {
        val data = loadDataSet(File(args[0])).merged
        val schemaFile = File(args[1])
        val schema = RDFDataMgr.loadModel(schemaFile.path)
        val rules = loadRules(File(args[2]))
        val dir = File(args[3]).also { it.mkdirs() }

        val entailed = entail(data, schema)
        File(dir, "data.ttl").writeText(dataTurtle(entailed))
        out.println("Wrote ${File(dir, "data.ttl").path}: ${entailed.size()} statements (${data.size()} recorded, the rest entailed)")

        File(dir, "schema.ttl").writeBytes(schemaFile.readBytes())
        out.println("Wrote ${File(dir, "schema.ttl").path}")

        File(dir, "rules.json").writeText(rulesJson(rules))
        out.println("Wrote ${File(dir, "rules.json").path}: ${rules.size} rule(s)")

        val leads = findRedFlags(data, schema, rules)
        File(dir, "red-flags.json").writeText(leadsJson(leads))
        out.println("Wrote ${File(dir, "red-flags.json").path}: ${leads.sumOf { it.matches.size }} lead(s)")
        0
    } catch (e: Exception) {
        err.println("Error: ${e.message?.printable()}")
        1
    }
}
