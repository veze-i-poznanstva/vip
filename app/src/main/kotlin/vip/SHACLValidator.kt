package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.Shapes
import org.apache.jena.shacl.ValidationReport
import org.apache.jena.shacl.lib.ShLib
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runShaclValidator(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runShaclValidator(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    val shapesFile = if (args.isNotEmpty()) args[0] else "schema.ttl"
    val dataFile = if (args.size > 1) args[1] else shapesFile

    try {
        out.println("=== SHACL Validation Report ===\n")
        out.println("Shapes file: ${shapesFile.printable()}")
        out.println("Data file: ${dataFile.printable()}")
        out.println()

        val shapesModel = RDFDataMgr.loadModel(shapesFile)
        val dataModel = if (dataFile != shapesFile) {
            loadDataSet(java.io.File(dataFile)).merged
        } else {
            out.println("Note: Validating schema against its own shapes\n")
            shapesModel
        }

        val shapes = Shapes.parse(shapesModel.graph)

        out.println("Found ${shapes.numShapes()} SHACL shapes:")
        shapes.iteratorAll().forEach { shape -> out.println("  - ${shape.toString().printable()}") }
        out.println()

        val report = if (dataModel === shapesModel) ShaclValidator.get().validate(shapes, dataModel.graph) else validateShacl(shapesModel, dataModel)

        if (report.conforms()) {
            out.println("✓ Data conforms to all SHACL shapes!")
        } else {
            out.println("✗ Validation failures found:\n")
            val text = ByteArrayOutputStream().also { ShLib.printReport(it, report) }.toString("UTF-8")
            text.lines().forEach { out.println(it.printable()) }
        }

        printDetailedSummary(report, out)
        return if (report.conforms()) 0 else 1
    } catch (e: Exception) {
        err.println("Error during SHACL validation: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}

/**
 * Validates [data] against the shapes in [shapes], together with what the vocabulary entails
 * (subclass, subproperty, domain and range), so that a foaf:Person counts as a foaf:Agent and a
 * URL used as a document URL counts as a foaf:Document. Without this every sh:class check would
 * demand that the data repeats the vocabulary's hierarchy.
 */
fun validateShacl(shapes: Model, data: Model): ValidationReport =
    ShaclValidator.get().validate(Shapes.parse(shapes.graph), ModelFactory.createRDFSModel(shapes, data).graph)

private fun printDetailedSummary(report: ValidationReport, out: PrintStream) {
    out.println("\n=== Validation Summary ===")
    out.println("Conforms: ${report.conforms()}")
    
    val entries = report.entries.toList()
    out.println("Total violations: ${entries.size}")
    
    if (entries.isNotEmpty()) {
        // Group by severity
        val severityGroups = entries.groupBy { entry ->
            entry.severity()?.toString() ?: "Unknown"
        }
        
        out.println("\nViolations by severity:")
        severityGroups.forEach { (severity, violations) ->
            out.println("  $severity: ${violations.size}")
        }
        
        // Group by shape
        val shapeGroups = entries.groupBy { entry ->
            entry.source()?.toString() ?: "Unknown"
        }
        
        out.println("\nViolations by shape:")
        shapeGroups.forEach { (shape, violations) ->
            out.println("  ${shape.printable()}: ${violations.size}")
            
            // Show first few violations for each shape
            violations.take(3).forEach { violation ->
                val focusNode = violation.focusNode()?.toString() ?: "Unknown"
                val path = violation.resultPath()?.toString()
                val message = violation.message()
                
                out.println("    - Focus: ${focusNode.printable()}")
                if (path != null) out.println("      Path: ${path.printable()}")
                if (message != null) out.println("      Message: ${message.printable()}")
            }
            
            if (violations.size > 3) {
                out.println("    ... and ${violations.size - 3} more")
            }
        }
    }
}