package se.joynes.aiterminalhub.domain.usecase

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import se.joynes.aiterminalhub.data.security.AuthResult
import se.joynes.aiterminalhub.data.security.BiometricAuthManager
import javax.inject.Inject

class AuthenticateWithBiometric @Inject constructor(
    private val biometricAuthManager: BiometricAuthManager
) {
    operator fun invoke(activity: FragmentActivity): Flow<AuthResult> =
        biometricAuthManager.authenticate(activity)
}
