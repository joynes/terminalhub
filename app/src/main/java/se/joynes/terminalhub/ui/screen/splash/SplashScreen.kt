package se.joynes.terminalhub.ui.screen.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import se.joynes.terminalhub.data.security.AuthResult
import se.joynes.terminalhub.data.security.BiometricAuthManager
import se.joynes.terminalhub.ui.components.BlinkingCursor
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.*
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SplashScreen(
    onAuthSuccess: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current

    // Auto-check biometric availability on first composition
    LaunchedEffect(Unit) {
        viewModel.checkBiometricAvailability(context as? FragmentActivity)
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success, is AuthState.NoBiometric -> onAuthSuccess()
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MegaDriveBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "AI TERMINAL HUB",
                color = MegaDrivePrimary,
                fontSize = 20.sp,
                fontFamily = MonoFontFamily
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SSH PROJECT MANAGER v1.0",
                color = MegaDriveAccent,
                fontSize = 12.sp,
                fontFamily = MonoFontFamily
            )
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("> READY", color = MegaDriveGreen, fontSize = 14.sp, fontFamily = MonoFontFamily)
                BlinkingCursor()
            }
            Spacer(Modifier.height(32.dp))
            when (authState) {
                is AuthState.Idle, is AuthState.Error -> {
                    RetroButton(
                        text = "[ AUTHENTICATE ]",
                        onClick = { viewModel.authenticate(context as FragmentActivity) }
                    )
                    if (authState is AuthState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = (authState as AuthState.Error).message,
                            color = MegaDriveError,
                            fontSize = 10.sp,
                            fontFamily = MonoFontFamily
                        )
                    }
                }
                is AuthState.Loading -> {
                    Text("Authenticating...", color = MegaDrivePrimary, fontSize = 12.sp, fontFamily = MonoFontFamily)
                }
                is AuthState.Success -> {
                    Text("ACCESS GRANTED", color = MegaDriveGreen, fontSize = 14.sp, fontFamily = MonoFontFamily)
                }
                is AuthState.NoBiometric -> {
                    Text("BIOMETRIC N/A — BYPASSING", color = MegaDriveDim, fontSize = 10.sp, fontFamily = MonoFontFamily)
                }
            }
        }
    }
}
