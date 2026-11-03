package se.joynes.terminalhub.data.security

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger

class KeyStoreHelperTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun privateKeysSurviveRecreationAndDeletionDoesNotTouchOtherAliases() {
        val context: Context = mock(); val logger: AppLogger = mock()
        whenever(context.filesDir).thenReturn(temp.root)
        val helper = KeyStoreHelper(context, logger)
        assertNull(helper.loadPrivateKey("absent")); assertTrue(helper.listKeyAliases().isEmpty())
        helper.savePrivateKey("first", "fixture PEM å")
        helper.savePrivateKey("second", "another fixture")
        val restored = KeyStoreHelper(context, logger)
        assertEquals("fixture PEM å", restored.loadPrivateKey("first"))
        assertEquals(setOf("first", "second"), restored.listKeyAliases().toSet())
        helper.savePrivateKey("first", "replacement")
        assertEquals("replacement", restored.loadPrivateKey("first"))
        helper.deletePrivateKey("first"); helper.deletePrivateKey("absent")
        assertNull(restored.loadPrivateKey("first")); assertEquals("another fixture", restored.loadPrivateKey("second"))
        val permissions = java.nio.file.Files.getPosixFilePermissions(File(temp.root, "ssh_keys/second.pem").toPath())
        assertFalse(permissions.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ))
        assertTrue(permissions.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ))
    }
}
