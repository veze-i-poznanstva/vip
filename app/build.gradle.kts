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
    implementation("net.sourceforge.owlapi:owlapi-distribution:5.5.0")
    
    // Reasoners
    implementation("net.sourceforge.owlapi:org.semanticweb.hermit:1.4.5.519")
    implementation("com.github.galigator.openllet:openllet-jena:2.6.5")
    
    // SHACL
    implementation("org.topbraid:shacl:1.4.2")
    
    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("vip.RDFValidatorKt")
}

tasks.test {
    useJUnitPlatform()
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
    val data = project.findProperty("data") as String? ?: shapes
    args = listOf(shapes, data)
    workingDir = projectDir.parentFile
}

tasks.register<JavaExec>("validateVeracity") {
    group = "rdf"
    description = "Validates veracity - checks if items have URLs from multiple domains"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("vip.VeracityValidatorKt")
    
    val file = project.findProperty("file") as String? ?: "test-data.ttl"
    args = listOf(file)
    workingDir = projectDir.parentFile
}

tasks.register("validateAll") {
    group = "rdf"
    description = "Runs all validation checks"
    
    dependsOn("validate", "checkConsistency", "analyzeSchema", "checkBestPractices", "validateVeracity")
}