package se.joynes.terminalhub.data.git

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Exercises the exact remote shell commands against Git, not a mocked Git output format. */
class GitReviewIntegrationTest {
    private val folder = Files.createTempDirectory("terminalhub-git-'space-").toFile()
    private val reader = GitReviewReader { command ->
        val process = ProcessBuilder("/bin/sh", "-c", command).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val error = process.errorStream.readBytes().toString(Charsets.UTF_8)
        GitCommandResult(output, process.waitFor(), error = error)
    }

    @After fun cleanup() { folder.deleteRecursively() }

    private fun git(vararg args: String, at: File = folder): String {
        val process = ProcessBuilder(listOf("git", "-C", at.path) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output
    }

    private fun init(at: File = folder) {
        at.mkdirs()
        git("init", "-b", "main", at = at)
        git("config", "user.name", "Fixture", at = at)
        git("config", "user.email", "fixture@example.invalid", at = at)
    }

    private fun commit(message: String = "First", at: File = folder) {
        git("add", ".", at = at); git("commit", "-m", message, at = at)
    }

    @Test fun `all unstaged staged and last commit describe different real changes`() = runBlocking {
        init()
        val file = File(folder, "sample.kt")
        file.writeText("val value = 1\n"); commit()
        file.writeText("val value = 2\n"); git("add", "sample.kt")
        file.writeText("val value = 3\n")
        File(folder, "new.txt").writeText("untracked\n")
        val all = reader.snapshot(folder.path, GitReviewMode.ALL)
        assertEquals(setOf("sample.kt", "new.txt"), all.files.map { it.path }.toSet())
        val staged = reader.snapshot(folder.path, GitReviewMode.STAGED)
        assertEquals(listOf("sample.kt"), staged.files.map { it.path })
        assertTrue(reader.diff(folder.path, GitReviewMode.STAGED, staged.files.single()).output.contains("+val value = 2"))
        val unstaged = reader.snapshot(folder.path, GitReviewMode.UNSTAGED)
        val unstagedPatch = reader.diff(folder.path, GitReviewMode.UNSTAGED, unstaged.files.first { !it.isUntracked }).output
        assertTrue(unstagedPatch.contains("-val value = 2")); assertTrue(unstagedPatch.contains("+val value = 3"))
        val allPatch = reader.diff(folder.path, GitReviewMode.ALL, all.files.first { !it.isUntracked }).output
        assertTrue(allPatch.contains("-val value = 1")); assertTrue(allPatch.contains("+val value = 3"))
        val latest = reader.snapshot(folder.path, GitReviewMode.COMMIT)
        assertTrue(latest.commit.contains("First"))
        assertTrue(reader.diff(folder.path, GitReviewMode.COMMIT, latest.files.single()).output.contains("+val value = 1"))
    }

    @Test fun `new repo without commits displays staged and working content`() = runBlocking {
        init()
        val file = File(folder, "new.py")
        file.writeText("print('staged')\n"); git("add", ".")
        file.writeText("print('working')\n")
        val all = reader.snapshot(folder.path, GitReviewMode.ALL)
        assertEquals("No commits yet", all.commit)
        assertTrue(reader.diff(folder.path, GitReviewMode.ALL, all.files.single()).output.contains("+print('working')"))
        val staged = reader.snapshot(folder.path, GitReviewMode.STAGED)
        assertTrue(reader.diff(folder.path, GitReviewMode.STAGED, staged.files.single()).output.contains("+print('staged')"))
        assertTrue(reader.snapshot(folder.path, GitReviewMode.COMMIT).files.isEmpty())
        file.delete()
        assertTrue(reader.snapshot(folder.path, GitReviewMode.ALL).files.isEmpty())
    }

    @Test fun `untracked binary and unusual filenames are safe and not pathspecs`() = runBlocking {
        init()
        File(folder, "baseline.txt").writeText("base\n"); commit()
        val names = listOf("åäö space.txt", "quote'file.txt", "line\nbreak.txt", ":(glob)*.txt", "\$(touch HACKED).txt", "--flag.txt")
        names.forEach { File(folder, it).writeText("hello $it\n") }
        File(folder, "binary.bin").writeBytes(byteArrayOf(0, 1, 2, 3))
        val snapshot = reader.snapshot(folder.path, GitReviewMode.ALL)
        assertEquals(names.toSet() + "binary.bin", snapshot.files.map { it.path }.toSet())
        snapshot.files.forEach {
            val diff = reader.diff(folder.path, GitReviewMode.ALL, it)
            assertTrue(diff.output.isNotEmpty())
            assertFalse(diff.truncated)
        }
        assertFalse(File(folder, "HACKED").exists())
    }

    @Test fun `rename deletion detached head and configured external diff`() = runBlocking {
        init()
        File(folder, "old.kt").writeText("fun sample() = 1\n"); commit()
        git("mv", "old.kt", "new.kt")
        git("config", "diff.external", "touch HACKED")
        val renamed = reader.snapshot(folder.path, GitReviewMode.STAGED).files.single()
        assertEquals("old.kt", renamed.previousPath)
        assertEquals("new.kt", renamed.path)
        assertTrue(reader.diff(folder.path, GitReviewMode.STAGED, renamed).output.contains("rename to new.kt"))
        commit("Rename")
        git("checkout", "--detach")
        File(folder, "new.kt").delete()
        val deleted = reader.snapshot(folder.path, GitReviewMode.ALL)
        assertTrue(deleted.branch.isNotBlank())
        assertEquals("D", deleted.files.single().status)
        assertTrue(reader.diff(folder.path, GitReviewMode.ALL, deleted.files.single()).output.contains("-fun sample()"))
        assertFalse(File(folder, "HACKED").exists())
    }

    @Test fun `discovers parent repo and multiple nested repos including worktree git files`() = runBlocking {
        val first = File(folder, "one")
        init(first); File(first, "a.txt").writeText("a"); commit(at = first)
        val child = File(first, "src/deep").apply { mkdirs() }
        assertEquals(listOf(first.canonicalPath), reader.repositories(child.path))
        val second = File(folder, "two")
        git("worktree", "add", "-b", "other", second.path, at = first)
        assertEquals(setOf(first.canonicalPath, second.canonicalPath), reader.repositories(folder.path).toSet())
    }

    @Test fun `empty repository folder reports no repositories`() = runBlocking {
        assertTrue(reader.repositories(folder.path).isEmpty())
    }
}
