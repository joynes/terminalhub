package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalViewTapRoutingTest {

    @Test
    public void trackedMouseTapIsNotSentToTmuxWhenTapContainsUrl() {
        assertFalse(TerminalView.shouldSendTrackedMouseTap(true, false, false, false, true));
    }

    @Test
    public void trackedMouseTapStillReachesTmuxForNormalTerminalText() {
        assertTrue(TerminalView.shouldSendTrackedMouseTap(true, false, false, false, false));
    }
}
