package se.joynes.terminalhub.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalInputHistoryRecorderTest {

    @Test fun `default off retains no drafts and saves no input from any source`() {
        val entries = mutableListOf<String>()
        val disabled = TerminalInputHistoryRecorder { _, text -> entries += text }
        disabled.input(1, "super-secret-password")
        disabled.paste(2, "voice or pasted token")
        disabled.complete(3, "panel or pinned input")
        disabled.input(1, "\r")
        assertEquals(0, disabled.retainedDraftCount())
        assertEquals(emptyList<String>(), entries)
    }

    @Test fun `disabling clears all projects before rapid re-enable`() {
        var enabled = true
        val entries = mutableListOf<String>()
        val gated = TerminalInputHistoryRecorder(enabled = { enabled }) { _, text -> entries += text }
        gated.input(1, "partial-secret")
        gated.paste(2, "another secret")
        enabled = false
        gated.clearAllDrafts()
        assertEquals(0, gated.retainedDraftCount())
        gated.input(1, "ignored")
        enabled = true
        gated.input(1, "hello\r")
        gated.input(2, "\r")
        assertEquals(listOf("hello"), entries)
    }
    private val saved = mutableListOf<Pair<Long, String>>()
    private val recorder = TerminalInputHistoryRecorder(enabled = { true }) { id, text -> saved += id to text }

    @Test fun `direct input saves on Enter and ignores blank Enter`() {
        "git status".forEach { recorder.input(1, it.toString()) }
        assertEquals(emptyList<Pair<Long, String>>(), saved)
        recorder.input(1, "\r\n")
        assertEquals(listOf(1L to "git status"), saved)
    }

    @Test fun `backspace removes a whole unicode code point and cancellation discards draft`() {
        recorder.input(1, "hej😀")
        recorder.input(1, "\u007f\r")
        recorder.input(1, "cancel me\u0003\r")
        recorder.input(1, "\b\u007fok\r")
        assertEquals(listOf(1L to "hej", 1L to "ok"), saved)
    }

    @Test fun `paste and voice preserve multiline text until user Enter`() {
        val text = "  first\nsecond\t😀  "
        recorder.paste(1, text)
        assertEquals(0, saved.size)
        recorder.input(1, "\r")
        assertEquals(listOf(1L to text), saved)
    }

    @Test fun `complete panel and pinned submissions clear draft without duplicate on delayed Enter`() {
        recorder.input(1, "old draft")
        recorder.complete(1, "new\ninput")
        recorder.input(1, "\r")
        assertEquals(listOf(1L to "new\ninput"), saved)
    }

    @Test fun `projects are isolated and navigation sequences are not input text`() {
        recorder.input(1, "one")
        recorder.input(2, "two")
        listOf("\u001b[A", "\u001b[1;5D", "\u001bOP", "\u001bx").forEach { recorder.input(1, it) }
        recorder.input(2, "\r")
        recorder.input(1, "\r")
        assertEquals(listOf(2L to "two", 1L to "one"), saved)
    }

    @Test fun `closed sessions do not leak unfinished drafts into new sessions`() {
        recorder.input(1, "old")
        recorder.forget(1)
        recorder.input(1, "new\r")
        assertEquals(listOf(1L to "new"), saved)
    }
}
