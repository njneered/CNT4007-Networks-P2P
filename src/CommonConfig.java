import java.io.*;
import java.util.*;

public class CommonConfig {
    public int numberOfPreferredNeighbors;
    public int unchokingInterval;          // in seconds
    public int optimisticUnchokingInterval; // in seconds
    public String fileName;
    public long fileSize;
    public int pieceSize;
    public int totalPieces;

    public static CommonConfig load(String path) throws IOException {
        CommonConfig cfg = new CommonConfig();
        Properties props = new Properties();
        props.load(new FileReader(path));

        cfg.numberOfPreferredNeighbors = Integer.parseInt(props.getProperty("NumberOfPreferredNeighbors").trim());
        cfg.unchokingInterval          = Integer.parseInt(props.getProperty("UnchokingInterval").trim());
        cfg.optimisticUnchokingInterval= Integer.parseInt(props.getProperty("OptimisticUnchokingInterval").trim());
        cfg.fileName                   = props.getProperty("FileName").trim();
        cfg.fileSize                   = Long.parseLong(props.getProperty("FileSize").trim());
        cfg.pieceSize                  = Integer.parseInt(props.getProperty("PieceSize").trim());

        // Calculate total number of pieces (last piece may be smaller)
        cfg.totalPieces = (int) Math.ceil((double) cfg.fileSize / cfg.pieceSize);

        return cfg;
    }
}
