package vip

import org.apache.jena.ontology.OntClass
import org.apache.jena.ontology.OntModel
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(runSchemaMetrics(args, System.out, System.err))
}

/** The command line as a function: returns the exit status and writes only to the given streams. */
fun runSchemaMetrics(args: Array<String>, out: PrintStream, err: PrintStream): Int {
    if (args.isEmpty()) {
        err.println("Usage: SchemaMetrics <schema-file>")
        return 1
    }
    
    try {
        out.println("=== Schema Metrics Analysis ===\n")
        
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM)
        RDFDataMgr.read(model, args[0])
        
        // Basic statistics
        printBasicStats(out, model)
        
        // Namespace analysis
        printNamespaceInfo(out, model)
        
        // Class hierarchy analysis
        printClassHierarchyInfo(out, model)
        
        // Property analysis
        printPropertyInfo(out, model)
        
        // Complexity metrics
        printComplexityMetrics(out, model)
        return 0
        
    } catch (e: Exception) {
        err.println("Error: ${(e.message ?: e.toString()).printable()}")
        return 1
    }
}

private fun printBasicStats(out: PrintStream, model: OntModel) {
    out.println("Basic Statistics:")
    out.println("  Total triples: ${model.size()}")
    
    val classes = model.listClasses().toList()
    val objectProps = model.listObjectProperties().toList()
    val dataProps = model.listDatatypeProperties().toList()
    val individuals = model.listIndividuals().toList()
    
    out.println("  Classes: ${classes.size}")
    out.println("  Object Properties: ${objectProps.size}")
    out.println("  Datatype Properties: ${dataProps.size}")
    out.println("  Individuals: ${individuals.size}")
    out.println("  Annotations: ${model.listStatements(null, RDF.type, OWL.AnnotationProperty).toList().size}")
}

private fun printNamespaceInfo(out: PrintStream, model: OntModel) {
    val prefixMap = model.nsPrefixMap
    out.println("\nNamespaces (${prefixMap.size}):")
    
    // Group by common prefixes
    val standardPrefixes = setOf("rdf", "rdfs", "owl", "xsd", "xml")
    val customPrefixes = prefixMap.filter { it.key !in standardPrefixes }
    
    out.println("  Standard:")
    prefixMap.filter { it.key in standardPrefixes }
        .forEach { (prefix, uri) -> out.println("    ${prefix.printable()}: ${uri.printable()}") }
    
    if (customPrefixes.isNotEmpty()) {
        out.println("  Custom:")
        customPrefixes.forEach { (prefix, uri) -> out.println("    ${prefix.printable()}: ${uri.printable()}") }
    }
}

private fun printClassHierarchyInfo(out: PrintStream, model: OntModel) {
    out.println("\nClass Hierarchy Analysis:")
    
    val classes = model.listClasses().asSequence()
        .filter { it.isURIResource }
        .toList()
    
    // Calculate depth for each class
    val depths = mutableMapOf<OntClass, Int>()
    classes.forEach { cls ->
        depths[cls] = calculateClassDepth(out, cls)
    }
    
    val maxDepth = depths.values.maxOrNull() ?: 0
    val rootClasses = depths.filter { it.value == 0 }.size
    val leafClasses = classes.count { cls ->
        try {
            cls.listSubClasses(true).asSequence().none { it != cls }
        } catch (e: Exception) {
            // Skip classes that can't be converted properly
            out.println("    Warning: Could not process subclasses for ${(cls.localName ?: cls.uri).printable()}: ${(e.message ?: e.toString()).printable()}")
            true // Assume it's a leaf class if we can't process it
        }
    }
    
    out.println("  Total classes: ${classes.size}")
    out.println("  Maximum hierarchy depth: $maxDepth")
    out.println("  Root classes: $rootClasses")
    out.println("  Leaf classes: $leafClasses")
    
    // Find classes with most subclasses
    val classWithMostSubclasses = classes.mapNotNull { cls ->
        try {
            cls to cls.listSubClasses(true).toList().size
        } catch (e: Exception) {
            out.println("    Warning: Could not process subclasses for ${(cls.localName ?: cls.uri).printable()}: ${(e.message ?: e.toString()).printable()}")
            null
        }
    }.maxByOrNull { it.second }
    
    classWithMostSubclasses?.let { (cls, count) ->
        if (count > 0) {
            out.println("  Most subclasses: ${cls.localName.printable()} ($count subclasses)")
        }
    }
}

