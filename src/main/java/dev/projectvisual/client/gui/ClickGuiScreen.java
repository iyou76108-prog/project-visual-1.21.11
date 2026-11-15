package dev.projectvisual.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen extends Screen {
    private static final int PANEL = 0xF0121420;
    private static final int ACCENT = 0xFF9B7BFF;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;

    public ClickGuiScreen() {
        super(Component.literal("PROJECT VISUAL"));
    }

    @Override
    protected void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int left = (width - 320) / 2;
        int top = (height - 196) / 2;
        graphics.fill(left, top, left + 320, top + 196, PANEL);
        graphics.fill(left, top, left + 320, top + 2, ACCENT);
        graphics.fill(left + 16, top + 48, left + 304, top + 49, 0x553F3B50);

        graphics.text(font, "PROJECT VISUAL", left + 16, top + 16, TEXT, false);
        graphics.text(font, "VISUAL CLIENT  /  BUILD 0.1.0", left + 16, top + 32, MUTED, false);
        graphics.text(font, "Your visual workspace is ready.", left + 16, top + 66, TEXT, false);
        graphics.text(font, "FPS-first rendering. No heavy blur or background loops.", left + 16, top + 84, MUTED, false);
        graphics.text(font, "RIGHT SHIFT  -  Close menu", left + 16, top + 132, ACCENT, false);
        graphics.text(font, "ESC  -  Close menu", left + 16, top + 150, MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
