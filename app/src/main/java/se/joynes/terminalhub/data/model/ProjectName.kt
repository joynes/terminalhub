package se.joynes.terminalhub.data.model

const val MAX_PROJECT_NAME_LENGTH = 64

private val SAFE_PROJECT_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

/**
 * Project names become local/remote directory names and tmux identifiers. Restrict them to a
 * portable ASCII subset so all three representations stay predictable on every server.
 */
fun projectNameValidationError(name: String): String? = when {
    name.isBlank() -> "Enter a project name."
    name.length > MAX_PROJECT_NAME_LENGTH -> "Use at most $MAX_PROJECT_NAME_LENGTH characters."
    !SAFE_PROJECT_NAME.matches(name) ->
        "Use only A-Z, a-z, 0-9, dots, dashes and underscores. Letters such as å, ä and ö are not supported."
    else -> null
}

fun isValidProjectName(name: String): Boolean = projectNameValidationError(name) == null
