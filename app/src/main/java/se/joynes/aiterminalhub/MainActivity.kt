package se.joynes.aiterminalhub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import se.joynes.aiterminalhub.service.SshSessionService
import se.joynes.aiterminalhub.ui.navigation.AppNavGraph
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme
import se.joynes.aiterminalhub.ui.viewmodel.SharedIntentViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sharedIntentViewModel: SharedIntentViewModel by viewModels()

    private var bound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

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

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { sharedIntentViewModel.set(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SshSessionService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onStop()
    }
}