private fun calculateClassDepth(out: PrintStream, cls: OntClass, visited: Set<String> = emptySet()): Int {
    if (!cls.isURIResource || cls.uri in visited) return 0
    
    val superClasses = try {
        cls.listSuperClasses(true).asSequence()
            .filter { it.isURIResource && it != cls }
            .toList()
    } catch (e: Exception) {
        // Skip classes that can't be converted properly
        emptyList()
    }
    
    return if (superClasses.isEmpty()) {
        0
    } else {
        1 + (superClasses.maxOfOrNull { 
            calculateClassDepth(out, it, visited + cls.uri) 
        } ?: 0)
    }
}

private fun printPropertyInfo(out: PrintStream, model: OntModel) {
    out.println("\nProperty Analysis:")
    
    val allProps = model.listAllOntProperties().toList()
    var functional = 0
    var inverseFunctional = 0
    var transitive = 0
    var symmetric = 0
    var asymmetric = 0
    var reflexive = 0
    var irreflexive = 0
    
    allProps.forEach { prop ->
        if (prop.isFunctionalProperty) functional++
        if (prop.isInverseFunctionalProperty) inverseFunctional++
        if (prop.isTransitiveProperty) transitive++
        if (prop.isSymmetricProperty) symmetric++
        
        // Check for additional characteristics via statements
        if (model.contains(prop, RDF.type, model.createResource(OWL.NS + "AsymmetricProperty"))) asymmetric++
        if (model.contains(prop, RDF.type, model.createResource(OWL.NS + "ReflexiveProperty"))) reflexive++
        if (model.contains(prop, RDF.type, model.createResource(OWL.NS + "IrreflexiveProperty"))) irreflexive++
    }
    
    out.println("  Total properties: ${allProps.size}")
    out.println("  Functional: $functional")
    out.println("  Inverse functional: $inverseFunctional")
    out.println("  Transitive: $transitive")
    out.println("  Symmetric: $symmetric")
    out.println("  Asymmetric: $asymmetric")
    out.println("  Reflexive: $reflexive")
    out.println("  Irreflexive: $irreflexive")
    
    // Domain/Range analysis
    val propsWithDomain = allProps.count { it.domain != null }
    val propsWithRange = allProps.count { it.range != null }
    out.println("  With domain: $propsWithDomain")
    out.println("  With range: $propsWithRange")
}

private fun printComplexityMetrics(out: PrintStream, model: OntModel) {
    out.println("\nComplexity Metrics:")
    
    // Restriction count
    val restrictions = model.listRestrictions().toList().size
    out.println("  Restrictions: $restrictions")
    
    // Disjoint classes
    var disjointPairs = 0
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            try {
                disjointPairs += cls.listDisjointWith().toList().size
            } catch (e: Exception) {
                // Skip classes that can't be converted properly
            }
        }
    out.println("  Disjoint class pairs: ${disjointPairs / 2}")
    
    // Equivalent classes
    var equivalentPairs = 0
    model.listClasses().asSequence()
        .filter { it.isURIResource }
        .forEach { cls ->
            try {
                equivalentPairs += cls.listEquivalentClasses().toList().size
            } catch (e: Exception) {
                // Skip classes that can't be converted properly
            }
        }
    out.println("  Equivalent class pairs: ${equivalentPairs / 2}")
    
    // Anonymous classes (typically from restrictions)
    val anonClasses = model.listClasses().asSequence()
        .filter { it.isAnon }
        .count()
    out.println("  Anonymous classes: $anonClasses")
    
    // Calculate schema "density" - ratio of relationships to entities
    val entities = model.listClasses().toList().size + 
                  model.listAllOntProperties().toList().size
    val relationships = model.size()
    val density = if (entities > 0) relationships.toDouble() / entities else 0.0
    out.println("  Schema density: %.2f triples/entity".format(density))
}