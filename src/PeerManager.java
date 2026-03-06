import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages all peer connections and runs the choke/unchoke logic.
 * TODO: Implement choke/unchoke timers and preferred neighbor selection.
 */
public class PeerManager {

    private final int myPeerID;
    private final CommonConfig cfg;
    private final List<PeerInfo> peerList;
    private final FileManager fileMgr;

    // Map from peerID -> PeerConnection (one per neighbor)
    private final Map<Integer, PeerConnection> connections = new ConcurrentHashMap<>();

    // Our own bitfield
    public final Bitfield myBitfield;

    // Bitfields of our neighbors (updated as we receive have/bitfield messages)
    private final Map<Integer, Bitfield> neighborBitfields = new ConcurrentHashMap<>();

    // Track which peers have the complete file
    private final Set<Integer> completedPeers = ConcurrentHashMap.newKeySet();

    public PeerManager(int myPeerID, CommonConfig cfg, List<PeerInfo> peerList, FileManager fileMgr) {
        this.myPeerID = myPeerID;
        this.cfg      = cfg;
        this.peerList = peerList;
        this.fileMgr = fileMgr;

        // Init our bitfield based on whether we start with the file
        boolean hasFile = peerList.stream()
            .filter(p -> p.peerID == myPeerID)
            .findFirst().map(p -> p.hasFile).orElse(false);

        myBitfield = new Bitfield(cfg.totalPieces, hasFile);
        if (hasFile) completedPeers.add(myPeerID);

        startUnchokeTimer();
        startOptimisticUnchokeTimer();
    }

    /** Outgoing connection — we initiate */
    public void connectToPeer(PeerInfo peer) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(peer.hostname, peer.port);
                PeerConnection conn = new PeerConnection(myPeerID, peer.peerID, socket, this, false);
                connections.put(peer.peerID, conn);
                conn.start();
                Logger.logConnectedTo(peer.peerID);
            } catch (IOException e) {
                System.err.println("Failed to connect to peer " + peer.peerID + ": " + e.getMessage());
            }
        }).start();
    }

    /** Incoming connection — they initiated */
    public void handleIncomingConnection(Socket socket) {
        new Thread(() -> {
            try {
                // We don't know the remote peer ID yet — PeerConnection will get it from handshake
                PeerConnection conn = new PeerConnection(myPeerID, -1, socket, this, true);
                conn.start();
                // After handshake, conn will register itself via registerConnection()
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /** Called by PeerConnection after handshake completes (for incoming connections) */
    public void registerConnection(int remotePeerID, PeerConnection conn) {
        connections.put(remotePeerID, conn);
        Logger.logConnectedFrom(remotePeerID);
    }

    /** Called when we receive a neighbor's bitfield */
    public void updateNeighborBitfield(int remotePeerID, Bitfield bf) {
        neighborBitfields.put(remotePeerID, bf);
    }

    /** Called when a neighbor sends a 'have' message */
    public void updateNeighborHasPiece(int remotePeerID, int pieceIndex) {
        neighborBitfields.computeIfAbsent(remotePeerID, id -> new Bitfield(cfg.totalPieces, false))
                         .setPiece(pieceIndex);
    }

    /** Returns a random interesting piece from neighbor, or -1 if none */
    public synchronized int pickPieceToRequest(int remotePeerID) {
        Bitfield neighborBf = neighborBitfields.get(remotePeerID);
        if (neighborBf == null) return -1;
        List<Integer> interesting = myBitfield.getInterestingPieces(neighborBf);
        // TODO: also filter out pieces already requested from other neighbors
        if (interesting.isEmpty()) return -1;
        return interesting.get(new Random().nextInt(interesting.size()));
    }

    /** Check if all peers in the network have completed the file */
    public boolean allPeersComplete() {
        return completedPeers.size() == peerList.size();
    }

    public void markPeerComplete(int peerID) {
        completedPeers.add(peerID);
    }

    public CommonConfig getConfig() { return cfg; }

    // ── Choke/Unchoke Timers ─────────────────────────────────────────────────

    private void startUnchokeTimer() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::recalculatePreferredNeighbors,
            cfg.unchokingInterval, cfg.unchokingInterval, TimeUnit.SECONDS);
    }

    private void startOptimisticUnchokeTimer() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::recalculateOptimisticNeighbor,
            cfg.optimisticUnchokingInterval, cfg.optimisticUnchokingInterval, TimeUnit.SECONDS);
    }

    /** TODO: Implement preferred neighbor selection based on download rates */
    private synchronized void recalculatePreferredNeighbors() {
        // Steps:
        // 1. Get all neighbors that are interested in our data
        // 2. If we have the complete file: pick k randomly
        //    Else: pick k with highest download rate (break ties randomly)
        // 3. Send unchoke to newly preferred, choke to dropped ones
        // 4. Log the change
        System.out.println("[TODO] recalculatePreferredNeighbors");
    }

    /** TODO: Implement optimistic unchoke selection */
    private synchronized void recalculateOptimisticNeighbor() {
        // Steps:
        // 1. Get all neighbors that are choked but interested
        // 2. Pick one randomly
        // 3. Send unchoke to them
        // 4. Log the change
        System.out.println("[TODO] recalculateOptimisticNeighbor");
    }
}
