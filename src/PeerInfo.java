import java.io.*;
import java.util.*;

public class PeerInfo {
    public int peerID;
    public String hostname;
    public int port;
    public boolean hasFile;

    public PeerInfo(int peerID, String hostname, int port, boolean hasFile) {
        this.peerID   = peerID;
        this.hostname = hostname;
        this.port     = port;
        this.hasFile  = hasFile;
    }

    public static List<PeerInfo> loadAll(String path) throws IOException {
        List<PeerInfo> peers = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            int id        = Integer.parseInt(parts[0]);
            String host   = parts[1];
            int port      = Integer.parseInt(parts[2]);
            boolean hasFile = parts[3].equals("1");
            peers.add(new PeerInfo(id, host, port, hasFile));
        }
        br.close();
        return peers;
    }

    @Override
    public String toString() {
        return "PeerInfo{id=" + peerID + ", host=" + hostname + ", port=" + port + ", hasFile=" + hasFile + "}";
    }
}
