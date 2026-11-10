package vip

/** URIs of the vocabulary terms the tools depend on. */
object Vocab {
    const val PODER = "http://poderopedia.com/vocab/"
    /** Our own additions to PoderVocab: ownership, kumstvo, kinship, governance roles, document types and statuses, archive records. */
    const val VIP = "https://w3id.org/vip/vocab#"
    /** Instance data for Serbia. Other countries get their own segment under https://w3id.org/vip/. */
    const val RS = "https://w3id.org/vip/rs/"
    const val FOAF = "http://xmlns.com/foaf/0.1/"
    const val BIO = "http://purl.org/vocab/bio/0.1/"

    const val AGENT = FOAF + "Agent"
    const val PERSON = FOAF + "Person"
    const val ORGANIZATION = FOAF + "Organization"
    const val NAME = FOAF + "name"

    const val CONNECTION = PODER + "Connection"
    const val WORK_CONNECTION = PODER + "WorkConnection"
    const val POLITICAL_ORGANIZATION = PODER + "PoliticalOrganization"
    const val ACADEMIC_ORGANIZATION = PODER + "AcademicOrganization"
    const val DOCUMENTATION = PODER + "Documentation"
    const val HAS_DOCUMENT_URL = PODER + "hasDocumentURL"
    const val HAS_WORK_ROLE = PODER + "hasWorkRole"
    const val HAS_DOCUMENT_STATUS = PODER + "hasDocumentStatus"
    const val HAS_ARCHIVE_URL = VIP + "hasArchiveURL"
    const val ARCHIVED_ON = VIP + "archivedOn"
    const val REMOVED = VIP + "removed"
    const val OWNER = VIP + "owner"
    const val PARENT = VIP + "parent"
    const val WIKIPEDIA_ARTICLE = VIP + "wikipediaArticle"
    const val WIKIDATA_ITEM = VIP + "wikidataItem"

    const val PARTICIPANT = BIO + "participant"

    const val HAS_DOCUMENTATION = PODER + "hasDocumentation"
    const val HAS_DOCUMENT_TYPE = PODER + "hasDocumentType"
    const val ALIAS = PODER + "alias"
    const val FIRST_NAME = FOAF + "firstName"
    const val FIRST_LAST_NAME = PODER + "firstLastName"
    const val CONNECTION_START_DATE = PODER + "connectionStartDate"
    const val CONNECTION_END_DATE = PODER + "connectionEndDate"
    const val OWNERSHIP_SHARE = VIP + "ownershipShare"
    const val DCTERMS_TITLE = "http://purl.org/dc/terms/title"
}
