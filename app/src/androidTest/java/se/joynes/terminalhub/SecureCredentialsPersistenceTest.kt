package se.joynes.terminalhub

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.security.SecurePrefsManager

@HiltAndroidTest
class SecureCredentialsPersistenceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var logger: AppLogger
    private lateinit var prefs: SecurePrefsManager
    @Before fun setup() { hilt.inject(); prefs = SecurePrefsManager(context, logger); prefs.clearAll() }
    @After fun cleanup() {
        prefs.clearAll()
        context.getSharedPreferences("test_ssh_prefs", 0).edit().remove("pwd_900001").remove("pk_900001").commit()
    }
    @Test fun credentialsAreEncryptedPersistedAndIsolatedByServerAndKeyAlias() {
        prefs.savePassword(900001, "fixture-password-å")
        prefs.savePrivateKey(900001, "fixture-private-key-body")
        prefs.savePassword(900002, "other-password")
        prefs.savePassphrase("fixture-key-alias", "fixture-passphrase")
        val restored = SecurePrefsManager(context, logger)
        assertEquals("fixture-password-å", restored.getPassword(900001))
        assertEquals("fixture-private-key-body", restored.getPrivateKey(900001))
        assertEquals("other-password", restored.getPassword(900002))
        assertEquals("fixture-passphrase", restored.getPassphrase("fixture-key-alias"))
        assertNull(restored.getPassword(900003)); assertNull(restored.getPrivateKey(900003)); assertNull(restored.getPassphrase("other-alias"))
        val stored = context.getSharedPreferences("ssh_secure_prefs", 0).all.toString()
        for (secret in listOf("fixture-password-å", "fixture-private-key-body", "fixture-passphrase", "other-password")) {
            assertFalse("Encrypted preference values must not contain plaintext fixtures", stored.contains(secret))
        }
        restored.deletePassword(900001); restored.deletePrivateKey(900001)
        assertNull(restored.getPassword(900001)); assertNull(restored.getPrivateKey(900001))
        assertEquals("other-password", restored.getPassword(900002))
        restored.clearAll()
        assertNull(SecurePrefsManager(context, logger).getPassphrase("fixture-key-alias"))
    }
    @Test fun diagnosticFallbackIsUsedOnlyWhenTheEncryptedValueIsAbsent() {
        context.getSharedPreferences("test_ssh_prefs", 0).edit()
            .putString("pwd_900001", "fixture-fallback-password").putString("pk_900001", "fixture-fallback-key").commit()
        assertEquals("fixture-fallback-password", prefs.getPassword(900001))
        assertEquals("fixture-fallback-key", prefs.getPrivateKey(900001))
        prefs.savePassword(900001, "encrypted-password"); prefs.savePrivateKey(900001, "encrypted-key")
        assertEquals("encrypted-password", prefs.getPassword(900001)); assertEquals("encrypted-key", prefs.getPrivateKey(900001))
    }
}
