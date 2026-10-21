package com.trilead.ssh2;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PipelinedSftpDownloadTest {
    /** Wire fixture shared with the downloader's transport/cleanup integration test. */
    public static SFTPv3FileHandle install(SFTPv3Client client, byte[] payload) {
        Wire wire = new Wire(payload);
        client.os = wire.requests;
        client.is = wire;
        return new SFTPv3FileHandle(client, new byte[]{1});
    }

    @Test public void reversedResponsesPreserveEveryByteAcrossMultipleWindows() throws Exception {
        Fixture f = new Fixture(50 * 1024 * 1024 + 17);
        f.copy(0);
        assertArrayEquals(f.wire.payload, f.destination.toByteArray());
        assertEquals(16, f.wire.maxPending);
        // Deterministic latency model: 50 MiB needs 101 response rounds, not 1601.
        assertEquals(101, f.wire.rounds);
    }

    @Test public void shortResponsesRequestMissingTailsWithoutGapsOrDuplicates() throws Exception {
        Fixture f = new Fixture(32768 * 19 + 7);
        f.wire.shortReads = true;
        f.copy(0);
        assertArrayEquals(f.wire.payload, f.destination.toByteArray());
        assertTrue(f.wire.maxPending <= 16);
    }

    @Test public void resumeStartsAtRequestedOffset() throws Exception {
        Fixture f = new Fixture(32768 * 3 + 9);
        f.copy(12345);
        assertArrayEquals(Arrays.copyOfRange(f.wire.payload, 12345, f.wire.payload.length), f.destination.toByteArray());
    }

    @Test public void emptyFileDoesNotIssueReads() throws Exception {
        Fixture f = new Fixture(0);
        f.copy(0);
        assertEquals(0, f.wire.rounds);
        assertEquals(0, f.destination.size());
    }

    @Test public void cancellationStopsBeforeAnyRequests() {
        Fixture f = new Fixture(100);
        assertThrows(java.util.concurrent.CancellationException.class, () ->
            PipelinedSftpDownload.copy(f.client, f.handle, 100, 0, f.destination,
                n -> { throw new java.util.concurrent.CancellationException(); }));
        assertEquals(0, f.wire.requests.size());
    }

    @Test public void cancellationDuringBatchDoesNotWriteUnverifiedBatch() {
        Fixture f = new Fixture(32768 * 20);
        int[] checks = {0};
        assertThrows(java.util.concurrent.CancellationException.class, () ->
            PipelinedSftpDownload.copy(f.client, f.handle, f.wire.payload.length, 0, f.destination,
                n -> { if (++checks[0] == 3) throw new java.util.concurrent.CancellationException(); }));
        assertEquals(0, f.destination.size());
    }

    @Test public void prematureEofIsNotSuccessfulDownload() {
        Fixture f = new Fixture(10);
        assertThrows(SFTPException.class, () -> PipelinedSftpDownload.copy(
            f.client, f.handle, 20, 0, f.destination, n -> {}));
    }

    @Test public void rejectsUnknownIdZeroDataOversizedAndTruncatedPackets() {
        for (byte[] response : new byte[][]{
            packet(103, 9999, new byte[]{1}),
            packet(103, 0, new byte[0]),
            new byte[]{0, 1, 0, 0},
            new byte[]{0, 0, 0, 10, 103}
        }) {
            Fixture f = new Fixture(1);
            f.client.is = new ByteArrayInputStream(response);
            assertThrows(IOException.class, () -> f.copy(0));
            assertEquals(0, f.destination.size());
        }
    }

    @Test public void rejectsClosedHandleAndInvalidRanges() {
        Fixture f = new Fixture(1);
        assertThrows(IllegalArgumentException.class, () -> f.copy(2));
        f.handle.isClosed = true;
        assertThrows(IOException.class, () -> f.copy(0));
    }

    @Test public void outputFailurePropagates() {
        Fixture f = new Fixture(10);
        assertThrows(IOException.class, () -> PipelinedSftpDownload.copy(f.client, f.handle, 10, 0,
            new OutputStream() { public void write(int value) throws IOException { throw new IOException("disk full"); } }, n -> {}));
    }

    private static final class Fixture {
        final SFTPv3Client client = mock(SFTPv3Client.class);
        final Wire wire;
        final SFTPv3FileHandle handle;
        final ByteArrayOutputStream destination = new ByteArrayOutputStream();
        Fixture(int size) {
            byte[] payload = new byte[size]; new Random(71).nextBytes(payload);
            wire = new Wire(payload); client.os = wire.requests; client.is = wire;
            handle = new SFTPv3FileHandle(client, new byte[]{1});
        }
        void copy(long start) throws IOException {
            PipelinedSftpDownload.copy(client, handle, wire.payload.length, start, destination, n -> {});
        }
    }

    /** Replies only when the client starts reading, so maxPending proves genuine read-ahead. */
    private static final class Wire extends InputStream {
        final byte[] payload;
        final ByteArrayOutputStream requests = new ByteArrayOutputStream();
        ByteArrayInputStream replies = new ByteArrayInputStream(new byte[0]);
        int maxPending, rounds;
        boolean shortReads;
        Wire(byte[] payload) { this.payload = payload; }
        @Override public int read() throws IOException {
            if (replies.available() == 0) respond();
            return replies.read();
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (replies.available() == 0) respond();
            return replies.read(b, off, len);
        }
        private void respond() throws IOException {
            if (requests.size() == 0) return;
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(requests.toByteArray()));
            requests.reset();
            List<byte[]> responses = new ArrayList<>();
            while (input.available() > 0) {
                int length = input.readInt(); assertTrue(length > 5);
                assertEquals(5, input.readUnsignedByte());
                int id = input.readInt();
                int handleLength = input.readInt(); input.skipBytes(handleLength);
                long offset = input.readLong(); int count = input.readInt();
                assertTrue(count > 0 && count <= 32768);
                if (offset >= payload.length || offset + count > payload.length) {
                    responses.add(packet(101, id, null));
                } else {
                    if (shortReads && count > 1) count = Math.max(1, count / 2);
                    responses.add(packet(103, id, Arrays.copyOfRange(payload, (int) offset, (int) offset + count)));
                }
            }
            maxPending = Math.max(maxPending, responses.size()); rounds++;
            Collections.reverse(responses);
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            for (byte[] response : responses) joined.write(response);
            replies = new ByteArrayInputStream(joined.toByteArray());
        }
    }

    private static byte[] packet(int type, int id, byte[] data) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(type == 103 ? 9 + data.length : 9);
            out.writeByte(type); out.writeInt(id);
            out.writeInt(type == 103 ? data.length : 1);
            if (data != null) out.write(data);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
}
