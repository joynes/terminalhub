package se.joynes.terminalhub.data.git

enum class GitReviewMode(val label: String, val explanation: String) {
    ALL("All changes", "Working files compared with the latest commit, plus new files."),
    UNSTAGED("Unstaged", "Working files compared with the staging area. New files are listed too."),
    STAGED("Staged", "Changes added with git add, ready for the next commit."),
    COMMIT("Latest commit", "Changes introduced by the latest commit, compared with its first parent.")
}

data class GitChangedFile(val path: String, val status: String, val previousPath: String? = null) {
    val isUntracked: Boolean get() = status == "?"
}

data class GitReviewSnapshot(
    val repository: String,
    val branch: String,
    val commit: String,
    val files: List<GitChangedFile>
)

/** Shell commands are shared by the SSH adapter and real-repository integration tests. */
object GitReviewCommands {
    fun quote(value: String): String {
        require('\u0000' !in value) { "Invalid path" }
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun path(value: String): String = when {
        value == "~" -> "\"\$HOME\""
        value.startsWith("~/") -> "\"\$HOME\"/" + quote(value.removePrefix("~/"))
        else -> quote(value)
    }

    // No pager, external diff/textconv programs, optional index writes or pathspec expansion.
    private const val GIT = "GIT_OPTIONAL_LOCKS=0 GIT_TERMINAL_PROMPT=0 git --no-pager --literal-pathspecs -c color.ui=false"
    private fun at(repo: String, command: String) = "cd ${path(repo)} && $command"
    private const val DIFF = "--no-ext-diff --no-textconv --no-color --find-renames"

    fun containingRepository(folder: String) = "command -v git >/dev/null 2>&1 || exit 127; " + at(folder, "$GIT rev-parse --show-toplevel")

    /** Bounded by the executor timeout/output limit. .git directories are never traversed. */
    fun nestedRepositories(folder: String) = at(folder,
        "find . \\( -name .git -print0 -prune \\) -o \\( -type d \\( -path './*/*/*/*/*' -o -name node_modules -o -name .gradle -o -name build -o -name vendor \\) -prune \\)")

    fun branch(repo: String) = at(repo,
        "$GIT symbolic-ref --short -q HEAD || $GIT rev-parse --short HEAD")
    fun latestCommit(repo: String) = at(repo,
        "$GIT log -1 --format='%h · %s%n%an · %ad' --date=short")
    fun hasCommit(repo: String) = at(repo, "$GIT rev-parse --verify HEAD")

    private fun comparison(mode: GitReviewMode, hasCommit: Boolean): String = when (mode) {
        GitReviewMode.ALL -> if (hasCommit) "diff HEAD" else "diff \"\$($GIT hash-object -t tree --stdin </dev/null)\""
        GitReviewMode.UNSTAGED -> "diff"
        GitReviewMode.STAGED -> "diff --cached"
        GitReviewMode.COMMIT -> "show --format= --first-parent HEAD"
    }

    fun files(repo: String, mode: GitReviewMode, hasCommit: Boolean) = at(repo,
        "$GIT ${comparison(mode, hasCommit)} $DIFF --name-status -z --")
    fun untracked(repo: String) = at(repo, "$GIT ls-files --others --exclude-standard -z --")

    fun diff(repo: String, mode: GitReviewMode, hasCommit: Boolean, file: GitChangedFile): String {
        val paths = listOfNotNull(file.previousPath, file.path).distinct().joinToString(" ", transform = ::quote)
        if (file.isUntracked) {
            // A relative prefix prevents an option-looking filename from becoming an option.
            return at(repo, "$GIT diff $DIFF --no-index -- /dev/null ${quote("./" + file.path)}")
        }
        return at(repo, "$GIT ${comparison(mode, hasCommit)} $DIFF --unified=4 -- $paths")
    }

    fun parseFiles(output: String): List<GitChangedFile> {
        val fields = output.split('\u0000')
        val files = mutableListOf<GitChangedFile>()
        var i = 0
        while (i < fields.size && fields[i].isNotEmpty()) {
            val status = fields[i++].trimStart('\n')
            require(i < fields.size && fields[i].isNotEmpty()) { "Incomplete Git file list" }
            val first = fields[i++]
            if (status.startsWith("R") || status.startsWith("C")) {
                require(i < fields.size && fields[i].isNotEmpty()) { "Incomplete Git rename" }
                files += GitChangedFile(fields[i++], status, first)
            } else files += GitChangedFile(first, status)
        }
        return files
    }
}

enum class DiffLineKind { HEADER, HUNK, CONTEXT, ADDED, REMOVED, NOTE }
data class GitDiffLine(val text: String, val kind: DiffLineKind, val oldLine: Int? = null, val newLine: Int? = null)

/** Line numbers are derived only inside hunks, so +++/--- in source remain source lines. */
fun parseGitDiff(patch: String, maxLines: Int = Int.MAX_VALUE): List<GitDiffLine> {
    val hunk = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*")
    var old = 0
    var new = 0
    var oldRemaining = 0
    var newRemaining = 0
    return patch.trimEnd('\n').lineSequence().filterIndexed { index, line -> index != 0 || line.isNotEmpty() }.take(maxLines).map { line ->
        val match = hunk.matchEntire(line)
        when {
            match != null -> {
                old = match.groupValues[1].toInt()
                new = match.groupValues[3].toInt()
                oldRemaining = match.groupValues[2].ifEmpty { "1" }.toInt()
                newRemaining = match.groupValues[4].ifEmpty { "1" }.toInt()
                GitDiffLine(line, DiffLineKind.HUNK)
            }
            line.startsWith("+") && newRemaining > 0 -> {
                newRemaining--; GitDiffLine(line.drop(1), DiffLineKind.ADDED, newLine = new++)
            }
            line.startsWith("-") && oldRemaining > 0 -> {
                oldRemaining--; GitDiffLine(line.drop(1), DiffLineKind.REMOVED, oldLine = old++)
            }
            line.startsWith(" ") && oldRemaining > 0 && newRemaining > 0 -> {
                oldRemaining--; newRemaining--
                GitDiffLine(line.drop(1), DiffLineKind.CONTEXT, old++, new++)
            }
            line.startsWith("\\") -> GitDiffLine(line, DiffLineKind.NOTE)
            else -> GitDiffLine(line, DiffLineKind.HEADER)
        }
    }.toList()
}
