plugins {
    kotlin("jvm") version "1.9.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Apache Jena
    implementation("org.apache.jena:apache-jena-libs:4.10.0")
    
    // OWL API
    
    // Reasoners
    
    // SHACL
    
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("vip.RDFValidatorKt")
}

tasks.test {
    useJUnitPlatform()
    // Tests load the real vocabulary from the repository root.
    workingDir = projectDir.parentFile
}

// RDF Validation tasks
tasks.register<JavaExec>("validate") {
    group = "rdf"
    description = "Validates RDF/TTL files"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.RDFValidatorKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
    
    // Look for files in the project root (one level up from app/)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("checkConsistency") {
    group = "rdf"
    description = "Checks OWL consistency"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.OWLConsistencyCheckerKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("analyzeSchema") {
    group = "rdf"
    description = "Analyzes schema metrics"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.SchemaMetricsKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("checkBestPractices") {
    group = "rdf"
    description = "Checks RDF/OWL best practices"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.BestPracticesCheckerKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("validateShacl") {
    group = "rdf"
    description = "Validates data against SHACL shapes"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.SHACLValidatorKt")
    
    val shapes = project.findProperty("shapes") as String? ?: "schema.ttl"
    val data = project.findProperty("data") as String? ?: "data"
    args = listOf(shapes, data)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("validateVeracity") {
    group = "rdf"
    description = "Checks the sourcing rule: two documents from two domains per entity and connection, each reachable live or through an archived copy"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.VeracityValidatorKt")
    
    val file = project.findProperty("data") as String? ?: "data"
    val schema = project.findProperty("schema") as String? ?: "schema.ttl"
    val archives = project.findProperty("archives") as String? ?: "archives.ttl"
    // Live checks cover files changed since the base; -PverifyFull, or VERIFY_FULL on the weekly CI run, checks every URL.
    val full = project.hasProperty("verifyFull") || System.getenv("VERIFY_FULL") == "true"
    val base = project.findProperty("verifyBase") as String? ?: System.getenv("VERIFY_BASE")?.takeIf { it.isNotBlank() } ?: "origin/main"
    args = listOf(file, schema, archives) + if (full) emptyList() else listOf("--since", base)
    workingDir = projectDir.parentFile
    mustRunAfter("archiveSources", "checkArchives", "linkWikidata", "checkWikidata")
}

tasks.register<JavaExec>("archiveSources") {
    group = "rdf"
    description = "Records an archived copy (Wayback Machine) for every cited document that has none; run locally and commit archives.ttl"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.SourceArchiverKt")
    
    val file = project.findProperty("data") as String? ?: "data"
    val archives = project.findProperty("archives") as String? ?: "archives.ttl"
    args = listOf(file, archives)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("checkArchives") {
    group = "rdf"
    description = "Fails if any cited document has no archive record (what CI runs; never contacts the archive)"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.SourceArchiverKt")
    
    val file = project.findProperty("data") as String? ?: "data"
    val archives = project.findProperty("archives") as String? ?: "archives.ttl"
    args = listOf(file, archives, "--check")
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("linkWikidata") {
    group = "rdf"
    description = "Locally: record each entity's Wikidata item from its Wikipedia article into the entity file; commit the files"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.WikidataLinkerKt")
    
    val file = project.findProperty("data") as String? ?: "data"
    args = listOf(file)
    workingDir = projectDir.parentFile
    mustRunAfter("checkDataLayout", "validateShacl")
}

tasks.register<JavaExec>("checkWikidata") {
    group = "rdf"
    description = "Fails if a recorded Wikidata item does not match the entity's Wikipedia article, or an article's item is not recorded (what CI runs)"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.WikidataLinkerKt")
    
    val file = project.findProperty("data") as String? ?: "data"
    args = listOf(file, "--check")
    workingDir = projectDir.parentFile
    mustRunAfter("checkDataLayout", "validateShacl")
}

tasks.register<JavaExec>("checkDataLayout") {
    group = "rdf"
    description = "Checks the data layout: entities under entities/, connections under cases/, one home file per entity, nothing referenced that no file or the vocabulary declares"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.DataLayoutCheckKt")
    
    val data = project.findProperty("data") as String? ?: "data"
    val schema = project.findProperty("schema") as String? ?: "schema.ttl"
    args = listOf(data, schema)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("checkRedFlags") {
    group = "rdf"
    description = "Runs every red-flag rule in rules/ over the data and prints the leads (never fails)"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.RedFlagsKt")
    
    val data = project.findProperty("data") as String? ?: "data"
    val schema = project.findProperty("schema") as String? ?: "schema.ttl"
    val rules = project.findProperty("rules") as String? ?: "rules"
    args = listOf(data, schema, rules)
    workingDir = projectDir.parentFile
}

tasks.register("validateSchema") {
    group = "rdf"
    description = "Runs every check that concerns the ontology itself (syntax, consistency, best practices, metrics)"
    
    dependsOn("validate", "checkConsistency", "checkBestPractices", "analyzeSchema")
}

tasks.register("validateData") {
    group = "rdf"
    description = "Runs every check that concerns instance data (data layout, SHACL shapes, archive records, veracity of sources)"
    
    // Locally, missing archive records are created; on CI they fail the build so they get committed.
    val onCi = System.getenv("CI") == "true"
    dependsOn("checkDataLayout", "validateShacl", if (onCi) "checkArchives" else "archiveSources",
        if (onCi) "checkWikidata" else "linkWikidata", "validateVeracity")
}

tasks.register("validateAll") {
    group = "rdf"
    description = "Runs all validation checks on schema and data"
    
    dependsOn("validateSchema", "validateData")
}

tasks.register<JavaExec>("ttlToCytoscape") {
    group = "rdf"
    description = "Converts the data directory (or one TTL file) to Cytoscape JSON for the web viewer"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.TtlToCytoscapeKt")
    
    val inputFile = project.findProperty("input") as String? ?: "data"
    val outputFile = project.findProperty("output") as String? ?: "web/public/test-data-cytoscape.json"
    val schema = project.findProperty("schema") as String? ?: "schema.ttl"
    val archives = project.findProperty("archives") as String? ?: "archives.ttl"
    args = listOf(inputFile, outputFile, schema, archives)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("exportQueryData") {
    group = "rdf"
    description = "Publishes data.ttl (records plus entailments), schema.ttl and rules.json for the viewer's query engine"

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.QueryExportKt")

    val data = project.findProperty("data") as String? ?: "data"
    val schema = project.findProperty("schema") as String? ?: "schema.ttl"
    val rules = project.findProperty("rules") as String? ?: "rules"
    val output = project.findProperty("output") as String? ?: "web/public"
    args = listOf(data, schema, rules, output)
    workingDir = projectDir.parentFile
}