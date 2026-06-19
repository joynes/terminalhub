package se.joynes.aiterminal.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.aiterminal.data.logging.AppLogger
import se.joynes.aiterminal.data.logging.LogLevel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) {
    private val TAG = "KeyStoreHelper"
    private val keysDir get() = File(context.filesDir, "ssh_keys").also { it.mkdirs() }

    fun savePrivateKey(alias: String, pemContent: String) {
        val file = File(keysDir, "$alias.pem")
        file.writeText(pemContent)
        file.setReadable(false, false)
        file.setReadable(true, true)
        logger.log(LogLevel.INFO, TAG, "Private key saved: $alias")
    }

    fun loadPrivateKey(alias: String): String? {
        val file = File(keysDir, "$alias.pem")
        return if (file.exists()) {
            logger.log(LogLevel.DEBUG, TAG, "Loading private key: $alias")
            file.readText()
        } else {
            logger.log(LogLevel.WARN, TAG, "Private key not found: $alias")
            null
        }
    }

    fun deletePrivateKey(alias: String) {
        File(keysDir, "$alias.pem").delete()
        logger.log(LogLevel.INFO, TAG, "Private key deleted: $alias")
    }

    fun listKeyAliases(): List<String> =
        keysDir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
}
