package com.trilead.ssh2;

import com.trilead.ssh2.packets.TypesWriter;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded read-ahead on an exclusively owned SFTP channel. Never share this client concurrently.
 * Kept beside AtomicSftpRename because sshlib exposes no public asynchronous read API.
 * Each batch is ordered before writing, including servers that reply out of order or read short.
 */
public final class PipelinedSftpDownload {
    public static final int BLOCK_SIZE = 32768;
    public static final int WINDOW = 16;
    private PipelinedSftpDownload() {}

    public interface Progress { void transferred(long bytes) throws IOException; }

    public static void copy(SFTPv3Client client, SFTPv3FileHandle handle, long size,
                            long start, OutputStream destination, Progress progress) throws IOException {
        if (start < 0 || size < start) throw new IllegalArgumentException("Invalid download range");
        if (handle.client != client || handle.isClosed) throw new IOException("Invalid SFTP handle");
        DataInputStream input = new DataInputStream(client.is);
        DataOutputStream output = new DataOutputStream(client.os);
        long offset = start;
        while (offset < size) {
            progress.transferred(offset); // Also lets callers check cancellation before issuing reads.
            Map<Integer, Block> pending = new LinkedHashMap<>();
            java.util.List<Block> batch = new java.util.ArrayList<>();
            for (int i = 0; i < WINDOW && offset < size; i++) {
                Block block = new Block(offset, (int) Math.min(BLOCK_SIZE, size - offset));
                batch.add(block);
                request(client, handle, output, pending, block);
                offset += block.bytes.length;
            }
            output.flush();
            while (!pending.isEmpty()) {
                int length = input.readInt();
                if (length < 5 || length > BLOCK_SIZE + 13) throw new IOException("Invalid SFTP packet size");
                byte[] packet = new byte[length];
                input.readFully(packet);
                DataInputStream reply = new DataInputStream(new ByteArrayInputStream(packet));
                int type = reply.readUnsignedByte();
                Block block = pending.remove(reply.readInt());
                if (block == null) throw new IOException("Unexpected SFTP request id");
                if (type == 101) {
                    if (reply.available() < 4) throw new IOException("Invalid SFTP status");
                    int code = reply.readInt();
                    throw new SFTPException(code == 1 ? "Remote file ended before completion" : "SFTP read failed", code);
                }
                if (type != 103 || reply.available() < 4) throw new IOException("Unexpected SFTP read response");
                int count = reply.readInt();
                int remaining = block.bytes.length - block.filled;
                if (count <= 0 || count > remaining || count != reply.available()) {
                    throw new IOException("Invalid SFTP data length");
                }
                reply.readFully(block.bytes, block.filled, count);
                block.filled += count;
                progress.transferred(batch.get(0).offset);
                if (block.filled < block.bytes.length) {
                    request(client, handle, output, pending, block);
                    output.flush();
                }
            }
            for (Block block : batch) {
                destination.write(block.bytes);
                progress.transferred(block.offset + block.bytes.length);
            }
        }
    }

    private static void request(SFTPv3Client client, SFTPv3FileHandle handle, DataOutputStream output,
                                Map<Integer, Block> pending, Block block) throws IOException {
        int id = client.next_request_id++;
        TypesWriter payload = new TypesWriter();
        payload.writeString(handle.fileHandle, 0, handle.fileHandle.length);
        payload.writeUINT64(block.offset + block.filled);
        payload.writeUINT32(block.bytes.length - block.filled);
        byte[] bytes = payload.getBytes();
        output.writeInt(bytes.length + 5);
        output.writeByte(5); // SSH_FXP_READ
        output.writeInt(id);
        output.write(bytes);
        pending.put(id, block);
    }

    private static final class Block {
        final long offset;
        final byte[] bytes;
        int filled;
        Block(long offset, int length) { this.offset = offset; bytes = new byte[length]; }
    }
}
