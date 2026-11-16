package dev.projectvisual.client;

import dev.projectvisual.client.gui.ClickGuiScreen;
import dev.projectvisual.client.chess.ChessWorldGame;
import dev.projectvisual.client.minesweeper.MinesweeperWorldGame;
import dev.projectvisual.client.hud.VisualHud;
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
        VisualHud.register();
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
