import http from "node:http";
import { createHash, randomBytes } from "node:crypto";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import Database from "better-sqlite3";
import { Chess } from "chess.js";
import { WebSocket, WebSocketServer } from "ws";

const PORT = Number(process.env.PORT || 8080);
const DB_PATH = resolve(process.env.PV_DB_PATH || "./data/project-visual.sqlite");
mkdirSync(dirname(DB_PATH), { recursive: true });
const db = new Database(DB_PATH);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  install_id TEXT NOT NULL UNIQUE,
  username TEXT NOT NULL,
  friend_code TEXT NOT NULL UNIQUE,
  token_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS friends (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK(status IN ('pending','accepted')),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(user_id, friend_id),
  CHECK(user_id <> friend_id)
);
CREATE TABLE IF NOT EXISTS games (
  id TEXT PRIMARY KEY,
  white_id INTEGER NOT NULL REFERENCES users(id),
  black_id INTEGER NOT NULL REFERENCES users(id),
  status TEXT NOT NULL,
  pgn TEXT NOT NULL DEFAULT '',
  fen TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
`);

const hash = value => createHash("sha256").update(value).digest("hex");
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
function code() {
  const bytes = randomBytes(8);
  let out = "";
  for (const b of bytes) out += ALPHABET[b % ALPHABET.length];
  return out;
}
function newToken() { return randomBytes(32).toString("base64url"); }
function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "content-length": Buffer.byteLength(data) });
  res.end(data);
}
async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 16_384) throw new Error("Request too large");
  }
  return raw ? JSON.parse(raw) : {};
}
function auth(req) {
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) return null;
  const token = header.slice(7);
  if (token.length < 32 || token.length > 128) return null;
  return db.prepare("SELECT id, username, friend_code FROM users WHERE token_hash = ?").get(hash(token)) || null;
}
function friendsOf(userId) {
  return db.prepare(`
    SELECT u.id, u.username, u.friend_code
    FROM friends f JOIN users u ON u.id = CASE WHEN f.user_id = ? THEN f.friend_id ELSE f.user_id END
    WHERE (f.user_id = ? OR f.friend_id = ?) AND f.status = 'accepted'
  `).all(userId, userId, userId);
}
const sockets = new Map();
const games = new Map();

function online(userId) {
  return [...(sockets.get(userId) || [])].some(ws => ws.readyState === WebSocket.OPEN);
}
function send(ws, message) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(message));
}
function sendUser(userId, message) {
  for (const ws of sockets.get(userId) || []) send(ws, message);
}
function broadcastFriends(userId, status) {
  for (const f of friendsOf(userId)) sendUser(f.id, { type: "presence", userId, status });
}
function friendLink(a, b) {
  return !!db.prepare(`
    SELECT 1 FROM friends WHERE status='accepted'
      AND ((user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?))
  `).get(a, b, b, a);
}
function gameFor(id) {
  const g = games.get(id);
  return g && g.status === "active" ? g : null;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://localhost");
  if (req.method === "GET" && url.pathname === "/health") return json(res, 200, { ok: true, service: "project-visual", protocol: 1 });

  try {
    if (req.method === "POST" && url.pathname === "/v1/register") {
      const body = await readJson(req);
      const installId = String(body.installId || "");
      const username = String(body.username || "").trim();
      if (!/^[0-9a-f-]{36}$/i.test(installId)) return json(res, 400, { error: "Invalid installation ID" });
      if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) return json(res, 400, { error: "Username must be 3–16 letters, digits or underscores" });
      const existing = db.prepare("SELECT id FROM users WHERE install_id=?").get(installId);
      if (existing) {
        const current = auth(req);
        if (!current || current.id !== existing.id) return json(res, 409, { error: "This installation is already registered. Keep its local account file." });
        db.prepare("UPDATE users SET username=? WHERE id=?").run(username, existing.id);
        const updated = db.prepare("SELECT id, username, friend_code FROM users WHERE id=?").get(existing.id);
        return json(res, 200, { user: updated });
      }
      const token = newToken();
      let friendCode;
      do { friendCode = code(); } while (db.prepare("SELECT 1 FROM users WHERE friend_code=?").get(friendCode));
      const result = db.prepare("INSERT INTO users(install_id, username, friend_code, token_hash) VALUES(?,?,?,?)")
        .run(installId, username, friendCode, hash(token));
      return json(res, 201, { user: { id: Number(result.lastInsertRowid), username, friend_code: friendCode }, token });
    }

    const user = auth(req);
    if (!user) return json(res, 401, { error: "Unauthorized" });

    if (req.method === "GET" && url.pathname === "/v1/me") {
      return json(res, 200, { user, friends: friendsOf(user.id).map(f => ({ ...f, online: online(f.id) })) });
    }

    if (req.method === "GET" && url.pathname === "/v1/friends") {
      const friends = friendsOf(user.id).map(f => ({ ...f, online: online(f.id) }));
      const requests = db.prepare(`
        SELECT f.id AS request_id, u.id, u.username, u.friend_code
        FROM friends f JOIN users u ON u.id=f.user_id
        WHERE f.friend_id=? AND f.status='pending'
        ORDER BY f.created_at
      `).all(user.id);
      return json(res, 200, { friends, incoming: requests });
    }

    if (req.method === "POST" && url.pathname === "/v1/friends/request") {
      const body = await readJson(req);
      const friendCode = String(body.friendCode || "").trim().toUpperCase();
      const target = db.prepare("SELECT id, username FROM users WHERE friend_code=?").get(friendCode);
      if (!target) return json(res, 404, { error: "Friend code not found" });
      if (target.id === user.id) return json(res, 400, { error: "You cannot add yourself" });
      if (friendLink(user.id, target.id)) return json(res, 409, { error: "Already friends" });
      const reverse = db.prepare("SELECT id,status FROM friends WHERE user_id=? AND friend_id=?").get(target.id, user.id);
      if (reverse?.status === "pending") {
        db.prepare("UPDATE friends SET status='accepted' WHERE id=?").run(reverse.id);
        sendUser(target.id, { type: "friend_accepted", userId: user.id, username: user.username });
        return json(res, 200, { accepted: true, friend: target });
      }
      db.prepare("INSERT OR IGNORE INTO friends(user_id,friend_id,status) VALUES(?,?,'pending')").run(user.id, target.id);
      sendUser(target.id, { type: "friend_request", userId: user.id, username: user.username });
      return json(res, 201, { requested: true, friend: target });
    }

    const acceptMatch = url.pathname.match(/^\/v1\/friends\/requests\/(\d+)\/accept$/);
    if (req.method === "POST" && acceptMatch) {
      const requestId = Number(acceptMatch[1]);
      const changed = db.prepare("UPDATE friends SET status='accepted' WHERE id=? AND friend_id=? AND status='pending'").run(requestId, user.id);
      if (!changed.changes) return json(res, 404, { error: "Friend request not found" });
      const request = db.prepare("SELECT user_id FROM friends WHERE id=?").get(requestId);
      const other = db.prepare("SELECT id,username,friend_code FROM users WHERE id=?").get(request.user_id);
      sendUser(other.id, { type: "friend_accepted", userId: user.id, username: user.username });
      return json(res, 200, { accepted: true, friend: other });
    }

    if (req.method === "GET" && url.pathname === "/v1/games") {
      const active = db.prepare("SELECT id,white_id,black_id,status,fen,updated_at FROM games WHERE (white_id=? OR black_id=?) AND status IN ('pending','active')").all(user.id,user.id);
      return json(res, 200, { games: active });
    }

    return json(res, 404, { error: "Not found" });
  } catch (error) {
    const status = error.message === "Request too large" ? 413 : 400;
    return json(res, status, { error: status === 413 ? error.message : "Invalid request" });
  }
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });
server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url || "/", "http://localhost");
  if (url.pathname !== "/ws") { socket.destroy(); return; }
  const user = auth(req);
  if (!user) { socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n"); socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws, req, user));
});

wss.on("connection", (ws, _req, user) => {
  if (!sockets.has(user.id)) sockets.set(user.id, new Set());
  sockets.get(user.id).add(ws);
  send(ws, { type: "hello", user, friends: friendsOf(user.id).map(f => ({ ...f, online: online(f.id) })) });
  broadcastFriends(user.id, "online");

  ws.on("message", raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { send(ws, { type: "error", error: "Invalid JSON" }); return; }
    if (!msg || typeof msg.type !== "string") return;

    if (msg.type === "challenge") {
      const targetId = Number(msg.friendId);
      if (!Number.isInteger(targetId) || !friendLink(user.id, targetId)) { send(ws, { type: "error", error: "You are not friends with this player" }); return; }
      if (!online(targetId)) { send(ws, { type: "error", error: "Friend is offline" }); return; }
      const id = randomBytes(12).toString("hex");
      const match = { id, whiteId: user.id, blackId: targetId, chess: new Chess(), status: "pending" };
      games.set(id, match);
      db.prepare("INSERT INTO games(id,white_id,black_id,status,fen) VALUES(?,?,?,'pending',?)").run(id,user.id,targetId,match.chess.fen());
      sendUser(targetId, { type: "challenge", gameId: id, from: { id: user.id, username: user.username } });
      send(ws, { type: "challenge_sent", gameId: id, to: targetId });
      return;
    }

    if (msg.type === "accept_challenge") {
      const match = games.get(String(msg.gameId || ""));
      if (!match || match.status !== "pending" || match.blackId !== user.id) { send(ws, { type: "error", error: "Challenge not found" }); return; }
      match.status = "active";
      db.prepare("UPDATE games SET status='active' WHERE id=?").run(match.id);
      const start = { type: "game_start", gameId: match.id, whiteId: match.whiteId, blackId: match.blackId, fen: match.chess.fen() };
      sendUser(match.whiteId, start); sendUser(match.blackId, start);
      return;
    }

    if (msg.type === "move") {
      const match = gameFor(String(msg.gameId || ""));
      if (!match || (user.id !== match.whiteId && user.id !== match.blackId)) { send(ws, { type: "error", error: "Game not found" }); return; }
      const expected = match.chess.turn() === "w" ? match.whiteId : match.blackId;
      if (user.id !== expected) { send(ws, { type: "error", error: "Not your turn" }); return; }
      const uci = String(msg.uci || "");
      if (!/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(uci)) { send(ws, { type: "error", error: "Invalid move format" }); return; }
      let played;
      try { played = match.chess.move({ from: uci.slice(0,2), to: uci.slice(2,4), ...(uci[4] ? { promotion: uci[4] } : {}) }); }
      catch { played = null; }
      if (!played) { send(ws, { type: "error", error: "Illegal chess move" }); return; }
      const over = match.chess.isGameOver();
      const state = {
        type: "move", gameId: match.id, uci, san: played.san, fen: match.chess.fen(),
        turn: match.chess.turn(), check: match.chess.isCheck(), checkmate: match.chess.isCheckmate(),
        stalemate: match.chess.isStalemate(), draw: match.chess.isDraw(), over
      };
      db.prepare("UPDATE games SET pgn=?,fen=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?")
        .run(match.chess.pgn(),match.chess.fen(),over?"finished":"active",match.id);
      sendUser(match.whiteId,state);sendUser(match.blackId,state);
      if (over) match.status = "finished";
      return;
    }

    if (msg.type === "resign") {
      const match = gameFor(String(msg.gameId || ""));
      if (!match || (user.id !== match.whiteId && user.id !== match.blackId)) { send(ws,{type:"error",error:"Game not found"});return; }
      match.status="finished";
      db.prepare("UPDATE games SET status='resigned',updated_at=CURRENT_TIMESTAMP WHERE id=?").run(match.id);
      const winnerId=user.id===match.whiteId?match.blackId:match.whiteId;
      const ended={type:"game_end",gameId:match.id,result:"resign",winnerId};
      sendUser(match.whiteId,ended);sendUser(match.blackId,ended);return;
    }
  });

  ws.on("close", () => {
    const set=sockets.get(user.id);if(set){set.delete(ws);if(set.size===0){sockets.delete(user.id);broadcastFriends(user.id,"offline");}}
  });
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) { ws.terminate(); continue; }
    ws.isAlive=false;ws.ping();
  }
}, 30_000);
wss.on("connection",ws=>{ws.isAlive=true;ws.on("pong",()=>{ws.isAlive=true;});});
server.listen(PORT,"0.0.0.0",()=>console.log(`PROJECT VISUAL service listening on :${PORT}`));
process.on("SIGTERM",()=>{clearInterval(heartbeat);for(const ws of wss.clients)ws.close(1001,"Server shutdown");server.close(()=>{db.close();process.exit(0);});});
