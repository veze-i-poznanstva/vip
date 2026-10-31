package vip

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runVeracityValidator(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runVeracityValidator(
    args: Array<String>,
    out: PrintStream,
    err: PrintStream,
    validation: VeracityValidation = VeracityValidation()
): Int {
    if (args.isEmpty()) {
        err.println("Usage: VeracityValidator <data-dir-or-file> [schema-file] [archives-file]")
        return 1
    }

    val filename = args[0]
    val schemaFile = args.getOrNull(1)
    val archivesFile = args.getOrNull(2)

    try {
        out.println("=== Veracity Validation Report ===\n")
        out.println("Checking: ${filename.printable()}")
        if (schemaFile != null) out.println("Vocabulary: ${schemaFile.printable()}")
        if (archivesFile != null) out.println("Archive records: ${archivesFile.printable()}")
        out.println("Rules:")
        out.println("  1. Every agent and every connection must have documentation with at least 2 URLs from different domains")
        out.println("  2. Every URL must be reachable, live or through an archived copy")
        out.println()

        // Load the RDF data together with the archive records, which are keyed by document URL
        val model = ModelFactory.createDefaultModel().add(loadDataSet(File(filename)).merged)
        if (archivesFile != null) {
            val archives = File(archivesFile)
            if (!archives.exists()) throw IllegalArgumentException("archives file '$archivesFile' not found")
            model.add(RDFDataMgr.loadModel(archives.path))
        }

        val schema = schemaFile?.let { RDFDataMgr.loadModel(it) }
        val result = validation.validate(model, schema)

        if (result.warnings.isNotEmpty()) {
            out.println("Warnings (${result.warnings.size}):")
            result.warnings.forEachIndexed { index, warning -> out.println("  ${index + 1}. ${warning.printable()}") }
            out.println()
        }

        if (result.isValid) {
            out.println("✓ All validation rules passed")
            out.println("\nValidation PASSED")
            return 0
        }
        out.println("✗ Validation FAILED")
        out.println("\nErrors found:")
        result.errors.forEachIndexed { index, error -> out.println("  ${index + 1}. ${error.printable()}") }
        out.println("\nTotal errors: ${result.errors.size}")
        return 1
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}
