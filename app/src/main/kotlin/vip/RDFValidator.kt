package vip

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RiotException
import java.io.FileInputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runRdfValidator(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runRdfValidator(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.isEmpty()) {
        err.println("Usage: RDFValidator <rdf-file>")
        return 1
    }
    
    val filename = args[0]
    
    try {
        out.println("Validating: ${filename.printable()}")
        
        val model = ModelFactory.createDefaultModel()
        val lang = RDFLanguages.filenameToLang(filename)
        
        FileInputStream(filename).use { RDFDataMgr.read(model, it, lang) }
        
        out.println("✓ Valid RDF file")
        out.println("  Format: ${lang?.name ?: "Unknown"}")
        out.println("  Triples: ${model.size()}")
        out.println("  Subjects: ${model.listSubjects().toSet().size}")
        out.println("  Predicates: ${model.listStatements().asSequence().map { it.predicate }.toSet().size}")
        out.println("  Objects: ${model.listObjects().toSet().size}")
        
        // Print namespaces
        if (model.nsPrefixMap.isNotEmpty()) {
            out.println("\nNamespaces:")
            model.nsPrefixMap.forEach { (prefix, uri) ->
                out.println("  ${prefix.printable()}: <${uri.printable()}>")
            }
        }
        return 0
    } catch (e: RiotException) {
        err.println("✗ Validation failed: ${(e.message ?: e.toString()).printable()}")
        return 1
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}