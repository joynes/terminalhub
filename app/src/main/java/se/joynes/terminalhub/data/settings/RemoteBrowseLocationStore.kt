package se.joynes.terminalhub.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.ssh.remoteSubdirectory
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class RemoteBrowseViewState(
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
    val anchorKey: String? = null,
    val panelScroll: Int = 0,
    val selectedFiles: Set<String> = emptySet()
)

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

    internal fun readView(identity: String, directory: String): RemoteBrowseViewState {
        val encoded = prefs.getString(viewKey(identity, directory), null) ?: return RemoteBrowseViewState()
        return runCatching {
            val json = JSONObject(encoded)
            val files = json.optJSONArray("selected") ?: JSONArray()
            RemoteBrowseViewState(
                json.optInt("index").coerceAtLeast(0), json.optInt("offset").coerceAtLeast(0),
                json.optString("anchor").takeIf(String::isNotEmpty), json.optInt("panel").coerceAtLeast(0),
                (0 until files.length()).mapNotNull { files.optString(it).takeIf(String::isNotEmpty) }.toSet()
            )
        }.getOrDefault(RemoteBrowseViewState())
    }

    internal fun saveView(identity: String, directory: String, state: RemoteBrowseViewState) {
        val json = JSONObject().put("index", state.firstVisibleIndex.coerceAtLeast(0))
            .put("offset", state.firstVisibleOffset.coerceAtLeast(0)).put("anchor", state.anchorKey.orEmpty())
            .put("panel", state.panelScroll.coerceAtLeast(0)).put("selected", JSONArray(state.selectedFiles.toList()))
        prefs.edit().putString(viewKey(identity, directory), json.toString()).apply()
    }

    private fun viewKey(identity: String, directory: String): String =
        "view:$identity:" + digest(directory)

    internal fun locationKey(server: Server, project: Project, projectRoot: String): String {
        // Identity and resolved root prevent reused import IDs or changed hosts/roots
        // from reopening a directory belonging to an unrelated project.
        val identity = listOf(server.id, project.id, server.host, server.port, server.username, projectRoot)
            .joinToString("\u0000")
        return digest(identity)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
