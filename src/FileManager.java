import java.io.*;

public class FileManager{

    private String myFolder;
    private String filePath;
    private long totalFileSize;
    private int chunkSize;
    private int totalChunks;
    private RandomAccessFile raf;

    public FileManager(int peerID, CommonConfig cfg) throws IOException{
        myFolder = "peer_" + peerID + "/";
        filePath = myFolder + cfg.fileName;
        totalFileSize = cfg.fileSize;
        chunkSize = cfg.pieceSize;
        totalChunks = cfg.totalPieces;

        // making folder first if it does not exist
        new File(myFolder).mkdirs();

        // then open or create file and reserve the full space
        raf=new RandomAccessFile(filePath, "rw");
        if (raf.length() == 0){
            // reserve spots for all pieces
            raf.setLength(totalFileSize);
        }
    }

    public synchronized byte[] readPiece(int pieceIndex) throws IOException{
        int howManyBytes = getSizeOfThisChunk(pieceIndex);
        byte[] buf = new byte[howManyBytes];
        long jumpTo = (long) pieceIndex * chunkSize;

        raf.seek(jumpTo);
        raf.readFully(buf);
        return buf;
    }

    public synchronized void writePiece(int pieceIndex, byte[] data) throws IOException{
        long jumpTo = (long) pieceIndex * chunkSize;
        raf.seek(jumpTo);
        raf.write(data);
    }

    public int getSizeOfThisChunk(int pieceIndex){
        if (pieceIndex == totalChunks - 1){
            int leftover = (int)(totalFileSize % chunkSize);
            return leftover == 0 ? chunkSize : leftover;
        }

        return chunkSize;
    }

    public void closeUp() throws IOException{
        raf.close();
    }
}
