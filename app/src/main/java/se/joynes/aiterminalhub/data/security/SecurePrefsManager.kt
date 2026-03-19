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

@Singleton
class SecurePrefsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    private val TAG = "SecurePrefsManager"

    private val prefs: SharedPreferences by lazy {
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

    fun savePassword(serverId: Long, password: String) {
        prefs.edit().putString("pwd_$serverId", password).apply()
        logger.log(LogLevel.DEBUG, TAG, "Password stored for server $serverId")
    }

    fun getPassword(serverId: Long): String? = prefs.getString("pwd_$serverId", null)

    fun deletePassword(serverId: Long) {
        prefs.edit().remove("pwd_$serverId").apply()
        logger.log(LogLevel.DEBUG, TAG, "Password deleted for server $serverId")
    }

    fun savePassphrase(keyAlias: String, passphrase: String) {
        prefs.edit().putString("pp_$keyAlias", passphrase).apply()
    }

    fun getPassphrase(keyAlias: String): String? = prefs.getString("pp_$keyAlias", null)
}
