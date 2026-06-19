package se.joynes.aiterminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminal.BuildConfig
import se.joynes.aiterminal.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetroTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = "> $title",
                fontFamily = MonoFontFamily,
                color = MegaDrivePrimary,
                fontSize = 14.sp
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MegaDrivePrimary)
                }
            }
        },
        actions = {
            actions()
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontFamily = MonoFontFamily,
                color = MegaDriveDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MegaDriveSurface,
            titleContentColor = MegaDrivePrimary
        )
    )
}
