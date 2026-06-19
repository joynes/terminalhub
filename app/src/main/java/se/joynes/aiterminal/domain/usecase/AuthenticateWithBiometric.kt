package se.joynes.aiterminal.domain.usecase

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminal.data.security.AuthResult
import se.joynes.aiterminal.data.security.BiometricAuthManager
import javax.inject.Inject

class AuthenticateWithBiometric @Inject constructor(
    private val biometricAuthManager: BiometricAuthManager
) {
    operator fun invoke(activity: FragmentActivity): Flow<AuthResult> =
        biometricAuthManager.authenticate(activity)
}
