import java.io.*;
import java.text.*;
import java.util.*;

public class Logger {

    private static PrintWriter writer;
    private static int myPeerID;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void init(int peerID) throws IOException {
        myPeerID = peerID;
        writer = new PrintWriter(new FileWriter("log_peer_" + peerID + ".log", true));
    }

    public static synchronized void log(String message) {
        String timestamp = sdf.format(new Date());
        String line = "[" + timestamp + "]: " + message;
        System.out.println(line);
        writer.println(line);
        writer.flush();
    }

    // ── Named log helpers ────────────────────────────────────────────────────

    public static void logConnectedTo(int remotePeerID) {
        log("Peer " + myPeerID + " makes a connection to Peer " + remotePeerID + ".");
    }

    public static void logConnectedFrom(int remotePeerID) {
        log("Peer " + myPeerID + " is connected from Peer " + remotePeerID + ".");
    }

    public static void logPreferredNeighbors(List<Integer> neighborIDs) {
        log("Peer " + myPeerID + " has the preferred neighbors " + neighborIDs.toString().replaceAll("[\\[\\] ]", ""));
    }

    public static void logOptimisticNeighbor(int neighborID) {
        log("Peer " + myPeerID + " has the optimistically unchoked neighbor " + neighborID + ".");
    }

    public static void logUnchoked(int byPeerID) {
        log("Peer " + myPeerID + " is unchoked by " + byPeerID + ".");
    }

    public static void logChoked(int byPeerID) {
        log("Peer " + myPeerID + " is choked by " + byPeerID + ".");
    }

    public static void logHaveReceived(int fromPeerID, int pieceIndex) {
        log("Peer " + myPeerID + " received the 'have' message from " + fromPeerID + " for the piece " + pieceIndex + ".");
    }

    public static void logInterestedReceived(int fromPeerID) {
        log("Peer " + myPeerID + " received the 'interested' message from " + fromPeerID + ".");
    }

    public static void logNotInterestedReceived(int fromPeerID) {
        log("Peer " + myPeerID + " received the 'not interested' message from " + fromPeerID + ".");
    }

    public static void logPieceDownloaded(int fromPeerID, int pieceIndex, int totalPiecesNow) {
        log("Peer " + myPeerID + " has downloaded the piece " + pieceIndex +
            " from " + fromPeerID + ". Now the number of pieces it has is " + totalPiecesNow + ".");
    }

    public static void logFileComplete() {
        log("Peer " + myPeerID + " has downloaded the complete file.");
    }
}
