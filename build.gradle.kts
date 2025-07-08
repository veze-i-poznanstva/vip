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
}

application {
    mainClass.set("validators.MainKt")
}

// Simple validation task
tasks.register<JavaExec>("validate") {
    group = "validation"
    description = "Validates RDF/TTL files"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validators.RDFValidatorKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
}

// OWL consistency check
tasks.register<JavaExec>("checkConsistency") {
    group = "validation"
    description = "Checks OWL consistency"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validators.OWLConsistencyCheckerKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
}

// Schema metrics
tasks.register<JavaExec>("analyzeSchema") {
    group = "validation"
    description = "Analyzes schema metrics"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validators.SchemaMetricsKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
}

// Best practices check
tasks.register<JavaExec>("checkBestPractices") {
    group = "validation"
    description = "Checks RDF/OWL best practices"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validators.BestPracticesCheckerKt")
    
    val file = project.findProperty("file") as String? ?: "schema.ttl"
    args = listOf(file)
}

// SHACL validation
tasks.register<JavaExec>("validateShacl") {
    group = "validation"
    description = "Validates data against SHACL shapes"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("validators.SHACLValidatorKt")
    
    val shapes = project.findProperty("shapes") as String? ?: "schema.ttl"
    val data = project.findProperty("data") as String? ?: shapes
    args = listOf(shapes, data)
}

// Run all validations
tasks.register("validateAll") {
    group = "validation"
    description = "Runs all validation checks"
    
    dependsOn("validate", "checkConsistency", "analyzeSchema", "checkBestPractices")
}