package com.termux.terminal;

public class ScreenBufferTest extends TerminalTestCase {

	public void testBasics() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		assertEquals("", screen.getTranscriptText());
		screen.setChar(0, 0, 'a', 0);
		assertEquals("a", screen.getTranscriptText());
		screen.setChar(0, 0, 'b', 0);
		assertEquals("b", screen.getTranscriptText());
		screen.setChar(2, 0, 'c', 0);
		assertEquals("b c", screen.getTranscriptText());
		screen.setChar(2, 2, 'f', 0);
		assertEquals("b c\n\n  f", screen.getTranscriptText());
		screen.blockSet(0, 0, 2, 2, 'X', 0);
	}

	public void testBlockSet() {
		TerminalBuffer screen = new TerminalBuffer(5, 3, 3);
		screen.blockSet(0, 0, 2, 2, 'X', 0);
		assertEquals("XX\nXX", screen.getTranscriptText());
		screen.blockSet(1, 1, 2, 2, 'Y', 0);
		assertEquals("XX\nXYY\n YY", screen.getTranscriptText());
	}

	public void testGetSelectedText() {
		withTerminalSized(5, 3).enterString("ABCDEFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("AB", mTerminal.getSelectedText(0, 0, 1, 0));
		assertEquals("BC", mTerminal.getSelectedText(1, 0, 2, 0));
		assertEquals("CDE", mTerminal.getSelectedText(2, 0, 4, 0));
		assertEquals("FG", mTerminal.getSelectedText(0, 1, 1, 1));
		assertEquals("GH", mTerminal.getSelectedText(1, 1, 2, 1));
		assertEquals("HIJ", mTerminal.getSelectedText(2, 1, 4, 1));

		assertEquals("ABCDEFG", mTerminal.getSelectedText(0, 0, 1, 1));
		withTerminalSized(5, 3).enterString("ABCDE\r\nFGHIJ").assertLinesAre("ABCDE", "FGHIJ", "     ");
		assertEquals("ABCDE\nFG", mTerminal.getSelectedText(0, 0, 1, 1));
	}

	public void testGetSelectedTextJoinFullLines() {
		withTerminalSized(5, 3).enterString("ABCDE\r\nFG");
		assertEquals("ABCDEFG", mTerminal.getScreen().getSelectedText(0, 0, 1, 1, true, true));

		withTerminalSized(5, 3).enterString("ABC\r\nFG");
		assertEquals("ABC\nFG", mTerminal.getScreen().getSelectedText(0, 0, 1, 1, true, true));
	}

	public void testGetWordAtLocation() {
		withTerminalSized(5, 3).enterString("ABCDEFGHIJ\r\nKLMNO");
		assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(0, 0));
		assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(4, 1));
		assertEquals("ABCDEFGHIJKLMNO", mTerminal.getScreen().getWordAtLocation(4, 2));

		withTerminalSized(5, 3).enterString("ABC DEF GHI ");
		assertEquals("ABC", mTerminal.getScreen().getWordAtLocation(0, 0));
		assertEquals("", mTerminal.getScreen().getWordAtLocation(3, 0));
		assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(4, 0));
		assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(0, 1));
		assertEquals("DEF", mTerminal.getScreen().getWordAtLocation(1, 1));
		assertEquals("GHI", mTerminal.getScreen().getWordAtLocation(0, 2));
		assertEquals("", mTerminal.getScreen().getWordAtLocation(1, 2));
		assertEquals("", mTerminal.getScreen().getWordAtLocation(2, 2));
	}

	public void testGetWrappedWordAtLocationAcrossRows() {
		String url = "https://example.com/a/very/long/path?value=123";
		withTerminalSized(12, 5).enterString("  " + url);

		assertEquals(url, mTerminal.getScreen().getWrappedWordAtLocation(2, 0));
		assertEquals(url, mTerminal.getScreen().getWrappedWordAtLocation(5, 1));
		assertEquals(url, mTerminal.getScreen().getWrappedWordAtLocation(3, 3));
	}

	public void testGetWrappedWordDoesNotJoinHardNewline() {
		withTerminalSized(12, 3).enterString("first\r\nsecond");

		assertEquals("first", mTerminal.getScreen().getWrappedWordAtLocation(2, 0));
		assertEquals("second", mTerminal.getScreen().getWrappedWordAtLocation(2, 1));
	}

	public void testFindTerminalUrl() {
		assertEquals("https://example.com/path", TerminalUrlFinder.find("(https://example.com/path)."));
		assertEquals("https://example.com/a_(b)", TerminalUrlFinder.find("https://example.com/a_(b)"));
		assertEquals("www.example.com/long/path", TerminalUrlFinder.find("url=www.example.com/long/path,"));
		assertNull(TerminalUrlFinder.find("not-a-link"));
	}

	public void testGetUrlCandidateAcrossIndentedHardWrap() {
		withTerminalSized(18, 5).enterString(
			" https://read-and-\r\n  resolve.vercel.\r\n  app/?game=quiz"
		);

		String expected = "https://read-and-resolve.vercel.app/?game=quiz";
		assertEquals(expected, mTerminal.getScreen().getUrlCandidateAtLocation(2, 0));
		assertEquals(expected, mTerminal.getScreen().getUrlCandidateAtLocation(4, 1));
		assertEquals(expected, mTerminal.getScreen().getUrlCandidateAtLocation(5, 2));
	}

	public void testGetUrlCandidateDoesNotJoinUnrelatedNextLine() {
		withTerminalSized(30, 3).enterString("https://example.com\r\nnext-command");

		assertEquals(
			"https://example.com",
			mTerminal.getScreen().getUrlCandidateAtLocation(4, 0)
		);
	}

	public void testGetUrlCandidateStopsBeforeProseAfterLink() {
		withTerminalSized(64, 4).enterString(
			"TerminalHub-hemsidan (https://joynes.github.io/\r\n" +
			"  terminalhub/). Den lyfter nu snabb växling\r\n" +
			"  mellan beständiga SSH-flikar"
		);

		String expected = "https://joynes.github.io/terminalhub/";
		assertEquals(
			expected,
			TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(32, 0))
		);
		assertEquals(
			expected,
			TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(5, 1))
		);
	}

	public void testUrlAcrossPaddedSoftWrappedRowsWithFollowingProse() {
		String first = "  inne i Music (https://joynes.github.io/";
		withTerminalSized(48, 5).enterString(
			first + " ".repeat(48 - first.length()) +
			"  joynes.se/#music). Den separata AI-sektionen är borttagen."
		);
		assertEquals("https://joynes.github.io/joynes.se/#music",
			TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(20, 0)));
		assertEquals("https://joynes.github.io/joynes.se/#music",
			TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(5, 1)));
		assertNull(TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(27, 1)));
	}

	public void testUrlSchemeAndPathCanSoftWrapAtAnyColumn() {
		String url = "https://example.com/a/very/long/path?value=123";
		for (int padding = 0; padding < 16; padding++) {
			withTerminalSized(16, 6).enterString(" ".repeat(padding) + url + " done");
			for (int index = 0; index < url.length(); index++) {
				int cell = padding + index;
				assertEquals("padding=" + padding + " index=" + index, url,
					TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(cell % 16, cell / 16)));
			}
		}
	}

	public void testNativeWrappedUrlLongerThanEightRows() {
		String url = "https://example.com/" + "segment/".repeat(18) + "#music";
		withTerminalSized(16, 16).enterString(url);
		assertEquals(url, TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(3, 0)));
		assertEquals(url, TerminalUrlFinder.find(mTerminal.getScreen().getUrlCandidateAtLocation(3, 9)));
	}
}
