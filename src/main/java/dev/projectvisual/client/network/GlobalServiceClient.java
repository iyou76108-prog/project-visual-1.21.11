package dev.projectvisual.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** HTTP + WebSocket connection to the separate PROJECT VISUAL global service. */
public final class GlobalServiceClient {
    private static final GlobalServiceClient INSTANCE = new GlobalServiceClient();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final Path configPath = FabricLoader.getInstance().getConfigDir()
            .resolve("project_visual_global.json");

    private String baseUrl = "http://localhost:8787";
    private String token = "";
    private String username = "";
    private WebSocket socket;
    private Consumer<JsonObject> eventListener = event -> { };

    private GlobalServiceClient() {
        loadConfig();
    }

    public static GlobalServiceClient get() {
        return INSTANCE;
    }

    public synchronized String baseUrl() {
        return baseUrl;
    }

    public synchronized String username() {
        return username;
    }

    public synchronized boolean isAuthenticated() {
        return !token.isBlank();
    }

    public synchronized void setEventListener(Consumer<JsonObject> listener) {
        eventListener = listener == null ? event -> { } : listener;
    }

    public synchronized void setBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))) {
            throw new IllegalArgumentException("Address must start with https:// or http://");
        }
        baseUrl = normalized;
        saveConfig();
        disconnect();
        if (!token.isBlank()) connectWebSocket();
    }

    public CompletableFuture<JsonObject> register(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return requestPublic("POST", "/api/register", body).thenApply(this::acceptAuth);
    }

    public CompletableFuture<JsonObject> login(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return requestPublic("POST", "/api/login", body).thenApply(this::acceptAuth);
    }

    private synchronized JsonObject acceptAuth(JsonObject response) {
        token = response.get("token").getAsString();
        JsonObject user = response.getAsJsonObject("user");
        username = user == null ? "" : user.get("username").getAsString();
        saveConfig();
        connectWebSocket();
        return response;
    }

    public CompletableFuture<JsonObject> me() {
        return request("GET", "/api/me", null);
    }

    public CompletableFuture<JsonObject> friends() {
        return request("GET", "/api/friends", null);
    }

    public CompletableFuture<JsonObject> friendRequests() {
        return request("GET", "/api/friend-requests", null);
    }

    public CompletableFuture<JsonObject> invites() {
        return request("GET", "/api/invites", null);
    }

    public CompletableFuture<JsonObject> addFriend(String friendName) {
        JsonObject body = new JsonObject();
        body.addProperty("username", friendName);
        return request("POST", "/api/friends/request", body);
    }

    public CompletableFuture<JsonObject> acceptFriendRequest(String requestId) {
        return request("POST", "/api/friend-requests/" + requestId + "/accept", new JsonObject());
    }

    public CompletableFuture<JsonObject> sendInvite(String friendId) {
        JsonObject body = new JsonObject();
        body.addProperty("friendId", friendId);
        return request("POST", "/api/invites", body);
    }

    public CompletableFuture<JsonObject> acceptInvite(String inviteId) {
        return request("POST", "/api/invites/" + inviteId + "/accept", new JsonObject());
    }

    public CompletableFuture<JsonObject> declineInvite(String inviteId) {
        return request("POST", "/api/invites/" + inviteId + "/decline", new JsonObject());
    }

    public void sendMove(String matchId, String uci) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "move");
        event.addProperty("matchId", matchId);
        event.addProperty("uci", uci);
        sendSocketEvent(event);
    }

    public void resign(String matchId) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "resign");
        event.addProperty("matchId", matchId);
        sendSocketEvent(event);
    }

    private CompletableFuture<JsonObject> requestPublic(String method, String route, JsonObject body) {
        return requestInternal(method, route, body, false);
    }

    private CompletableFuture<JsonObject> request(String method, String route, JsonObject body) {
        return requestInternal(method, route, body, true);
    }

    private CompletableFuture<JsonObject> requestInternal(
            String method, String route, JsonObject body, boolean authenticated) {
        final String endpoint;
        final String sessionToken;
        synchronized (this) {
            endpoint = baseUrl;
            sessionToken = token;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + route))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json");
        if (authenticated) {
            if (sessionToken.isBlank()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Sign in to your PROJECT VISUAL account first."));
            }
            builder.header("Authorization", "Bearer " + sessionToken);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body.toString());
        builder.method(method, publisher);
        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject json;
                    try {
                        json = JsonParser.parseString(response.body()).getAsJsonObject();
                    } catch (RuntimeException error) {
                        throw new CompletionException(
                                new IllegalStateException("Global service returned invalid JSON."));
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        String message = json.has("error")
                                ? json.get("error").getAsString() : "HTTP " + response.statusCode();
                        throw new CompletionException(new IllegalStateException(message));
                    }
                    return json;
                });
    }

    private synchronized void connectWebSocket() {
        if (token.isBlank() || socket != null) return;
        String wsBase = baseUrl.startsWith("https://")
                ? "wss://" + baseUrl.substring(8)
                : "ws://" + baseUrl.substring(7);
        URI uri = URI.create(wsBase + "/ws?token=" + token);
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder pending = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        synchronized (GlobalServiceClient.this) {
                            socket = webSocket;
                        }
                        webSocket.request(1);
                    }

                    @Override
                    public java.util.concurrent.CompletionStage<?> onText(
                            WebSocket webSocket, CharSequence data, boolean last) {
                        pending.append(data);
                        if (last) {
                            String message = pending.toString();
                            pending.setLength(0);
                            try {
                                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                                Minecraft.getInstance().execute(() -> {
                                    Consumer<JsonObject> listener;
                                    synchronized (GlobalServiceClient.this) {
                                        listener = eventListener;
                                    }
                                    listener.accept(json);
                                });
                            } catch (RuntimeException ignored) {
                                // Ignore malformed or unknown messages instead of crashing the client.
                            }
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public java.util.concurrent.CompletionStage<?> onClose(
                            WebSocket webSocket, int statusCode, String reason) {
                        synchronized (GlobalServiceClient.this) {
                            if (socket == webSocket) socket = null;
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        synchronized (GlobalServiceClient.this) {
                            if (socket == webSocket) socket = null;
                        }
                    }
                }).exceptionally(error -> {
                    synchronized (GlobalServiceClient.this) {
                        socket = null;
                    }
                    return null;
                });
    }

    private synchronized void sendSocketEvent(JsonObject event) {
        if (socket != null && socket.isOutputClosed() == false) {
            socket.sendText(event.toString(), true);
        }
    }

    private synchronized void disconnect() {
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnected");
            socket = null;
        }
    }

    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) return;
            JsonObject json = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            if (json.has("baseUrl")) baseUrl = json.get("baseUrl").getAsString();
            if (json.has("token")) token = json.get("token").getAsString();
            if (json.has("username")) username = json.get("username").getAsString();
            if (!token.isBlank()) connectWebSocket();
        } catch (Exception ignored) {
            token = "";
            username = "";
        }
    }

    private synchronized void saveConfig() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("baseUrl", baseUrl);
            json.addProperty("token", token);
            json.addProperty("username", username);
            Files.writeString(configPath, json.toString());
        } catch (IOException ignored) {
            // The active session remains usable for this launch even if saving fails.
        }
    }
}
