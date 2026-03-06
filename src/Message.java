import java.io.*;
import java.nio.*;

/**
 * Represents a protocol message.
 * Format: [4-byte length][1-byte type][variable payload]
 * Length field = type byte + payload length (does NOT include itself)
 */
public class Message {

    // Message type constants
    public static final byte CHOKE         = 0;
    public static final byte UNCHOKE       = 1;
    public static final byte INTERESTED    = 2;
    public static final byte NOT_INTERESTED= 3;
    public static final byte HAVE          = 4;
    public static final byte BITFIELD      = 5;
    public static final byte REQUEST       = 6;
    public static final byte PIECE         = 7;

    public byte type;
    public byte[] payload; // may be null for choke/unchoke/interested/not-interested

    public Message(byte type, byte[] payload) {
        this.type    = type;
        this.payload = payload;
    }

    // ── Factory methods ──────────────────────────────────────────────────────

    public static Message choke()        { return new Message(CHOKE, null); }
    public static Message unchoke()      { return new Message(UNCHOKE, null); }
    public static Message interested()   { return new Message(INTERESTED, null); }
    public static Message notInterested(){ return new Message(NOT_INTERESTED, null); }

    public static Message have(int pieceIndex) {
        byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(HAVE, payload);
    }

    public static Message bitfield(byte[] bitfieldBytes) {
        return new Message(BITFIELD, bitfieldBytes);
    }

    public static Message request(int pieceIndex) {
        byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
        return new Message(REQUEST, payload);
    }

    public static Message piece(int pieceIndex, byte[] data) {
        byte[] payload = new byte[4 + data.length];
        ByteBuffer.wrap(payload).putInt(pieceIndex);
        System.arraycopy(data, 0, payload, 4, data.length);
        return new Message(PIECE, payload);
    }

    // ── Helpers to extract payload fields ───────────────────────────────────

    /** For HAVE and REQUEST messages */
    public int getPieceIndex() {
        return ByteBuffer.wrap(payload, 0, 4).getInt();
    }

    /** For PIECE messages — returns just the piece data (without the index prefix) */
    public byte[] getPieceData() {
        byte[] data = new byte[payload.length - 4];
        System.arraycopy(payload, 4, data, 0, data.length);
        return data;
    }

    // ── Serialization ────────────────────────────────────────────────────────

    /** Write this message to an output stream */
    public void send(DataOutputStream out) throws IOException {
        int payloadLen = (payload == null) ? 0 : payload.length;
        int msgLen     = 1 + payloadLen; // type byte + payload
        out.writeInt(msgLen);
        out.writeByte(type);
        if (payload != null) out.write(payload);
        out.flush();
    }

    /** Read one message from an input stream */
    public static Message receive(DataInputStream in) throws IOException {
        int msgLen = in.readInt();
        byte type  = in.readByte();
        byte[] payload = null;
        if (msgLen > 1) {
            payload = new byte[msgLen - 1];
            in.readFully(payload);
        }
        return new Message(type, payload);
    }

    // ── Handshake (separate from actual messages) ────────────────────────────

    public static final String HANDSHAKE_HEADER = "P2PFILESHARINGPROJ";

    public static void sendHandshake(DataOutputStream out, int peerID) throws IOException {
        out.write(HANDSHAKE_HEADER.getBytes("UTF-8")); // 18 bytes
        out.write(new byte[10]);                        // 10 zero bytes
        out.writeInt(peerID);                           // 4 bytes
        out.flush();
    }

    /** Returns peer ID extracted from handshake, or -1 if header is wrong */
    public static int receiveHandshake(DataInputStream in) throws IOException {
        byte[] header = new byte[18];
        in.readFully(header);
        in.readFully(new byte[10]); // skip zero bits
        int peerID = in.readInt();

        String receivedHeader = new String(header, "UTF-8");
        if (!receivedHeader.equals(HANDSHAKE_HEADER)) return -1;
        return peerID;
    }
}
