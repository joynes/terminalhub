package com.termux.terminal;

/** Legacy terminal Ctrl encoding, shared by IME, hardware input and the on-screen key bar. */
public final class TerminalKeyEncoding {
    private TerminalKeyEncoding() {}

    public static int controlCodePoint(int codePoint) {
        if (codePoint >= 'a' && codePoint <= 'z') return codePoint - 'a' + 1;
        if (codePoint >= 'A' && codePoint <= 'Z') return codePoint - 'A' + 1;
        switch (codePoint) {
            case ' ': case '@': case '2': return 0;
            case '[': case '3': return 27;
            case '\\': case '4': return 28;
            case ']': case '5': return 29;
            case '^': case '6': return 30;
            case '_': case '/': case '7': return 31;
            case '?': case '8': return 127;
            // Unsupported Ctrl combinations must not corrupt punctuation or Unicode.
            default: return codePoint;
        }
    }
}
