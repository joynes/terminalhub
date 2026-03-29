package se.joynes.aiterminalhub.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-scoped ViewModel for passing share intents into the Compose tree. */
class SharedIntentViewModel : ViewModel() {
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    fun set(uri: Uri) { _pendingUri.value = uri }
    fun consume() { _pendingUri.value = null }
}
