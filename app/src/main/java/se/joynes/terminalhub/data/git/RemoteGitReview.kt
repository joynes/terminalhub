package se.joynes.terminalhub.data.git

// Source-compatible names for the Git reader; the bounded SSH executor is shared with tmux.
typealias GitCommandResult = se.joynes.terminalhub.data.ssh.RemoteCommandResult
typealias RemoteGitExecutor = se.joynes.terminalhub.data.ssh.RemoteCommandExecutor

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
