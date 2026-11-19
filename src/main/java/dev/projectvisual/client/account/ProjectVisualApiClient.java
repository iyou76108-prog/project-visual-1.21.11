package dev.projectvisual.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.projectvisual.client.VisualFeatures;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ProjectVisualApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<String, String> REMOTE_SKINS = new ConcurrentHashMap<>();
    private static final String[] COSMETIC_MODULES = {
            "Queen Skin", "Invoker Skin", "Interworlder Mask", "Guts", "Akashi Seijuro",
            "Dual Blades", "Flying Spellbook", "Pencil Sword", "Divine Rapier",
            "Hand Cosmetics", "Cape Effects"
    };
    private static String apiToken;
    private static String minecraftUserId;
    private static int syncTicks;
    private static String lastAppliedSkin = "";

    private ProjectVisualApiClient() {}

    static {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(accountPath())) {
            p.load(in);
            apiToken = p.getProperty("token", "");
        } catch (IOException ignored) {
            apiToken = "";
        }
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!isLoggedIn() || client.player == null || client.level == null) {
                syncTicks = 0;
                return;
            }
            if (++syncTicks < 200) return;
            syncTicks = 0;
            request("POST", "/api/minecraft/presence", "{}", ignored -> {});
            request("GET", "/api/minecraft/players", null, ProjectVisualApiClient::updateRemotePlayers);
            refreshProfile(ignored -> {});
        });
    }

    public static boolean isLoggedIn() {
        return apiToken != null && !apiToken.isBlank();
    }

    public static void logout() {
        apiToken = "";
        REMOTE_SKINS.clear();
        lastAppliedSkin = "";
        minecraftUserId = null;
        saveToken();
    }

    public static String minecraftUserId() {
        return minecraftUserId;
    }

    public static void refreshProfile(Consumer<String> callback) {
        request("GET", "/api/minecraft/me", null, body -> {
            updateOwnSkin(body);
            if (callback != null) callback.accept(body);
        });
    }

    public static String remoteSkinFor(String playerName) {
        if (playerName == null) return null;
        return REMOTE_SKINS.get(playerName.toLowerCase(Locale.ROOT));
    }

    private static void updateRemotePlayers(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray players = root.getAsJsonArray("players");
            REMOTE_SKINS.clear();
            if (players == null) return;
            for (var element : players) {
                JsonObject player = element.getAsJsonObject();
                if (!player.has("username") || player.get("username").isJsonNull()) continue;
                if (!player.has("equippedSkin") || player.get("equippedSkin").isJsonNull()) continue;
                String name = player.get("username").getAsString().toLowerCase(Locale.ROOT);
                String skin = player.get("equippedSkin").getAsString();
                if (!skin.isBlank()) REMOTE_SKINS.put(name, skin);
            }
        } catch (RuntimeException ignored) {
            // Keep the previous remote cosmetic cache if a response is incomplete.
        }
    }

    private static void updateOwnSkin(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject user = root.getAsJsonObject("user");
            if (user == null) return;
            if (user.has("id") && !user.get("id").isJsonNull()) {
                minecraftUserId = user.get("id").getAsString();
            }
            String skin = user.has("equippedSkin") && !user.get("equippedSkin").isJsonNull()
                    ? user.get("equippedSkin").getAsString() : "";
            if (skin.equals(lastAppliedSkin)) return;
            lastAppliedSkin = skin;
            for (String module : COSMETIC_MODULES) VisualFeatures.setEnabled(module, module.equals(skin));
        } catch (RuntimeException ignored) {
            // Local cosmetics stay as-is if the website is temporarily unreachable.
        }
    }

    public static void pairWithWebsite(String code, Consumer<String> callback) {
        String clean = code == null ? "" : code.replaceAll("[^A-Za-z0-9-]", "")
                .toUpperCase(Locale.ROOT);
        if (clean.length() < 6 || clean.length() > 12) {
            callback.accept("Введи код подключения с сайта.");
            return;
        }
        String body = "{\"code\":\"" + clean + "\"}";
        HttpRequest request = HttpRequest.newBuilder(apiUri("/api/minecraft/pair"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        callback.accept("Не удалось связаться с PROJECT VISUAL: " + error.getMessage());
                        return;
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            callback.accept(json.has("error") ? json.get("error").getAsString()
                                    : "Сайт отклонил код подключения.");
                            return;
                        }
                        apiToken = json.get("accessToken").getAsString();
                        if (json.has("userId") && !json.get("userId").isJsonNull()) {
                            minecraftUserId = json.get("userId").getAsString();
                        }
                        saveToken();
                        lastAppliedSkin = "";
                        callback.accept("Вход выполнен: " + json.get("username").getAsString());
                        syncTicks = 199;
                    } catch (RuntimeException parseError) {
                        callback.accept("Сайт вернул некорректный ответ.");
                    }
                }));
    }

    public static void request(String method, String path, String jsonBody, Consumer<String> callback) {
        if (!isLoggedIn()) {
            Minecraft.getInstance().execute(() -> callback.accept("{\"error\":\"Сначала подключи аккаунт: /pv login\"}"));
            return;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(apiUri(path))
                .timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + apiToken);
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        callback.accept("{\"error\":\"Ошибка сети: " + escapeJson(error.getMessage()) + "\"}");
                    } else {
                        callback.accept(response.body());
                    }
                }));
    }

    public static void getFriends(Consumer<String> callback) {
        request("GET", "/api/minecraft/friends", null, callback);
    }

    public static void addFriend(String username, Consumer<String> callback) {
        request("POST", "/api/minecraft/friends", "{\"username\":\"" + escapeJson(username) + "\"}", callback);
    }

    public static void respondToFriendRequest(String requestId, boolean accept, Consumer<String> callback) {
        request("PATCH", "/api/minecraft/friends",
                "{\"requestId\":\"" + escapeJson(requestId) + "\",\"action\":\""
                        + (accept ? "accept" : "decline") + "\"}", callback);
    }

    public static void getGames(Consumer<String> callback) {
        request("GET", "/api/minecraft/games", null, callback);
    }

    public static void createGame(String username, String type, Consumer<String> callback) {
        request("POST", "/api/minecraft/games",
                "{\"username\":\"" + escapeJson(username) + "\",\"type\":\"" + type + "\"}", callback);
    }

    public static void acceptGame(String gameId, Consumer<String> callback) {
        request("POST", "/api/minecraft/games/" + gameId + "/accept", "{}", callback);
    }

    public static void getGame(String gameId, Consumer<String> callback) {
        request("GET", "/api/minecraft/games/" + gameId, null, callback);
    }

    public static void submitTicTacToeMove(String gameId, int index, int version, Consumer<String> callback) {
        request("POST", "/api/minecraft/games/" + gameId + "/move",
                "{\"index\":" + index + ",\"version\":" + version + "}", callback);
    }

    public static void submitChessMove(String gameId, String from, String to, char promotion,
                                       int version, Consumer<String> callback) {
        String suffix = promotion == 0 ? "" : ",\"promotion\":\"" + Character.toLowerCase(promotion) + "\"";
        request("POST", "/api/minecraft/games/" + gameId + "/move",
                "{\"from\":\"" + from + "\",\"to\":\"" + to + "\""
                        + suffix + ",\"version\":" + version + "}", callback);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static URI apiUri(String path) {
        return URI.create(readBaseUrl() + path);
    }

    private static String readBaseUrl() {
        Properties p = new Properties();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("project_visual_server.properties");
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException ignored) {
            return "http://localhost:3000";
        }
        String value = p.getProperty("api_url", "http://localhost:3000").trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static Path accountPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("project_visual_account.properties");
    }

    private static void saveToken() {
        try {
            Files.createDirectories(accountPath().getParent());
            Properties p = new Properties();
            p.setProperty("token", apiToken == null ? "" : apiToken);
            try (OutputStream out = Files.newOutputStream(accountPath())) {
                p.store(out, "PROJECT VISUAL account token - keep this file private");
            }
        } catch (IOException ignored) {
            // The user can reconnect if local token persistence is unavailable.
        }
    }
}
