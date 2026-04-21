import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

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

    // Choke/unchoke state
    private final Set<Integer> preferredNeighbors = ConcurrentHashMap.newKeySet();
    private volatile int optimisticNeighbor = -1;

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

    /** Read a piece from disk; returns null on error */
    public byte[] readPiece(int pieceIndex) {
        try {
            return fileMgr.readPiece(pieceIndex);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Write a piece to disk */
    public void writePiece(int pieceIndex, byte[] data) {
        try {
            fileMgr.writePiece(pieceIndex, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Broadcast a 'have' message to all connected neighbors */
    public void broadcastHave(int pieceIndex) {
        Message haveMsg = Message.have(pieceIndex);
        for (PeerConnection conn : connections.values()) {
            try {
                conn.send(haveMsg);
            } catch (IOException ignored) {}
        }
    }

    /**
     * After downloading a piece, send 'not interested' to any neighbor
     * that no longer has pieces we need.
     */
    public void checkAndUpdateInterest() {
        for (Map.Entry<Integer, PeerConnection> entry : connections.entrySet()) {
            Bitfield neighborBf = neighborBitfields.get(entry.getKey());
            if (neighborBf == null) continue;
            if (myBitfield.getInterestingPieces(neighborBf).isEmpty()) {
                try {
                    entry.getValue().send(Message.notInterested());
                } catch (IOException ignored) {}
            }
        }
    }

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

    private synchronized void recalculatePreferredNeighbors() {
        int k = cfg.numberOfPreferredNeighbors;

        // Collect all interested neighbors
        List<PeerConnection> interested = connections.values().stream()
            .filter(PeerConnection::isRemoteInterested)
            .collect(Collectors.toList());

        // Shuffle first so ties are broken randomly
        Collections.shuffle(interested);

        if (!myBitfield.isComplete()) {
            // Sort descending by download rate (shuffle above handles tie-breaking)
            interested.sort((a, b) -> Long.compare(b.getBytesDownloaded(), a.getBytesDownloaded()));
        }
        // If we have the complete file, the shuffled order IS the random selection

        List<PeerConnection> newPreferred = interested.subList(0, Math.min(k, interested.size()));
        Set<Integer> newPreferredIDs = newPreferred.stream()
            .map(PeerConnection::getRemotePeerID)
            .collect(Collectors.toSet());

        // Choke peers that were preferred but no longer are (skip optimistic neighbor)
        for (Integer peerID : new HashSet<>(preferredNeighbors)) {
            if (!newPreferredIDs.contains(peerID) && peerID != optimisticNeighbor) {
                PeerConnection conn = connections.get(peerID);
                if (conn != null) {
                    try { conn.sendChoke(); } catch (IOException ignored) {}
                }
            }
        }

        // Unchoke new preferred neighbors
        for (PeerConnection conn : newPreferred) {
            try { conn.sendUnchoke(); } catch (IOException ignored) {}
        }

        // Reset download counters for next interval
        connections.values().forEach(PeerConnection::resetDownloadCounter);

        preferredNeighbors.clear();
        preferredNeighbors.addAll(newPreferredIDs);

        Logger.logPreferredNeighbors(new ArrayList<>(newPreferredIDs));
    }

    private synchronized void recalculateOptimisticNeighbor() {
        // Candidates: choked + interested
        List<PeerConnection> candidates = connections.values().stream()
            .filter(c -> c.isRemoteInterested() && c.isWeChokingRemote())
            .collect(Collectors.toList());

        if (candidates.isEmpty()) return;

        // Choke the previous optimistic neighbor if it's not a preferred neighbor
        if (optimisticNeighbor != -1 && !preferredNeighbors.contains(optimisticNeighbor)) {
            PeerConnection prev = connections.get(optimisticNeighbor);
            if (prev != null) {
                try { prev.sendChoke(); } catch (IOException ignored) {}
            }
        }

        // Pick a random candidate
        PeerConnection chosen = candidates.get(new Random().nextInt(candidates.size()));
        optimisticNeighbor = chosen.getRemotePeerID();
        try { chosen.sendUnchoke(); } catch (IOException ignored) {}

        Logger.logOptimisticNeighbor(optimisticNeighbor);
    }
}
