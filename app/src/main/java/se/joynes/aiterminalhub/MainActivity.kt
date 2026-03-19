package se.joynes.aiterminalhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import se.joynes.aiterminalhub.ui.navigation.AppNavGraph
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AITerminalHubTheme {
                AppNavGraph()
            }
        }
    }
}
