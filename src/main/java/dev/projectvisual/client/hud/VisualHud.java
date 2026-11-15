package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class VisualHud {
    private static final int PANEL = 0xC910121B;
    private static final int ACCENT = 0xFF9B7BFF;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;

    private VisualHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "visual_hud"),
                VisualHud::render);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.player == null || client.level == null) return;

        int x = 10, y = 10, width = 118, height = 40;
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + 2, y + height, ACCENT);
        graphics.fill(x + 10, y + 9, x + 15, y + 14, ACCENT);
        graphics.text(client.font, "PROJECT VISUAL", x + 21, y + 7, TEXT, false);
        graphics.text(client.font, "FPS", x + 10, y + 24, MUTED, false);
        graphics.text(client.font, Integer.toString(client.getFps()), x + 36, y + 24, TEXT, false);
        graphics.text(client.font, "1.21.11", x + 80, y + 24, ACCENT, false);
    }
}
