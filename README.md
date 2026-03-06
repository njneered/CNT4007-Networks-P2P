# P2P File Sharing (BitTorrent-like)

**CNT4007 – Computer Networks | University of Florida**

## Team Members
- [Name 1]
- [Name 2]
- [Name 3]

---

## Project Overview

A simplified BitTorrent implementation in Java supporting:
- TCP-based peer connections
- Handshake + bitfield exchange
- Piece request/download with random selection
- Choking/unchoking with preferred neighbor selection (rate-based)
- Optimistic unchoking
- Logging per protocol spec

## File Structure

```
src/
  peerProcess.java     ← Main entry point
  CommonConfig.java    ← Parses Common.cfg
  PeerInfo.java        ← Parses PeerInfo.cfg
  PeerManager.java     ← Manages all connections + choke/unchoke timers
  PeerConnection.java  ← One per neighbor: handles send/receive
  Message.java         ← Protocol message builder/parser + handshake
  Bitfield.java        ← Bitfield data structure
  FileManager.java     ← Disk I/O for pieces
  Logger.java          ← Log file writer
```

## Config Files (not included in repo)

Place these in your working directory before running:
- `Common.cfg`
- `PeerInfo.cfg`
- `peer_[peerID]/[FileName]` — for peers that start with the file

## How to Run

```bash
# Compile
javac src/*.java -d out/

# Start peers (in order listed in PeerInfo.cfg)
java -cp out peerProcess 1001
java -cp out peerProcess 1002
# ... etc
```

## Due Dates
- Midpoint check: **March 12, 11:59 PM** (500+ compiled lines)
- Final project: **April 22, 11:59 PM**

## TODO
- [ ] `FileManager.readPiece()` and `writePiece()`
- [ ] `PeerManager.recalculatePreferredNeighbors()` — rate-based selection
- [ ] `PeerManager.recalculateOptimisticNeighbor()`
- [ ] `PeerConnection` — handle REQUEST by actually sending the piece
- [ ] `PeerManager.broadcastHave()` — send have to all neighbors on piece download
- [ ] Termination — all peers detect when everyone has the file
