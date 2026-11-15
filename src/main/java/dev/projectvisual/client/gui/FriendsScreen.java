package dev.projectvisual.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.projectvisual.client.network.GlobalServiceClient;
import dev.projectvisual.client.chess.OnlineChessScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Global PROJECT VISUAL account, friends, requests and chess invite screen. */
public final class FriendsScreen extends Screen {
    private static final int TEXT = 0xFFF4F0FA;
    private static final int MUTED = 0xFFAAA6BA;
    private static final int ACCENT = 0xFF9C7CF5;
    private static final int PANEL = 0xF0181C28;

    private EditBox endpointField;
    private EditBox usernameField;
    private EditBox passwordField;
    private EditBox addFriendField;

    private String status = "Connect to the global service. Use a unique PV password, not your Microsoft password.";
    private List<JsonObject> friends = new ArrayList<>();
    private List<JsonObject> incomingRequests = new ArrayList<>();
    private List<JsonObject> incomingInvites = new ArrayList<>();

    public FriendsScreen() {
        super(Component.literal("PROJECT VISUAL — Friends"));
        GlobalServiceClient.get().setEventListener(this::handleEvent);
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(620, width - 28);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - 8) / 2;

        endpointField = new EditBox(font, left, 50, contentWidth, 20, Component.literal("Service URL"));
        endpointField.setMaxLength(240);
        endpointField.setValue(GlobalServiceClient.get().baseUrl());
        endpointField.setHint(Component.literal("https://your-global-service.example"));

        usernameField = new EditBox(font, left, 82, half, 20, Component.literal("PV username"));
        usernameField.setMaxLength(20);
        usernameField.setHint(Component.literal("PV username"));
        usernameField.setValue(GlobalServiceClient.get().username());

        passwordField = new EditBox(font, left + half + 8, 82, half, 20, Component.literal("Password"));
        passwordField.setMaxLength(128);
        passwordField.setHint(Component.literal("PV password"));
        passwordField.addFormatter((value, firstCharacterIndex) ->
                FormattedCharSequence.forward("•".repeat(value.length()), Style.EMPTY));

        addFriendField = new EditBox(font, left, 145, contentWidth - 116, 20, Component.literal("Friend username"));
        addFriendField.setMaxLength(20);
        addFriendField.setHint(Component.literal("Friend's PROJECT VISUAL username"));

        addRenderableWidget(endpointField);
        addRenderableWidget(usernameField);
        addRenderableWidget(passwordField);
        addRenderableWidget(addFriendField);

        addRenderableWidget(Button.builder(Component.literal("Sign in"), button -> authenticate(false))
                .bounds(left, 111, 112, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Create account"), button -> authenticate(true))
                .bounds(left + 120, 111, 130, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refreshData())
                .bounds(left + contentWidth - 104, 111, 104, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Add friend"), button -> addFriend())
                .bounds(left + contentWidth - 108, 145, 108, 20).build());

        if (GlobalServiceClient.get().isAuthenticated()) refreshData();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0090B12);
        graphics.drawCenteredString(font, "PROJECT VISUAL  /  FRIENDS", width / 2, 20, TEXT);
        int contentWidth = Math.min(620, width - 28);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - 8) / 2;
        graphics.drawString(font, "Global server address", left, 38, MUTED, false);
        graphics.drawString(font, "Account name", left, 70, MUTED, false);
        graphics.drawString(font, "PV password", left + half + 8, 70, MUTED, false);
        graphics.drawString(font, "Add a friend by their PROJECT VISUAL username", left, 133, MUTED, false);
        graphics.drawString(font, status, left, 174, ACCENT, false);

        int columnGap = 14;
        int colWidth = (contentWidth - columnGap) / 2;
        int right = left + colWidth + columnGap;
        graphics.fill(left, 202, left + colWidth, height - 24, PANEL);
        graphics.fill(right, 202, right + colWidth, height - 24, PANEL);
        graphics.drawString(font, "FRIENDS", left + 10, 212, ACCENT, false);
        graphics.drawString(font, "REQUESTS & INVITES", right + 10, 212, ACCENT, false);

