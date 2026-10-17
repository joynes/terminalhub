package com.termux.terminal;

import junit.framework.TestCase;

public class TerminalKeyEncodingTest extends TestCase {
    public void testSlashAndUnderscoreAreUnitSeparator() {
        assertEquals(0x1f, TerminalKeyEncoding.controlCodePoint('/'));
        assertEquals(0x1f, TerminalKeyEncoding.controlCodePoint('_'));
        assertEquals(0x1f, TerminalKeyEncoding.controlCodePoint('7'));
    }

    public void testAllAsciiControlLettersIncludingInterrupt() {
        for (int i = 0; i < 26; i++) {
            assertEquals(i + 1, TerminalKeyEncoding.controlCodePoint('a' + i));
            assertEquals(i + 1, TerminalKeyEncoding.controlCodePoint('A' + i));
        }
        assertEquals(3, TerminalKeyEncoding.controlCodePoint('c'));
    }

    public void testLegacyPunctuationAndDigitAliases() {
        String[] aliases = {" @2", "[3", "\\4", "]5", "^6", "_/7", "?8"};
        int[] expected = {0, 27, 28, 29, 30, 31, 127};
        for (int i = 0; i < aliases.length; i++) {
            for (char character : aliases[i].toCharArray()) {
                assertEquals(expected[i], TerminalKeyEncoding.controlCodePoint(character));
            }
        }
    }

    public void testUnsupportedPunctuationUnicodeAndExistingControlsArePreserved() {
        int[] unchanged = {'!', '-', '.', '\u00e5', 0x1f600, 0, 3, 31, 127};
        for (int codePoint : unchanged) assertEquals(codePoint, TerminalKeyEncoding.controlCodePoint(codePoint));
    }
}
