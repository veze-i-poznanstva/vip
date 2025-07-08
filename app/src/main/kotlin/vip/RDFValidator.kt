package vip

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.apache.jena.riot.RiotException
import java.io.FileInputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: RDFValidator <rdf-file>")
        exitProcess(1)
    }
    
    val filename = args[0]
    
    try {
        println("Validating: $filename")
        
        val model = ModelFactory.createDefaultModel()
        val lang = RDFLanguages.filenameToLang(filename)
        
        RDFDataMgr.read(model, FileInputStream(filename), lang)
        
        println("✓ Valid RDF file")
        println("  Format: ${lang?.name ?: "Unknown"}")
        println("  Triples: ${model.size()}")
        println("  Subjects: ${model.listSubjects().toSet().size}")
        println("  Predicates: ${model.listStatements().asSequence().map { it.predicate }.toSet().size}")
        println("  Objects: ${model.listObjects().toSet().size}")
        
        // Print namespaces
        if (model.nsPrefixMap.isNotEmpty()) {
            println("\nNamespaces:")
            model.nsPrefixMap.forEach { (prefix, uri) ->
                println("  $prefix: <$uri>")
            }
        }
        
    } catch (e: RiotException) {
        System.err.println("✗ Validation failed: ${e.message}")
        exitProcess(1)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}