package dev.projectvisual.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClickGuiScreen extends Screen {
    private static final int PANEL = 0xF0121420;
    private static final int ACCENT = 0xFF9B7BFF;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;

    public ClickGuiScreen() { super(Component.literal("PROJECT VISUAL")); }

    @Override
    protected void init() {
        int left = (width - 300) / 2;
        int top = (height - 190) / 2;
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 218, top + 150, 70, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - 300) / 2;
        int top = (height - 190) / 2;
        graphics.fill(left, top, left + 300, top + 190, PANEL);
        graphics.fill(left, top, left + 300, top + 2, ACCENT);
        graphics.drawString(font, "PROJECT VISUAL", left + 16, top + 16, TEXT, false);
        graphics.drawString(font, "VISUAL CLIENT  /  BUILD 0.1.0", left + 16, top + 32, MUTED, false);
        graphics.fill(left + 16, top + 53, left + 284, top + 54, 0x553F3B50);
        graphics.drawString(font, "Welcome to your visual workspace.", left + 16, top + 68, TEXT, false);
        graphics.drawString(font, "Modules are being added in performance-first order.", left + 16, top + 84, MUTED, false);
        graphics.drawString(font, "RIGHT SHIFT  -  Toggle this menu", left + 16, top + 116, ACCENT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
