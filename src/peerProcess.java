import java.io.*;
import java.net.*;
import java.util.*;

public class peerProcess {

    public static int myPeerID;
    public static CommonConfig commonCfg;
    public static List<PeerInfo> peerList = new ArrayList<>();
    public static PeerManager peerManager;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java peerProcess <peerID>");
            System.exit(1);
        }

        myPeerID = Integer.parseInt(args[0]);

        // Load config files
        commonCfg = CommonConfig.load("Common.cfg");
        peerList = PeerInfo.loadAll("PeerInfo.cfg");

        // Find our own info
        PeerInfo myInfo = peerList.stream()
            .filter(p -> p.peerID == myPeerID)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Peer ID not found in PeerInfo.cfg"));

        // Init logger
        Logger.init(myPeerID);

        FileManager fileMgr = new FileManager(myPeerID, commonCfg);

        // Init peer manager (handles choking/unchoking timers, bitfields, etc.)
        peerManager = new PeerManager(myPeerID, commonCfg, peerList, fileMgr);

        // Connect to all peers that started before us (listed above us in PeerInfo.cfg)
        for (PeerInfo peer : peerList) {
            if (peer.peerID == myPeerID) break; // stop when we reach ourselves
            peerManager.connectToPeer(peer);
        }

        // Start listening for incoming connections
        ServerSocket serverSocket = new ServerSocket(myInfo.port);
        System.out.println("Peer " + myPeerID + " listening on port " + myInfo.port);

        while (true) {
            Socket incoming = serverSocket.accept();
            peerManager.handleIncomingConnection(incoming);

            // Check if all peers are done — if so, shut down
            if (peerManager.allPeersComplete()) {
                Logger.log("All peers have downloaded the complete file. Shutting down.");
                serverSocket.close();
                break;
            }
        }
    }
}
