package com.trilead.ssh2;

import java.io.*;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AtomicSftpRenameTest {
    @Test public void sendsAdvertisedPosixRenameAndChecksResponse() throws Exception {
        SFTPv3Client client = mock(SFTPv3Client.class);
        client.server_extensions = new HashMap<>();
        client.server_extensions.put("posix-rename@openssh.com", new byte[]{49});
        client.charsetName = "UTF-8";
        client.next_request_id = 7;
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        client.os = sent;
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        DataOutputStream reply = new DataOutputStream(response);
        reply.writeInt(9); reply.writeByte(101); reply.writeInt(7); reply.writeInt(0);
        client.is = new ByteArrayInputStream(response.toByteArray());
        AtomicSftpRename.replace(client, "/temporary", "/note.md");
        DataInputStream request = new DataInputStream(new ByteArrayInputStream(sent.toByteArray()));
        assertEquals(sent.size() - 4, request.readInt());
        assertEquals(200, request.readUnsignedByte());
        assertEquals(7, request.readInt());
        assertEquals("posix-rename@openssh.com", readString(request));
        assertEquals("/temporary", readString(request));
        assertEquals("/note.md", readString(request));
        verify(client, never()).rm(anyString());
    }

    @Test public void unsupportedServerNeverDeletesExistingNote() throws Exception {
        SFTPv3Client client = mock(SFTPv3Client.class);
        client.server_extensions = new HashMap<>();
        assertThrows(IOException.class, () -> AtomicSftpRename.replace(client, "temp", "note"));
        verify(client, never()).rm(anyString());
        verify(client, never()).mv(anyString(), anyString());
    }
    private String readString(DataInputStream in) throws IOException {
        byte[] value = new byte[in.readInt()]; in.readFully(value); return new String(value, "UTF-8");
    }
}
