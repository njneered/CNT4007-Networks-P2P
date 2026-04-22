import java.io.*;
import java.net.*;


public class PeerConnection extends Thread {

    private final int myPeerID;
    private int remotePeerID;
    private final Socket socket;
    private final PeerManager manager;
    private final boolean isIncoming;

    private DataInputStream in;
    private DataOutputStream out;

    // Download rate tracking (bytes received in current interval)
    private long bytesDownloadedThisInterval = 0;

    // Choke state
    private boolean amChokedByRemote   = true;  // remote is choking us
    private boolean weAreChokingRemote = true;  // we are choking remote
    private boolean remoteIsInterested = false; // remote is interested in our data

    public PeerConnection(int myPeerID, int remotePeerID, Socket socket, PeerManager manager, boolean isIncoming) {
        this.myPeerID      = myPeerID;
        this.remotePeerID  = remotePeerID;
        this.socket        = socket;
        this.manager       = manager;
        this.isIncoming    = isIncoming;
    }

    @Override
    public void run() {
        try {
            out = new DataOutputStream(socket.getOutputStream());
            in  = new DataInputStream(socket.getInputStream());

            if (!isIncoming) {
                // We initiated — send first, then receive
                Message.sendHandshake(out, myPeerID);
                remotePeerID = Message.receiveHandshake(in);
            } else {
                // They initiated — receive first, then send
                remotePeerID = Message.receiveHandshake(in);
                Message.sendHandshake(out, myPeerID);
                manager.registerConnection(remotePeerID, this);
            }

            if (remotePeerID == -1) {
                System.err.println("Handshake failed — invalid header");
                socket.close();
                return;
            }

            Bitfield myBf = manager.myBitfield;
            if (myBf.countPieces() > 0) {
                send(Message.bitfield(myBf.toBytes()));
            }

            while (true) {
                Message msg = Message.receive(in);
                handleMessage(msg);
            }

        } catch (EOFException | SocketException e) {
            System.out.println("Connection closed with peer " + remotePeerID);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(Message msg) throws IOException {
        switch (msg.type) {
            case Message.CHOKE:
                amChokedByRemote = true;
                Logger.logChoked(remotePeerID);
                break;

            case Message.UNCHOKE:
                amChokedByRemote = false;
                Logger.logUnchoked(remotePeerID);
                // Request a piece now that we're unchoked
                requestNextPiece();
                break;

            case Message.INTERESTED:
                remoteIsInterested = true;
                Logger.logInterestedReceived(remotePeerID);
                break;

            case Message.NOT_INTERESTED:
                remoteIsInterested = false;
                Logger.logNotInterestedReceived(remotePeerID);
                break;

            case Message.HAVE:
                int pieceIdx = msg.getPieceIndex();
                manager.updateNeighborHasPiece(remotePeerID, pieceIdx);
                Logger.logHaveReceived(remotePeerID, pieceIdx);
                // Send interested if we don't have this piece
                if (!manager.myBitfield.hasPiece(pieceIdx)) {
                    send(Message.interested());
                }
                break;

            case Message.BITFIELD:
                CommonConfig cfg = manager.getConfig();
                Bitfield neighborBf = new Bitfield(cfg.totalPieces, msg.payload);
                manager.updateNeighborBitfield(remotePeerID, neighborBf);
                // Decide interested or not
                if (!manager.myBitfield.getInterestingPieces(neighborBf).isEmpty()) {
                    send(Message.interested());
                } else {
                    send(Message.notInterested());
                }
                break;

            case Message.REQUEST:
                int requestedPiece = msg.getPieceIndex();
                if (!weAreChokingRemote) {
                    byte[] pieceData = manager.readPiece(requestedPiece);
                    if (pieceData != null) {
                        send(Message.piece(requestedPiece, pieceData));
                    }
                }
                break;

            case Message.PIECE:
                int idx     = msg.getPieceIndex();
                byte[] data = msg.getPieceData();
                bytesDownloadedThisInterval += data.length;
                manager.writePiece(idx, data);
                manager.pieceReceived(idx);
                manager.myBitfield.setPiece(idx);
                int count = manager.myBitfield.countPieces();
                Logger.logPieceDownloaded(remotePeerID, idx, count);
                manager.broadcastHave(idx);
                manager.checkAndUpdateInterest();
                if (manager.myBitfield.isComplete()) {
                    Logger.logFileComplete();
                    manager.markPeerComplete(myPeerID);
                }
                requestNextPiece();
                break;
        }
    }

    /** Send interested/request if unchoked and there are pieces to get */
    private void requestNextPiece() throws IOException {
        if (amChokedByRemote) return;
        int piece = manager.pickPieceToRequest(remotePeerID);
        if (piece != -1) {
            send(Message.request(piece));
        } else {
            send(Message.notInterested());
        }
    }

    public synchronized void send(Message msg) throws IOException {
        msg.send(out);
    }

    /** Called by PeerManager to choke this remote peer (sends choke message if state changes) */
    public synchronized void sendChoke() throws IOException {
        if (!weAreChokingRemote) {
            weAreChokingRemote = true;
            send(Message.choke());
        }
    }

    /** Called by PeerManager to unchoke this remote peer (sends unchoke message if state changes) */
    public synchronized void sendUnchoke() throws IOException {
        if (weAreChokingRemote) {
            weAreChokingRemote = false;
            send(Message.unchoke());
        }
    }

    public boolean isRemoteInterested()   { return remoteIsInterested; }
    public boolean isWeChokingRemote()    { return weAreChokingRemote; }
    public long getBytesDownloaded()      { return bytesDownloadedThisInterval; }
    public void resetDownloadCounter()    { bytesDownloadedThisInterval = 0; }
    public int  getRemotePeerID()         { return remotePeerID; }
}
