package se.joynes.terminalhub.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MacroRunState(val name: String, val projectName: String, val step: Int = 0,
    val total: Int, val running: Boolean = true, val message: String? = null, val projectId: Long? = null)

/** One explicit run at a time. No queue and no restart after a disconnect or process death. */
class PinnedMacroController(private val scope: CoroutineScope, private val runner: TerminalMacroRunner = TerminalMacroRunner()) {
    private val mutable = MutableStateFlow<MacroRunState?>(null)
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0L

    fun start(name: String, projectName: String, steps: List<MacroStep>, target: MacroTerminalTarget, projectId: Long? = null): Boolean {
        if (mutable.value?.running == true || !target.isConnected()) return false
        TerminalMacro.validate(steps)
        val run = ++generation
        mutable.value = MacroRunState(name, projectName, total = steps.size, projectId = projectId)
        work = scope.launch(start = CoroutineStart.LAZY) {
            try {
                runner.run(steps, target) { step, total -> if (generation == run) mutable.value = MacroRunState(name, projectName, step, total, projectId = projectId) }
                if (generation == run) mutable.value = mutable.value?.copy(running = false, message = "Macro completed")
            } catch (_: TimeoutCancellationException) {
                if (generation == run) mutable.value = mutable.value?.copy(running = false, message = "Macro stopped: timed out waiting for the terminal. Reconnect and try again.")
            } catch (cancelled: CancellationException) {
                if (generation == run) mutable.value = mutable.value?.copy(running = false, message = "Macro stopped")
                throw cancelled
            } catch (_: Exception) {
                // No terminal contents or exception messages containing user text in logs/UI.
                if (generation == run) mutable.value = mutable.value?.copy(running = false, message = "Macro stopped: its terminal disconnected or could not receive input. Reconnect and try again.")
            }
        }.also { it.start() }
        return true
    }

    fun stop() {
        generation++
        work?.cancel()
        mutable.value = mutable.value?.copy(running = false, message = "Macro stopped")
    }

    fun dismiss() { if (mutable.value?.running != true) mutable.value = null }
}
