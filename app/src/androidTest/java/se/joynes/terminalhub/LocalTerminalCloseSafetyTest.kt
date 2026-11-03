package se.joynes.terminalhub

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl

class LocalTerminalCloseSafetyTest {
    @Test fun closingBeforeTheFirstLayoutDoesNotKillTheAppOrStartAShellLater() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appPid = Process.myPid()
        instrumentation.runOnMainSync {
            val session = TerminalSession("/system/bin/sh", context.cacheDir.absolutePath,
                arrayOf("/system/bin/sh"), arrayOf("PATH=/system/bin"), 100,
                TerminalSessionClientImpl(context))
            assertEquals(0, session.pid)
            session.finishIfRunning() // Previously sent SIGKILL to PID 0 and killed the test process.
            assertEquals(appPid, Process.myPid())
            assertFalse(session.isRunning)
            assertEquals(-1, session.pid)
            session.finishIfRunning() // Closing twice must not signal PID -1 either.
            session.updateSize(80, 24, 0, 0) // A queued, late view layout must not resurrect the shell.
            assertNotNull(session.emulator)
            assertEquals(-1, session.pid)
            assertFalse(session.isRunning)
        }
    }
}
