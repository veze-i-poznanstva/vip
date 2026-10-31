package vip

import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.PrintStream
import kotlin.system.exitProcess

data class ValidationIssue(val severity: Severity, val message: String)
enum class Severity { ERROR, WARNING, INFO }

fun main(args: Array<String>) {
    exitProcess(runBestPracticesChecker(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runBestPracticesChecker(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.isEmpty()) {
        err.println("Usage: BestPracticesChecker <schema-file>")
        return 1
    }
    
    try {
        out.println("=== OWL/RDF Best Practices Check ===\n")
        
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM)
        RDFDataMgr.read(model, args[0])
        
        val issues = mutableListOf<ValidationIssue>()
        
        // Run all checks
        checkMissingAnnotations(out, model, issues)
        checkNamingConventions(out, model, issues)
        checkOrphanClasses(out, model, issues)
        checkPropertyUsage(out, model, issues)
        checkMultipleInheritance(out, model, issues)
        checkURIPatterns(out, model, issues)
        checkLanguageTags(out, model, issues)
        checkDeprecatedElements(out, model, issues)
        
        // Report results; errors make the check fail, warnings and suggestions do not
        printReport(out, issues)
        return if (issues.any { it.severity == Severity.ERROR }) 1 else 0
        
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}

private fun checkMissingAnnotations(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking for missing annotations...")
    
    // Check classes for labels and comments
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            if (!cls.hasProperty(RDFS.label)) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Class '${cls.localName}' missing rdfs:label"))
            }
            if (!cls.hasProperty(RDFS.comment)) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Class '${cls.localName}' missing rdfs:comment"))
            }
        }
    
    // Check properties
    model.listAllOntProperties().asSequence()
        .filter { it.isURIResource }
        .forEach { prop ->
            if (!prop.hasProperty(RDFS.label)) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Property '${prop.localName}' missing rdfs:label"))
            }
            if (!prop.hasProperty(RDFS.comment)) {
                issues.add(ValidationIssue(Severity.INFO, 
                    "Property '${prop.localName}' missing rdfs:comment"))
            }
        }
}

private fun checkNamingConventions(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking naming conventions...")
    
    // Check class names (should start with uppercase)
    model.listClasses().asSequence()
        .filter { it.isURIResource && it.localName != null }
        .forEach { cls ->
            val name = cls.localName
            if (name.isNotEmpty() && !name[0].isUpperCase()) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Class name should start with uppercase: '$name'"))
            }
            if (name.contains("_")) {
                issues.add(ValidationIssue(Severity.INFO, 
                    "Class name contains underscore (consider CamelCase): '$name'"))
            }
        }
    
    // Check property names (should start with lowercase)
    model.listAllOntProperties().asSequence()
        .filter { it.isURIResource && it.localName != null }
        .forEach { prop ->
            val name = prop.localName
            if (name.isNotEmpty() && !name[0].isLowerCase()) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Property name should start with lowercase: '$name'"))
            }
        }
}

private fun checkOrphanClasses(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking for orphan classes...")
    
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            val hasSuper = try { cls.listSuperClasses(true).hasNext() } catch (e: Exception) { false }
            val hasSub = try { cls.listSubClasses(true).hasNext() } catch (e: Exception) { false }
            val hasInstances = model.listIndividuals(cls).hasNext()
            val usedInDomain = model.contains(null, RDFS.domain, cls)
            val usedInRange = model.contains(null, RDFS.range, cls)
            val usedInRestriction = try {
                model.listRestrictions().asSequence()
                    .any { restriction ->
                        try {
                            restriction.onProperty != null
                        } catch (e: Exception) {
                            false
                        }
                    }
            } catch (e: Exception) {
                false
            }
            
            if (!hasSuper && !hasSub && !hasInstances && 
                !usedInDomain && !usedInRange && !usedInRestriction) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Potentially orphan class: '${cls.localName}'"))
            }
        }
}

private fun checkPropertyUsage(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking property usage...")
    
    model.listAllOntProperties().asSequence()
        .filter { it.isURIResource }
        .forEach { prop ->
            // Check for missing domain/range
            if (prop.domain == null) {
                issues.add(ValidationIssue(Severity.INFO, 
                    "Property '${prop.localName}' has no domain specified"))
            }
            if (prop.range == null) {
                issues.add(ValidationIssue(Severity.INFO, 
                    "Property '${prop.localName}' has no range specified"))
            }
            
            // Check for inverse properties on symmetric properties
            if (prop.isSymmetricProperty && prop.hasInverse()) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Symmetric property '${prop.localName}' should not have an inverse"))
            }
        }
}

