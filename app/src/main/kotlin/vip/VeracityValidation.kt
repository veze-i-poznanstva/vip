package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import java.net.URL
import java.net.HttpURLConnection

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

class VeracityValidation {
    
    private val EX_NS = "http://example.org/ontologija/"
    private val hasDocumentURLProperty = EX_NS + "hasDocumentURL"
    
    fun extractDomain(urlString: String): String {
        return try {
            val url = URL(urlString)
            var host = url.host
            
            // Remove www. prefix if present
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            
            host
        } catch (e: Exception) {
            urlString // Return original if parsing fails
        }
    }
    
    fun checkUrlAccessibility(urlString: String): Pair<Boolean, String> {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10000 // 10 seconds
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = true
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Pair(true, "OK")
            } else {
                Pair(false, "HTTP $responseCode")
            }
        } catch (e: Exception) {
            Pair(false, "Error: ${e.message}")
        }
    }
    
    fun validateItem(resource: Resource): ValidationResult {
        val model = resource.model
        val hasDocumentURL = model.createProperty(hasDocumentURLProperty)
        val errors = mutableListOf<String>()
        val resourceName = if (resource.isAnon) "Blank node" else resource.uri
        
        // Get all hasDocumentURL values for this resource
        val urls = mutableListOf<String>()
        val stmts = resource.listProperties(hasDocumentURL)
        
        while (stmts.hasNext()) {
            val stmt = stmts.next()
            val obj = stmt.`object`
            if (obj.isURIResource) {
                urls.add(obj.asResource().uri)
            }
        }
        
        // Check if resource has any URLs
        if (urls.isEmpty()) {
            return ValidationResult(false, listOf("$resourceName has no URLs"))
        }
        
        // Check if resource has at least 2 URLs
        if (urls.size < 2) {
            errors.add("$resourceName has only ${urls.size} URL")
        }
        
        // Extract unique domains
        val domains = urls.map { extractDomain(it) }.toSet()
        
        // Check if there are at least 2 different domains
        if (domains.size < 2) {
            errors.add("$resourceName has URLs from only ${domains.size} domain(s): ${domains.joinToString(", ")}")
        }
        
        // Check URL accessibility
        val inaccessibleUrls = mutableListOf<String>()
        for (url in urls) {
            val (isAccessible, status) = checkUrlAccessibility(url)
            if (!isAccessible) {
                inaccessibleUrls.add("$url ($status)")
            }
        }
        
        if (inaccessibleUrls.isNotEmpty()) {
            errors.add("$resourceName has inaccessible URLs: ${inaccessibleUrls.joinToString(", ")}")
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    fun validate(model: Model): ValidationResult {
        val errors = mutableListOf<String>()
        val hasDocumentURL = model.createProperty(hasDocumentURLProperty)
        
        // Find all resources that have hasDocumentURL property
        val stmts = model.listStatements(null, hasDocumentURL, null as RDFNode?)
        val resourcesToValidate = mutableSetOf<Resource>()
        
        while (stmts.hasNext()) {
            val stmt = stmts.next()
            resourcesToValidate.add(stmt.subject)
        }
        
        // Validate each resource
        for (resource in resourcesToValidate) {
            val result = validateItem(resource)
            if (!result.isValid) {
                errors.addAll(result.errors)
            }
        }
        
        return ValidationResult(errors.isEmpty(), errors)
    }
}