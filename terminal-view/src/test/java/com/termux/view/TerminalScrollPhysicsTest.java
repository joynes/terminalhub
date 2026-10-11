package com.termux.view;

import org.junit.Test;
import static org.junit.Assert.*;

public class TerminalScrollPhysicsTest {
    @Test public void touchFlingInMouseTrackingModeIsNotMistakenForMouseModeChange() {
        assertTrue(TerminalScrollPhysics.validFlingContext(true, true, true, true));
        assertTrue(TerminalScrollPhysics.validFlingContext(false, false, false, false));
        assertFalse(TerminalScrollPhysics.validFlingContext(true, true, false, true));
        assertFalse(TerminalScrollPhysics.validFlingContext(true, true, true, false));
    }
    @Test public void speedDirectionAndRemoteBurstLimitsAreSymmetric() {
        assertEquals(200f, TerminalScrollPhysics.scaledDistance(100, 2, false), 0);
        assertEquals(-200f, TerminalScrollPhysics.scaledDistance(100, 2, true), 0);
        assertEquals(-25f, TerminalScrollPhysics.scaledDistance(-100, .25f, false), 0);
        assertEquals(6, TerminalScrollPhysics.boundedRows(100, 6));
        assertEquals(-6, TerminalScrollPhysics.boundedRows(-100, 6));
        assertEquals(2, TerminalScrollPhysics.boundedRows(2, 6));
    }
}
