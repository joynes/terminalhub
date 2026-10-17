package se.joynes.terminalhub.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.ssh.remoteSubdirectory
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Device-local browser position, separate from the phone's download destination. */
@Singleton
class RemoteBrowseLocationStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("remote_browse_locations", Context.MODE_PRIVATE)

    fun lastDirectory(server: Server, project: Project, projectRoot: String): String? {
        val key = locationKey(server, project, projectRoot)
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            remoteSubdirectory(projectRoot, stored)
            stored
        }.getOrElse {
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun save(server: Server, project: Project, projectRoot: String, relativeDirectory: String) {
        remoteSubdirectory(projectRoot, relativeDirectory) // Never restore an absolute path or traversal.
        val normalized = relativeDirectory.split('/').filter(String::isNotEmpty).joinToString("/")
        prefs.edit().putString(locationKey(server, project, projectRoot), normalized).apply()
    }

    private fun locationKey(server: Server, project: Project, projectRoot: String): String {
        // Identity and resolved root prevent reused import IDs or changed hosts/roots
        // from reopening a directory belonging to an unrelated project.
        val identity = listOf(server.id, project.id, server.host, server.port, server.username, projectRoot)
            .joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
