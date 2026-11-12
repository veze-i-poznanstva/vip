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
        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
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
            ex:AS a vip:SiblingConnection ; bio:participant ex:A, ex:S .
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

    private fun ownerInOffice(ownership: String, post: String) = matches("owner-in-office", """
        ex:A a foaf:Person . ex:Firm a :Company . ex:Ministry a :GovernmentOrganization .
        ex:A_Firm a vip:OwnershipConnection ; vip:owner ex:A ; vip:ownedCompany ex:Firm $ownership .
        ex:A_Ministry a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Ministry $post .
    """)

    @Test
    fun `owner in office drops a case where ownership ended before the office began`() {
        val found = ownerInOffice(
            """; :connectionStartDate "1995"^^xsd:gYear ; :connectionEndDate "2000"^^xsd:gYear""",
            """; :connectionStartDate "2020-03"^^xsd:gYearMonth ; :connectionEndDate "2022-01-15"^^xsd:date"""
        )
        assertEquals(emptyList(), found)
    }

    @Test
    fun `owner in office drops a case where the office ended before ownership began`() {
        assertEquals(emptyList(), ownerInOffice("""; :connectionStartDate "2020"^^xsd:gYear""", """; :connectionEndDate "2010"^^xsd:gYear"""))
    }

    @Test
    fun `owner in office says the periods overlap only when all four dates show it`() {
        val found = ownerInOffice(
            """; :connectionStartDate "2005"^^xsd:gYear ; :connectionEndDate "2021"^^xsd:gYear""",
            """; :connectionStartDate "2020"^^xsd:gYear ; :connectionEndDate "2022"^^xsd:gYear"""
        )
        assertEquals("overlapping", found.single()["overlap"])
    }

    @Test
    fun `owner in office keeps a case with missing dates and marks the overlap unknown`() {
        assertEquals("unknown", ownerInOffice("", "").single()["overlap"])
        assertEquals("unknown", ownerInOffice("""; :connectionStartDate "2005"^^xsd:gYear""", """; :connectionStartDate "2020"^^xsd:gYear""").single()["overlap"])
    }

    private fun schoolmates(attendanceA: String, attendanceB: String, tieA: String = "", tieB: String = "") = matches("schoolmates-in-power", """
        ex:A a foaf:Person . ex:B a foaf:Person . ex:School a :AcademicOrganization . ex:Party a :PoliticalOrganization .
        ex:A_School a :AlumniEducationalConnection ; :hasStudent ex:A ; :hasAcademicOrganization ex:School $attendanceA .
        ex:B_School a :AlumniEducationalConnection ; :hasStudent ex:B ; :hasAcademicOrganization ex:School $attendanceB .
        ex:A_Party a :PoliticalConnection ; :personParticipant ex:A ; :politicalParticipant ex:Party $tieA .
        ex:B_Party a :PoliticalConnection ; :personParticipant ex:B ; :politicalParticipant ex:Party $tieB .
    """)

    @Test
    fun `schoolmates in power drops people who were at the school in different years`() {
        assertEquals(emptyList(), schoolmates(
            """; :connectionStartDate "1970"^^xsd:gYear ; :connectionEndDate "1974"^^xsd:gYear""",
            """; :connectionStartDate "2010"^^xsd:gYear ; :connectionEndDate "2014"^^xsd:gYear"""
        ))
    }

    @Test
    fun `schoolmates in power drops people whose ties to the organisation never overlapped`() {
        assertEquals(emptyList(), schoolmates("", "",
            """; :connectionStartDate "1990"^^xsd:gYear ; :connectionEndDate "1995"^^xsd:gYear""",
            """; :connectionStartDate "2010"^^xsd:gYear"""
        ))
    }

    @Test
    fun `schoolmates in power reports the school and organisation overlaps separately`() {
        val found = schoolmates(
            """; :connectionStartDate "1987"^^xsd:gYear ; :connectionEndDate "1991"^^xsd:gYear""",
            """; :connectionStartDate "1988"^^xsd:gYear ; :connectionEndDate "1992"^^xsd:gYear"""
        ).single()
        assertEquals("overlapping", found["schoolOverlap"])
        assertEquals("unknown", found["organizationOverlap"])
    }

    @Test
    fun `nepotism matches a sibling of a mayor employed by the city`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:S a foaf:Person . ex:City a :GovernmentOrganization .
            ex:A_City a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:City ; :hasWorkRole vip:mayor .
            ex:S_City a :WorkConnection ; bio:participant ex:S ; :hasEconomicOrganizationParticipant ex:City ; :hasWorkRole :advisor .
            ex:AS a vip:SiblingConnection ; bio:participant ex:A, ex:S .
        """)
        assertEquals(listOf("https://w3id.org/vip/rs/S"), found.map { it["relative"] })
    }

    @Test
    fun `nepotism matches a sibling of a prime minister employed by the government`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:S a foaf:Person . ex:Gov a :GovernmentOrganization .
            ex:A_Gov a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Gov ; :hasWorkRole vip:primeMinister .
            ex:S_Gov a :WorkConnection ; bio:participant ex:S ; :hasEconomicOrganizationParticipant ex:Gov ; :hasWorkRole :advisor .
            ex:AS a vip:SiblingConnection ; bio:participant ex:A, ex:S .
        """)
        assertEquals(listOf("https://w3id.org/vip/rs/S" to "https://w3id.org/vip/vocab#primeMinister"), found.map { it["relative"] to it["leaderRole"] })
    }

    @Test
    fun `nepotism matches a kum of a minister employed by the government`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:K a foaf:Person . ex:Gov a :GovernmentOrganization .
            ex:A_Gov a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Gov ; :hasWorkRole vip:minister .
            ex:K_Gov a :WorkConnection ; bio:participant ex:K ; :hasEconomicOrganizationParticipant ex:Gov ; :hasWorkRole :advisor .
            ex:AK a vip:GodparentConnection ; vip:godparent ex:A ; vip:godchild ex:K .
        """)
        assertEquals(listOf("https://w3id.org/vip/rs/K" to "kum"), found.map { it["relative"] to it["relation"] })
    }

    @Test
    fun `nepotism matches a child of a director employed by the company`() {
        val found = matches("nepotism", """
            ex:A a foaf:Person . ex:C a foaf:Person . ex:Org a foaf:Organization .
            ex:A_Org a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :director .
            ex:C_Org a :WorkConnection ; bio:participant ex:C ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :employee .
            ex:AC a vip:ParentChildConnection ; vip:parent ex:A ; vip:child ex:C .
        """)
        assertEquals(listOf("https://w3id.org/vip/rs/C" to "family"), found.map { it["relative"] to it["relation"] })
    }

    private fun nepotism(leaderJob: String, relativeJob: String) = matches("nepotism", """
        ex:A a foaf:Person . ex:B a foaf:Person . ex:Org a foaf:Organization .
        ex:A_Org a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :ceo $leaderJob .
        ex:B_Org a :WorkConnection ; bio:participant ex:B ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole :advisor $relativeJob .
        ex:AB a :Marriage ; :hasSpouse ex:A, ex:B .
    """)

    @Test
    fun `nepotism drops jobs that never overlapped and labels the rest`() {
        assertEquals(emptyList(), nepotism("""; :connectionEndDate "2000"^^xsd:gYear""", """; :connectionStartDate "2005"^^xsd:gYear"""))
        assertEquals("unknown", nepotism("", "").single()["overlap"])
        assertEquals("overlapping", nepotism(
            """; :connectionStartDate "2000"^^xsd:gYear ; :connectionEndDate "2010"^^xsd:gYear""",
            """; :connectionStartDate "2005"^^xsd:gYear ; :connectionEndDate "2008"^^xsd:gYear"""
        ).single()["overlap"])
    }
}
