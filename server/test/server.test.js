import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes } from "node:crypto";
import WebSocket from "ws";

const here = dirname(fileURLToPath(import.meta.url));
const serverFile = join(here, "..", "src", "server.js");

async function freePort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

async function waitFor(predicate, timeoutMs = 10_000) {
  const started = Date.now();
  let lastError;
  while (Date.now() - started < timeoutMs) {
    try {
      const result = await predicate();
      if (result) return result;
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw lastError || new Error("Timed out waiting for global service.");
}

async function api(base, route, token, body) {
  const response = await fetch(base + route, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      ...(token ? { authorization: "Bearer " + token } : {}),
      ...(body === undefined ? {} : { "content-type": "application/json" })
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "HTTP " + response.status);
  return payload;
}

function connectSocket(url) {
  const socket = new WebSocket(url);
  socket.__eventBuffer = [];
  socket.on("message", raw => {
    try {
      socket.__eventBuffer.push(JSON.parse(raw.toString()));
      if (socket.__eventBuffer.length > 64) socket.__eventBuffer.shift();
    } catch { }
  });
  return new Promise((resolve, reject) => {
    socket.once("open", () => resolve(socket));
    socket.once("error", reject);
  });
}

function waitForEvent(socket, predicate, timeoutMs = 5_000) {
  const buffered = socket.__eventBuffer || [];
  const index = buffered.findIndex(predicate);
  if (index >= 0) return Promise.resolve(buffered.splice(index, 1)[0]);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.off("message", onMessage);
      reject(new Error("Timed out waiting for WebSocket event."));
    }, timeoutMs);
    const onMessage = raw => {
      let event;
      try {
        event = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (!predicate(event)) return;
      clearTimeout(timer);
      socket.off("message", onMessage);
      resolve(event);
    };
    socket.on("message", onMessage);
  });
}

test("global service: accounts, friend requests, invitations and validated online moves",
  { timeout: 30_000 }, async () => {
    const folder = mkdtempSync(join(tmpdir(), "project-visual-global-"));
    const port = await freePort();
    const base = "http://127.0.0.1:" + port;
    const child = spawn(process.execPath, [serverFile], {
      cwd: join(here, ".."),
      env: {
        ...process.env,
        HOST: "127.0.0.1",
        PORT: String(port),
        PV_TOKEN_SECRET: randomBytes(48).toString("hex"),
        PV_DATA_FILE: join(folder, "data.json")
      },
      stdio: "ignore"
    });

    let whiteSocket;
    let blackSocket;
    try {
      await waitFor(async () => {
        const response = await fetch(base + "/health");
        return response.ok;
      });

      const white = await api(base, "/api/register", null, {
        username: "WhitePlayer",
        password: "White-password-123"
      });
      const black = await api(base, "/api/register", null, {
        username: "BlackPlayer",
        password: "Black-password-456"
      });

      await api(base, "/api/friends/request", white.token, { username: "BlackPlayer" });
      const requests = await api(base, "/api/friend-requests", black.token);
      assert.equal(requests.incoming.length, 1);
      await api(base, "/api/friend-requests/" + requests.incoming[0].id + "/accept",
        black.token, {});
      const friends = await api(base, "/api/friends", white.token);
      assert.equal(friends.friends.length, 1);
      assert.equal(friends.friends[0].username, "BlackPlayer");

      const wsBase = "ws://127.0.0.1:" + port + "/ws?token=";
      whiteSocket = await connectSocket(wsBase + encodeURIComponent(white.token));
      blackSocket = await connectSocket(wsBase + encodeURIComponent(black.token));
      await Promise.all([
        waitForEvent(whiteSocket, event => event.type === "connected"),
        waitForEvent(blackSocket, event => event.type === "connected")
      ]);

      const inviteEvent = waitForEvent(blackSocket, event => event.type === "chess_invite");
      await api(base, "/api/invites", white.token, { friendId: black.user.id });
      const invite = await inviteEvent;
      const whiteStarted = waitForEvent(whiteSocket, event => event.type === "match_started");
      const blackStarted = waitForEvent(blackSocket, event => event.type === "match_started");
      const accepted = await api(base, "/api/invites/" + invite.inviteId + "/accept",
        black.token, {});
      const [whiteMatch, blackMatch] = await Promise.all([whiteStarted, blackStarted]);
      assert.equal(whiteMatch.color, "white");
      assert.equal(blackMatch.color, "black");
      assert.equal(whiteMatch.matchId, accepted.matchId);
      assert.equal(blackMatch.matchId, accepted.matchId);

      const whiteMoveState = waitForEvent(whiteSocket, event =>
        event.type === "match_state" && event.matchId === accepted.matchId
        && event.fen.includes(" e3 "));
      whiteSocket.send(JSON.stringify({
        type: "move", matchId: accepted.matchId, uci: "e2e4"
      }));
      const state = await whiteMoveState;
      assert.match(state.fen, /4P3/);
      assert.equal(state.turn, "black");

      const illegalMove = waitForEvent(blackSocket, event => event.type === "error");
      blackSocket.send(JSON.stringify({
        type: "move", matchId: accepted.matchId, uci: "e7e4"
      }));
      const rejected = await illegalMove;
      assert.match(rejected.error, /Invalid move|Illegal/i);

      const resignEvent = waitForEvent(blackSocket, event => event.type === "match_resigned");
      whiteSocket.send(JSON.stringify({ type: "resign", matchId: accepted.matchId }));
      const resigned = await resignEvent;
      assert.equal(resigned.winnerId, black.user.id);
    } finally {
      if (whiteSocket) whiteSocket.close();
      if (blackSocket) blackSocket.close();
      child.kill("SIGTERM");
      rmSync(folder, { recursive: true, force: true });
    }
  });
