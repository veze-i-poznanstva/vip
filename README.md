# VIP: RDF/OWL Validation and Analysis Tools

A comprehensive toolkit for validating, analyzing, and checking RDF/OWL ontologies and data against best practices, consistency rules, and SHACL constraints.

## Features

- 🔍 **RDF Validation** - Validate RDF/Turtle syntax and structure
- 🧠 **OWL Consistency Checking** - Check logical consistency of OWL ontologies  
- 📊 **Schema Metrics Analysis** - Analyze ontology complexity and statistics
- ✅ **Best Practices Checker** - Validate against RDF/OWL best practices
- 🛡️ **SHACL Validation** - Validate data against SHACL shape constraints
- 🎯 **Comprehensive Validation** - Run all validation checks together

## Quick Start

### Prerequisites

- Java 17 or higher
- Gradle (included via wrapper)

### Running Validations

All validation tools are located in the `app` subproject. Use the following commands:

```bash
# Basic RDF syntax validation
./gradlew :app:validate -Pfile=schema.ttl

# SHACL validation against shapes
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=test-data.ttl

# Best practices check
./gradlew :app:checkBestPractices -Pfile=schema.ttl

# OWL consistency check
./gradlew :app:checkConsistency -Pfile=schema.ttl

# Schema metrics analysis
./gradlew :app:analyzeSchema -Pfile=schema.ttl

# Run all validations
./gradlew :app:validateAll -Pfile=schema.ttl
```

## Project Structure

```
vip/
├── README.md
├── build.gradle.kts              # Root project (simplified)
├── settings.gradle.kts           # Multi-project settings
├── schema.ttl                    # Main ontology schema
├── test-data.ttl                 # Test data for validation
└── app/                          # Main application subproject
    ├── build.gradle.kts          # App dependencies and tasks
    └── src/main/kotlin/vip/      # Validation tools source code
        ├── RDFValidator.kt       # RDF syntax validation
        ├── OWLConsistencyChecker.kt  # OWL consistency checking
        ├── SchemaMetrics.kt      # Ontology metrics analysis
        ├── BestPracticesChecker.kt   # Best practices validation
        └── SHACLValidator.kt     # SHACL constraint validation
```

## Validation Tools

### 1. RDF Validator (`RDFValidator.kt`)

Validates RDF/Turtle syntax and provides basic statistics.

**Usage:**
```bash
./gradlew :app:validate -Pfile=schema.ttl
```

**Output:**
- ✅ Syntax validation status
- 📈 Triple count, subjects, predicates, objects
- 🏷️ Namespace information

### 2. SHACL Validator (`SHACLValidator.kt`)

Validates data against SHACL shape constraints defined in the schema.

**Usage:**
```bash
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=test-data.ttl
```

**Features:**
- Validates against all SHACL shapes in the schema
- Detailed violation reports with focus nodes and messages
- Supports both self-validation and separate data validation

### 3. Best Practices Checker (`BestPracticesChecker.kt`)

Checks adherence to RDF/OWL best practices and conventions.

**Usage:**
```bash
./gradlew :app:checkBestPractices -Pfile=schema.ttl
```

**Checks:**
- Missing annotations (labels, comments)
- Naming conventions (CamelCase for classes, camelCase for properties)
- Orphan classes detection
- Property usage patterns
- Multiple inheritance analysis
- URI consistency patterns
- Language tag consistency
- Deprecated element detection

### 4. OWL Consistency Checker (`OWLConsistencyChecker.kt`)

Validates logical consistency of OWL ontologies using reasoning.

**Usage:**
```bash
./gradlew :app:checkConsistency -Pfile=schema.ttl
```

**Features:**
- Logical consistency validation
- Unsatisfiable class detection
- Circular dependency analysis
- Uses built-in Jena reasoners

### 5. Schema Metrics (`SchemaMetrics.kt`)

Provides detailed analysis and metrics about the ontology structure.

**Usage:**
```bash
./gradlew :app:analyzeSchema -Pfile=schema.ttl
```

**Metrics:**
- Basic statistics (classes, properties, individuals)
- Namespace analysis
- Class hierarchy depth and structure
- Property characteristics analysis
- Complexity metrics (restrictions, disjoint classes, etc.)
- Schema density calculations

## Schema Overview

The project includes a complete RDF/OWL vocabulary for representing people, organizations, and their relationships:

### Core Classes
- **foaf:Person** - People with biographical information
- **foaf:Organization** - Organizations with various subtypes
- **Connection** - Relationships between entities
- **SourcedEntity** - Entities with provenance information

### Key Features
- **65 classes** with proper OWL declarations
- **84 properties** (64 object properties, 19 datatype properties)
- **18 SHACL shapes** for data validation
- **Multilingual support** (English/Spanish labels and comments)
- **Comprehensive relationship modeling**

### SHACL Validation

The schema includes built-in SHACL shapes that validate:
- **PersonShape** - Ensures persons have required firstName and firstLastName
- **OrganizationShape** - Validates organization structure
- **ConnectionShape** - Validates relationship constraints
- **DocumentationShape** - Ensures proper documentation
- **FinancialInformationShape** - Validates financial data

## Example Data

The `test-data.ttl` file contains example data demonstrating:
- Person entities with biographical information
- Organization relationships
- Business and political connections
- Leadership roles and ownership
- Academic affiliations

## Dependencies

- **Apache Jena 4.10.0** - RDF processing and reasoning
- **OWL API 5.5.0** - OWL ontology handling
- **TopBraid SHACL 1.4.2** - SHACL validation
- **Kotlin 1.9.21** - Implementation language

## Development

### Building
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Code Structure

All validation tools follow a consistent pattern:
1. Load RDF/OWL data using Apache Jena
2. Perform specific validation or analysis
3. Generate detailed reports with multilingual messages
4. Exit with appropriate status codes

## Validation Results Summary

### Current Schema Status
- ✅ **0 errors**
- ✅ **0 warnings**
- ℹ️ **33 informational suggestions** (mainly missing property comments)
- ✅ **All SHACL shapes validate successfully**
- ✅ **Ontology is logically consistent**
- ✅ **1,186 triples** with proper structure

### Test Data Status
- ✅ **Validates against all SHACL constraints**
- ✅ **No syntax errors**
- ✅ **Proper use of vocabulary terms**

## Contributing

When adding new validation rules or modifying the schema:

1. Ensure all validation tools pass
2. Update SHACL shapes as needed
3. Add appropriate test data
4. Update documentation

## License

This project is provided as-is for validation and analysis of RDF/OWL data.