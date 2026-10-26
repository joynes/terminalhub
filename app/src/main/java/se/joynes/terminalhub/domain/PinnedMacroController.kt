package se.joynes.terminalhub.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

data class MacroRunState(val name: String, val projectName: String, val step: Int = 0,
    val total: Int, val running: Boolean = true, val message: String? = null, val projectId: Long? = null,
    val projectIds: Set<Long> = emptySet()) {
    fun affectsProject(id: Long?) = id != null && (projectId == id || id in projectIds)
}

data class MacroDestination(val projectId: Long, val projectName: String, val target: MacroTerminalTarget)

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

    /** Capture every destination before starting; failures never replay input in another terminal. */
    fun startMany(name: String, steps: List<MacroStep>, destinations: List<MacroDestination>): Boolean {
        if (mutable.value?.running == true || destinations.isEmpty() ||
            destinations.map { it.projectId }.distinct().size != destinations.size ||
            destinations.any { !it.target.isConnected() }) return false
        TerminalMacro.validate(steps)
        val captured = destinations.toList()
        val run = ++generation
        mutable.value = MacroRunState(name, "${captured.size} projects", total = steps.size * captured.size,
            projectIds = captured.map { it.projectId }.toSet())
        work = scope.launch(start = CoroutineStart.LAZY) {
            val progress = mutableMapOf<Long, Int>()
            val outcomes = supervisorScope {
                captured.map { destination -> async {
                    try {
                        runner.run(steps, destination.target) { step, _ ->
                            progress[destination.projectId] = step
                            if (generation == run) mutable.value = mutable.value?.copy(step = progress.values.sum())
                        }
                        true
                    } catch (_: TimeoutCancellationException) { false }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                } }.awaitAll()
            }
            if (generation == run) {
                val failed = captured.zip(outcomes).filter { !it.second }.map { it.first.projectName }
                mutable.value = mutable.value?.copy(running = false, message =
                    if (failed.isEmpty()) "Completed in ${captured.size} projects"
                    else "Completed in ${outcomes.count { it }}/${captured.size}. Failed: ${failed.joinToString()}. Check connections before retrying.")
            }
        }.also { it.start() }
        return true
    }

    fun dismiss() { if (mutable.value?.running != true) mutable.value = null }
}
