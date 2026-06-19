package se.joynes.aiterminal.data.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalSessionClientImpl(
    private val context: Context,
    private val onScreenUpdate: ((TerminalSession) -> Unit)? = null
) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        onScreenUpdate?.invoke(changedSession)
    }
    override fun onTitleChanged(updatedSession: TerminalSession) {}
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clipData = clipboard.primaryClip ?: return
        val text = clipData.getItemAt(0)?.coerceToText(context)
        if (!TextUtils.isEmpty(text)) {
            session?.getEmulator()?.paste(text.toString())
        }
    }
    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    override fun getTerminalCursorStyle(): Int = 0

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: "TerminalSession", message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "TerminalSession", message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "TerminalSession", message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "TerminalSession", message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "TerminalSession", message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "TerminalSession", message ?: "", e)
    }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "TerminalSession", "", e) }
}
