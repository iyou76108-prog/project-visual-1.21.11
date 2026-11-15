import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  createHmac, randomBytes, randomUUID, scryptSync, timingSafeEqual
} from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { Chess } from "chess.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const TOKEN_SECRET = process.env.PV_TOKEN_SECRET || "";
const DATA_FILE = process.env.PV_DATA_FILE || path.resolve(__dirname, "../data/project-visual.json");
const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_BODY = 16 * 1024;

if (Buffer.byteLength(TOKEN_SECRET) < 32) {
  throw new Error("Set PV_TOKEN_SECRET to a random secret of at least 32 bytes.");
}

fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });

function loadStore() {
  try {
    const parsed = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    return {
      users: Array.isArray(parsed.users) ? parsed.users : [],
      friendRequests: Array.isArray(parsed.friendRequests) ? parsed.friendRequests : [],
      invites: Array.isArray(parsed.invites) ? parsed.invites : []
    };
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    return { users: [], friendRequests: [], invites: [] };
  }
}

const store = loadStore();
const sockets = new Map();
const matches = new Map();

function persist() {
  const tmp = DATA_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(store, null, 2), { mode: 0o600 });
  fs.renameSync(tmp, DATA_FILE);
}

function reply(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
    "access-control-allow-headers": "authorization, content-type",
    "access-control-allow-methods": "GET, POST, OPTIONS"
  });
  res.end(body);
}

function publicUser(user) {
  return { id: user.id, username: user.username };
}

function findUser(id) {
  return store.users.find(user => user.id === id);
}

function findUserByName(name) {
  const key = String(name || "").trim().toLowerCase();
  return store.users.find(user => user.username.toLowerCase() === key);
}

function hashPassword(password, salt) {
  return scryptSync(password, salt, 64).toString("hex");
}

function issueToken(userId) {
  const payload = Buffer.from(JSON.stringify({
    sub: userId,
    exp: Date.now() + TOKEN_TTL_MS
  })).toString("base64url");
  const signature = createHmac("sha256", TOKEN_SECRET).update(payload).digest("base64url");
  return payload + "." + signature;
}

function verifyToken(token) {
  if (typeof token !== "string") return null;
  const parts = token.split(".");
  if (parts.length !== 2) return null;
  const expected = createHmac("sha256", TOKEN_SECRET).update(parts[0]).digest();
  let supplied;
  try {
    supplied = Buffer.from(parts[1], "base64url");
  } catch {
    return null;
  }
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[0], "base64url").toString("utf8"));
    if (!payload.sub || !Number.isFinite(payload.exp) || payload.exp < Date.now()) return null;
    return findUser(payload.sub) || null;
  } catch {
    return null;
  }
}

function bearer(req) {
  const value = req.headers.authorization || "";
  return value.startsWith("Bearer ") ? value.slice(7) : "";
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", chunk => {
      body += chunk;
      if (Buffer.byteLength(body) > MAX_BODY) {
        reject(Object.assign(new Error("Request body too large"), { status: 413 }));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!body) return resolve({});
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(Object.assign(new Error("Invalid JSON"), { status: 400 }));
      }
    });
    req.on("error", reject);
  });
}

function sendToUser(userId, payload) {
  const socket = sockets.get(userId);
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

function pushFriendLists(userIds) {
  for (const userId of userIds) {
    const socket = sockets.get(userId);
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: "friends_changed" }));
    }
  }
}

function friendsOf(user) {
  return (user.friends || []).map(findUser).filter(Boolean).map(friend => ({
    ...publicUser(friend),
    online: sockets.has(friend.id)
  }));
}

function matchPayload(match, userId) {
  return {
    type: "match_state",
    matchId: match.id,
    color: match.whiteId === userId ? "white" : "black",
    fen: match.game.fen(),
    pgn: match.game.pgn(),
    turn: match.game.turn() === "w" ? "white" : "black",
    check: match.game.isCheck(),
    checkmate: match.game.isCheckmate(),
    draw: match.game.isDraw(),
    gameOver: match.game.isGameOver()
  };
}

function broadcastMatch(match) {
  for (const userId of [match.whiteId, match.blackId]) {
    sendToUser(userId, matchPayload(match, userId));
  }
}

function notifyPresence(userId, online) {
  const user = findUser(userId);
  if (!user) return;
  for (const friendId of user.friends || []) {
    sendToUser(friendId, {
      type: "presence",
      userId,
      username: user.username,
      online
    });
  }
}

