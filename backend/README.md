# PROJECT VISUAL global service

This service is independent from Minecraft servers. It hosts friend codes, friend requests, online presence, chess challenges and authoritative move validation.

## Run locally

Requires Node.js 22+ and a persistent writable data directory.

```sh
npm install
npm run check
npm start
```

The HTTP health check is `GET /health`. The WebSocket endpoint is `/ws` and requires an `Authorization: Bearer <token>` header. The service should be exposed through HTTPS/WSS by a reverse proxy when placed on the public internet.

## Docker

```sh
docker compose up --build -d
```

Keep the named `project-visual-data` volume: it stores account tokens, friend relations and game records. Back it up.

## HTTP API

- `POST /v1/register` with `{ "installId": "<UUID>", "username": "Player" }`; returns a private token once. Keep the local client account file. Existing installations must send the stored bearer token.
- `GET /v1/me` — profile and friend list.
- `GET /v1/friends` — accepted friends, online flags and incoming requests.
- `POST /v1/friends/request` with `{ "friendCode": "ABCDEFGH" }`.
- `POST /v1/friends/requests/:requestId/accept`.

## WebSocket messages

Client to service: `challenge`, `accept_challenge`, `move` (UCI), and `resign`.

Service to client: `hello`, `presence`, `friend_request`, `friend_accepted`, `challenge`, `game_start`, `move`, `game_end`, and `error`.

The service validates each move using chess.js; clients cannot change the board by sending an arbitrary FEN. Game state is independent of the Minecraft server each player is connected to.

## Important

The Minecraft mod currently has the local chess-vs-AI screen. The Java client connection, friend-management UI, and online chess screen still need to be wired to this service. The public service also needs to be deployed on a host with HTTPS/WSS and persistent storage before global friends can work for players.
