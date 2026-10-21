package com.termux.view;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class TerminalSearchHighlightTest {
    @Test public void highlightingHonorsCaseAndSearchOffset() {
        assertEquals(0, TerminalView.indexOfSearchMatch("needle Needle", "needle", 0, true));
        assertEquals(-1, TerminalView.indexOfSearchMatch("needle Needle", "needle", 1, true));
        assertEquals(7, TerminalView.indexOfSearchMatch("needle Needle", "needle", 1, false));
        assertEquals(-1, TerminalView.indexOfSearchMatch("abc", "", 0, false));
    }
    @Test public void highlightingDoesNotDependOnPhonesLocaleOrChangeCharacterPositions() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(0, TerminalView.indexOfSearchMatch("I FILE", "i", 0, false));
            assertEquals(2, TerminalView.indexOfSearchMatch("İ FILE", "file", 0, false));
        } finally { Locale.setDefault(previous); }
    }
}
