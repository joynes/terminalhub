package se.joynes.aiterminalhub.data.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Failed : AuthResult()
}

@Singleton
class BiometricAuthManager @Inject constructor(
    private val logger: AppLogger
) {
    private val TAG = "BiometricAuthManager"

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "AITerminal",
        subtitle: String = "Authenticate to continue"
    ): Flow<AuthResult> = callbackFlow {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                logger.log(LogLevel.INFO, TAG, "Biometric auth succeeded")
                trySend(AuthResult.Success)
                close()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                logger.log(LogLevel.ERROR, TAG, "Biometric auth error $errorCode: $errString")
                trySend(AuthResult.Error(errString.toString()))
                close()
            }
            override fun onAuthenticationFailed() {
                logger.log(LogLevel.WARN, TAG, "Biometric auth failed")
                trySend(AuthResult.Failed)
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
        awaitClose { prompt.cancelAuthentication() }
    }
}
