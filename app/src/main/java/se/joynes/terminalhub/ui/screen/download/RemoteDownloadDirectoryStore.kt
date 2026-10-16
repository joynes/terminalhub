package se.joynes.terminalhub.ui.screen.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

internal class RemoteDownloadDirectoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(directoryUri: Uri) {
        preferences.edit().putString(KEY_LAST_DIRECTORY_URI, directoryUri.toString()).apply()
    }

    fun clear() = preferences.edit().remove(KEY_LAST_DIRECTORY_URI).apply()

    /** Run off the UI thread: providers may live in another process or in the cloud. */
    fun isWritableDirectory(uri: Uri): Boolean = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        appContext.contentResolver.query(document, arrayOf(
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS
        ), null, null, null)?.use { cursor ->
            cursor.moveToFirst() &&
                cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                (cursor.getLong(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()) != 0L
        } ?: false
    }.getOrDefault(false)

    fun lastPersistedDirectory(): Uri? {
        val storedUri = preferences.getString(KEY_LAST_DIRECTORY_URI, null)
        val writableDirectories = runCatching {
            appContext.contentResolver.persistedUriPermissions
                .asSequence()
                .filter { it.isWritePermission }
                .map { it.uri.toString() }
                .toSet()
        }.getOrDefault(emptySet())
        val usableUri = usableRememberedDownloadDirectory(storedUri, writableDirectories)
        if (storedUri != null && usableUri == null) {
            preferences.edit().remove(KEY_LAST_DIRECTORY_URI).apply()
        }
        return usableUri?.let(Uri::parse)
    }

    private companion object {
        const val PREFERENCES_NAME = "remote_download_destination"
        const val KEY_LAST_DIRECTORY_URI = "last_directory_uri"
    }
}

internal fun usableRememberedDownloadDirectory(
    storedUri: String?,
    writablePersistedUris: Set<String>
): String? = storedUri
    ?.takeIf(String::isNotBlank)
    ?.takeIf(writablePersistedUris::contains)
