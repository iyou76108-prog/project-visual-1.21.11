# PROJECT VISUAL — Chess and global friends

## Current implementation

The `feature/chess-minigames` branch contains:

- A local chess board and a computer opponent using iterative-deepening alpha-beta search.
- Chess rules for legal moves, check/checkmate, stalemate, castling, en passant, promotion,
  undo, the 50-move rule, threefold repetition and insufficient material.
- A global Node.js service for PROJECT VISUAL accounts, friend requests, online presence,
  chess invitations and authoritative online matches.
- A Minecraft client connection using HTTP + WebSocket.
- An online board that restores the server's FEN position and submits UCI moves for validation.

The main branch has not been changed. The global service source is not yet deployed to a
public host, and the 3D set described below has not yet been bundled into the JAR.

## Local testing

Requirements:

- Minecraft Java 1.21.11, Fabric and Java 21 for the mod.
- Node.js 20 or newer for the global service.

Start the service on the computer that will host it:

```sh
cd server
npm install
# Set a private random secret. Do not commit it.
export PV_TOKEN_SECRET="$(openssl rand -hex 32)"
npm start
```

The default local URL is `http://localhost:8787`.

Open PROJECT VISUAL with Right Shift, choose **Friends**, and set the service address:

- On the same computer: `http://localhost:8787`
- On another computer on the same LAN: `http://<host-LAN-IP>:8787`

The host firewall must allow the service port. For friends connecting from different
networks, deploy the service on a public Node.js host and use an HTTPS URL. The client
will then connect over WSS automatically.

Each player creates a separate PROJECT VISUAL account in the Friends screen. These are
not Mojang/Microsoft accounts. Never enter a Microsoft password into PROJECT VISUAL.

## Production deployment

- Use HTTPS/WSS through a managed Node host or a reverse proxy.
- Set `PV_TOKEN_SECRET` to a stable random secret of at least 32 bytes.
- Mount persistent storage for `PV_DATA_FILE`.
- Keep the first deployment to one service instance: presence and active matches currently
  live in process memory.
- Add rate limiting, monitoring and backups before opening the service to the public.

## 3D chess set

Candidate source: https://grab3d.com/models/toys/chess-set/

The product page describes a 1.5 MB GLB chess set with a board and 32 separate pieces
under CC0 1.0. The actual GLB file has not been downloaded or packaged in this repository.
Minecraft does not load GLB natively; integration requires a model conversion/loader and
rendering work. The current playable board uses 2D chess glyphs.

## Verification

GitHub Actions runs:

- Gradle build on Java 21, including JUnit tests for chess rules.
- Node syntax check and an integration test covering account registration, friend requests,
  live presence, chess invites, match creation, a valid move and rejection of an invalid move.

A successful CI build does not replace launching the mod in Minecraft and testing with two
real players. Public matchmaking is not live until the service is deployed.
