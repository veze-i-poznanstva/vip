package vip

import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

/** One rule file: its name, the explanation in its leading comment lines, and the query text. */
data class Rule(val name: String, val explanation: String, val query: String) {
    companion object {
        fun load(file: File): Rule {
            val lines = file.readLines()
            val explanation = lines.takeWhile { it.startsWith("#") }.joinToString("\n") { it.removePrefix("#").trim() }
            return Rule(file.name.removeSuffix(".rq"), explanation, lines.joinToString("\n"))
        }
    }
}

/** The matches of one rule: each match is the bound variables in the query's projection order. */
data class RuleMatches(val rule: Rule, val variables: List<String>, val matches: List<Map<String, String>>)

/** Every `.rq` file in [rulesDir], in name order. */
fun loadRules(rulesDir: File): List<Rule> =
    (rulesDir.listFiles { f -> f.isFile && f.name.endsWith(".rq") } ?: emptyArray()).sortedBy { it.name }.map(Rule.Companion::load)

/**
 * Runs each rule over the data together with what the vocabulary entails (subclasses and
 * subproperties), so a rule written against `bio:participant` or `:Connection` sees every
 * specialised property and class. Matches are leads, never errors.
 */
fun findRedFlags(data: Model, schema: Model, rules: List<Rule>): List<RuleMatches> {
    val inferred = ModelFactory.createRDFSModel(schema, data)
    return rules.map { rule ->
        val query = QueryFactory.create(rule.query)
        QueryExecutionFactory.create(query, inferred).use { execution ->
            val results = execution.execSelect()
            val variables = results.resultVars.toList()
            val matches = results.asSequence().map { row ->
                variables.filter { row.contains(it) }.associateWith { render(row.get(it)) }
            }.toList()
            RuleMatches(rule, variables, matches)
        }
    }
}

private fun render(node: RDFNode): String = if (node.isLiteral) node.asLiteral().lexicalForm else node.toString()

fun main(args: Array<String>) {
    exitProcess(runRedFlags(args, System.out, System.err))
}

/** Prints every rule's matches; exits 0 whether or not anything matched, 1 only when it could not run. */
fun runRedFlags(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.size < 3) {
        err.println("Usage: RedFlags <data-dir-or-file> <schema-file> <rules-dir>")
        return 1
    }
    return try {
        out.println("=== Red Flags ===\n")
        val data = loadDataSet(File(args[0])).merged
        val schema = RDFDataMgr.loadModel(args[1])
        val rules = loadRules(File(args[2]))
        if (rules.isEmpty()) {
            out.println("No rules found in ${args[2]}")
            return 0
        }
        val report = findRedFlags(data, schema, rules)
        for (found in report) {
            out.println("${found.rule.name}: ${found.matches.size} match(es)")
            found.rule.explanation.lineSequence().take(1).forEach { out.println("  $it") }
            found.matches.forEachIndexed { i, match ->
                out.println("  ${i + 1}. " + found.variables.filter { it in match }.joinToString("; ") { "$it=${match.getValue(it).printable()}" })
            }
            out.println()
        }
        out.println("Total: ${report.sumOf { it.matches.size }} lead(s) across ${rules.size} rule(s). Leads are for a reporter to check, not findings.")
        0
    } catch (e: Exception) {
        err.println("Error: ${e.message?.printable()}")
        1
    }
}
