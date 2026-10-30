package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import se.joynes.terminalhub.data.model.projectDisplayNameValidationError

@Composable
internal fun ProjectRenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    val error = projectDisplayNameValidationError(input.text)
    val canSave = input.text.isNotBlank() && error == null
    val save = { if (canSave) onSave(input.text.trim()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rename project") }, text = {
        Column {
            OutlinedTextField(input, { input = it }, label = { Text("Display name") }, singleLine = true,
                isError = error != null, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { key ->
                    val event = key.nativeKeyEvent
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed) {
                        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) save()
                        true
                    } else false
                })
            Text("Changes the name shown in the app. Keeps the same folder and terminal sessions.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }, confirmButton = { TextButton(enabled = canSave, onClick = { save() }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } })
}
