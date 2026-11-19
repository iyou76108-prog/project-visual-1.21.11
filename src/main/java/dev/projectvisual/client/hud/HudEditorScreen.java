package dev.projectvisual.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import dev.projectvisual.client.hud.HudDragCoordinates;
import net.minecraft.network.chat.Component;

/** Transparent HUD layout editor. The screen releases the game cursor while editing. */
public final class HudEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 258;
    private static final int PANEL_HEIGHT = 34;

    public HudEditorScreen() {
        super(Component.literal("PROJECT VISUAL — HUD Editor"));
    }

    @Override
    protected void init() {
        HudWindowManager.setEditMode(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = 8;
        int y = 8;
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xE8101420);
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, 0xFFB89AFF);
        graphics.drawString(font, "HUD EDITOR", x + 10, y + 6, 0xFFB89AFF, false);
        graphics.drawString(font, "Drag headers · − collapse · × hide", x + 10, y + 19, 0xFFE3E0EF, false);

        int closeX = width - 104;
        graphics.fill(closeX, 8, width - 8, 42, 0xE83A2636);
        graphics.drawCenteredString(font, "DONE  [ESC]", (closeX + width - 8) / 2, 21, 0xFFFFB5C4);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= width - 104 && event.y() <= 42) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        HudWindowManager.onEditorMouseButton(event.button(), true, event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        HudDragCoordinates.Point pointer = HudDragCoordinates.fromDrag(
                event.x(), event.y(), dragX, dragY);
        HudWindowManager.onEditorMouseMove(pointer.x(), pointer.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        HudWindowManager.onEditorMouseButton(event.button(), false, event.x(), event.y());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        HudWindowManager.setEditMode(false);
        Minecraft.getInstance().setScreen(null);
    }
}
