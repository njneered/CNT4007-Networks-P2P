import java.io.*;
import java.nio.file.*;

/**
 * Handles reading and writing file pieces to/from disk.
 * Each peer stores files in peer_[peerID]/ directory.
 * TODO: Implement readPiece and writePiece.
 */
public class FileManager {

    private final String dir;       // e.g. "peer_1001/"
    private final String fileName;
    private final long fileSize;
    private final int pieceSize;
    private final int totalPieces;

    public FileManager(int peerID, CommonConfig cfg) {
        this.dir         = "peer_" + peerID + "/";
        this.fileName    = cfg.fileName;
        this.fileSize    = cfg.fileSize;
        this.pieceSize   = cfg.pieceSize;
        this.totalPieces = cfg.totalPieces;
    }

    /**
     * Read a piece from disk.
     * @param pieceIndex the piece to read
     * @return byte array of piece data
     */
    public byte[] readPiece(int pieceIndex) throws IOException {
        // TODO: implement
        // Hint: open the file with RandomAccessFile, seek to pieceIndex * pieceSize, read pieceSize bytes
        // (last piece may be smaller)
        throw new UnsupportedOperationException("readPiece not yet implemented");
    }

    /**
     * Write a received piece to disk.
     * @param pieceIndex the piece index
     * @param data       the piece bytes
     */
    public void writePiece(int pieceIndex, byte[] data) throws IOException {
        // TODO: implement
        // Hint: use RandomAccessFile to write at offset pieceIndex * pieceSize
        throw new UnsupportedOperationException("writePiece not yet implemented");
    }

    /** Returns the size in bytes of a given piece (last piece may differ) */
    public int getPieceSize(int pieceIndex) {
        if (pieceIndex == totalPieces - 1) {
            int remainder = (int)(fileSize % pieceSize);
            return remainder == 0 ? pieceSize : remainder;
        }
        return pieceSize;
    }
}