function createMatch(invite) {
  const id = randomUUID();
  const match = {
    id,
    whiteId: invite.fromId,
    blackId: invite.toId,
    game: new Chess(),
    createdAt: Date.now()
  };
  matches.set(id, match);
  invite.status = "accepted";
  invite.matchId = id;
  invite.updatedAt = Date.now();
  persist();
  sendToUser(match.whiteId, {
    type: "match_started",
    matchId: id,
    color: "white",
    opponent: publicUser(findUser(match.blackId)),
    fen: match.game.fen()
  });
  sendToUser(match.blackId, {
    type: "match_started",
    matchId: id,
    color: "black",
    opponent: publicUser(findUser(match.whiteId)),
    fen: match.game.fen()
  });
  broadcastMatch(match);
  return match;
}

async function handleApi(req, res, url) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS"
    });
    return res.end();
  }

  if (req.method === "GET" && url.pathname === "/health") {
    return reply(res, 200, { ok: true, service: "project-visual-presence", users: store.users.length });
  }

  if (req.method === "POST" && url.pathname === "/api/register") {
    const body = await readJson(req);
    const username = String(body.username || "").trim();
    const password = String(body.password || "");
    if (!/^[A-Za-z0-9_.-]{3,20}$/.test(username)) {
      return reply(res, 400, { error: "Username must be 3–20 letters, digits, dot, dash or underscore." });
    }
    if (password.length < 10 || password.length > 128) {
      return reply(res, 400, { error: "Password must contain 10–128 characters." });
    }
    if (findUserByName(username)) return reply(res, 409, { error: "That PROJECT VISUAL name is already taken." });
    const salt = randomBytes(16).toString("hex");
    const user = {
      id: randomUUID(),
      username,
      salt,
      passwordHash: hashPassword(password, salt),
      friends: [],
      createdAt: Date.now()
    };
    store.users.push(user);
    persist();
    return reply(res, 201, { user: publicUser(user), token: issueToken(user.id) });
  }

  if (req.method === "POST" && url.pathname === "/api/login") {
    const body = await readJson(req);
    const user = findUserByName(body.username);
    if (!user || typeof body.password !== "string") {
      return reply(res, 401, { error: "Incorrect username or password." });
    }
    const candidateHash = Buffer.from(hashPassword(body.password, user.salt), "hex");
    const storedHash = Buffer.from(user.passwordHash, "hex");
    if (candidateHash.length !== storedHash.length
        || !timingSafeEqual(candidateHash, storedHash)) {
      return reply(res, 401, { error: "Incorrect username or password." });
    }
    return reply(res, 200, { user: publicUser(user), token: issueToken(user.id) });
  }

  const user = verifyToken(bearer(req));
  if (!user) return reply(res, 401, { error: "Sign in to PROJECT VISUAL first." });

  if (req.method === "GET" && url.pathname === "/api/me") {
    return reply(res, 200, { user: publicUser(user) });
  }

  if (req.method === "GET" && url.pathname === "/api/friends") {
    return reply(res, 200, { friends: friendsOf(user) });
  }

  if (req.method === "GET" && url.pathname === "/api/friend-requests") {
    const incoming = store.friendRequests
      .filter(item => item.toId === user.id && item.status === "pending")
      .map(item => ({ id: item.id, from: publicUser(findUser(item.fromId)) }));
    const outgoing = store.friendRequests
      .filter(item => item.fromId === user.id && item.status === "pending")
      .map(item => ({ id: item.id, to: publicUser(findUser(item.toId)) }));
    return reply(res, 200, { incoming, outgoing });
  }

  if (req.method === "POST" && url.pathname === "/api/friends/request") {
    const body = await readJson(req);
    const target = findUserByName(body.username);
    if (!target) return reply(res, 404, { error: "PROJECT VISUAL user not found." });
    if (target.id === user.id) return reply(res, 400, { error: "You cannot add yourself." });
    if ((user.friends || []).includes(target.id)) return reply(res, 409, { error: "You are already friends." });

    const reverse = store.friendRequests.find(item =>
      item.fromId === target.id && item.toId === user.id && item.status === "pending");
    if (reverse) {
      reverse.status = "accepted";
      user.friends = [...new Set([...(user.friends || []), target.id])];
      target.friends = [...new Set([...(target.friends || []), user.id])];
      persist();
      pushFriendLists([user.id, target.id]);
      return reply(res, 200, { accepted: true, friend: publicUser(target) });
    }

    const exists = store.friendRequests.some(item =>
      item.fromId === user.id && item.toId === target.id && item.status === "pending");
    if (exists) return reply(res, 409, { error: "Friend request already sent." });
    const request = {
      id: randomUUID(), fromId: user.id, toId: target.id,
      status: "pending", createdAt: Date.now()
    };
    store.friendRequests.push(request);
    persist();
    sendToUser(target.id, { type: "friend_request", requestId: request.id, from: publicUser(user) });
    return reply(res, 201, { requestId: request.id, sentTo: publicUser(target) });
  }

  const acceptRequest = url.pathname.match(new RegExp("^/api/friend-requests/([0-9a-f-]+)/accept$", "i"));
  if (req.method === "POST" && acceptRequest) {
    const request = store.friendRequests.find(item =>
      item.id === acceptRequest[1] && item.toId === user.id && item.status === "pending");
    if (!request) return reply(res, 404, { error: "Pending friend request not found." });
    const sender = findUser(request.fromId);
    if (!sender) return reply(res, 404, { error: "The other account no longer exists." });
    request.status = "accepted";
    user.friends = [...new Set([...(user.friends || []), sender.id])];
    sender.friends = [...new Set([...(sender.friends || []), user.id])];
    persist();
    pushFriendLists([user.id, sender.id]);
    return reply(res, 200, { accepted: true, friend: publicUser(sender) });
  }

  const declineRequest = url.pathname.match(new RegExp("^/api/friend-requests/([0-9a-f-]+)/decline$", "i"));
  if (req.method === "POST" && declineRequest) {
    const request = store.friendRequests.find(item =>
      item.id === declineRequest[1] && item.toId === user.id && item.status === "pending");
    if (!request) return reply(res, 404, { error: "Pending friend request not found." });
    request.status = "declined";
    persist();
    return reply(res, 200, { declined: true });
  }

  if (req.method === "GET" && url.pathname === "/api/invites") {
    const now = Date.now();
    const invites = store.invites.filter(item =>
      item.status === "pending" && item.expiresAt > now
      && (item.fromId === user.id || item.toId === user.id));
    return reply(res, 200, {
      invites: invites.map(item => ({
        id: item.id,
        direction: item.toId === user.id ? "incoming" : "outgoing",
        from: publicUser(findUser(item.fromId)),
        to: publicUser(findUser(item.toId)),
        createdAt: item.createdAt
      }))
    });
  }

  if (req.method === "POST" && url.pathname === "/api/invites") {
    const body = await readJson(req);
    const friend = findUser(String(body.friendId || ""));
    if (!friend || !(user.friends || []).includes(friend.id)) {
      return reply(res, 403, { error: "You can only invite an accepted friend." });
    }
    if (!sockets.has(friend.id)) {
      return reply(res, 409, { error: "Your friend is offline in PROJECT VISUAL." });
    }
    const pending = store.invites.find(item =>
      item.fromId === user.id && item.toId === friend.id && item.status === "pending"
      && item.expiresAt > Date.now());
    if (pending) return reply(res, 409, { error: "An invitation is already pending." });
    const invite = {
      id: randomUUID(),
      fromId: user.id,
      toId: friend.id,
      status: "pending",
      createdAt: Date.now(),
      expiresAt: Date.now() + 2 * 60 * 1000
    };
    store.invites.push(invite);
    persist();
    sendToUser(friend.id, {
      type: "chess_invite",
      inviteId: invite.id,
      from: publicUser(user),
      expiresAt: invite.expiresAt
    });
    return reply(res, 201, { inviteId: invite.id, sentTo: publicUser(friend) });
  }

  const acceptInvite = url.pathname.match(new RegExp("^/api/invites/([0-9a-f-]+)/accept$", "i"));
  if (req.method === "POST" && acceptInvite) {
    const invite = store.invites.find(item =>
      item.id === acceptInvite[1] && item.toId === user.id && item.status === "pending"
      && item.expiresAt > Date.now());
    if (!invite) return reply(res, 404, { error: "Invitation expired or not found." });
    if (!sockets.has(invite.fromId) || !sockets.has(invite.toId)) {
      return reply(res, 409, { error: "Both players must be online in PROJECT VISUAL to start." });
    }
    const match = createMatch(invite);
    return reply(res, 200, { accepted: true, matchId: match.id, fen: match.game.fen() });
  }

  const declineInvite = url.pathname.match(new RegExp("^/api/invites/([0-9a-f-]+)/decline$", "i"));
  if (req.method === "POST" && declineInvite) {
    const invite = store.invites.find(item =>
      item.id === declineInvite[1] && item.toId === user.id && item.status === "pending");
    if (!invite) return reply(res, 404, { error: "Invitation not found." });
    invite.status = "declined";
    invite.updatedAt = Date.now();
    persist();
    sendToUser(invite.fromId, { type: "invite_declined", inviteId: invite.id });
    return reply(res, 200, { declined: true });
  }

  const matchRoute = url.pathname.match(new RegExp("^/api/matches/([0-9a-f-]+)$", "i"));
  if (req.method === "GET" && matchRoute) {
    const match = matches.get(matchRoute[1]);
    if (!match || ![match.whiteId, match.blackId].includes(user.id)) {
      return reply(res, 404, { error: "Match not found." });
    }
    return reply(res, 200, matchPayload(match, user.id));
  }

  return reply(res, 404, { error: "Endpoint not found." });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  try {
    await handleApi(req, res, url);
  } catch (error) {
    if (!res.headersSent) reply(res, error.status || 500, {
      error: error.status ? error.message : "Internal server error."
    });
    if (!error.status) console.error("API error:", error);
  }
});

