package se.joynes.terminalhub

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.ui.components.openAppUpdates

class AppUpdateIntentTest {
    @Test fun updateButtonUsesProductionListingAndFallsBackWhenPlayIsMissingOrRestricted() {
        for (failure in listOf<Exception?>(null, ActivityNotFoundException("No Play"), SecurityException("Restricted"))) {
            val intents = mutableListOf<Intent>()
            val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                override fun startActivity(intent: Intent) {
                    intents += intent
                    if (intents.size == 1 && failure != null) throw failure
                }
            }
            openAppUpdates(context)
            assertEquals("com.android.vending", intents.first().`package`)
            assertEquals("market://details?id=se.joynes.terminalhub", intents.first().data.toString())
            assertTrue(intents.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
            assertEquals(if (failure == null) 1 else 2, intents.size)
            if (failure != null) assertEquals("https://play.google.com/store/apps/details?id=se.joynes.terminalhub", intents.last().data.toString())
        }
    }
}
