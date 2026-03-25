package com.termux.terminal;

public interface TerminalInputListener {
    boolean onTerminalInput(byte[] data, int offset, int count);
}
