package vip

import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.riot.RDFDataMgr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards against the tools and schema.ttl drifting apart: a renamed term must fail here. */
class VocabTest {

    private val namespaces = setOf("PODER", "VIP", "RS", "FOAF", "BIO")

    @Test
    fun `every vocabulary term the tools depend on is defined in schema ttl`() {
        val schema = RDFDataMgr.loadModel("schema.ttl")
        val terms = Vocab::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name !in namespaces }
            .map { it.name to (it.apply { isAccessible = true }.get(null) as String) }
        assertTrue(terms.size >= 15, "reflection found ${terms.size} terms")
        val missing = terms.filterNot { (_, uri) -> schema.containsResource(ResourceFactory.createResource(uri)) }
        assertEquals(emptyList(), missing)
    }

    @Test
    fun `namespace prefixes end in a separator so term concatenation is valid`() {
        for (ns in listOf(Vocab.PODER, Vocab.VIP, Vocab.RS, Vocab.FOAF, Vocab.BIO)) assertTrue(ns.endsWith("/") || ns.endsWith("#"), ns)
    }
}
