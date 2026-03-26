package se.joynes.aiterminalhub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import se.joynes.aiterminalhub.service.SshSessionService
import se.joynes.aiterminalhub.ui.navigation.AppNavGraph
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
        enableEdgeToEdge()
        setContent {
            AITerminalHubTheme {
                AppNavGraph()
            }
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
