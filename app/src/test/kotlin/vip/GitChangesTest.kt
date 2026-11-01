package vip

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Against a real throwaway repository: no network, only local git. */
class GitChangesTest {

    @TempDir
    lateinit var dir: File

    private fun git(vararg args: String) {
        val process = ProcessBuilder("git", "-c", "user.name=t", "-c", "user.email=t@example.org", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false", *args)
            .directory(dir).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        check(process.waitFor() == 0) { args.joinToString(" ") }
    }

    private fun write(path: String, text: String = "x") = File(dir, path).apply { parentFile.mkdirs() }.writeText(text)
    private fun file(path: String) = File(dir, path).canonicalFile

    private fun repository() {
        git("init", "-q", "-b", "main")
        write("data/a.ttl"); write("data/b.ttl")
        git("add", "-A"); git("commit", "-q", "-m", "first")
        git("branch", "base")
    }

    @Test
    fun `committed, modified and untracked files all count as changed`() {
        repository()
        write("data/committed.ttl"); git("add", "-A"); git("commit", "-q", "-m", "second")
        write("data/a.ttl", "changed")
        write("data/untracked.ttl")
        assertEquals(setOf(file("data/committed.ttl"), file("data/a.ttl"), file("data/untracked.ttl")), gitChangedSince("base", dir))
    }

    @Test
    fun `an unchanged tree has no changed files`() {
        repository()
        assertEquals(emptySet(), gitChangedSince("base", dir))
    }

    @Test
    fun `changes are measured from where the branches split, not from a base that moved on`() {
        repository()
        git("checkout", "-q", "-b", "work")
        write("data/mine.ttl"); git("add", "-A"); git("commit", "-q", "-m", "mine")
        git("checkout", "-q", "base")
        write("data/theirs.ttl"); git("add", "-A"); git("commit", "-q", "-m", "theirs")
        git("checkout", "-q", "work")
        assertEquals(setOf(file("data/mine.ttl")), gitChangedSince("base", dir))
    }

    @Test
    fun `an unknown reference gives null, so the caller checks everything`() {
        repository()
        assertNull(gitChangedSince("no-such-ref", dir))
    }

    @Test
    fun `outside a repository gives null`() {
        assertNull(gitChangedSince("main", dir))
    }
}
