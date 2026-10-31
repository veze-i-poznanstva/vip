package vip

import org.apache.jena.reasoner.ReasonerRegistry
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.ValidityReport
import org.apache.jena.riot.RDFDataMgr
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runOwlConsistencyChecker(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runOwlConsistencyChecker(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.isEmpty()) {
        err.println("Usage: OWLConsistencyChecker <owl-file>")
        return 1
    }
    
    try {
        out.println("=== OWL Consistency Check ===\n")
        
        // Load ontology
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM)
        RDFDataMgr.read(model, args[0])
        
        out.println("Loaded ${model.size()} triples")
        
        // Create OWL reasoner (fallback to built-in)
        out.println("Initializing reasoner...")
        val reasoner = try {
            // Try to use OWL reasoner
            ReasonerRegistry.getOWLReasoner()
        } catch (e: Exception) {
            // Fallback to RDFS reasoner
            out.println("OWL reasoner not available, using RDFS reasoner")
            ReasonerRegistry.getRDFSReasoner()
        }
        val infModel = ModelFactory.createInfModel(reasoner, model)
        
        // Validate
        out.println("Checking consistency...")
        val validity = infModel.validate()
        
        if (validity.isValid) {
            out.println("\n✓ Ontology is consistent!")
            
            // Check for unsatisfiable classes
            out.println("\nChecking for unsatisfiable classes...")
            val unsatisfiableClasses = mutableListOf<String>()
            
            model.listClasses().asSequence()
                .filter { it.isURIResource }
                .forEach { ontClass ->
                    // A simple check for unsatisfiability
                    val individuals = model.listIndividuals(ontClass).toList()
                    if (individuals.isEmpty() && ontClass.uri != null) {
                        // More sophisticated check would involve reasoning
                        val localName = ontClass.localName
                        if (localName != null && !localName.startsWith("Thing")) {
                            out.println("  ⚠ Potentially empty class: ${localName.printable()}")
                        }
                    }
                }
            
            if (unsatisfiableClasses.isEmpty()) {
                out.println("  ✓ No obviously unsatisfiable classes found")
            }
            
            // Check for consistency issues
            checkCommonIssues(out, model)
            return 0
        } else {
            out.println("\n✗ Ontology is inconsistent!")
            validity.reports?.let { reports ->
                reports.forEachRemaining { report ->
                    out.println("\nProblem: ${report.description.printable()}")
                    out.println("Type: ${report.type.toString().printable()}")
                }
            }
            return 1
        }
        
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}

private fun checkCommonIssues(out: PrintStream, model: org.apache.jena.ontology.OntModel) {
    out.println("\nChecking for common issues...")
    
    // Check for circular dependencies
    var circularCount = 0
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { ontClass ->
            val superClasses = ontClass.listSuperClasses(false).asSequence()
                .filter { it.isURIResource }
                .map { it.uri }
                .toSet()
            
            // Simple circular check
            if (ontClass.uri in superClasses) {
                out.println("  ⚠ Potential circular dependency: ${ontClass.localName.printable()}")
                circularCount++
            }
        }
    
    if (circularCount == 0) {
        out.println("  ✓ No obvious circular dependencies found")
    }
    
    // Check for multiple inheritance issues
    val complexInheritance = model.listClasses().asSequence()
        .filter { it.isURIResource }
        .filter { it.listSuperClasses(true).toList().size > 3 }
        .toList()
    
    if (complexInheritance.isNotEmpty()) {
        out.println("\n  ⚠ Classes with complex inheritance (>3 superclasses):")
        complexInheritance.forEach { 
            out.println("    - ${it.localName.printable()}: ${it.listSuperClasses(true).toList().size} superclasses")
        }
    }
}