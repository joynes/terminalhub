package com.trilead.ssh2;

import com.trilead.ssh2.channel.Channel;
import com.trilead.ssh2.channel.ChannelManager;
import com.trilead.ssh2.transport.ITransportConnection;
import com.trilead.ssh2.transport.MessageHandler;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.function.Function;

/** In-memory SSH channel for host-screen tests; production SSH/PTY code remains real. */
public final class FixtureSshSession extends Session {
    private final Object lock = new Object();
    private final ArrayDeque<Byte> output = new ArrayDeque<>();
    private final Function<String, String> execute;
    private final boolean interactive;
    private boolean closed;
    private boolean executed;
    public final StringBuilder typed = new StringBuilder();

    public FixtureSshSession(boolean interactive, Function<String, String> execute) throws IOException {
        super(new ChannelManager(new EmptyTransport()) {
            @Override public Channel openSessionChannel() { return null; }
        }, new SecureRandom());
        this.interactive = interactive;
        this.execute = execute;
    }
    private void feed(String text) {
        synchronized (lock) {
            if (closed) return;
            for (byte b : text.getBytes(StandardCharsets.UTF_8)) output.add(b);
            lock.notifyAll();
        }
    }
    @Override public void requestPTY(String term, int columns, int rows, int width, int height, byte[] modes) {}
    @Override public void resizePTY(int columns, int rows, int width, int height) {}
    @Override public void startShell() {
        Thread ready = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { return; }
            feed("Fixture terminal ready\r\n");
        }, "fixture-shell-ready");
        ready.setDaemon(true); ready.start();
    }
    @Override public void execCommand(String command) {
        feed(execute.apply(command));
        synchronized (lock) { executed = true; lock.notifyAll(); }
    }
    @Override public InputStream getStdout() {
        return new InputStream() {
            @Override public int available() { synchronized (lock) { return output.size(); } }
            @Override public int read() { synchronized (lock) { return output.isEmpty() ? -1 : output.remove() & 255; } }
            @Override public int read(byte[] bytes, int offset, int count) {
                synchronized (lock) {
                    if (output.isEmpty()) return -1;
                    int size = Math.min(count, output.size());
                    for (int i = 0; i < size; i++) bytes[offset + i] = output.remove();
                    return size;
                }
            }
        };
    }
    @Override public InputStream getStderr() { return new java.io.ByteArrayInputStream(new byte[0]); }
    @Override public OutputStream getStdin() {
        return new OutputStream() {
            @Override public void write(int value) {
                synchronized (lock) { typed.append((char) value); }
                if (value == '\n' || value == '\r') feed("fixture> ");
            }
            @Override public void write(byte[] bytes, int offset, int count) {
                for (int i = offset; i < offset + count; i++) write(bytes[i] & 255);
            }
        };
    }
    @Override public int waitForCondition(int condition, long timeout) {
        synchronized (lock) {
            while (interactive && output.isEmpty() && !closed) {
                try { lock.wait(timeout > 0 ? timeout : 1000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return ChannelCondition.EOF; }
                if (timeout > 0 && output.isEmpty() && !closed) return ChannelCondition.TIMEOUT;
            }
            int result = output.isEmpty() ? 0 : ChannelCondition.STDOUT_DATA;
            if (closed || (!interactive && executed)) result |= ChannelCondition.EOF | ChannelCondition.CLOSED | ChannelCondition.EXIT_STATUS;
            return result;
        }
    }
    @Override public Integer getExitStatus() { synchronized (lock) { return closed || executed ? 0 : null; } }
    @Override public void close() { synchronized (lock) { closed = true; lock.notifyAll(); } }

    private static class EmptyTransport implements ITransportConnection {
        @Override public void registerMessageHandler(MessageHandler handler, int low, int high) {}
        @Override public void sendMessage(byte[] message) {}
        @Override public void sendAsynchronousMessage(byte[] message) {}
        @Override public int getPacketOverheadEstimate() { return 0; }
        @Override public ConnectionInfo getConnectionInfo(int exchange) { return null; }
        @Override public byte[] getSessionIdentifier() { return new byte[0]; }
        @Override public String getHostname() { return "fixture.invalid"; }
        @Override public int getPort() { return 22; }
        @Override public ServerHostKeyVerifier getServerHostKeyVerifier() { return null; }
    }
}
