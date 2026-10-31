package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import java.io.File
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each rule in rules/ against a fixture that must match and one that must not. */
class RedFlagsTest {

    private val schema: Model by lazy { RDFDataMgr.loadModel("schema.ttl") }
    private val rules: Map<String, Rule> by lazy { loadRules(File("rules")).associateBy { it.name } }

    private val prefixes = """
        @prefix ex: <https://w3id.org/vip/rs/> .
        @prefix : <http://poderopedia.com/vocab/> .
        @prefix vip: <https://w3id.org/vip/vocab#> .
        @prefix foaf: <http://xmlns.com/foaf/0.1/> .
        @prefix bio: <http://purl.org/vocab/bio/0.1/> .
    """.trimIndent()

    private fun matches(rule: String, ttl: String): List<Map<String, String>> {
        val data = ModelFactory.createDefaultModel()
        data.read(StringReader(prefixes + "\n" + ttl.trimIndent()), null, "TTL")
        return findRedFlags(data, schema, listOf(rules.getValue(rule))).single().matches
    }

    @Test
    fun `nepotism matches a spouse of the chair employed as advisor at the same organisation`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:B a foaf:Person . ex:Org a foaf:Organization .
            ex:A_Org a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole vip:boardChair .
            ex:B_Org a :WorkConnection ; bio:participant ex:B ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :advisor .
            ex:AB a :Marriage ; :hasSpouse ex:A, ex:B .
        """)
        assertEquals(1, found.size, found.toString())
        assertEquals("https://w3id.org/vip/rs/A", found[0]["leader"])
        assertEquals("https://w3id.org/vip/rs/B", found[0]["relative"])
        assertEquals("spouse or partner", found[0]["relation"])
        assertEquals("https://w3id.org/vip/rs/Org", found[0]["organization"])
    }

    @Test
    fun `nepotism matches a kum and a sibling of a director employed anywhere in the organisation`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:K a foaf:Person . ex:S a foaf:Person . ex:Org a foaf:Organization .
            ex:A_Org a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :director .
            ex:K_Org a :WorkConnection ; bio:participant ex:K ; :hasEconomicOrganizationParticipant ex:Org .
            ex:S_Org a :WorkConnection ; bio:participant ex:S ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :employee .
            ex:AK a vip:GodparentConnection ; vip:godparent ex:A ; vip:godchild ex:K .
            ex:A :hasSibling ex:S .
        """)
        assertEquals(mapOf("https://w3id.org/vip/rs/K" to "kum", "https://w3id.org/vip/rs/S" to "family"), found.associate { it.getValue("relative") to it.getValue("relation") })
    }

    @Test
    fun `nepotism ignores a spouse employed elsewhere, an ordinary colleague, and the leader's own job`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:B a foaf:Person . ex:C a foaf:Person . ex:Org a foaf:Organization . ex:Other a foaf:Organization .
            ex:A_Org a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :ceo .
            ex:B_Other a :WorkConnection ; bio:participant ex:B ; :hasEconomicOrganizationParticipant ex:Other ; :hasWorkRole :advisor .
            ex:C_Org a :WorkConnection ; bio:participant ex:C ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :advisor .
            ex:AB a :Marriage ; :hasSpouse ex:A, ex:B .
        """)
        assertEquals(emptyList(), found)
    }

    @Test
    fun `owner in office matches a company owner with a post in a government organisation and ignores a private employer`() {
        val found = matches("owner-in-office", """
            ex:A a foaf:Person . ex:B a foaf:Person . ex:Firm a :Company . ex:Ministry a :GovernmentOrganization . ex:Shop a :Company .
            ex:A_Firm a vip:OwnershipConnection ; vip:owner ex:A ; vip:ownedCompany ex:Firm .
            ex:A_Ministry a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Ministry ; :hasWorkRole :advisor .
            ex:B_Firm a vip:OwnershipConnection ; vip:owner ex:B ; vip:ownedCompany ex:Firm .
            ex:B_Shop a :WorkConnection ; bio:participant ex:B ; :hasEconomicOrganizationParticipant ex:Shop .
        """)
        assertEquals(1, found.size, found.toString())
        assertEquals("https://w3id.org/vip/rs/A", found[0]["person"])
        assertEquals("https://w3id.org/vip/rs/Ministry", found[0]["office"])
        assertEquals("http://poderopedia.com/vocab/advisor", found[0]["role"])
    }

    @Test
    fun `schoolmates in power matches two alumni of one school tied to one party, once, and not the school itself`() {
        val found = matches("schoolmates-in-power", """
            ex:A a foaf:Person . ex:B a foaf:Person . ex:C a foaf:Person .
            ex:School a :AcademicOrganization . ex:Party a :PoliticalOrganization .
            ex:A_School a :AlumniEducationalConnection ; :hasStudent ex:A ; :hasAcademicOrganization ex:School .
            ex:B_School a :AlumniEducationalConnection ; :hasStudent ex:B ; :hasAcademicOrganization ex:School .
            ex:C_School a :AlumniEducationalConnection ; :hasStudent ex:C ; :hasAcademicOrganization ex:School .
            ex:A_Party a :PoliticalConnection ; :personParticipant ex:A ; :politicalParticipant ex:Party .
            ex:B_Party a :WorkConnection ; bio:participant ex:B ; :hasEconomicOrganizationParticipant ex:Party .
        """)
        assertEquals(1, found.size, found.toString())
        assertEquals("https://w3id.org/vip/rs/A", found[0]["personA"])
        assertEquals("https://w3id.org/vip/rs/B", found[0]["personB"])
        assertEquals("https://w3id.org/vip/rs/Party", found[0]["organization"])
    }

    @Test
    fun `a rule file carries its name and its leading comment as the explanation`() {
        val rule = rules.getValue("nepotism")
        assertEquals("nepotism", rule.name)
        assertEquals("Nepotism / Nepotizam", rule.explanation.lineSequence().first())
        assertEquals(3, rules.size)
    }
}
