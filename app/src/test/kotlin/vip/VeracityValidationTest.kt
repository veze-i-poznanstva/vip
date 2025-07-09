package vip

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF

class VeracityValidationTest {
    
    private val EX_NS = "http://example.org/ontologija/"
    private val FOAF_NS = "http://xmlns.com/foaf/0.1/"
    private val PODEROPEDIA_NS = "http://poderopedia.com/vocab/"
    
    @Test
    fun `extractDomain should extract domain from URL correctly`() {
        val validator = VeracityValidation()
        
        assertEquals("example.com", validator.extractDomain("https://example.com/page"))
        assertEquals("example.com", validator.extractDomain("http://example.com/page"))
        assertEquals("example.com", validator.extractDomain("https://www.example.com/page"))
        assertEquals("subdomain.example.com", validator.extractDomain("https://subdomain.example.com/page"))
        assertEquals("example.co.uk", validator.extractDomain("https://www.example.co.uk/page"))
        assertEquals("localhost", validator.extractDomain("http://localhost:8080/page"))
        assertEquals("sr.wikipedia.org", validator.extractDomain("https://sr.wikipedia.org/wiki/page"))
        assertEquals("istinomer.rs", validator.extractDomain("https://www.istinomer.rs/akter/someone"))
    }
    
    @Test
    fun `validate should pass when item has two URLs from different domains`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val person = model.createResource(EX_NS + "TestPerson")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(hasDocumentURL, model.createResource("https://wikipedia.org/page1"))
        person.addProperty(hasDocumentURL, model.createResource("https://example.com/page2"))
        
        val result = validator.validateItem(person)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun `validate should pass when item has URLs with www and without from different domains`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val person = model.createResource(EX_NS + "TestPerson")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(hasDocumentURL, model.createResource("https://www.wikipedia.org/page1"))
        person.addProperty(hasDocumentURL, model.createResource("https://example.com/page2"))
        
        val result = validator.validateItem(person)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun `validate should fail when item has only one URL`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val person = model.createResource(EX_NS + "Sinisa_Mali")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(hasDocumentURL, model.createResource("https://sr.wikipedia.org/wiki/Синиша_Мали"))
        
        val result = validator.validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("only 1 URL"))
    }
    
    @Test
    fun `validate should fail when item has multiple URLs from same domain`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val connection = model.createResource()
        connection.addProperty(RDF.type, model.createResource(PODEROPEDIA_NS + "BusinessAndPoliticalConnection"))
        connection.addProperty(hasDocumentURL, model.createResource("https://www.istinomer.rs/akter/ana-brnabic/"))
        connection.addProperty(hasDocumentURL, model.createResource("https://www.istinomer.rs/akter/ivan-bosnjak/"))
        
        val result = validator.validateItem(connection)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("from only 1 domain"))
    }
    
    @Test
    fun `validate should treat www and non-www as same domain`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val item = model.createResource(EX_NS + "TestItem")
        item.addProperty(hasDocumentURL, model.createResource("https://www.example.com/page1"))
        item.addProperty(hasDocumentURL, model.createResource("https://example.com/page2"))
        
        val result = validator.validateItem(item)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("from only 1 domain"))
    }
    
    @Test
    fun `validate should pass for Ivan Bosnjak with two different domains`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val person = model.createResource(EX_NS + "Ivan_Bosnjak")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(hasDocumentURL, model.createResource("https://www.istinomer.rs/akter/ivan-bosnjak/"))
        person.addProperty(hasDocumentURL, model.createResource("https://www.krik.rs/tag/ivan-bosnjak/"))
        
        val result = validator.validateItem(person)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun `validate should pass for complete model with valid items`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        
        // Person with valid URLs from different domains
        val person = model.createResource(EX_NS + "ValidPerson")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(hasDocumentURL, model.createResource("https://wikipedia.org/person"))
        person.addProperty(hasDocumentURL, model.createResource("https://example.com/person"))
        
        // Company with valid URLs from different domains
        val company = model.createResource(EX_NS + "ValidCompany")
        company.addProperty(RDF.type, model.createResource(PODEROPEDIA_NS + "Company"))
        company.addProperty(hasDocumentURL, model.createResource("https://sr.wikipedia.org/wiki/Company"))
        company.addProperty(hasDocumentURL, model.createResource("https://www.companywall.rs/firma/company"))
        
        val result = validator.validate(model)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
    
    @Test
    fun `validate should fail for model with invalid items`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        
        // Person with only one URL
        val person1 = model.createResource(EX_NS + "Person1")
        person1.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person1.addProperty(hasDocumentURL, model.createResource("https://wikipedia.org/person1"))
        
        // Relationship with URLs from same domain
        val relationship = model.createResource()
        relationship.addProperty(RDF.type, model.createResource(PODEROPEDIA_NS + "BusinessAndPoliticalConnection"))
        relationship.addProperty(hasDocumentURL, model.createResource("https://istinomer.rs/page1"))
        relationship.addProperty(hasDocumentURL, model.createResource("https://www.istinomer.rs/page2"))
        
        val result = validator.validate(model)
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
    }
    
    @Test
    fun `validate should handle items without any URLs`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val person = model.createResource(EX_NS + "PersonWithoutURLs")
        person.addProperty(RDF.type, model.createResource(FOAF_NS + "Person"))
        person.addProperty(model.createProperty(FOAF_NS + "name"), "Test Person")
        
        val result = validator.validateItem(person)
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("has no URLs"))
    }
    
    @Test
    fun `validate should handle blank nodes with URLs`() {
        val validator = VeracityValidation()
        val model = ModelFactory.createDefaultModel()
        
        val hasDocumentURL = model.createProperty(EX_NS + "hasDocumentURL")
        val blankNode = model.createResource()
        blankNode.addProperty(RDF.type, model.createResource(PODEROPEDIA_NS + "LeadershipRole"))
        blankNode.addProperty(hasDocumentURL, model.createResource("https://naled.rs/document.pdf"))
        blankNode.addProperty(hasDocumentURL, model.createResource("https://www.uzmiracun.rs/vest-1345"))
        
        val result = validator.validateItem(blankNode)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
    }
}