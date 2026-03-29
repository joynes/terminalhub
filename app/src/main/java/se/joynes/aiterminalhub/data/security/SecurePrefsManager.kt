package se.joynes.aiterminalhub.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import javax.inject.Inject
import javax.inject.Singleton

// Plain (unencrypted) prefs used only for injecting test credentials via adb run-as
private const val TEST_PREFS = "test_ssh_prefs"

@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    private val TAG = "SecurePrefsManager"

    private val prefs: SharedPreferences by lazy { openOrReset() }

    private fun openOrReset(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "ssh_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore key invalidated (reinstall, fingerprint change, etc.) — wipe and retry
            logger.log(LogLevel.WARN, TAG, "Encrypted prefs corrupted, resetting: ${e.message}")
            context.deleteSharedPreferences("ssh_secure_prefs")
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "ssh_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun savePassword(serverId: Long, password: String) {
        prefs.edit().putString("pwd_$serverId", password).apply()
        logger.log(LogLevel.DEBUG, TAG, "Password stored for server $serverId")
    }

    fun getPassword(serverId: Long): String? =
        prefs.getString("pwd_$serverId", null)
            ?: context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
                .getString("pwd_$serverId", null)

    fun deletePassword(serverId: Long) {
        prefs.edit().remove("pwd_$serverId").apply()
        logger.log(LogLevel.DEBUG, TAG, "Password deleted for server $serverId")
    }

    fun savePrivateKey(serverId: Long, pem: String) {
        prefs.edit().putString("pk_$serverId", pem).apply()
        logger.log(LogLevel.DEBUG, TAG, "Private key stored for server $serverId")
    }

    fun getPrivateKey(serverId: Long): String? =
        prefs.getString("pk_$serverId", null)
            ?: context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
                .getString("pk_$serverId", null)

    fun savePassphrase(keyAlias: String, passphrase: String) {
        prefs.edit().putString("pp_$keyAlias", passphrase).apply()
    }

    fun getPassphrase(keyAlias: String): String? = prefs.getString("pp_$keyAlias", null)
}
