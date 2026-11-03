package se.joynes.terminalhub.ui.screen.splash

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.security.AuthResult
import se.joynes.terminalhub.data.security.BiometricAuthManager

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val manager: BiometricAuthManager = mock()
    private val activity: FragmentActivity = mock()
    private lateinit var vm: SplashViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        vm = SplashViewModel(manager)
    }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun missingActivityBypassesWithoutConsultingTheBiometricService() {
        assertEquals(AuthState.Idle, vm.authState.value)
        vm.checkBiometricAvailability(null)
        assertEquals(AuthState.NoBiometric, vm.authState.value)
        verifyNoInteractions(manager)
    }
    @Test fun supportedBiometricsWaitForExplicitAuthentication() {
        whenever(manager.canAuthenticate(activity)).thenReturn(true)
        vm.checkBiometricAvailability(activity)
        assertEquals(AuthState.Idle, vm.authState.value)
        verify(manager, never()).authenticate(any(), any(), any())
    }
    @Test fun unsupportedBiometricsBypassBothAvailabilityCheckAndAuthentication() {
        vm.checkBiometricAvailability(activity)
        assertEquals(AuthState.NoBiometric, vm.authState.value)
        vm.authenticate(activity)
        assertEquals(AuthState.NoBiometric, vm.authState.value)
        verify(manager, never()).authenticate(any(), any(), any())
    }
    @Test fun authenticationShowsLoadingThenSuccessAndConsumesOnlyFirstResult() = runTest(dispatcher) {
        whenever(manager.canAuthenticate(activity)).thenReturn(true)
        whenever(manager.authenticate(activity)).thenReturn(flowOf(AuthResult.Success, AuthResult.Failed))
        vm.authenticate(activity)
        assertEquals(AuthState.Loading, vm.authState.value)
        runCurrent()
        assertEquals(AuthState.Success, vm.authState.value)
    }
    @Test fun authenticationErrorIsShownAndCanBeRetried() = runTest(dispatcher) {
        whenever(manager.canAuthenticate(activity)).thenReturn(true)
        whenever(manager.authenticate(activity)).thenReturn(flowOf(AuthResult.Error("Cancelled")), flowOf(AuthResult.Success))
        vm.authenticate(activity); runCurrent()
        assertEquals(AuthState.Error("Cancelled"), vm.authState.value)
        vm.authenticate(activity); runCurrent()
        assertEquals(AuthState.Success, vm.authState.value)
    }
    @Test fun rejectedFingerprintShowsFailureWithoutGrantingAccess() = runTest(dispatcher) {
        whenever(manager.canAuthenticate(activity)).thenReturn(true)
        whenever(manager.authenticate(activity)).thenReturn(flowOf(AuthResult.Failed))
        vm.authenticate(activity); runCurrent()
        assertEquals(AuthState.Error("Authentication failed"), vm.authState.value)
    }
}
