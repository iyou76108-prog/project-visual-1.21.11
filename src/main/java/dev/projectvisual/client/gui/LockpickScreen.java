package dev.projectvisual.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Original PROJECT VISUAL adaptation of Dota's Pick The Lock timing game.
 * The Watcher replaces the original character; only the timing/score concept is reused.
 */
public final class LockpickScreen extends Screen {
    private static final int PANEL = 0xF0141723;
    private static final int PANEL_EDGE = 0xFF65617E;
    private static final int TRACK = 0xFF252B39;
    private static final int GOLD = 0xFFFFD34E;
    private static final int BLUE = 0xFF50CFFF;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAAFC0;
    private static final int PURPLE = 0xFF9C6CFF;

    private final double[] zones = new double[4];
    private final int[] zoneTypes = {0, 0, 0, 1}; // 0 = score, 1 = time bonus
    private long lastFrameNanos;
    private double cursor = 0.04D;
    private double direction = 1.0D;
    private double remainingSeconds = 55.0D;
    private int score;
    private int streak;
    private int misses;
    private int round;
    private boolean finished;
    private boolean won;

    public LockpickScreen() {
        super(Component.literal("PROJECT VISUAL — Pick the Lock"));
        lastFrameNanos = System.nanoTime();
        rollZones();
    }

