package se.joynes.terminalhub.domain.usecase

import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import se.joynes.terminalhub.data.security.AuthResult
import se.joynes.terminalhub.data.security.BiometricAuthManager
import javax.inject.Inject

class AuthenticateWithBiometric @Inject constructor(
    private val biometricAuthManager: BiometricAuthManager
) {
    operator fun invoke(activity: FragmentActivity): Flow<AuthResult> =
        biometricAuthManager.authenticate(activity)
}
