package se.joynes.aiterminalhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import se.joynes.aiterminalhub.data.logging.AppLogger
import se.joynes.aiterminalhub.data.logging.LogLevel
import se.joynes.aiterminalhub.data.runtime.AppRuntimeRepository
import se.joynes.aiterminalhub.ui.navigation.AppNavGraph
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme
import se.joynes.aiterminalhub.ui.viewmodel.SharedIntentViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedIntentViewModel: SharedIntentViewModel by viewModels()
    @Inject lateinit var appLogger: AppLogger
    @Inject lateinit var runtimeRepository: AppRuntimeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.isForceDarkAllowed = false
        }
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#0D0D1A"))
        setContent {
            val pendingUri by sharedIntentViewModel.pendingUri.collectAsState()
            AITerminalHubTheme {
                AppNavGraph(
                    sharedUri = pendingUri,
                    onConsumeSharedUri = { sharedIntentViewModel.consume() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        runtimeRepository.noteAppForeground()
        appLogger.log(LogLevel.INFO, "AppRuntime", "MainActivity onStart")
    }

    override fun onStop() {
        runtimeRepository.noteAppBackground()
        appLogger.log(LogLevel.INFO, "AppRuntime", "MainActivity onStop")
        super.onStop()
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { sharedIntentViewModel.set(it) }
        }
    }
}
