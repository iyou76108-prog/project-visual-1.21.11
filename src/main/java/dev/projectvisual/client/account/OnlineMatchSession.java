package dev.projectvisual.client.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.concurrent.atomic.AtomicBoolean;

/** Client-side view of a server-authoritative chess or tic-tac-toe match. */
public final class OnlineMatchSession {
    public record Snapshot(String id, String type, String status,
                           String player1Id, String player2Id, String nextTurnUserId,
                           String player1Name, String player2Name,
                           String fen, String board, String winnerId, int version) {
        public boolean isChess() { return "CHESS".equals(type); }
        public boolean isTicTacToe() { return "TIC_TAC_TOE".equals(type); }
        public boolean isActive() { return "ACTIVE".equals(status); }
        public boolean isFinished() { return "FINISHED".equals(status); }
        public boolean isPlayer1() {
            String local = ProjectVisualApiClient.minecraftUserId();
            return local != null && local.equals(player1Id);
        }
        public boolean isMyTurn() {
            String local = ProjectVisualApiClient.minecraftUserId();
            return isActive() && local != null && local.equals(nextTurnUserId);
        }
        public String opponentName() {
            return isPlayer1() ? player2Name : player1Name;
        }
    }

    private static volatile Snapshot current;
    private static final AtomicBoolean pollPending = new AtomicBoolean();
    private static int pollTicks;
    private static String lastError = "";

    private OnlineMatchSession() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static void tick() {
        Snapshot snapshot = current;
        if (snapshot == null || !snapshot.isActive()) return;
        if (++pollTicks < 20 || !pollPending.compareAndSet(false, true)) return;
        pollTicks = 0;
        ProjectVisualApiClient.getGame(snapshot.id(), response -> {
            pollPending.set(false);
            applyResponse(response);
        });
    }

    public static Snapshot snapshot() {
        return current;
    }

    public static String lastError() {
        return lastError;
    }

    public static boolean attach(String response) {
        return applyResponse(response);
    }

    public static boolean applyResponse(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            if (root.has("error")) {
                lastError = root.get("error").getAsString();
                return false;
            }
            JsonObject game = root.has("game") && root.get("game").isJsonObject()
                    ? root.getAsJsonObject("game") : root;
            JsonObject state = game.has("state") && game.get("state").isJsonObject()
                    ? game.getAsJsonObject("state") : new JsonObject();
            JsonObject p1 = game.has("player1") && game.get("player1").isJsonObject()
                    ? game.getAsJsonObject("player1") : new JsonObject();
            JsonObject p2 = game.has("player2") && game.get("player2").isJsonObject()
                    ? game.getAsJsonObject("player2") : new JsonObject();
            Snapshot next = new Snapshot(
                    value(game, "id"), value(game, "type"), value(game, "status"),
                    value(game, "player1Id"), value(game, "player2Id"), value(game, "nextTurnUserId"),
                    value(p1, "username"), value(p2, "username"),
                    value(state, "fen"), value(state, "board"), value(game, "winnerId"),
                    game.has("version") ? game.get("version").getAsInt() : 0
            );
            if (next.id() == null || next.type() == null || next.status() == null) return false;
            current = next;
            lastError = "";
            pollTicks = 0;
            return true;
        } catch (RuntimeException ignored) {
            lastError = "Сервер вернул некорректное состояние матча.";
            return false;
        }
    }

    public static void submitChessMove(String from, String to, char promotion) {
        Snapshot snapshot = current;
        if (snapshot == null || !snapshot.isMyTurn() || !snapshot.isChess()) return;
        ProjectVisualApiClient.submitChessMove(snapshot.id(), from, to, promotion, snapshot.version(),
                OnlineMatchSession::applyResponse);
    }

    public static void submitTicTacToeMove(int index) {
        Snapshot snapshot = current;
        if (snapshot == null || !snapshot.isMyTurn() || !snapshot.isTicTacToe()) return;
        ProjectVisualApiClient.submitTicTacToeMove(snapshot.id(), index, snapshot.version(),
                OnlineMatchSession::applyResponse);
    }

    public static void clear() {
        current = null;
        pollTicks = 0;
        lastError = "";
        pollPending.set(false);
    }

    private static String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }
}
