package dev.projectvisual.client.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Properties;
import java.util.function.Consumer;

public final class ProjectVisualApiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private static String apiToken;

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

    public static boolean isLoggedIn() {
        return apiToken != null && !apiToken.isBlank();
    }

    public static void logout() {
        apiToken = "";
        saveToken();
    }

    public static void pairWithWebsite(String code, Consumer<String> callback) {
        String clean = code == null ? "" : code.replaceAll("[^A-Za-z0-9-]", "").toUpperCase(java.util.Locale.ROOT);
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
                        saveToken();
                        String username = json.has("username") ? json.get("username").getAsString() : "аккаунт";
                        callback.accept("Вход выполнен: " + username);
                    } catch (RuntimeException parseError) {
                        callback.accept("Сайт вернул некорректный ответ.");
                    }
                }));
    }

    public static void getFriends(Consumer<String> callback) {
        if (!isLoggedIn()) {
            callback.accept("Сначала подключи аккаунт: /pv login");
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(apiUri("/api/minecraft/friends"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiToken)
                .GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) callback.accept("Ошибка сети: " + error.getMessage());
                    else callback.accept(response.body());
                }));
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
