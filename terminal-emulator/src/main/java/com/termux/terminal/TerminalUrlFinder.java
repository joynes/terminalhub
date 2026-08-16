package com.termux.terminal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts browser-safe HTTP(S) URLs from terminal tokens. */
public final class TerminalUrlFinder {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)(?<![a-z0-9])(?:https?://|www\\.)[^\\s<>\\\"']+"
    );

    private TerminalUrlFinder() {}

    public static String find(String token) {
        if (token == null || token.isEmpty()) return null;
        Matcher matcher = URL_PATTERN.matcher(token);
        if (!matcher.find()) return null;

        String url = matcher.group();
        int end = url.length();
        while (end > 0) {
            char last = url.charAt(end - 1);
            if (last == '.' || last == ',' || last == ';' || last == ':' || last == '!' || last == '?') {
                end--;
            } else if ((last == ')' && hasUnmatchedClosing(url, end, '(', ')')) ||
                       (last == ']' && hasUnmatchedClosing(url, end, '[', ']')) ||
                       (last == '}' && hasUnmatchedClosing(url, end, '{', '}'))) {
                end--;
            } else {
                break;
            }
        }
        return end == 0 ? null : url.substring(0, end);
    }

    private static boolean hasUnmatchedClosing(String text, int end, char open, char close) {
        int balance = 0;
        for (int i = 0; i < end; i++) {
            char c = text.charAt(i);
            if (c == open) balance++;
            else if (c == close) balance--;
        }
        return balance < 0;
    }
}
