/**
 * Tracks which pieces a peer has.
 * Internally stored as a boolean array for simplicity;
 * serialized to/from a byte array for bitfield messages.
 */
public class Bitfield {

    private boolean[] pieces;
    private int totalPieces;

    public Bitfield(int totalPieces, boolean initAll) {
        this.totalPieces = totalPieces;
        this.pieces = new boolean[totalPieces];
        if (initAll) {
            for (int i = 0; i < totalPieces; i++) pieces[i] = true;
        }
    }

    /** Construct from a raw bitfield byte array (from a bitfield message) */
    public Bitfield(int totalPieces, byte[] bytes) {
        this.totalPieces = totalPieces;
        this.pieces = new boolean[totalPieces];
        for (int i = 0; i < totalPieces; i++) {
            int byteIndex = i / 8;
            int bitIndex  = 7 - (i % 8); // high bit = piece 0
            pieces[i] = ((bytes[byteIndex] >> bitIndex) & 1) == 1;
        }
    }

    /** Serialize to byte array for sending in a bitfield message */
    public byte[] toBytes() {
        int numBytes = (int) Math.ceil(totalPieces / 8.0);
        byte[] bytes = new byte[numBytes];
        for (int i = 0; i < totalPieces; i++) {
            if (pieces[i]) {
                int byteIndex = i / 8;
                int bitIndex  = 7 - (i % 8);
                bytes[byteIndex] |= (1 << bitIndex);
            }
        }
        return bytes;
    }

    public boolean hasPiece(int index)  { return pieces[index]; }
    public void    setPiece(int index)  { pieces[index] = true; }
    public int     getTotalPieces()     { return totalPieces; }

    /** Returns true if this peer has all pieces */
    public boolean isComplete() {
        for (boolean p : pieces) if (!p) return false;
        return true;
    }

    /** Returns count of pieces owned */
    public int countPieces() {
        int count = 0;
        for (boolean p : pieces) if (p) count++;
        return count;
    }

    /**
     * Returns a list of piece indices that 'other' has but we don't.
     * Used for determining interesting pieces.
     */
    public java.util.List<Integer> getInterestingPieces(Bitfield other) {
        java.util.List<Integer> interesting = new java.util.ArrayList<>();
        for (int i = 0; i < totalPieces; i++) {
            if (!pieces[i] && other.pieces[i]) interesting.add(i);
        }
        return interesting;
    }
}
