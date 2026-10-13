package se.joynes.terminalhub.data.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppSettingsRepository
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val notificationGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val batteryExempt = context.getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        val text = SettingsYaml.export(repository.settings.value) +
            "# Read-only device context for AI discussion (not imported):\n" +
            "# Android API ${android.os.Build.VERSION.SDK_INT}; notification permission granted: $notificationGranted\n" +
            "# Battery optimization exemption: $batteryExempt (change in Android Settings)\n"
        (context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open settings destination"))
            .use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
    suspend fun preview(uri: Uri): SettingsImportPreview = withContext(Dispatchers.IO) {
        val text = (context.contentResolver.openInputStream(uri) ?: error("Cannot open settings file")).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= SettingsYaml.MAX_BYTES) { "Settings file is too large (maximum 64 KB)." }
                output.write(buffer, 0, count)
            }
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
        }
        SettingsYaml.preview(text, repository.settings.value)
    }
}
