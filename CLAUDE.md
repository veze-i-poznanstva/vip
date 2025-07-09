# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

VIP (RDF/OWL Validation and Analysis Tools) is a Kotlin-based validation toolkit for RDF/OWL data. It provides comprehensive validation including syntax checking, OWL consistency, SHACL validation, best practices checking, and veracity validation.

## Common Commands

### Build and Test
```bash
./gradlew build                    # Build the project
./gradlew test                     # Run all tests
./gradlew :app:test                # Run tests for app module only
```

### Validation Commands
```bash
# RDF Syntax Validation
./gradlew :app:validate -Pfile=schema.ttl

# SHACL Validation (requires both shapes and data files)
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=test-data.ttl

# Best Practices Check
./gradlew :app:checkBestPractices -Pfile=schema.ttl

# OWL Consistency Check
./gradlew :app:checkConsistency -Pfile=schema.ttl

# Schema Metrics Analysis
./gradlew :app:analyzeSchema -Pfile=schema.ttl

# Veracity Validation (checks URL diversity and accessibility)
./gradlew :app:validateVeracity -Pfile=test-data.ttl

# Run All Validations
./gradlew :app:validateAll -Pfile=schema.ttl
```

## Architecture

### Project Structure
- Multi-module Gradle project with main code in `app/` subproject
- All validators located in `/app/src/main/kotlin/vip/`
- Tests in `/app/src/test/kotlin/vip/`
- Kotlin 1.9.21 with Java 17+ required

### Key Components
1. **RDFValidator** - Basic RDF syntax validation using Apache Jena
2. **OWLConsistencyChecker** - OWL ontology consistency using HermiT/Openllet reasoners
3. **SchemaMetrics** - Analyzes ontology complexity and structure
4. **BestPracticesChecker** - Validates against RDF/OWL best practices
5. **SHACLValidator** - SHACL shape constraint validation
6. **VeracityValidator/VeracityValidation** - Validates URL diversity (≥2 domains) and accessibility

### Validator Pattern
Each validator follows this pattern:
1. Main function accepts file path as command-line argument
2. Loads RDF data using Apache Jena's ModelFactory
3. Performs specific validation logic
4. Outputs detailed report to console
5. Exits with status 0 (success) or 1 (failure)

### Key Libraries
- **Apache Jena 4.10.0** - RDF processing and parsing
- **OWL API 5.5.0** - OWL ontology handling
- **TopBraid SHACL 1.4.2** - SHACL validation engine
- **HermiT & Openllet** - OWL reasoning engines
- **OkHttp3** - HTTP client for URL accessibility checks

### Important Notes
- The schema (schema.ttl) defines PoderVocab vocabulary with 65 classes, 84 properties, and 18 SHACL shapes
- Multilingual support with English and Spanish labels/comments
- Veracity validation requires entities to have URLs from at least 2 different domains
- All validators are standalone command-line tools that can be run independently