const wss = new WebSocketServer({ noServer: true });
server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url, "http://localhost");
  if (url.pathname !== "/ws") return socket.destroy();
  const user = verifyToken(url.searchParams.get("token") || "");
  if (!user) return socket.destroy();
  wss.handleUpgrade(req, socket, head, ws => {
    wss.emit("connection", ws, req, user);
  });
});

wss.on("connection", (ws, _req, user) => {
  const previous = sockets.get(user.id);
  if (previous && previous !== ws) previous.close(4001, "Replaced by a newer session");
  sockets.set(user.id, ws);
  ws.isAlive = true;
  ws.on("pong", () => { ws.isAlive = true; });
  ws.send(JSON.stringify({
    type: "connected",
    user: publicUser(user),
    friends: friendsOf(user),
    pendingInvites: store.invites.filter(item =>
      item.toId === user.id && item.status === "pending" && item.expiresAt > Date.now())
      .map(item => ({ id: item.id, from: publicUser(findUser(item.fromId)) })),
    matches: [...matches.values()]
      .filter(match => [match.whiteId, match.blackId].includes(user.id))
      .map(match => matchPayload(match, user.id))
  }));
  notifyPresence(user.id, true);

  ws.on("message", raw => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      ws.send(JSON.stringify({ type: "error", error: "Invalid message JSON." }));
      return;
    }
    if (message.type === "move") {
      const match = matches.get(String(message.matchId || ""));
      if (!match || ![match.whiteId, match.blackId].includes(user.id)) {
        ws.send(JSON.stringify({ type: "error", error: "Match not found." }));
        return;
      }
      const expectedColor = user.id === match.whiteId ? "w" : "b";
      if (match.game.turn() !== expectedColor) {
        ws.send(JSON.stringify({ type: "error", error: "It is not your turn." }));
        return;
      }
      const uci = String(message.uci || "").toLowerCase();
      const parsed = uci.match(/^([a-h][1-8])([a-h][1-8])([qrbn])?$/);
      if (!parsed) {
        ws.send(JSON.stringify({ type: "error", error: "Move must use UCI format, e.g. e2e4." }));
        return;
      }
      try {
        const played = match.game.move({
          from: parsed[1],
          to: parsed[2],
          ...(parsed[3] ? { promotion: parsed[3] } : {})
        });
        if (!played) throw new Error("Illegal move.");
        broadcastMatch(match);
      } catch (error) {
        ws.send(JSON.stringify({ type: "error", error: error.message || "Illegal move." }));
      }
    } else if (message.type === "resign") {
      const match = matches.get(String(message.matchId || ""));
      if (!match || ![match.whiteId, match.blackId].includes(user.id)) return;
      const winnerId = user.id === match.whiteId ? match.blackId : match.whiteId;
      match.resignedBy = user.id;
      match.winnerId = winnerId;
      for (const playerId of [match.whiteId, match.blackId]) {
        sendToUser(playerId, {
          type: "match_resigned",
          matchId: match.id,
          resignedBy: user.id,
          winnerId
        });
      }
    }
  });

  ws.on("close", () => {
    if (sockets.get(user.id) === ws) {
      sockets.delete(user.id);
      notifyPresence(user.id, false);
    }
  });
});

const heartbeat = setInterval(() => {
  for (const ws of wss.clients) {
    if (ws.isAlive === false) {
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, 30_000);

server.listen(PORT, HOST, () => {
  console.log("PROJECT VISUAL global service listening on " + HOST + ":" + PORT);
  console.log("Persistent data file: " + DATA_FILE);
});

function shutdown() {
  clearInterval(heartbeat);
  for (const ws of wss.clients) ws.close(1001, "Server shutting down");
  server.close(() => process.exit(0));
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
