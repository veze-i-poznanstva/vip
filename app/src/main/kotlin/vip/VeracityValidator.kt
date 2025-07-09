package vip

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import java.io.FileInputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: VeracityValidator <rdf-file>")
        exitProcess(1)
    }
    
    val filename = args[0]
    
    try {
        println("=== Veracity Validation Report ===\n")
        println("Checking: $filename")
        println("Rules:")
        println("  1. Every item must have at least 2 URLs from different domains")
        println("  2. Every URL must be accessible (respond with HTTP 200)")
        println()
        
        // Load the RDF data
        val model = ModelFactory.createDefaultModel()
        val lang = RDFLanguages.filenameToLang(filename)
        RDFDataMgr.read(model, FileInputStream(filename), lang)
        
        // Run veracity validation
        val validator = VeracityValidation()
        val result = validator.validate(model)
        
        if (result.isValid) {
            println("✓ All validation rules passed")
            println("\nValidation PASSED")
        } else {
            println("✗ Validation FAILED")
            println("\nErrors found:")
            result.errors.forEachIndexed { index, error ->
                println("  ${index + 1}. $error")
            }
            println("\nTotal errors: ${result.errors.size}")
            exitProcess(1)
        }
        
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}