package se.joynes.terminalhub.data.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.logging.AppLogger

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricAuthenticationFlowTest {
    @Test fun failureDoesNotGrantAccessAndSuccessClosesPromptAndFlow() = runTest {
        val activity: FragmentActivity = mock()
        val logger: AppLogger = mock()
        val executor = Executor { it.run() }
        var callback: BiometricPrompt.AuthenticationCallback? = null
        Mockito.mockStatic(ContextCompat::class.java).use { compat ->
            compat.`when`<Executor> { ContextCompat.getMainExecutor(activity) }.thenReturn(executor)
            Mockito.mockConstruction(BiometricPrompt.PromptInfo.Builder::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)) { builder, _ ->
                whenever(builder.build()).thenReturn(mock())
            }.use {
                Mockito.mockConstruction(BiometricPrompt::class.java) { _, construction ->
                    callback = construction.arguments()[2] as BiometricPrompt.AuthenticationCallback
                }.use { prompts ->
                    val results = mutableListOf<AuthResult>()
                    val job = launch { BiometricAuthManager(logger).authenticate(activity).collect { results += it } }
                    runCurrent()
                    callback!!.onAuthenticationFailed(); runCurrent()
                    assertEquals(listOf(AuthResult.Failed), results); assertTrue(job.isActive)
                    callback!!.onAuthenticationSucceeded(mock()); runCurrent()
                    assertEquals(listOf(AuthResult.Failed, AuthResult.Success), results); assertTrue(job.isCompleted)
                    verify(prompts.constructed().single()).cancelAuthentication()
                }
            }
        }
    }
    @Test fun systemErrorIsReportedAndCollectorCancellationCancelsAuthentication() = runTest {
        val activity: FragmentActivity = mock()
        var callback: BiometricPrompt.AuthenticationCallback? = null
        Mockito.mockStatic(ContextCompat::class.java).use { compat ->
            compat.`when`<Executor> { ContextCompat.getMainExecutor(activity) }.thenReturn(Executor { it.run() })
            Mockito.mockConstruction(BiometricPrompt.PromptInfo.Builder::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)) { builder, _ ->
                whenever(builder.build()).thenReturn(mock())
            }.use {
                Mockito.mockConstruction(BiometricPrompt::class.java) { _, construction -> callback = construction.arguments()[2] as BiometricPrompt.AuthenticationCallback }.use { prompts ->
                    val results = mutableListOf<AuthResult>()
                    val manager = BiometricAuthManager(mock())
                    val first = launch { manager.authenticate(activity).collect { results += it } }; runCurrent()
                    callback!!.onAuthenticationError(5, "Cancelled by user"); runCurrent()
                    assertEquals(listOf(AuthResult.Error("Cancelled by user")), results); assertTrue(first.isCompleted)
                    val second = launch { manager.authenticate(activity).collect { error("Cancelled collector cannot receive results") } }; runCurrent()
                    second.cancel(); runCurrent()
                    prompts.constructed().forEach { verify(it).cancelAuthentication() }
                }
            }
        }
    }
}
