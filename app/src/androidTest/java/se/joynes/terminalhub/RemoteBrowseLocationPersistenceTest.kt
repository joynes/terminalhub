package se.joynes.terminalhub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.settings.RemoteBrowseLocationStore

class RemoteBrowseLocationPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = Server(id = 7, name = "server", host = "example.com", username = "demo")
    private val project = Project(id = 11, serverId = 7, name = "music")
    private val root = "~/terminalhub/music"

    @Before @After fun clear() {
        context.getSharedPreferences("remote_browse_locations", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun nestedDirectoryAndExplicitRootSurviveStoreRecreation() {
        assertNull(RemoteBrowseLocationStore(context).lastDirectory(server, project, root))
        RemoteBrowseLocationStore(context).save(server, project, root, "images/nested")
        assertEquals("images/nested", RemoteBrowseLocationStore(context).lastDirectory(server, project, root))
        RemoteBrowseLocationStore(context).save(server, project, root, "")
        assertEquals("", RemoteBrowseLocationStore(context).lastDirectory(server, project, root))
    }

    @Test fun projectsAndServerIdentitiesNeverShareTheirRememberedLocation() {
        val store = RemoteBrowseLocationStore(context)
        val other = project.copy(id = 12, name = "other")
        store.save(server, project, root, "photos")
        store.save(server, other, "~/terminalhub/other", "docs")
        assertEquals("photos", store.lastDirectory(server, project, root))
        assertEquals("docs", store.lastDirectory(server, other, "~/terminalhub/other"))
        assertNull(store.lastDirectory(server.copy(host = "different.example"), project, root))
        assertNull(store.lastDirectory(server.copy(username = "other"), project, root))
        assertNull(store.lastDirectory(server.copy(port = 2222), project, root))
        assertNull(store.lastDirectory(server, project, "~/renamed-project"))
    }

    @Test fun traversalAndAbsolutePathsAreRejectedWithoutReplacingGoodLocation() {
        val store = RemoteBrowseLocationStore(context)
        store.save(server, project, root, "photos")
        listOf("../secret", "/tmp", "photos/../secret", "x\\y", "x\u0000y").forEach { path ->
            assertTrue(path, runCatching { store.save(server, project, root, path) }.isFailure)
        }
        assertEquals("photos", store.lastDirectory(server, project, root))
    }
}
