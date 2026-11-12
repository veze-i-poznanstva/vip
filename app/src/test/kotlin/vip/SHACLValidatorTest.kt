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
            @prefix bio: <http://purl.org/vocab/bio/0.1/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            $ttl
        """.trimIndent()), null, "TTL")
    }

    private val documented = """:hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/> ; :hasDocumentType vip:newsArticle ; :hasDocumentStatus vip:published ]"""

    private val quoted = """:hasDocumentation [ a :Documentation ; :hasDocumentURL <https://a.example/> ; :hasDocumentType vip:newsArticle ; :hasDocumentStatus vip:published ; rdfs:comment "Quoted passage"@sr ]"""

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
            rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS ; $quoted .
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
    fun `a connection citation without a quoted passage violates the connection shape`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
            rs:SNS a :PoliticalOrganization ; foaf:name "SNS" ; $documented .
            rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS ; $documented .
        """))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("AnaSNS") }, report.entries.joinToString { it.focusNode().toString() })
    }

    @Test
    fun `a connection citation with a quoted passage conforms`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
            rs:SNS a :PoliticalOrganization ; foaf:name "SNS" ; $documented .
            rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS ; $quoted .
        """))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a person citation without a comment still conforms`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented ."""))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a person without a first name violates the person shape`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; :firstLastName "B" ; $documented ."""))
        assertFalse(report.conforms())
    }

    @Test
    fun `a person with a tax ID violates the person shape, because it is personal data`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; :hasTaxId "1234567890123" ; $documented ."""))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Ana") && it.resultPath().toString().contains("hasTaxId") }, report.entries.joinToString { it.message() })
    }

    @Test
    fun `an organisation may record its public tax ID`() {
        val report = validateShacl(schema, data("""rs:Firma a :Company ; foaf:name "Firma" ; :hasTaxId "109964527" ; $documented ."""))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a Wikidata item that is not a wikidata org entity address violates the agent shape`() {
        val report = validateShacl(schema, data("""rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; vip:wikidataItem <https://www.wikidata.org/wiki/Q42> ; $documented ."""))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Ana") }, report.entries.joinToString { it.focusNode().toString() })
    }

    @Test
    fun `a person and an organisation with one Wikidata entity IRI each conform`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; vip:wikidataItem <http://www.wikidata.org/entity/Q114870170> ; $documented .
            rs:Firma a :Company ; foaf:name "Firma" ; vip:wikidataItem <http://www.wikidata.org/entity/Q126264685> ; $documented .
        """))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `two Wikidata items on one organisation violate the agent shape`() {
        val report = validateShacl(schema, data("""rs:Firma a :Company ; foaf:name "Firma" ; vip:wikidataItem <http://www.wikidata.org/entity/Q1>, <http://www.wikidata.org/entity/Q2> ; $documented ."""))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Firma") }, report.entries.joinToString { it.focusNode().toString() })
    }

    private fun dated(dates: String) = validateShacl(schema, data("""
        rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
        rs:SNS a :PoliticalOrganization ; foaf:name "SNS" ; $documented .
        rs:AnaSNS a :PoliticalConnection ; :personParticipant rs:Ana ; :politicalParticipant rs:SNS ; $quoted $dates .
    """))

    private fun ownerships(records: String) = validateShacl(schema, data("""
        rs:OwnerA a :Company ; foaf:name "Owner A" ; $documented .
        rs:OwnerB a :Company ; foaf:name "Owner B" ; $documented .
        rs:OwnerC a :Company ; foaf:name "Owner C" ; $documented .
        rs:Firm a :Company ; foaf:name "Firm" ; $documented .
        $records
    """))

    @Test
    fun `ownership shares of different owners cannot total more than one hundred percent`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 90.0 ; $quoted .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 20.0 ; $quoted .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Firm") })
    }

    @Test
    fun `a connection may date itself to the year, the month or the day`() {
        val report = dated(""" ; :connectionStartDate "2017"^^xsd:gYear ; :connectionEndDate "2019-10"^^xsd:gYearMonth""")
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
        assertTrue(dated(""" ; :connectionStartDate "2019-10-02"^^xsd:date""").conforms())
    }

    @Test
    fun `a connection date that is a plain string violates the shape`() {
        assertFalse(dated(""" ; :connectionStartDate "oktobar 2019" """).conforms())
    }

    @Test
    fun `a connection that ends before it starts violates the shape, across precisions`() {
        assertFalse(dated(""" ; :connectionStartDate "2019"^^xsd:gYear ; :connectionEndDate "2017-06-01"^^xsd:date""").conforms())
        assertFalse(dated(""" ; :connectionStartDate "2017-08"^^xsd:gYearMonth ; :connectionEndDate "2017-03"^^xsd:gYearMonth""").conforms())
    }

    @Test
    fun `a connection that starts and ends within the same year conforms whatever the precision`() {
        val report = dated(""" ; :connectionStartDate "2016-04"^^xsd:gYearMonth ; :connectionEndDate "2016"^^xsd:gYear""")
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `adjacent ownership states are not summed`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted ; :connectionStartDate "2025-12-24"^^xsd:date ; :connectionEndDate "2026-02"^^xsd:gYearMonth .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 90.0 ; $quoted ; :connectionStartDate "2026-02"^^xsd:gYearMonth .
            rs:C a vip:OwnershipConnection ; vip:owner rs:OwnerC ; vip:ownedCompany rs:Firm ; vip:ownershipShare 10.0 ; $quoted ; :connectionStartDate "2026-02"^^xsd:gYearMonth .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a continuing stake does not sum mutually exclusive states of another owner`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 50.0 ; $quoted ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 75.0 ; $quoted ; :connectionStartDate "2026-03"^^xsd:gYearMonth .
            rs:C a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 25.0 ; $quoted .
            rs:D a vip:OwnershipConnection ; vip:owner rs:OwnerC ; vip:ownedCompany rs:Firm ; vip:ownershipShare 25.0 ; $quoted ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `the later state of a changed stake is still summed with a stake that continues`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 50.0 ; $quoted ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 75.0 ; $quoted ; :connectionStartDate "2026-03"^^xsd:gYearMonth .
            rs:C a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 30.0 ; $quoted .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.value().toString().endsWith("B") }, report.entries.joinToString { it.value().toString() })
    }

    @Test
    fun `the earlier state of a changed stake is still summed with what applied before the change`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 50.0 ; $quoted ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionStartDate "2026-03"^^xsd:gYearMonth .
            rs:C a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 30.0 ; $quoted .
            rs:D a vip:OwnershipConnection ; vip:owner rs:OwnerC ; vip:ownedCompany rs:Firm ; vip:ownershipShare 30.0 ; $quoted ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.none { it.value().toString().endsWith("B") }, report.entries.joinToString { it.value().toString() })
    }

    @Test
    fun `records without a start are summed with each other whatever their ends`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionEndDate "2020"^^xsd:gYear .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionEndDate "2025"^^xsd:gYear .
        """)
        assertFalse(report.conforms())
    }

    @Test
    fun `a record without a start that ended is not summed with one that starts later`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionEndDate "2020"^^xsd:gYear .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionStartDate "2021-05"^^xsd:gYearMonth .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a year-precise start is summed with a month-precise start in the same year`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionStartDate "2026"^^xsd:gYear .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionStartDate "2026-03"^^xsd:gYearMonth .
        """)
        assertFalse(report.conforms())
    }

    @Test
    fun `three states of one stake in a row are never summed with each other`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted ; :connectionStartDate "2020"^^xsd:gYear ; :connectionEndDate "2022"^^xsd:gYear .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 80.0 ; $quoted ; :connectionStartDate "2022"^^xsd:gYear ; :connectionEndDate "2024-06"^^xsd:gYearMonth .
            rs:C a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 60.0 ; $quoted ; :connectionStartDate "2024-06"^^xsd:gYearMonth .
            rs:D a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 20.0 ; $quoted ; :connectionStartDate "2022"^^xsd:gYear .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `ownership states that overlap in time are summed`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted ; :connectionStartDate "2025-12-24"^^xsd:date ; :connectionEndDate "2026-03"^^xsd:gYearMonth .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 10.0 ; $quoted ; :connectionStartDate "2026-02"^^xsd:gYearMonth .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Firm") })
    }

    @Test
    fun `a day-precise end in the same month as a month-precise start is adjacent`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted ; :connectionEndDate "2026-02-10"^^xsd:date .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted ; :connectionStartDate "2026-02"^^xsd:gYearMonth .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `an undated record is summed with every open record`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted  .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 10.0 ; $quoted ; :connectionStartDate "2026"^^xsd:gYear .
        """)
        assertFalse(report.conforms())
    }

    @Test
    fun `a record without a share does not count`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted  .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ;  $quoted  .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `shares that total exactly one hundred conform`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 50.0 ; $quoted  .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 50.0 ; $quoted  .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `shares of different companies are not summed`() {
        val report = ownerships("""
            rs:Other a :Company ; foaf:name "Other" ; $documented .
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 90.0 ; $quoted  .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Other ; vip:ownershipShare 90.0 ; $quoted  .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `the total violation names the company and the record`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 90.0 ; $quoted  .
            rs:B a vip:OwnershipConnection ; vip:owner rs:OwnerB ; vip:ownedCompany rs:Firm ; vip:ownershipShare 20.0 ; $quoted  .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.all { it.focusNode().toString().endsWith("Firm") })
        assertTrue(report.entries.any { it.value().toString().let { v -> v.endsWith("A") || v.endsWith("B") } })
    }

    @Test
    fun `an ownership share above one hundred violates`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.5 ; $quoted .
        """)
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.message().contains("share") }, report.entries.joinToString { it.message() })
    }

    @Test
    fun `an ownership share of zero violates`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 0.0 ; $quoted .
        """)
        assertFalse(report.conforms())
    }

    @Test
    fun `an integer ownership share violates`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 51 ; $quoted .
        """)
        assertFalse(report.conforms())
    }

    @Test
    fun `a share of one hundred decimal conforms`() {
        val report = ownerships("""
            rs:A a vip:OwnershipConnection ; vip:owner rs:OwnerA ; vip:ownedCompany rs:Firm ; vip:ownershipShare 100.0 ; $quoted .
        """)
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }

    @Test
    fun `a bare kinship property on a person is refused, because kinship is a connection record`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; :hasSibling rs:Bo ; $documented .
            rs:Bo a foaf:Person ; foaf:firstName "Bo" ; :firstLastName "B" ; $documented .
        """))
        assertFalse(report.conforms())
        assertTrue(report.entries.any { it.focusNode().toString().endsWith("Ana") }, report.entries.joinToString { it.focusNode().toString() })
    }

    @Test
    fun `a sibling connection and a parent-and-child connection with quoted citations conform`() {
        val report = validateShacl(schema, data("""
            rs:Ana a foaf:Person ; foaf:firstName "Ana" ; :firstLastName "B" ; $documented .
            rs:Bo a foaf:Person ; foaf:firstName "Bo" ; :firstLastName "B" ; $documented .
            rs:Cile a foaf:Person ; foaf:firstName "Cile" ; :firstLastName "B" ; $documented .
            rs:AnaBo a vip:SiblingConnection ; bio:participant rs:Ana, rs:Bo ; $quoted .
            rs:AnaCile a vip:ParentChildConnection ; vip:parent rs:Ana ; vip:child rs:Cile ; $quoted .
        """))
        assertTrue(report.conforms(), report.entries.joinToString { it.message() })
    }
}
