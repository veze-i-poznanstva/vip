// Root project. Validation tools live in :app, the demo viewer in :web.
//
// The single entry point for automation is `./gradlew verify`. CI calls only that task,
// so every check that gates a release is defined here, not in the CI configuration.

tasks.register("verify") {
    group = "verification"
    description = "Runs the complete pipeline: unit tests, schema validation, data validation, data export and demo build"
    
    dependsOn(
        ":app:test",
        ":app:validateSchema",
        ":app:validateData",
        ":app:checkRedFlags",
        ":app:ttlToCytoscape",
        ":web:build"
    )
}
