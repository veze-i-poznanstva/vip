package vip

import org.apache.jena.rdf.model.ModelFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.StringReader

class TtlToCytoscapeTest {

    @Test
    fun `should create valid node IDs from URIs`() {
        assertEquals("person", createNodeId("http://example.org/Person"))
        assertEquals("john_doe", createNodeId("http://example.org/John_Doe"))
        assertTrue(createNodeId("https://example.com/").startsWith("node_")) // hash-based for empty
    }

    @Test
    fun `should handle ownership relationships correctly`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            
            ex:Ivan a ex:Person .
            ex:Company a ex:Company .
            
            _:ownership a ex:Ownership ;
                ex:owner ex:Ivan ;
                ex:ownedCompany ex:Company .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val edges = elements.filter { it.data.source != null }
        
        // Should have Ivan -> Company ownership, not both directions
        val ownershipEdges = edges.filter { it.data.label?.contains("own") == true }
        assertEquals(1, ownershipEdges.size)
        
        val edge = ownershipEdges.first()
        assertEquals("ivan", edge.data.source)
        assertEquals("company", edge.data.target)
        assertTrue(edge.data.label!!.contains("own"))
    }

    @Test
    fun `should handle NALED leadership roles correctly`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            @prefix : <http://poderopedia.com/vocab/> .
            
            ex:Ivan_Bosnjak a ex:Person .
            ex:Ana_Brnabic a ex:Person .
            ex:NALED a :NonProfitOrganization .
            
            _:relNALED1 a :LeadershipRole ;
                ex:personParticipant ex:Ivan_Bosnjak ;
                ex:organizationParticipant ex:NALED ;
                :role "Board Member" .
                
            _:relNALED2 a :LeadershipRole ;
                ex:personParticipant ex:Ana_Brnabic ;
                ex:organizationParticipant ex:NALED ;
                :role "Chairman" .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val edges = elements.filter { it.data.source != null }
        
        // Should have both Ivan and Ana connected to NALED
        val leadershipEdges = edges.filter { it.data.label?.contains("leadership") == true }
        assertEquals(2, leadershipEdges.size)
        
        val sources = leadershipEdges.map { it.data.source }.toSet()
        val targets = leadershipEdges.map { it.data.target }.toSet()
        
        assertTrue(sources.contains("ivan_bosnjak"))
        assertTrue(sources.contains("ana_brnabic"))
        assertTrue(targets.contains("naled"))
    }

    @Test
    fun `should not create bidirectional ownership`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            
            ex:Person a ex:Person .
            ex:Company a ex:Company .
            ex:Company ex:hasOwner ex:Person .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val edges = elements.filter { it.data.source != null }
        
        // Should only have one ownership edge: Person owns Company
        val ownershipEdges = edges.filter { it.data.label?.contains("owned by") == true }
        assertEquals(1, ownershipEdges.size)
        
        val edge = ownershipEdges.first()
        assertEquals("company", edge.data.source) // Company is owned by Person
        assertEquals("person", edge.data.target)
    }

    @Test
    fun `should filter out document URLs`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            
            ex:Person a ex:Person .
            ex:Person ex:hasDocumentURL <https://wikipedia.org/Person> .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val nodes = elements.filter { it.data.source == null }
        val edges = elements.filter { it.data.source != null }
        
        // Should have Person node but no document URL node
        assertEquals(1, nodes.size)
        assertEquals("person", nodes.first().data.id)
        
        // Should have no edges to document URLs
        val docEdges = edges.filter { it.data.label?.contains("Document") == true }
        assertEquals(0, docEdges.size)
    }

    @Test
    fun `should determine correct node types`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            @prefix : <http://poderopedia.com/vocab/> .
            
            ex:John a foaf:Person .
            ex:Acme a :Company .
            ex:Harvard a :AcademicOrganization .
            ex:NALED a :NonProfitOrganization .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val nodes = elements.filter { it.data.source == null }
        
        val nodeTypes = nodes.associate { it.data.id to it.data.nodeType }
        
        assertEquals("person", nodeTypes["john"])
        assertEquals("organization", nodeTypes["acme"])
        assertEquals("organization", nodeTypes["harvard"]) // Academic is mapped to organization
        assertEquals("organization", nodeTypes["naled"])
    }

    @Test
    fun `should generate correct labels for different connection types`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            @prefix : <http://poderopedia.com/vocab/> .
            
            ex:Ana_Brnabic a ex:Person .
            ex:Sinisa_Mali a ex:Person .
            ex:Ivan_Bosnjak a ex:Person .
            
            # Political connection
            _:politicalRel a :PoliticalConnection ;
                ex:personParticipant ex:Ana_Brnabic ;
                ex:personParticipant ex:Sinisa_Mali .
                
            # Business and political connection  
            _:businessRel a :BusinessAndPoliticalConnection ;
                ex:personParticipant ex:Ana_Brnabic ;
                ex:personParticipant ex:Ivan_Bosnjak .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val edges = elements.filter { it.data.source != null }
        
        // Should have 2 person-to-person connections
        assertEquals(2, edges.size)
        
        // Find the edges by their participants
        val anaSinisaEdge = edges.find { 
            (it.data.source == "ana_brnabic" && it.data.target == "sinisa_mali") ||
            (it.data.source == "sinisa_mali" && it.data.target == "ana_brnabic")
        }
        
        val anaIvanEdge = edges.find {
            (it.data.source == "ana_brnabic" && it.data.target == "ivan_bosnjak") ||
            (it.data.source == "ivan_bosnjak" && it.data.target == "ana_brnabic")
        }
        
        assertNotNull(anaSinisaEdge, "Ana-Sinisa political connection should exist")
        assertNotNull(anaIvanEdge, "Ana-Ivan business connection should exist")
        
        // Debug: Print actual edges
        println("Edges found:")
        edges.forEach { edge ->
            println("  ${edge.data.source} -> ${edge.data.target}: ${edge.data.label}")
        }
        
        // Check correct labels
        assertEquals("political connection", anaSinisaEdge!!.data.label)
        assertEquals("business connection", anaIvanEdge!!.data.label)
    }

    @Test
    fun `should not confuse different blank node connection types`() {
        val ttl = """
            @prefix ex: <http://example.org/> .
            @prefix : <http://poderopedia.com/vocab/> .
            
            ex:PersonA a ex:Person .
            ex:PersonB a ex:Person .
            ex:PersonC a ex:Person .
            ex:PersonD a ex:Person .
            
            # Different connection types should get different labels
            _:rel1 a :PoliticalConnection ;
                ex:personParticipant ex:PersonA, ex:PersonB .
                
            _:rel2 a :BusinessAndPoliticalConnection ;
                ex:personParticipant ex:PersonC, ex:PersonD .
                
            _:rel3 a :SomeOtherConnection ;
                ex:personParticipant ex:PersonA, ex:PersonC .
        """.trimIndent()

        val model = ModelFactory.createDefaultModel()
        model.read(StringReader(ttl), null, "TTL")
        
        val elements = convertModelToElements(model)
        val edges = elements.filter { it.data.source != null }
        
        val labels = edges.map { it.data.label }.toSet()
        
        // Should have different labels for different connection types
        assertTrue(labels.contains("political connection"), "Should have political connection label")
        assertTrue(labels.contains("business connection"), "Should have business connection label")  
        assertTrue(labels.contains("connected to"), "Should have generic label for unknown types")
        
        // Should not have the wrong label
        assertFalse(labels.contains("business connection") && labels.size == 1, 
                   "Should not label all connections as business connections")
    }

    // Helper function to test the conversion logic
    private fun convertModelToElements(model: org.apache.jena.rdf.model.Model): List<CytoscapeElement> {
        return convertModelToCytoscape(model)
    }
}

// Additional test data
object TestData {
    val MINIMAL_OWNERSHIP = """
        @prefix ex: <http://example.org/> .
        
        ex:Owner a ex:Person ;
            ex:name "Owner Person" .
            
        ex:Company a ex:Company ;
            ex:name "Test Company" .
            
        _:ownership a ex:Ownership ;
            ex:owner ex:Owner ;
            ex:ownedCompany ex:Company .
    """.trimIndent()

    val NALED_RELATIONSHIPS = """
        @prefix ex: <http://example.org/> .
        @prefix : <http://poderopedia.com/vocab/> .
        
        ex:Ivan_Bosnjak a ex:Person .
        ex:Ana_Brnabic a ex:Person .
        ex:NALED a :NonProfitOrganization .
        
        _:rel1 a :LeadershipRole ;
            ex:personParticipant ex:Ivan_Bosnjak ;
            ex:organizationParticipant ex:NALED .
            
        _:rel2 a :LeadershipRole ;
            ex:personParticipant ex:Ana_Brnabic ;
            ex:organizationParticipant ex:NALED .
    """.trimIndent()
}