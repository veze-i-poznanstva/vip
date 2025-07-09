plugins {
    id("com.github.node-gradle.node") version "7.0.1"
}

node {
    version = "20.11.0"
    download = true
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmInstall") {
    args = listOf("install")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
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

tasks.named("build") {
    dependsOn("npmBuild")
}

tasks.register<Delete>("clean") {
    delete("dist", "node_modules")
}