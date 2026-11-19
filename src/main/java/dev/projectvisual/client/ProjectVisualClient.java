package dev.projectvisual.client;

import dev.projectvisual.client.gui.ClickGuiScreen;
import dev.projectvisual.client.account.MinecraftLinkScreen;
import dev.projectvisual.client.account.ProjectVisualApiClient;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import dev.projectvisual.client.chess.ChessWorldGame;
import dev.projectvisual.client.minesweeper.MinesweeperWorldGame;
import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.hud.HudWindowManager;
import dev.projectvisual.client.VisualWorldEffects;
import dev.projectvisual.client.training.TrainingManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ProjectVisualClient implements ClientModInitializer {
    public static final String MOD_ID = "project_visual";
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("fromhaker").executes(context -> {
                ClickGuiScreen.unlockSecretTab();
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§dСекретная вкладка «Читы» открыта в PROJECT VISUAL."), false);
                }
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("pv")
                    .then(ClientCommandManager.literal("login").executes(context -> {
                        Minecraft.getInstance().setScreen(new MinecraftLinkScreen());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("logout").executes(context -> {
                        ProjectVisualApiClient.logout();
                        Minecraft client = Minecraft.getInstance();
                        if (client.player != null) client.player.displayClientMessage(
                                Component.literal("§7Аккаунт PROJECT VISUAL отключён."), false);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("friends").executes(context -> {
                        ProjectVisualApiClient.getFriends(result -> {
                            Minecraft client = Minecraft.getInstance();
                            if (client.player != null) client.player.displayClientMessage(
                                    Component.literal("§bPROJECT VISUAL §7" + result), false);
                        });
                        return 1;
                    })));
        });

        VisualHud.register();
        HudWindowManager.register();
        ProjectVisualApiClient.register();
        ChessWorldGame.register();
        MinesweeperWorldGame.register();
        VisualWorldEffects.register();
        ClientTickEvents.END_CLIENT_TICK.register(TrainingManager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new ClickGuiScreen());
                else if (client.screen instanceof ClickGuiScreen) client.setScreen(null);
            }
        });
    }
}
