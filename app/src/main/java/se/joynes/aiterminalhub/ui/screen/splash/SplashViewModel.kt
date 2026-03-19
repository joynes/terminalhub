package se.joynes.aiterminalhub.ui.screen.splash

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.data.security.AuthResult
import se.joynes.aiterminalhub.data.security.BiometricAuthManager
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    object NoBiometric : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun checkBiometricAvailability(activity: FragmentActivity?) {
        if (activity == null || !biometricAuthManager.canAuthenticate(activity)) {
            _authState.value = AuthState.NoBiometric
        }
    }

    fun authenticate(activity: FragmentActivity) {
        if (!biometricAuthManager.canAuthenticate(activity)) {
            _authState.value = AuthState.NoBiometric
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            when (val result = biometricAuthManager.authenticate(activity).first()) {
                is AuthResult.Success -> _authState.value = AuthState.Success
                is AuthResult.Error -> _authState.value = AuthState.Error(result.message)
                AuthResult.Failed -> _authState.value = AuthState.Error("Authentication failed")
            }
        }
    }
}
