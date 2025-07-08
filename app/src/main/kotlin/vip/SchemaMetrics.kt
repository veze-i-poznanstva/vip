package vip

import org.apache.jena.ontology.OntClass
import org.apache.jena.ontology.OntModel
import org.apache.jena.ontology.OntModelSpec
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: SchemaMetrics <schema-file>")
        exitProcess(1)
    }
    
    try {
        println("=== Schema Metrics Analysis ===\n")
        
        val model = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM)
        RDFDataMgr.read(model, args[0])
        
        // Basic statistics
        printBasicStats(model)
        
        // Namespace analysis
        printNamespaceInfo(model)
        
        // Class hierarchy analysis
        printClassHierarchyInfo(model)
        
        // Property analysis
        printPropertyInfo(model)
        
        // Complexity metrics
        printComplexityMetrics(model)
        
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun printBasicStats(model: OntModel) {
    println("Basic Statistics:")
    println("  Total triples: ${model.size()}")
    
    val classes = model.listClasses().toList()
    val objectProps = model.listObjectProperties().toList()
    val dataProps = model.listDatatypeProperties().toList()
    val individuals = model.listIndividuals().toList()
    
    println("  Classes: ${classes.size}")
    println("  Object Properties: ${objectProps.size}")
    println("  Datatype Properties: ${dataProps.size}")
    println("  Individuals: ${individuals.size}")
    println("  Annotations: ${model.listStatements(null, RDF.type, OWL.AnnotationProperty).toList().size}")
}

private fun printNamespaceInfo(model: OntModel) {
    val prefixMap = model.nsPrefixMap
    println("\nNamespaces (${prefixMap.size}):")
    
    // Group by common prefixes
    val standardPrefixes = setOf("rdf", "rdfs", "owl", "xsd", "xml")
    val customPrefixes = prefixMap.filter { it.key !in standardPrefixes }
    
    println("  Standard:")
    prefixMap.filter { it.key in standardPrefixes }
        .forEach { (prefix, uri) -> println("    $prefix: $uri") }
    
    if (customPrefixes.isNotEmpty()) {
        println("  Custom:")
        customPrefixes.forEach { (prefix, uri) -> println("    $prefix: $uri") }
    }
}

private fun printClassHierarchyInfo(model: OntModel) {
    println("\nClass Hierarchy Analysis:")
    
    val classes = model.listClasses().asSequence()
        .filter { it.isURIResource }
        .toList()
    
    // Calculate depth for each class
    val depths = mutableMapOf<OntClass, Int>()
    classes.forEach { cls ->
        depths[cls] = calculateClassDepth(cls)
    }
    
    val maxDepth = depths.values.maxOrNull() ?: 0
    val rootClasses = depths.filter { it.value == 0 }.size
    val leafClasses = classes.count { cls ->
        try {
            cls.listSubClasses(true).asSequence().none { it != cls }
        } catch (e: Exception) {
            // Skip classes that can't be converted properly
            println("    Warning: Could not process subclasses for ${cls.localName ?: cls.uri}: ${e.message}")
            true // Assume it's a leaf class if we can't process it
        }
    }
    
    println("  Total classes: ${classes.size}")
    println("  Maximum hierarchy depth: $maxDepth")
    println("  Root classes: $rootClasses")
    println("  Leaf classes: $leafClasses")
    
    // Find classes with most subclasses
    val classWithMostSubclasses = classes.mapNotNull { cls ->
        try {
            cls to cls.listSubClasses(true).toList().size
        } catch (e: Exception) {
            println("    Warning: Could not process subclasses for ${cls.localName ?: cls.uri}: ${e.message}")
            null
        }
    }.maxByOrNull { it.second }
    
    classWithMostSubclasses?.let { (cls, count) ->
        if (count > 0) {
            println("  Most subclasses: ${cls.localName} ($count subclasses)")
        }
    }
}

private fun calculateClassDepth(cls: OntClass, visited: Set<String> = emptySet()): Int {
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
            calculateClassDepth(it, visited + cls.uri) 
        } ?: 0)
    }
}

private fun printPropertyInfo(model: OntModel) {
    println("\nProperty Analysis:")
    
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
    
    println("  Total properties: ${allProps.size}")
    println("  Functional: $functional")
    println("  Inverse functional: $inverseFunctional")
    println("  Transitive: $transitive")
    println("  Symmetric: $symmetric")
    println("  Asymmetric: $asymmetric")
    println("  Reflexive: $reflexive")
    println("  Irreflexive: $irreflexive")
    
    // Domain/Range analysis
    val propsWithDomain = allProps.count { it.domain != null }
    val propsWithRange = allProps.count { it.range != null }
    println("  With domain: $propsWithDomain")
    println("  With range: $propsWithRange")
}

private fun printComplexityMetrics(model: OntModel) {
    println("\nComplexity Metrics:")
    
    // Restriction count
    val restrictions = model.listRestrictions().toList().size
    println("  Restrictions: $restrictions")
    
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
    println("  Disjoint class pairs: ${disjointPairs / 2}")
    
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
    println("  Equivalent class pairs: ${equivalentPairs / 2}")
    
    // Anonymous classes (typically from restrictions)
    val anonClasses = model.listClasses().asSequence()
        .filter { it.isAnon }
        .count()
    println("  Anonymous classes: $anonClasses")
    
    // Calculate schema "density" - ratio of relationships to entities
    val entities = model.listClasses().toList().size + 
                  model.listAllOntProperties().toList().size
    val relationships = model.size()
    val density = if (entities > 0) relationships.toDouble() / entities else 0.0
    println("  Schema density: %.2f triples/entity".format(density))
}