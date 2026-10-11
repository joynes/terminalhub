package com.termux.view;

/** Platform-independent helpers shared by drag/fling; distances are pixels until row conversion. */
public final class TerminalScrollPhysics {
    private TerminalScrollPhysics() {}
    public static float scaledDistance(float pixels, float speed, boolean reverse) {
        return pixels * speed * (reverse ? -1f : 1f);
    }
    public static boolean validFlingContext(boolean originalMouse, boolean originalAlternate,
                                            boolean currentMouse, boolean currentAlternate) {
        return originalMouse == currentMouse && originalAlternate == currentAlternate;
    }
    public static int boundedRows(int rows, int limit) {
        return Math.max(-limit, Math.min(limit, rows));
    }
}
