package dev.projectvisual.client.account;

import dev.projectvisual.client.gui.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MinecraftLinkScreen extends Screen {
    private EditBox codeBox;
    private String status = "Получи одноразовый код в личном кабинете на сайте.";

    public MinecraftLinkScreen() {
        super(Component.literal("PROJECT VISUAL — вход"));
    }

    @Override
    protected void init() {
        codeBox = new EditBox(font, width / 2 - 110, height / 2 - 7, 220, 22,
                Component.literal("Код подключения"));
        codeBox.setMaxLength(12);
        codeBox.setHint(Component.literal("Например: PV-7K3D9Q"));
        addRenderableWidget(codeBox);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC9000000);
        int x = width / 2 - 170, y = height / 2 - 100;
        g.fill(x, y, x + 340, y + 205, 0xF00D0F18);
        g.fill(x, y, x + 340, y + 3, 0xFF9B7BFF);
        g.drawCenteredString(font, "ВХОД В PROJECT VISUAL", width / 2, y + 18, 0xFFF3F0FF);
        g.drawCenteredString(font, "1. Войди на сайт и создай код подключения", width / 2, y + 48, 0xFFAAA6BA);
        g.drawCenteredString(font, "2. Введи одноразовый код ниже", width / 2, y + 62, 0xFFAAA6BA);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, status, width / 2, y + 98, 0xFFE4DDF4);
        g.fill(width / 2 - 72, y + 132, width / 2 + 72, y + 160, 0xFF443568);
        g.drawCenteredString(font, "ПОДКЛЮЧИТЬ", width / 2, y + 142, 0xFFFFFFFF);
        g.drawCenteredString(font, "ESC — назад", width / 2, y + 178, 0xFFAAA6BA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int y = height / 2 - 100;
        if (event.button() == 0 && event.x() >= width / 2 - 72 && event.x() <= width / 2 + 72
                && event.y() >= y + 132 && event.y() <= y + 160) {
            status = "Подключаю...";
            ProjectVisualApiClient.pairWithWebsite(codeBox.getValue(), result -> status = result);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ClickGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
