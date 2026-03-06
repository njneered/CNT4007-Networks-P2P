# P2P File Sharing (BitTorrent-like)

**CNT4007 – Computer Networks | University of Florida**


## Project Overview

A simplified BitTorrent implementation in Java supporting:
- TCP-based peer connections
- Handshake + bitfield exchange
- Piece request/download with random selection
- Choking/unchoking with preferred neighbor selection (rate-based)
- Optimistic unchoking
- Logging per protocol spec

---

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

---

## Team Split

### NJ — Foundation & Startup
- `FileManager.java` — reading and writing pieces to disk
- `peerProcess.java` — startup flow, config loading, peer folder init
- Making sure the program boots correctly before any messages are exchanged

### Message Handling
- `PeerConnection.java` — full message loop (request, piece, have, bitfield)
- `Message.java` — any updates to message building/parsing
- Broadcasting `have` messages to all neighbors after a piece is downloaded

### Choking/Unchoking
- `PeerManager.java` — preferred neighbor selection (rate-based)
- `PeerManager.java` — optimistic unchoking logic
- Termination logic — detecting when all peers have the complete file

---

## Progress

### Done 
- Project scaffold and file structure
- `CommonConfig.java` — parses `Common.cfg`
- `PeerInfo.java` — parses `PeerInfo.cfg`
- `Message.java` — all message types + handshake serialization
- `Bitfield.java` — bitfield data structure
- `Logger.java` — all required log events
- `FileManager.java` — `readPiece()`, `writePiece()`, folder creation
- `peerProcess.java` — startup flow with `FileManager` hooked in

### In Progress 
- `PeerConnection.java` — handle REQUEST by sending piece back
- `PeerManager.java` — preferred neighbor + optimistic unchoke timers

### Not Started 
- `PeerManager.broadcastHave()` — notify all neighbors on piece download
- Termination — all peers detect when everyone has the file
- End-to-end testing with real config files

---

## Config Files (not included in repo)

Place these in your working directory before running:
- `Common.cfg`
- `PeerInfo.cfg`
- `peer_[peerID]/[FileName]` — for peers that start with the file

---

## How to Run

```bash
# Compile
javac src/*.java -d out/

# Start peers in order listed in PeerInfo.cfg
java -cp out peerProcess 1001
java -cp out peerProcess 1002
# ... etc
```

---