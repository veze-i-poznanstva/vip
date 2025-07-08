package vip

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport
import org.apache.jena.shacl.lib.ShLib
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val shapesFile = if (args.isNotEmpty()) args[0] else "schema.ttl"
    val dataFile = if (args.size > 1) args[1] else shapesFile
    
    try {
        println("=== SHACL Validation Report ===\n")
        println("Shapes file: $shapesFile")
        println("Data file: $dataFile")
        println()
        
        // Load shapes and data
        val shapesModel = RDFDataMgr.loadModel(shapesFile)
        val dataModel = if (dataFile != shapesFile) {
            RDFDataMgr.loadModel(dataFile)
        } else {
            // If validating the schema itself, extract only the data (non-SHACL) parts
            println("Note: Validating schema against its own shapes\n")
            shapesModel
        }
        
        // Parse shapes
        val shapes = Shapes.parse(shapesModel.graph)
        
        println("Found ${shapes.numShapes()} SHACL shapes:")
        shapes.iteratorAll().forEach { shape ->
            println("  - $shape")
        }
        println()
        
        // Perform validation
        val report = ShaclValidator.get().validate(shapes, dataModel.graph)
        
        // Print results
        if (report.conforms()) {
            println("✓ Data conforms to all SHACL shapes!")
        } else {
            println("✗ Validation failures found:\n")
            ShLib.printReport(report)
        }
        
        // Detailed summary
        printDetailedSummary(report)
        
        // Exit with appropriate code
        if (!report.conforms()) {
            exitProcess(1)
        }
        
    } catch (e: Exception) {
        System.err.println("Error during SHACL validation: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun printDetailedSummary(report: ValidationReport) {
    println("\n=== Validation Summary ===")
    println("Conforms: ${report.conforms()}")
    
    val entries = report.entries.toList()
    println("Total violations: ${entries.size}")
    
    if (entries.isNotEmpty()) {
        // Group by severity
        val severityGroups = entries.groupBy { entry ->
            entry.severity()?.toString() ?: "Unknown"
        }
        
        println("\nViolations by severity:")
        severityGroups.forEach { (severity, violations) ->
            println("  $severity: ${violations.size}")
        }
        
        // Group by shape
        val shapeGroups = entries.groupBy { entry ->
            entry.source()?.toString() ?: "Unknown"
        }
        
        println("\nViolations by shape:")
        shapeGroups.forEach { (shape, violations) ->
            println("  $shape: ${violations.size}")
            
            // Show first few violations for each shape
            violations.take(3).forEach { violation ->
                val focusNode = violation.focusNode()?.toString() ?: "Unknown"
                val path = violation.resultPath()?.toString()
                val message = violation.message()
                
                println("    - Focus: $focusNode")
                if (path != null) println("      Path: $path")
                if (message != null) println("      Message: $message")
            }
            
            if (violations.size > 3) {
                println("    ... and ${violations.size - 3} more")
            }
        }
    }
}