        int rowY = 236;
        for (int i = 0; i < Math.min(8, friends.size()); i++) {
            JsonObject friend = friends.get(i);
            String name = friend.get("username").getAsString();
            boolean online = friend.get("online").getAsBoolean();
            graphics.drawString(font, name + (online ? "  • online" : "  • offline"),
                    left + 10, rowY + i * 21, online ? 0xFF83E0B0 : MUTED, false);
            graphics.drawString(font, "INVITE", left + colWidth - 52, rowY + i * 21,
                    online ? ACCENT : 0xFF666273, false);
        }
        if (friends.isEmpty()) {
            graphics.drawString(font, GlobalServiceClient.get().isAuthenticated()
                    ? "No friends yet." : "Sign in to load friends.", left + 10, rowY, MUTED, false);
        }

        int requestY = rowY;
        for (int i = 0; i < Math.min(4, incomingRequests.size()); i++) {
            JsonObject request = incomingRequests.get(i);
            JsonObject from = request.getAsJsonObject("from");
            String name = from == null ? "Unknown" : from.get("username").getAsString();
            graphics.drawString(font, name, right + 10, requestY + i * 21, TEXT, false);
            graphics.drawString(font, "ACCEPT", right + colWidth - 55, requestY + i * 21,
                    ACCENT, false);
        }
        int inviteY = requestY + Math.max(1, Math.min(4, incomingRequests.size())) * 21 + 12;
        graphics.drawString(font, "CHESS INVITATIONS", right + 10, inviteY, ACCENT, false);
        inviteY += 23;
        for (int i = 0; i < Math.min(4, incomingInvites.size()); i++) {
            JsonObject invite = incomingInvites.get(i);
            JsonObject from = invite.getAsJsonObject("from");
            String name = from == null ? "Player" : from.get("username").getAsString();
            graphics.drawString(font, name, right + 10, inviteY + i * 21, TEXT, false);
            graphics.drawString(font, "PLAY", right + colWidth - 42, inviteY + i * 21,
                    ACCENT, false);
        }
        if (incomingRequests.isEmpty() && incomingInvites.isEmpty()) {
            graphics.drawString(font, "No pending requests.", right + 10, requestY, MUTED, false);
        }
        graphics.drawCenteredString(font, "ESC  ·  Back", width / 2, height - 14, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && GlobalServiceClient.get().isAuthenticated()) {
            int contentWidth = Math.min(620, width - 28);
            int left = (width - contentWidth) / 2;
            int columnGap = 14;
            int colWidth = (contentWidth - columnGap) / 2;
            int right = left + colWidth + columnGap;
            int rowY = 236;

            for (int i = 0; i < Math.min(8, friends.size()); i++) {
                int y = rowY + i * 21;
                if (event.x() >= left + colWidth - 62 && event.x() <= left + colWidth - 6
                        && event.y() >= y - 3 && event.y() <= y + 15) {
                    JsonObject friend = friends.get(i);
                    if (!friend.get("online").getAsBoolean()) {
                        status = "That friend is offline.";
                    } else {
                        runAction(GlobalServiceClient.get().sendInvite(
                                friend.get("id").getAsString()), "Chess invitation sent.");
                    }
                    return true;
                }
            }

            for (int i = 0; i < Math.min(4, incomingRequests.size()); i++) {
                int y = rowY + i * 21;
                if (event.x() >= right + colWidth - 62 && event.x() <= right + colWidth - 6
                        && event.y() >= y - 3 && event.y() <= y + 15) {
                    String id = incomingRequests.get(i).get("id").getAsString();
                    runAction(GlobalServiceClient.get().acceptFriendRequest(id), "Friend request accepted.");
                    return true;
                }
            }

            int inviteY = rowY + Math.max(1, Math.min(4, incomingRequests.size())) * 21 + 35;
            for (int i = 0; i < Math.min(4, incomingInvites.size()); i++) {
                int y = inviteY + i * 21;
                if (event.x() >= right + colWidth - 50 && event.x() <= right + colWidth - 6
                        && event.y() >= y - 3 && event.y() <= y + 15) {
                    String id = incomingInvites.get(i).get("id").getAsString();
                    runAction(GlobalServiceClient.get().acceptInvite(id),
                            "Invitation accepted. Opening the online board...");
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void authenticate(boolean register) {
        try {
            GlobalServiceClient.get().setBaseUrl(endpointField.getValue());
        } catch (RuntimeException error) {
            status = error.getMessage();
            return;
        }
        String username = usernameField.getValue().trim();
        String password = passwordField.getValue();
        if (username.isBlank() || password.isBlank()) {
            status = "Enter your PV username and password.";
            return;
        }
        status = register ? "Creating account..." : "Signing in...";
        var request = register
                ? GlobalServiceClient.get().register(username, password)
                : GlobalServiceClient.get().login(username, password);
        request.whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
            passwordField.setValue("");
            if (error != null) status = "Sign-in failed: " + message(error);
            else {
                status = "Connected as " + GlobalServiceClient.get().username();
                refreshData();
            }
        }));
    }

