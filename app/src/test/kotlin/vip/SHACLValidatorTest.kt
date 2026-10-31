package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SHACLValidatorTest {

    private val schema: Model by lazy { RDFDataMgr.loadModel("schema.ttl") }

    private fun data(ttl: String): Model = ModelFactory.createDefaultModel().also {
        it.read(StringReader("""
            @prefix rs: <https://w3id.org/vip/rs/> .
            @prefix : <http://poderopedia.com/vocab/> .
            @prefix vip: <https://w3id.org/vip/vocab#> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            $ttl
        """.trimIndent()), null, "TTL")
    }

    private val documented = """:hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/> ; :hasDocumentType vip:newsArticle ; :hasDocumentStatus vip:published ]"""

    @Test
    fun `a person with the required names and documentation conforms`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented ."""))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a connection whose participant is a person satisfies a shape that asks for an agent, through inference`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
            rs:SNS a :PoliticalOrganization ; foaf:name "SNS" ; $documented .
            rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS ; $documented .
        """))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a connection without documentation violates the connection shape`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
            rs:SNS a :PoliticalOrganization ; foaf:name "SNS" ; $documented .
            rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS .
        """))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("AnaSNS") }, report.entries.joinToString { it.focusNode().toString() })
    }

    @Test
    fun `a person without a first name violates the person shape`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; :firstLastName "B" ; $documented ."""))
        assertFalse(report.conforms())
    }
}
