package se.joynes.terminalhub.data.git

import com.trilead.ssh2.ChannelCondition
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.SharedSshTransportPool

data class GitCommandResult(val output: String, val exitCode: Int, val truncated: Boolean = false, val error: String = "")
private const val MAX_OUTPUT = 1024 * 1024

/** One bounded exec channel at a time; never sends Git commands to the interactive PTY. */
class RemoteGitExecutor @Inject constructor(
    private val pool: SharedSshTransportPool,
    private val credentials: SecurePrefsManager
) {
    suspend fun run(server: Server, command: String): GitCommandResult = withContext(Dispatchers.IO) {
        val lease = pool.acquireTransfer(server, credentials.getPassword(server.id), credentials.getPrivateKey(server.id))
        try {
            lease.openAuxiliarySession().use { channel ->
                coroutineScope {
                    val pending = async(Dispatchers.IO) {
                        val session = channel.session
                        val output = ByteArrayOutputStream()
                        val errors = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        session.execCommand(command)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            for ((input, destination) in listOf(session.stdout to output, session.stderr to errors)) {
                                if (input.available() > 0) {
                                    val count = input.read(buffer, 0, minOf(buffer.size, input.available()))
                                    if (count > 0) {
                                        val remaining = MAX_OUTPUT - output.size() - errors.size()
                                        destination.write(buffer, 0, minOf(count, remaining))
                                        if (count >= remaining) return@async GitCommandResult(output.toString("UTF-8"), -1, true)
                                    }
                                }
                            }
                            val conditions = session.waitForCondition(
                                ChannelCondition.STDOUT_DATA or ChannelCondition.STDERR_DATA or ChannelCondition.EOF or ChannelCondition.CLOSED, 100)
                            if (conditions and (ChannelCondition.EOF or ChannelCondition.CLOSED) != 0 &&
                                session.stdout.available() == 0 && session.stderr.available() == 0) {
                                session.waitForCondition(ChannelCondition.EXIT_STATUS, 300)
                                val code = session.exitStatus ?: throw IOException("Git connection closed before completion. Reconnect and retry.")
                                return@async GitCommandResult(output.toString("UTF-8"), code, error = errors.toString("UTF-8").trim().take(600))
                            }
                        }
                        @Suppress("UNREACHABLE_CODE") error("Unreachable")
                    }
                    // Closing only this channel also unblocks exec/read on cancellation or timeout.
                    try {
                        withTimeoutOrNull(20_000) { pending.await() }
                            ?: throw IOException("Git took too long. Try again or choose a smaller repository.")
                    } finally {
                        channel.close()
                        pending.cancel()
                    }
                }
            }
        } finally { lease.release() }
    }
}

/** Pure orchestration also exercised against real Git repositories in integration tests. */
class GitReviewReader(private val execute: suspend (String) -> GitCommandResult) {
    private suspend fun checked(command: String): String {
        val result = execute(command)
        check(!result.truncated) { "Too many files to list. Open a smaller project folder." }
        check(result.exitCode == 0) { "Could not read this Git repository. Check that Git is installed and the folder is accessible." }
        return result.output
    }

    suspend fun repositories(folder: String): List<String> {
        val containing = execute(GitReviewCommands.containingRepository(folder))
        check(containing.exitCode != 127) { "Git is not installed on this server. Install Git to review changes." }
        val roots = mutableListOf<String>()
        if (containing.exitCode == 0 && !containing.truncated) roots += containing.output.removeSuffix("\n")
        val candidates = checked(GitReviewCommands.nestedRepositories(folder)).split('\u0000').filter(String::isNotEmpty)
        require(candidates.size <= 30) { "More than 30 repositories found. Open a more specific project folder." }
        roots += candidates.mapNotNull { candidate ->
            val path = folder.trimEnd('/') + "/" + candidate.removePrefix("./").removeSuffix("/.git")
            val root = execute(GitReviewCommands.containingRepository(path))
            root.output.removeSuffix("\n").takeIf { root.exitCode == 0 && !root.truncated }
        }
        return roots.distinct()
    }

    suspend fun snapshot(repo: String, mode: GitReviewMode): GitReviewSnapshot {
        val hasCommit = execute(GitReviewCommands.hasCommit(repo)).exitCode == 0
        val branch = checked(GitReviewCommands.branch(repo)).trimEnd('\n')
        val commit = if (hasCommit) checked(GitReviewCommands.latestCommit(repo)).trimEnd('\n') else "No commits yet"
        val tracked = if (mode == GitReviewMode.COMMIT && !hasCommit) emptyList() else
            GitReviewCommands.parseFiles(checked(GitReviewCommands.files(repo, mode, hasCommit)))
        val untracked = if (mode in listOf(GitReviewMode.ALL, GitReviewMode.UNSTAGED))
            checked(GitReviewCommands.untracked(repo)).split('\u0000').filter(String::isNotEmpty).map { GitChangedFile(it, "?") }
        else emptyList()
        return GitReviewSnapshot(repo, branch, commit, (tracked + untracked).distinctBy { it.path })
    }

    suspend fun diff(repo: String, mode: GitReviewMode, file: GitChangedFile): GitCommandResult {
        val hasCommit = execute(GitReviewCommands.hasCommit(repo)).exitCode == 0
        val result = execute(GitReviewCommands.diff(repo, mode, hasCommit, file))
        check(result.exitCode in 0..1 || result.truncated) { "Could not read the diff. Refresh and try again." }
        return result
    }
}
