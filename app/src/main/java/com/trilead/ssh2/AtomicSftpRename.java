package com.trilead.ssh2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import com.trilead.ssh2.packets.TypesWriter;

/** Small adapter for sshlib's advertised OpenSSH extension, absent from its public API.
 * Used only on an exclusively owned auxiliary client; never remove the old destination.
 */
public final class AtomicSftpRename {
    private AtomicSftpRename() {}
    public static final class UnsupportedServerException extends IOException {
        public UnsupportedServerException() { super("Server does not support atomic note replacement"); }
    }

    public static void replace(SFTPv3Client client, String from, String to) throws IOException {
        String extension = "posix-rename@openssh.com";
        if (!client.server_extensions.containsKey(extension)) {
            throw new UnsupportedServerException();
        }
        int id = client.next_request_id++;
        TypesWriter payload = new TypesWriter();
        payload.writeString(extension);
        payload.writeString(from, client.charsetName);
        payload.writeString(to, client.charsetName);
        byte[] bytes = payload.getBytes();
        DataOutputStream output = new DataOutputStream(client.os);
        output.writeInt(bytes.length + 5);
        output.writeByte(200); // SSH_FXP_EXTENDED
        output.writeInt(id);
        output.write(bytes);
        output.flush();
        DataInputStream input = new DataInputStream(client.is);
        int length = input.readInt();
        if (length < 9 || length > 65536) throw new IOException("Invalid SFTP response");
        byte[] response = new byte[length];
        input.readFully(response);
        DataInputStream status = new DataInputStream(new java.io.ByteArrayInputStream(response));
        if (status.readUnsignedByte() != 101 || status.readInt() != id) {
            throw new IOException("Unexpected SFTP response");
        }
        int code = status.readInt();
        if (code != 0) throw new SFTPException("Atomic note replacement failed", code);
    }
}
