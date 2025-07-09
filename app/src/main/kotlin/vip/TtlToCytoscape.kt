package vip

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import kotlin.system.exitProcess

data class CytoscapeElement(
    val data: ElementData,
    val classes: String? = null
)

data class ElementData(
    val id: String,
    val label: String? = null,
    val source: String? = null,
    val target: String? = null,
    val type: String? = null,
    val nodeType: String? = null,
    val uri: String? = null
)

fun main(args: Array<String>) {
    if (args.size != 2) {
        println("Usage: ttlToCytoscape <input.ttl> <output.json>")
        println("Example: ttlToCytoscape test-data.ttl web/public/test-data-cytoscape.json")
        exitProcess(1)
    }

    val inputFile = File(args[0])
    val outputFile = File(args[1])

    if (!inputFile.exists()) {
        println("Error: Input file '${inputFile.path}' not found")
        exitProcess(1)
    }

    // Create output directory if needed
    outputFile.parentFile?.mkdirs()

    try {
        println("Converting ${inputFile.path} to Cytoscape format...")
        
        // Load RDF model
        val model = ModelFactory.createDefaultModel()
        model.read(inputFile.inputStream(), null, "TTL")
        
        val elements = convertModelToCytoscape(model)
        
        // Write JSON output
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(elements)
        outputFile.writeText(json)
        
        val nodeCount = elements.count { it.data.source == null }
        val edgeCount = elements.count { it.data.source != null }
        println("Successfully converted to ${outputFile.path}")
        println("Created $nodeCount nodes and $edgeCount edges")
        
    } catch (e: Exception) {
        println("Error converting file: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

fun convertModelToCytoscape(model: Model): List<CytoscapeElement> {
    val elements = mutableListOf<CytoscapeElement>()
    val nodeIds = mutableSetOf<String>()
    
    // Process all resources as potential nodes
    val subjects = model.listSubjects()
        while (subjects.hasNext()) {
            val subject = subjects.next()
            if (subject.isURIResource) {
                val nodeId = createNodeId(subject.uri)
                if (!nodeIds.contains(nodeId)) {
                    nodeIds.add(nodeId)
                    val nodeElement = createNodeElement(subject, model)
                    elements.add(nodeElement)
                }
            }
        }
        
        // Process objects that might not be subjects (but filter out URLs and types)
        val statements = model.listStatements()
        while (statements.hasNext()) {
            val stmt = statements.next()
            val obj = stmt.`object`
            
            if (obj.isURIResource && !stmt.predicate.uri.contains("hasDocumentURL") && stmt.predicate != RDF.type) {
                val objResource = obj.asResource()
                val uri = objResource.uri
                
                // Skip URLs and type definitions
                if (!uri.startsWith("http://www.") && !uri.startsWith("https://www.") && 
                    !uri.startsWith("https://sr.wikipedia.org") && !uri.startsWith("https://naled.rs") &&
                    !uri.contains("companywall.rs") && !uri.contains("danas.rs") && 
                    !uri.contains("istinomer.rs") && !uri.contains("krik.rs") &&
                    !uri.contains("nova.rs") && !uri.contains("uzmiracun.rs")) {
                    
                    val nodeId = createNodeId(uri)
                    if (!nodeIds.contains(nodeId)) {
                        nodeIds.add(nodeId)
                        val nodeElement = createNodeElement(objResource, model)
                        elements.add(nodeElement)
                    }
                }
            }
        }
        
        // Create edges (excluding type declarations and literal properties)
        var edgeCounter = 0
        val stmts = model.listStatements()
        while (stmts.hasNext()) {
            val stmt = stmts.next()
            
            // Skip type declarations, literal objects, and document URLs
            if (stmt.predicate != RDF.type && stmt.`object`.isURIResource && stmt.subject.isURIResource &&
                !stmt.predicate.uri.contains("hasDocumentURL")) {
                val sourceId = createNodeId(stmt.subject.uri)
                val targetId = createNodeId(stmt.`object`.asResource().uri)
                val edgeLabel = getLabel(stmt.predicate)
                
                val edgeElement = CytoscapeElement(
                    data = ElementData(
                        id = "edge${edgeCounter++}",
                        source = sourceId,
                        target = targetId,
                        label = edgeLabel
                    )
                )
                elements.add(edgeElement)
            }
        }
        
        // Handle qualified relationships with blank nodes (like NALED leadership roles)
        val blankNodes = model.listSubjects().toList().filter { it.isAnon }
        for (blankNode in blankNodes) {
            val typeStmts = model.listStatements(blankNode, RDF.type, null as RDFNode?)
            val types = typeStmts.toList().map { it.`object`.toString() }
            
            if (types.any { it.contains("LeadershipRole") || it.contains("Connection") || it.contains("Ownership") }) {
                // Extract participants (can be multiple)
                val persons = mutableListOf<Resource>()
                val organizations = mutableListOf<Resource>()
                var owner: Resource? = null
                var company: Resource? = null
                
                val props = model.listStatements(blankNode, null, null as RDFNode?)
                while (props.hasNext()) {
                    val prop = props.next()
                    if (prop.predicate.uri.contains("personParticipant") && prop.`object`.isURIResource) {
                        persons.add(prop.`object`.asResource())
                    } else if (prop.predicate.uri.contains("organizationParticipant") && prop.`object`.isURIResource) {
                        organizations.add(prop.`object`.asResource())
                    } else if (prop.predicate.uri.contains("owner") && prop.`object`.isURIResource) {
                        owner = prop.`object`.asResource()
                    } else if (prop.predicate.uri.contains("ownedCompany") && prop.`object`.isURIResource) {
                        company = prop.`object`.asResource()
                    }
                }
                
                // Create edges for person-organization relationships
                for (person in persons) {
                    for (organization in organizations) {
                        val roleLabel = if (types.any { it.contains("LeadershipRole") }) "leadership role" else "connected to"
                        val edgeElement = CytoscapeElement(
                            data = ElementData(
                                id = "edge${edgeCounter++}",
                                source = createNodeId(person.uri),
                                target = createNodeId(organization.uri),
                                label = roleLabel
                            )
                        )
                        elements.add(edgeElement)
                    }
                }
                
                // Create edges for person-person connections (like BusinessAndPoliticalConnection)
                if (persons.size >= 2 && organizations.isEmpty()) {
                    // Determine connection type based on the relationship type
                    val connectionLabel = when {
                        types.any { it.contains("BusinessAndPoliticalConnection") } -> "business connection"
                        types.any { it.contains("PoliticalConnection") } -> "political connection"
                        else -> "connected to"
                    }
                    
                    // Connect all persons to each other
                    for (i in persons.indices) {
                        for (j in i + 1 until persons.size) {
                            val edgeElement = CytoscapeElement(
                                data = ElementData(
                                    id = "edge${edgeCounter++}",
                                    source = createNodeId(persons[i].uri),
                                    target = createNodeId(persons[j].uri),
                                    label = connectionLabel
                                )
                            )
                            elements.add(edgeElement)
                        }
                    }
                }
                
                if (owner != null && company != null) {
                    val edgeElement = CytoscapeElement(
                        data = ElementData(
                            id = "edge${edgeCounter++}",
                            source = createNodeId(owner.uri),
                            target = createNodeId(company.uri),
                            label = "owns"
                        )
                    )
                    elements.add(edgeElement)
                }
            }
        }
        
        return elements
}

fun createNodeElement(resource: Resource, model: Model): CytoscapeElement {
    val nodeId = createNodeId(resource.uri)
    val label = getResourceLabel(resource, model)
    val types = getResourceTypes(resource, model)
    val nodeType = determineNodeType(types, resource.uri)
    
    return CytoscapeElement(
        data = ElementData(
            id = nodeId,
            label = label,
            type = if (types.isNotEmpty()) "instance" else "resource",
            nodeType = nodeType,
            uri = resource.uri
        )
    )
}

fun getResourceLabel(resource: Resource, model: Model): String {
    // Try to find a label property
    val labelProps = listOf(
        "http://xmlns.com/foaf/0.1/name",
        RDFS.label.uri,
        "http://purl.org/dc/terms/title"
    )
    
    for (prop in labelProps) {
        val stmt = model.getProperty(resource, model.getProperty(prop))
        if (stmt != null) {
            return stmt.`object`.toString().replace("@sr", "").replace("\"", "")
        }
    }
    
    // Fallback to extracting from URI
    return extractLabelFromUri(resource.uri)
}

fun getResourceTypes(resource: Resource, model: Model): List<String> {
    val types = mutableListOf<String>()
    val typeStmts = model.listStatements(resource, RDF.type, null as RDFNode?)
    
    while (typeStmts.hasNext()) {
        val stmt = typeStmts.next()
        if (stmt.`object`.isURIResource) {
            types.add(stmt.`object`.asResource().uri)
        }
    }
    
    return types
}

fun determineNodeType(types: List<String>, uri: String): String {
    // Check types for categorization
    val typeString = types.joinToString(" ")
    
    return when {
        typeString.contains("Person") -> "person"
        typeString.contains("Company") -> "organization"
        typeString.contains("Organization") -> "organization"
        typeString.contains("Connection") || typeString.contains("Role") -> "relationship"
        typeString.contains("Academic") -> "academic"
        uri.contains("Party") || uri.contains("stranka") -> "party"
        else -> "default"
    }
}

fun createNodeId(uri: String): String {
    // Convert URI to a valid Cytoscape node ID
    val id = uri
        .substringAfterLast("/")
        .substringAfterLast("#")
        .replace(Regex("[^a-zA-Z0-9_]"), "_")
        .lowercase()
    
    // If ID is empty or just underscores, use a hash of the full URI
    return if (id.isBlank() || id.all { it == '_' }) {
        "node_" + uri.hashCode().toString().replace("-", "n")
    } else {
        id
    }
}

fun extractLabelFromUri(uri: String): String {
    val label = uri
        .substringAfterLast("/")
        .substringAfterLast("#")
        .replace("_", " ")
        .split(Regex("(?=[A-Z])"))
        .joinToString(" ")
        .trim()
    
    return label.ifEmpty { uri }
}

fun getLabel(property: Property): String {
    val uri = property.uri
    return when {
        uri.contains("subsidiaryCompanyOf") -> "subsidiary of"
        uri.contains("hasOwner") -> "owned by"
        uri.contains("personParticipant") -> "involves"
        uri.contains("organizationParticipant") -> "connected to"
        uri.contains("party") -> "member of"
        uri.contains("hasAcademicOrganizationParticipant") -> "attended"
        else -> extractLabelFromUri(uri)
    }
}