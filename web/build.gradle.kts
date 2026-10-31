plugins {
    id("com.github.node-gradle.node") version "7.0.1"
}

node {
    version = "20.11.0"
    download = true
    // Reproducible installs from package-lock.json; `npm install` would be free to drift.
    npmInstallCommand = "ci"
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmTest") {
    // Vitest over the plain TypeScript modules (translations, later graph logic); no browser needed.
    dependsOn("npmInstall")
    args = listOf("test")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    // The demo always renders the data that the converter just produced, and only after its tests pass.
    dependsOn("npmInstall", "npmTest", ":app:ttlToCytoscape")
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