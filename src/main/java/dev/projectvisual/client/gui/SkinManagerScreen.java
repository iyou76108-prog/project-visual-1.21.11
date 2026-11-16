package dev.projectvisual.client.gui;

import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.training.TrainingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ThreadLocalRandom;

public final class SkinManagerScreen extends Screen {
    private static final int BG = 0xF00D0F18;
    private static final int CARD = 0xFF1D2130;
    private static final int HOVER = 0xFF292E40;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;
    private static final SkinOption[] SKINS = {
            new SkinOption("Queen Skin", "Королева", "3D-модель шахматной королевы"),
            new SkinOption("Invoker Skin", "Инвокер", "Маг с тремя вращающимися сферами"),
            new SkinOption("Interworlder Mask", "Маска Междумирца", "Маска на голове персонажа"),
            new SkinOption("Magnus AFK", "Магнус AFK", "Случайная сценка после простоя"),
            new SkinOption("Axe AFK", "Акс AFK", "Акс появляется и замахивается"),
            new SkinOption("Watcher AFK", "Смотрящий AFK", "Смотрящий затягивает в пустоту"),
            new SkinOption("Guts", "Гатс", "Доспехи, плащ и огромный меч"),
            new SkinOption("Akashi Seijuro", "Акаши Сейджуро", "Красные волосы и форма №4"),
            new SkinOption("Dual Blades", "Клинки в обеих руках", "Два клинка поверх модели"),
            new SkinOption("Flying Spellbook", "Летающая книга заклинаний", "Книга парит у плеча")
    };

    private long caseStartedAt;
    private String caseReward;
    private boolean rewardGranted;
    private String lastReward;
    private long lastRewardAt;

    public SkinManagerScreen() {
        super(Component.literal("PROJECT VISUAL — Skin Manager"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB9000000);
        int panelW = Math.min(780, width - 28);
        int panelH = Math.min(490, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        g.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0xFF5B5278);
        g.fill(px, py, px + panelW, py + panelH, BG);
        g.fill(px, py, px + panelW, py + 3, VisualFeatures.accentColor());

        g.drawString(font, "СКИНЫ", px + 20, py + 17, TEXT, false);
        g.drawString(font, "Выбери образ. Четыре редких скина открываются только из тренировочных кейсов.",
                px + 20, py + 37, MUTED, false);

        int gridX = px + 20;
        int gridY = py + 70;
        int gap = 10;
        int cardW = (panelW - 50) / 2;
        int cardH = 47;
        for (int i = 0; i < SKINS.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int x = gridX + col * (cardW + gap);
            int y = gridY + row * (cardH + 8);
            SkinOption skin = SKINS[i];
            boolean unlocked = TrainingManager.isUnlocked(skin.id());
            boolean active = VisualFeatures.isEnabled(skin.id());
            boolean hover = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH;
            g.fill(x, y, x + cardW, y + cardH, hover ? HOVER : CARD);
            g.fill(x, y, x + 2, y + cardH, active ? 0xFF67DDAA : unlocked ? 0xFF565C70 : 0xFF8B6C3B);
            g.drawString(font, skin.title(), x + 10, y + 7,
                    unlocked ? TEXT : 0xFFD8C59C, false);
            g.drawString(font, skin.description(), x + 10, y + 25, MUTED, false);
            String state = !unlocked ? "ЗАКРЫТ" : active ? "ON" : "OFF";
            int stateColor = !unlocked ? 0xFFFFC66D : active ? 0xFF83E0B0 : MUTED;
            g.drawString(font, state, x + cardW - font.width(state) - 9, y + 8, stateColor, false);
        }

        int caseY = py + panelH - 92;
        g.fill(px + 20, caseY, px + panelW - 20, caseY + 72, 0xFF191B2A);
        g.fill(px + 20, caseY, px + 23, caseY + 72, 0xFFB58B4D);
        g.drawString(font, "ТРЕНИРОВОЧНЫЙ КЕЙС", px + 34, caseY + 9, 0xFFFFD58A, false);
        g.drawString(font, "В кейсе случайный скин: 1 кейс за каждые 2 выполненных квеста.",
                px + 34, caseY + 27, MUTED, false);

        int buttonX = px + panelW - 188;
        int buttonY = caseY + 41;
        boolean canOpen = TrainingManager.getCaseCount() > 0 && caseReward == null;
        g.fill(buttonX, buttonY, buttonX + 150, buttonY + 24,
                canOpen ? 0xFF55407E : 0xFF343545);
        String openText = "ОТКРЫТЬ  (" + TrainingManager.getCaseCount() + ")";
        g.drawCenteredString(font, openText, buttonX + 75, buttonY + 8, canOpen ? TEXT : MUTED);

        if (caseReward != null) {
            long elapsed = System.currentTimeMillis() - caseStartedAt;
            boolean finished = elapsed >= 4300L;
            if (finished && !rewardGranted) {
                TrainingManager.unlockSkin(caseReward);
                rewardGranted = true;
                lastReward = caseReward;
                lastRewardAt = System.currentTimeMillis();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§6КЕЙС §fПолучен скин: §e"
                            + TrainingManager.getSkinDisplayName(caseReward)), false);
                }
            }