    private void rollZones() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < zones.length; i++) {
            boolean accepted;
            double candidate;
            int attempts = 0;
            do {
                candidate = 0.10D + random.nextDouble() * 0.80D;
                accepted = true;
                for (int j = 0; j < i; j++) {
                    if (Math.abs(zones[j] - candidate) < 0.14D) {
                        accepted = false;
                        break;
                    }
                }
            } while (!accepted && ++attempts < 100);
            zones[i] = candidate;
        }
        Arrays.sort(zones);
        // Keep the blue bonus in a different lane as the rounds change.
        int blueIndex = random.nextInt(zoneTypes.length);
        Arrays.fill(zoneTypes, 0);
        zoneTypes[blueIndex] = 1;
        round++;
    }

    private void update(long now) {
        if (finished) {
            lastFrameNanos = now;
            return;
        }
        double dt = Math.min(0.05D, Math.max(0.0D, (now - lastFrameNanos) / 1_000_000_000.0D));
        lastFrameNanos = now;
        remainingSeconds = Math.max(0.0D, remainingSeconds - dt);
        cursor += direction * dt * 0.92D;
        if (cursor >= 1.0D) {
            cursor = 1.0D;
            direction = -1.0D;
        } else if (cursor <= 0.0D) {
            cursor = 0.0D;
            direction = 1.0D;
        }
        if (score >= 6000) {
            won = true;
            finished = true;
        } else if (remainingSeconds <= 0.0D) {
            finished = true;
        }
    }

    private void attemptPick() {
        if (finished) return;
        update(System.nanoTime());
        double zoneHalfWidth = Math.max(0.018D, 0.034D - round * 0.0007D);
        for (int i = 0; i < zones.length; i++) {
            if (Math.abs(cursor - zones[i]) <= zoneHalfWidth) {
                streak++;
                direction = -direction;
                if (zoneTypes[i] == 1) {
                    remainingSeconds = Math.min(75.0D, remainingSeconds + 5.0D);
                } else {
                    score += 1000;
                }
                if (score >= 6000) {
                    won = true;
                    finished = true;
                } else {
                    rollZones();
                }
                return;
            }
        }
        misses++;
        streak = 0;
        remainingSeconds = Math.max(0.0D, remainingSeconds - 1.25D);
        if (remainingSeconds <= 0.0D) finished = true;
    }

    private void restart() {
        cursor = 0.04D;
        direction = 1.0D;
        remainingSeconds = 55.0D;
        score = 0;
        streak = 0;
        misses = 0;
        round = 0;
        finished = false;
        won = false;
        lastFrameNanos = System.nanoTime();
        rollZones();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        update(System.nanoTime());
        g.fill(0, 0, width, height, 0xEC080A12);

        // Soft starfield and the framed game panel.
        for (int i = 0; i < 36; i++) {
            int sx = (i * 137 + 41) % Math.max(1, width);
            int sy = (i * 83 + 19) % Math.max(1, height);
            g.fill(sx, sy, sx + 2, sy + 2, (i % 3 == 0) ? 0xFF6F61A8 : 0xFF343B58);
        }

        int panelW = Math.min(900, width - 28);
        int panelH = Math.min(430, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, PANEL_EDGE);
        g.fill(px, py, px + panelW, py + panelH, PANEL);
        g.fill(px, py, px + panelW, py + 3, PURPLE);

        g.drawString(font, "PICK THE LOCK", px + 26, py + 19, TEXT, false);
        g.drawString(font, "PROJECT VISUAL  /  WATCHER'S CHALLENGE", px + 26, py + 38, MUTED, false);

        drawWatcher(g, px + 145, py + 160);
        g.drawString(font, "СМОТРЯЩИЙ", px + 77, py + 296, 0xFFE6DFFF, false);
        g.drawString(font, "«Попробуй открыть.»", px + 57, py + 313, MUTED, false);

        int contentX = px + 300;
        int right = px + panelW - 28;
        g.drawString(font, "LOCK CONTROL", contentX, py + 83, 0xFFC7B6FF, false);

        g.drawString(font, "SCORE", contentX, py + 112, MUTED, false);
        g.drawString(font, score + " / 6000", contentX, py + 130, GOLD, false);
        g.drawString(font, "TIME", contentX + 145, py + 112, MUTED, false);
        g.drawString(font, String.format(java.util.Locale.ROOT, "%.1fs", remainingSeconds),
                contentX + 145, py + 130, remainingSeconds < 10.0D ? 0xFFFF6677 : BLUE, false);
        g.drawString(font, "STREAK  " + streak + "     MISSES  " + misses, contentX + 300,
                py + 130, MUTED, false);

        int trackX = contentX;
        int trackW = Math.max(180, right - contentX);
        int trackY = py + 205;
        int trackH = 34;
        g.drawString(font, "Нажми ЛКМ, когда метка окажется внутри жёлтой или синей зоны",
                contentX, trackY - 27, TEXT, false);
        g.fill(trackX, trackY, trackX + trackW, trackY + trackH, TRACK);
        g.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF596276);
        g.fill(trackX, trackY + trackH - 2, trackX + trackW, trackY + trackH, 0xFF596276);

        double halfWidth = Math.max(0.018D, 0.034D - round * 0.0007D);
        for (int i = 0; i < zones.length; i++) {
            int color = zoneTypes[i] == 1 ? BLUE : GOLD;
            int left = trackX + (int) ((zones[i] - halfWidth) * trackW);
            int rightEdge = trackX + (int) ((zones[i] + halfWidth) * trackW);
            g.fill(left, trackY + 5, rightEdge, trackY + trackH - 5, color);
            g.fill(left, trackY + 5, rightEdge, trackY + 8, 0x99FFFFFF);
        }

        int markerX = trackX + (int) (cursor * trackW);
        g.fill(markerX - 2, trackY - 10, markerX + 3, trackY + trackH + 10, 0xFFFFFFFF);
        g.fill(markerX - 6, trackY - 14, markerX + 7, trackY - 9, 0xFFEEE8FF);
        g.fill(markerX - 6, trackY + trackH + 9, markerX + 7, trackY + trackH + 14, 0xFFEEE8FF);

        g.fill(contentX, py + 281, contentX + 15, py + 296, GOLD);
        g.drawString(font, "+1000 очков", contentX + 23, py + 285, MUTED, false);
        g.fill(contentX + 150, py + 281, contentX + 165, py + 296, BLUE);
        g.drawString(font, "+5 секунд", contentX + 173, py + 285, MUTED, false);
        g.drawString(font, "Раунд " + round + "   ·   Зоны постепенно сужаются", contentX, py + 323,
                MUTED, false);

        if (finished) {
            g.fill(px + 250, py + 105, px + panelW - 30, py + 270, 0xE90B0D17);
            g.drawString(font, won ? "ЗАМОК ОТКРЫТ!" : "ВРЕМЯ ВЫШЛО",
                    px + 285, py + 133, won ? 0xFF86F0B5 : 0xFFFF718A, false);
            g.drawString(font, "Итоговый счёт: " + score, px + 285, py + 160, TEXT, false);
            g.drawString(font, "Нажми кнопку ниже, чтобы попробовать снова.", px + 285, py + 187,
                    MUTED, false);
            int bx = px + 285, by = py + 218;
            g.fill(bx, by, bx + 190, by + 32, 0xFF51417D);
            g.drawString(font, "ИГРАТЬ ЕЩЁ", bx + 55, by + 11, TEXT, false);
        } else {
            g.drawString(font, "Цель: набрать 6000 очков до конца времени.",
                    contentX, py + panelH - 37, 0xFFB9C1D4, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawWatcher(GuiGraphics g, int cx, int top) {
        // Pixel-art Watcher: featureless dark face, white eyes, long coat and pale hood trim.
        g.fill(cx - 66, top + 64, cx + 66, top + 205, 0xFF090B13);
        g.fill(cx - 82, top + 92, cx - 45, top + 205, 0xFF171827);
        g.fill(cx + 45, top + 92, cx + 82, top + 205, 0xFF171827);
        g.fill(cx - 80, top + 94, cx - 62, top + 180, 0xFFB8BBC8);
        g.fill(cx + 62, top + 94, cx + 80, top + 180, 0xFFB8BBC8);
        g.fill(cx - 57, top + 21, cx + 57, top + 122, 0xFF171827);
        g.fill(cx - 47, top + 28, cx + 47, top + 115, 0xFF04050A);
        g.fill(cx - 66, top + 39, cx - 42, top + 104, 0xFF171827);
        g.fill(cx + 42, top + 39, cx + 66, top + 104, 0xFF171827);
        // Fur edging around the hood.
        for (int i = 0; i < 7; i++) {
            int y = top + 35 + i * 11;
            g.fill(cx - 63, y, cx - 48, y + 7, 0xFFD9D8E1);
            g.fill(cx + 48, y, cx + 63, y + 7, 0xFFD9D8E1);
        }
        g.fill(cx - 29, top + 70, cx - 8, top + 78, 0xFFF9F8FF);
        g.fill(cx + 8, top + 70, cx + 29, top + 78, 0xFFF9F8FF);
        g.fill(cx - 27, top + 72, cx - 10, top + 76, 0xFFFFFFFF);
        g.fill(cx + 10, top + 72, cx + 27, top + 76, 0xFFFFFFFF);
        g.fill(cx - 22, top + 123, cx + 22, top + 131, 0xFFB9B7C8);
        g.fill(cx - 5, top + 139, cx + 5, top + 154, 0xFFD8D9E3);
        g.fill(cx - 3, top + 161, cx + 3, top + 176, 0xFFD8D9E3);
        g.fill(cx - 3, top + 184, cx + 3, top + 198, 0xFFD8D9E3);
        g.fill(cx - 90, top + 205, cx + 90, top + 214, 0xFF0A0C15);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int panelW = Math.min(900, width - 28);
        int panelH = Math.min(430, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        int contentX = px + 300;
        int trackX = contentX;
        int trackW = Math.max(180, px + panelW - 28 - contentX);
        int trackY = py + 205;
        if (finished) {
            int bx = px + 285, by = py + 218;
            if (event.x() >= bx && event.x() <= bx + 190
                    && event.y() >= by && event.y() <= by + 32) {
                restart();
            }
            return true;
        }
        if (event.x() >= trackX - 12 && event.x() <= trackX + trackW + 12
                && event.y() >= trackY - 18 && event.y() <= trackY + 52) {
            attemptPick();
            return true;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
