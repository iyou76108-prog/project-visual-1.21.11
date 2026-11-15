# PROJECT VISUAL Global Service

This is the separate online service for global PROJECT VISUAL accounts, friend lists,
presence, chess invitations and server-authoritative chess matches. It is independent
of the Minecraft server: two players can be connected to different Minecraft servers
(or single-player worlds) and still play each other.

## Run locally

Requirements: Node.js 20 or newer.

```sh
cd server
npm install
# Set a private random value of 32+ bytes for PV_TOKEN_SECRET.
export PV_TOKEN_SECRET="$(openssl rand -hex 32)"
npm start
```

Health check: `GET http://localhost:8787/health`.

## Deployment requirements

- Put the service behind HTTPS/WSS (for example, a managed Node host or reverse proxy).
- Set `PV_TOKEN_SECRET` to a stable secret of at least 32 bytes. Do not commit it.
- Mount persistent storage and set `PV_DATA_FILE` to a file on that volume.
- Run a single service instance for this first version. Live sockets and match objects are
  held in process memory; horizontal scaling needs shared presence/match storage.
- Back up the data volume and add rate limiting at the reverse proxy before public launch.

## Account model

PROJECT VISUAL accounts are separate service accounts. Players register a PV username and
password in the mod; the service does not claim to authenticate a Minecraft/Microsoft
account. Do not use your Microsoft password here. Use a unique password for PROJECT VISUAL.

## API

All protected routes use `Authorization: Bearer <token>`.

| Method | Route | Purpose |
| --- | --- | --- |
| POST | `/api/register` | Create a PROJECT VISUAL account |
| POST | `/api/login` | Sign in |
| GET | `/api/me` | Current account |
| GET | `/api/friends` | Friends and online state |
| POST | `/api/friends/request` | Send a friend request by PV username |
| GET | `/api/friend-requests` | Incoming and outgoing requests |
| POST | `/api/friend-requests/:id/accept` | Accept a request |
| POST | `/api/friend-requests/:id/decline` | Decline a request |
| GET | `/api/invites` | Pending chess invitations |
| POST | `/api/invites` | Invite an accepted friend |
| POST | `/api/invites/:id/accept` | Accept and start a match |
| POST | `/api/invites/:id/decline` | Decline an invitation |
| GET | `/api/matches/:id` | Read current match state |

WebSocket: `wss://HOST/ws?token=<token>`. The token is required. Avoid logging request
URLs because they contain the short-lived session credential.

Client-to-server messages:

```json
{ "type": "move", "matchId": "match-id", "uci": "e2e4" }
{ "type": "resign", "matchId": "match-id" }
```

The server validates every online move with chess.js and broadcasts FEN, PGN, side to move,
check, checkmate and draw state. The server, not either player's client, is authoritative
for online chess rules.

WebSocket server events include `connected`, `presence`, `friend_request`,
`friends_changed`, `chess_invite`, `match_started`, `match_state`,
`match_resigned` and `error`.

## Current boundary

The service source is included, but it is not publicly deployed yet. The Minecraft mod still
needs its account/friends screen and HTTPS/WebSocket client to connect to the deployed URL.
Do not advertise global matchmaking as live until both deployment and that client integration
are complete.
