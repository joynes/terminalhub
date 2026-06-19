package se.joynes.terminalhub.data.logging

sealed class LogEvent {
    data class SshConnect(val host: String, val port: Int) : LogEvent()
    data class SshSend(val sessionId: String, val bytes: Int) : LogEvent()
    data class SshReceive(val sessionId: String, val bytes: Int) : LogEvent()
    data class Reconnect(val host: String, val attempt: Int) : LogEvent()
    data class TmuxCmd(val sessionName: String, val command: String) : LogEvent()
    data class AppEvent(val name: String) : LogEvent()
}
