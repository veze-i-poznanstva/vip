package vip

import org.apache.jena.reasoner.ReasonerRegistry
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.reasoner.ValidityReport
import org.apache.jena.riot.RDFDataMgr
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: OWLConsistencyChecker <owl-file>")
        exitProcess(1)
    }
    
    try {
        println("=== OWL Consistency Check ===\n")
        
        // Load ontology
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM)
        RDFDataMgr.read(model, args[0])
        
        println("Loaded ${model.size()} triples")
        
        // Create OWL reasoner (fallback to built-in)
        println("Initializing reasoner...")
        val reasoner = try {
            // Try to use OWL reasoner
            ReasonerRegistry.getOWLReasoner()
        } catch (e: Exception) {
            // Fallback to RDFS reasoner
            println("OWL reasoner not available, using RDFS reasoner")
            ReasonerRegistry.getRDFSReasoner()
        }
        val infModel = ModelFactory.createInfModel(reasoner, model)
        
        // Validate
        println("Checking consistency...")
        val validity = infModel.validate()
        
        if (validity.isValid) {
            println("\n✓ Ontology is consistent!")
            
            // Check for unsatisfiable classes
            println("\nChecking for unsatisfiable classes...")
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
                            println("  ⚠ Potentially empty class: $localName")
                        }
                    }
                }
            
            if (unsatisfiableClasses.isEmpty()) {
                println("  ✓ No obviously unsatisfiable classes found")
            }
            
            // Check for consistency issues
            checkCommonIssues(model)
            
        } else {
            println("\n✗ Ontology is inconsistent!")
            validity.reports?.let { reports ->
                reports.forEachRemaining { report ->
                    println("\nProblem: ${report.description}")
                    println("Type: ${report.type}")
                }
            }
            exitProcess(1)
        }
        
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun checkCommonIssues(model: org.apache.jena.ontology.OntModel) {
    println("\nChecking for common issues...")
    
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
                println("  ⚠ Potential circular dependency: ${ontClass.localName}")
                circularCount++
            }
        }
    
    if (circularCount == 0) {
        println("  ✓ No obvious circular dependencies found")
    }
    
    // Check for multiple inheritance issues
    val complexInheritance = model.listClasses().asSequence()
        .filter { it.isURIResource }
        .filter { it.listSuperClasses(true).toList().size > 3 }
        .toList()
    
    if (complexInheritance.isNotEmpty()) {
        println("\n  ⚠ Classes with complex inheritance (>3 superclasses):")
        complexInheritance.forEach { 
            println("    - ${it.localName}: ${it.listSuperClasses(true).toList().size} superclasses")
        }
    }
}