    private void addFriend() {
        if (!GlobalServiceClient.get().isAuthenticated()) {
            status = "Sign in first.";
            return;
        }
        String name = addFriendField.getValue().trim();
        if (name.isBlank()) {
            status = "Enter your friend's PV username.";
            return;
        }
        status = "Sending friend request...";
        runAction(GlobalServiceClient.get().addFriend(name), "Friend request sent.");
        addFriendField.setValue("");
    }

    private void refreshData() {
        if (!GlobalServiceClient.get().isAuthenticated()) return;
        var friendsFuture = GlobalServiceClient.get().friends();
        var requestsFuture = GlobalServiceClient.get().friendRequests();
        var invitesFuture = GlobalServiceClient.get().invites();
        CompletableFuture.allOf(friendsFuture, requestsFuture, invitesFuture)
                .whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        status = "Refresh failed: " + message(error);
                        return;
                    }
                    friends = objects(friendsFuture.join().getAsJsonArray("friends"));
                    incomingRequests = objects(requestsFuture.join().getAsJsonArray("incoming"));
                    incomingInvites = objects(invitesFuture.join().getAsJsonArray("invites"))
                            .stream().filter(item -> item.has("direction")
                                    && "incoming".equals(item.get("direction").getAsString())).toList();
                    status = "Global service connected.";
                }));
    }

    private void handleEvent(JsonObject event) {
        String type = event.has("type") ? event.get("type").getAsString() : "update";
        if ("match_started".equals(type)) {
            String matchId = event.get("matchId").getAsString();
            String color = event.get("color").getAsString();
            String fen = event.get("fen").getAsString();
            JsonObject opponent = event.has("opponent")
                    ? event.getAsJsonObject("opponent") : null;
            String opponentName = opponent != null && opponent.has("username")
                    ? opponent.get("username").getAsString() : "Friend";
            Minecraft.getInstance().setScreen(
                    new OnlineChessScreen(matchId, color, fen, opponentName));
            return;
        }
        status = switch (type) {
            case "friend_request" -> "New friend request received.";
            case "chess_invite" -> "You received a chess invitation.";
            case "match_state" -> "Online chess board updated.";
            case "presence" -> event.has("online") && event.get("online").getAsBoolean()
                    ? "A friend came online." : "A friend went offline.";
            default -> "Global service: " + type;
        };
        refreshData();
    }

    private void runAction(java.util.concurrent.CompletableFuture<JsonObject> future, String success) {
        future.whenComplete((response, error) -> Minecraft.getInstance().execute(() -> {
            status = error == null ? success : message(error);
            if (error == null) refreshData();
        }));
    }

    private static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> result = new ArrayList<>();
        if (array != null) {
            array.forEach(element -> {
                if (element.isJsonObject()) result.add(element.getAsJsonObject());
            });
        }
        return result;
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getCause() != null)
                && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Unknown connection error." : current.getMessage();
    }

    @Override
    public void onClose() {
        GlobalServiceClient.get().setEventListener(null);
        Minecraft.getInstance().setScreen(new ClickGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
