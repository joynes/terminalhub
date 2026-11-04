package se.joynes.terminalhub

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.view.inspector.WindowInspector
import androidx.test.platform.app.InstrumentationRegistry

/** Dialog tests can have several native roots; hide the IME using each actual window token. */
internal fun hideTestKeyboard() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        WindowInspector.getGlobalWindowViews().filter { it.isShown }.forEach { root ->
            (root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(root.windowToken, 0)
        }
    }
}
