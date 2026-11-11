plugins {
    id("com.github.node-gradle.node") version "7.0.1"
}

node {
    // Node 22 LTS: Comunica's HTTP dependency needs it, and Node 20 is end of life.
    version = "22.22.1"
    download = true
    // Reproducible installs from package-lock.json; `npm install` would be free to drift.
    npmInstallCommand = "ci"
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmTest") {
    // Vitest over the plain TypeScript modules (translations, graph logic, the query engine); no browser
    // needed. The equivalence test reads the files exportQueryData publishes, so they are made fresh first.
    dependsOn("npmInstall", ":app:exportQueryData")
    args = listOf("test")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    // The demo always renders the data that the converter just produced, and only after its tests pass.
    dependsOn("npmInstall", "npmTest", ":app:ttlToCytoscape", ":app:exportQueryData")
    args = listOf("run", "build")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmDev") {
    dependsOn("npmInstall")
    args = listOf("run", "dev")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmPreview") {
    dependsOn("npmBuild")
    args = listOf("run", "preview")
}

tasks.register("build") {
    dependsOn("npmBuild")
}

tasks.register<Delete>("clean") {
    delete("dist", "node_modules")
}