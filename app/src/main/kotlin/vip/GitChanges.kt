package vip

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Files that differ from where the current work split from [ref]: committed, modified or untracked.
 * Null whenever that cannot be determined (unknown reference, no repository, no git), so the caller
 * falls back to checking everything rather than quietly checking nothing.
 */
fun gitChangedSince(ref: String, directory: File = File(".")): Set<File>? {
    fun git(vararg args: String): List<String>? = try {
        val process = ProcessBuilder("git", *args).directory(directory).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.bufferedReader().readLines()
        if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) output else null
    } catch (e: Exception) {
        null
    }
    val root = git("rev-parse", "--show-toplevel")?.singleOrNull() ?: return null
    val base = git("merge-base", "HEAD", ref)?.singleOrNull() ?: return null
    val tracked = git("-c", "core.quotePath=false", "diff", "--name-only", base, "--") ?: return null
    val untracked = git("-c", "core.quotePath=false", "ls-files", "--others", "--exclude-standard") ?: return null
    return (tracked.map { File(root, it) } + untracked.map { File(directory, it) }).map { it.canonicalFile }.toSet()
}