private fun checkMultipleInheritance(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking multiple inheritance...")
    
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            val directSuperClasses = cls.listSuperClasses(true).toList()
            if (directSuperClasses.size > 3) {
                issues.add(ValidationIssue(Severity.WARNING, 
                    "Class '${cls.localName}' has ${directSuperClasses.size} direct superclasses (consider refactoring)"))
            }
        }
}

private fun checkURIPatterns(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking URI patterns...")
    
    val namespaces = model.listNameSpaces().toSet()
    val mainNamespace = namespaces.firstOrNull { ns ->
        model.listStatements().asSequence()
            .count { it.subject.isURIResource && it.subject.uri.startsWith(ns) } > 10
    }
    
    if (mainNamespace != null) {
        // Check for consistent use of trailing # or /
        val hashCount = model.listStatements().asSequence()
            .filter { it.subject.isURIResource }
            .count { it.subject.uri.contains("#") }
        val slashCount = model.listStatements().asSequence()
            .filter { it.subject.isURIResource }
            .count { it.subject.uri.substringBeforeLast("/") == it.subject.nameSpace }
        
        if (hashCount > 0 && slashCount > 0) {
            issues.add(ValidationIssue(Severity.WARNING, 
                "Inconsistent URI patterns: both # and / separators used"))
        }
    }
}

private fun checkLanguageTags(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking language tags...")
    
    val languageTags = mutableSetOf<String>()
    
    // Collect all language tags
    model.listStatements().asSequence()
        .filter { it.`object`.isLiteral }
        .forEach { stmt ->
            val lit = stmt.`object`.asLiteral()
            if (lit.language.isNotEmpty()) {
                languageTags.add(lit.language)
            }
        }
    
    // Check for consistency
    val expectedLanguages = setOf("en", "es") // Based on your schema
    
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            val labels = cls.listProperties(RDFS.label).toList()
            val labelLangs = labels.map { it.`object`.asLiteral().language }.toSet()
            
            expectedLanguages.forEach { lang ->
                if (lang !in labelLangs && labels.isNotEmpty()) {
                    issues.add(ValidationIssue(Severity.INFO, 
                        "Class '${cls.localName}' missing label for language: $lang"))
                }
            }
        }
}

private fun checkDeprecatedElements(out: PrintStream, model: org.apache.jena.ontology.OntModel, issues: MutableList<ValidationIssue>) {
    out.println("Checking for deprecated elements...")
    
    // Check for deprecated properties
    model.listStatements(null, RDF.type, OWL.DeprecatedProperty).asSequence()
        .forEach { stmt ->
            issues.add(ValidationIssue(Severity.WARNING, 
                "Using deprecated property: ${stmt.subject.localName}"))
        }
    
    // Check for deprecated classes
    model.listStatements(null, RDF.type, OWL.DeprecatedClass).asSequence()
        .forEach { stmt ->
            issues.add(ValidationIssue(Severity.WARNING, 
                "Using deprecated class: ${stmt.subject.localName}"))
        }
}

private fun printReport(out: PrintStream, issues: List<ValidationIssue>) {
    out.println("\n=== Validation Report ===")
    
    val errors = issues.filter { it.severity == Severity.ERROR }
    val warnings = issues.filter { it.severity == Severity.WARNING }
    val info = issues.filter { it.severity == Severity.INFO }
    
    if (issues.isEmpty()) {
        out.println("\n✓ No issues found! Your schema follows best practices.")
    } else {
        if (errors.isNotEmpty()) {
            out.println("\n❌ Errors (${errors.size}):")
            errors.forEach { out.println("  - ${it.message.printable()}") }
        }
        
        if (warnings.isNotEmpty()) {
            out.println("\n⚠️  Warnings (${warnings.size}):")
            warnings.take(10).forEach { out.println("  - ${it.message.printable()}") }
            if (warnings.size > 10) {
                out.println("  ... and ${warnings.size - 10} more warnings")
            }
        }
        
        if (info.isNotEmpty()) {
            out.println("\nℹ️  Information (${info.size}):")
            info.take(5).forEach { out.println("  - ${it.message.printable()}") }
            if (info.size > 5) {
                out.println("  ... and ${info.size - 5} more suggestions")
            }
        }
        
        out.println("\nSummary:")
        out.println("  Total issues: ${issues.size}")
        out.println("  Errors: ${errors.size}")
        out.println("  Warnings: ${warnings.size}")
        out.println("  Info: ${info.size}")
    }
}