            int overlayTop = py + panelH / 2 - 38;
            g.fill(px + 90, overlayTop - 16, px + panelW - 90, overlayTop + 62, 0xF00B0D16);
            g.drawCenteredString(font, finished ? "НАГРАДА" : "ОТКРЫТИЕ КЕЙСА",
                    px + panelW / 2, overlayTop - 5, 0xFFFFD58A);

            String shown;
            if (finished) {
                shown = TrainingManager.getSkinDisplayName(caseReward);
            } else {
                int index = (int) ((elapsed / Math.max(55L, 270L + Math.max(0L, elapsed - 2400L) / 5L))
                        % SKINS.length);
                if (elapsed > 3500L) {
                    shown = TrainingManager.getSkinDisplayName(caseReward);
                } else {
                    shown = SKINS[index].title();
                }
            }
            g.drawCenteredString(font, shown, px + panelW / 2, overlayTop + 22,
                    finished ? 0xFF85E8B0 : TEXT);
        } else if (lastReward != null && System.currentTimeMillis() - lastRewardAt < 5500L) {
            g.drawString(font, "Последний выигрыш: " + TrainingManager.getSkinDisplayName(lastReward),
                    px + 34, caseY + 51, 0xFF85E8B0, false);
        }

        g.drawString(font, "Выполнено квестов: " + TrainingManager.getCompletedQuests(),
                px + 20, py + panelH - 16, MUTED, false);
        g.drawString(font, "ESC — назад", px + panelW - 94, py + panelH - 16, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void beginCaseOpening() {
        if (caseReward != null) return;
        String reward = TrainingManager.beginCaseOpening();
        if (reward == null) return;
        caseReward = reward;
        rewardGranted = false;
        caseStartedAt = System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mouseX = event.x();
        double mouseY = event.y();
        int panelW = Math.min(780, width - 28);
        int panelH = Math.min(490, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        int gridX = px + 20;
        int gridY = py + 70;
        int gap = 10;
        int cardW = (panelW - 50) / 2;
        int cardH = 47;

        if (caseReward != null && System.currentTimeMillis() - caseStartedAt < 4300L) return true;

        for (int i = 0; i < SKINS.length; i++) {
            int col = i % 2, row = i / 2;
            int x = gridX + col * (cardW + gap);
            int y = gridY + row * (cardH + 8);
            if (mouseX < x || mouseX > x + cardW || mouseY < y || mouseY > y + cardH) continue;
            SkinOption skin = SKINS[i];
            if (!TrainingManager.isUnlocked(skin.id())) return true;
            VisualFeatures.toggle(skin.id());
            VisualHud.notifyToggle(skin.title(), VisualFeatures.isEnabled(skin.id()));
            return true;
        }

        int caseY = py + panelH - 92;
        int buttonX = px + panelW - 188;
        int buttonY = caseY + 41;
        if (mouseX >= buttonX && mouseX <= buttonX + 150
                && mouseY >= buttonY && mouseY <= buttonY + 24) {
            beginCaseOpening();
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ClickGuiScreen());
    }

    private record SkinOption(String id, String title, String description) {}
}
