package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.training.TrainingManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class VisualHud {
    private static final int PANEL = 0xC910121B;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;
    private static boolean enabled = true;
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static final Deque<Notification> notifications = new ArrayDeque<>();
    private static long damageFlashUntil;
    private static int totemPops;
    private static Object lastWorld;

    private record Notification(String text, long expiresAt) {}

    private VisualHud() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void triggerDamageFlash() {
        damageFlashUntil = System.currentTimeMillis() + 520L;
    }

    public static void recordTotemPop() {
        totemPops++;
    }

    public static void notifyToggle(String name, boolean active) {
        if (!VisualFeatures.isEnabled("Notifications")) return;
        String message = name + (active ? " enabled" : " disabled");
        notifications.addLast(new Notification(message, System.currentTimeMillis() + 2600L));
        while (notifications.size() > 4) notifications.removeFirst();
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "visual_hud"), VisualHud::render);
    }

    public static void recordMouseClick(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) leftClicks.addLast(now);
        if (button == 1) rightClicks.addLast(now);
        while (!leftClicks.isEmpty() && now - leftClicks.peekFirst() > 1000L) leftClicks.removeFirst();
        while (!rightClicks.isEmpty() && now - rightClicks.peekFirst() > 1000L) rightClicks.removeFirst();
    }

    private static void renderTrainerPanel(GuiGraphics g, Minecraft mc, int screenWidth) {
        int panelW = 246;
        int panelH = 88;
        int panelX = screenWidth - panelW - 12;
        int panelY = 52;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD910121D);
        g.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFFD7A85D);
        g.fill(panelX, panelY, panelX + 2, panelY + panelH, VisualFeatures.accentColor());
        g.drawString(mc.font, "ТРЕНЕР  /  ЕЖЕДНЕВНОЕ ЗАДАНИЕ",
                panelX + 10, panelY + 8, 0xFFFFD58A, false);
        String quest = TrainingManager.getQuestTitle();
        g.drawString(mc.font, fitLine(mc, quest, panelW - 20),
                panelX + 10, panelY + 27, TEXT, false);
        int barX = panelX + 10;
        int barY = panelY + 47;
        int barW = panelW - 20;
        g.fill(barX, barY, barX + barW, barY + 7, 0xFF343545);
        int fillW = (int) Math.round(barW * TrainingManager.getProgressRatio());
        if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + 7, 0xFF69D8A7);
        String stats = "КВЕСТЫ " + TrainingManager.getCompletedQuests()
                + "   КЕЙСЫ " + TrainingManager.getCaseCount();
        g.drawString(mc.font, stats, panelX + 10, panelY + 64, MUTED, false);
    }

    private static String fitLine(Minecraft mc, String value, int maxWidth) {
        if (mc.font.width(value) <= maxWidth) return value;
        String suffix = "...";
        String fitted = value;
        while (!fitted.isEmpty() && mc.font.width(fitted + suffix) > maxWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted + suffix;
    }

    private static void line(GuiGraphics g, Minecraft mc, String value, int x, int y) {
        int w = mc.font.width(value);
        g.fill(x - 4, y - 3, x + w + 5, y + 10, PANEL);
        g.fill(x - 4, y - 3, x - 2, y + 10, VisualFeatures.accentColor());
        g.drawString(mc.font, value, x, y, TEXT, false);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        if (!enabled) {
            if (VisualFeatures.isEnabled("Trainer")) renderTrainerPanel(g, mc, screenWidth);
            return;
        }

        long now = System.currentTimeMillis();

        if (lastWorld != mc.level) {
            lastWorld = mc.level;
            totemPops = 0;
        }
        if (VisualFeatures.isEnabled("Damage Tint") && now < damageFlashUntil) {
            float remaining = Math.max(0.0F, Math.min(1.0F, (float) (damageFlashUntil - now) / 520.0F));
            for (int layer = 0; layer < 6; layer++) {
                int alpha = Math.max(0, (int) (remaining * (54 - layer * 8)));
                int tint = (alpha << 24) | 0x00F04444;
                int inset = layer * 7;
                int band = 7;
                g.fill(0, inset, screenWidth, inset + band, tint);
                g.fill(0, screenHeight - inset - band, screenWidth, screenHeight - inset, tint);
                g.fill(inset, 0, inset + band, screenHeight, tint);
                g.fill(screenWidth - inset - band, 0, screenWidth - inset, screenHeight, tint);
            }
        }
        if (VisualFeatures.isEnabled("Ambience")) {
            g.fill(0, 0, screenWidth, screenHeight, 0x0B9B7BFF);
        }

        if (VisualFeatures.isEnabled("Trainer")) renderTrainerPanel(g, mc, screenWidth);

        int x = 10;
        int y = 10;

        if (VisualFeatures.isEnabled("Watermark")) {
            g.fill(x, y, x + 142, y + 40, PANEL);
            g.fill(x, y, x + 2, y + 40, VisualFeatures.accentColor());
            g.fill(x + 10, y + 9, x + 15, y + 14, VisualFeatures.accentColor());
            g.drawString(mc.font, "PROJECT VISUAL", x + 21, y + 7, TEXT, false);
            if (VisualFeatures.isEnabled("FPS Counter")) {
                g.drawString(mc.font, "FPS  " + mc.getFps(), x + 10, y + 24, MUTED, false);
            }
            if (VisualFeatures.isEnabled("Ping")) {
                var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) g.drawString(mc.font, "PING  " + info.getLatency() + " ms",
                        x + 77, y + 24, VisualFeatures.accentColor(), false);
            }
        } else if (VisualFeatures.isEnabled("FPS Counter")) {
            line(g, mc, "FPS  " + mc.getFps(), x, y);
        }

        int yy = 56;
        if (VisualFeatures.isEnabled("Keystrokes")) {
            String keys = (mc.options.keyUp.isDown() ? "W " : "_ ")
                    + (mc.options.keyLeft.isDown() ? "A " : "_ ")
                    + (mc.options.keyDown.isDown() ? "S " : "_ ")
                    + (mc.options.keyRight.isDown() ? "D" : "_");
            line(g, mc, "KEYS  " + keys, x, yy);
            yy += 17;
            line(g, mc, "JUMP " + (mc.options.keyJump.isDown() ? "ON" : "OFF"), x, yy);
            yy += 17;
        }
        if (VisualFeatures.isEnabled("CPS Counter")) {
            line(g, mc, "CPS  L " + leftClicks.size() + "  R " + rightClicks.size(), x, yy);
            yy += 17;
        }
        if (VisualFeatures.isEnabled("Target HUD") || VisualFeatures.isEnabled("Target Info")) {
            var target = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    ? hit.getEntity() : null;
            if (target != null && target.isAlive() && target != mc.player) {
                int cardW = 158;
                int cardH = 52;
                int bx = screenWidth - cardW - 12;
                int by = screenHeight - cardH - 14;
                g.fill(bx, by, bx + cardW, by + cardH, PANEL);
                g.fill(bx, by, bx + 2, by + cardH, VisualFeatures.accentColor());
                String name = target.getName().getString();
                while (mc.font.width(name) > cardW - 62 && name.length() > 5) {
                    name = name.substring(0, name.length() - 4) + "...";
                }
                g.drawString(mc.font, name, bx + 9, by + 7, TEXT, false);
                String distance = String.format(java.util.Locale.ROOT, "%.1fm", mc.player.distanceTo(target));
                g.drawString(mc.font, distance, bx + cardW - mc.font.width(distance) - 8, by + 7, MUTED, false);
                if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                    float maxHealth = Math.max(1.0F, living.getMaxHealth());
                    float health = Math.max(0.0F, Math.min(maxHealth, living.getHealth()));
                    String hp = String.format(java.util.Locale.ROOT, "%.1f / %.1f HP", health, maxHealth);
                    g.drawString(mc.font, hp, bx + 9, by + 20, TEXT, false);
                    int barX = bx + 9;
                    int barY = by + 34;
                    int barW = cardW - 18;
                    g.fill(barX, barY, barX + barW, barY + 5, 0xFF393545);
                    int fillW = Math.max(0, Math.min(barW, Math.round(barW * health / maxHealth)));
                    if (fillW > 0) g.fill(barX, barY, barX + fillW, barY + 5, 0xFF57D99A);
                    String armor = "ARMOR " + living.getArmorValue();
                    g.drawString(mc.font, armor, bx + 9, by + 41, MUTED, false);
                } else {
                    g.drawString(mc.font, "ENTITY", bx + 9, by + 25, MUTED, false);
                }
            }
        }
        if (VisualFeatures.isEnabled("Armor HUD")) {
            StringBuilder armor = new StringBuilder("ARMOR  ");
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                armor.append(stack.isEmpty() ? "-" : stack.getMaxDamage() - stack.getDamageValue()).append("  ");
            }
            line(g, mc, armor.toString(), x, yy);
            yy += 17;
        }
        if (VisualFeatures.isEnabled("Potion HUD")) {
            int count = 0;
            for (var effect : mc.player.getActiveEffects()) {
                if (count++ >= 5) break;
                line(g, mc, effect.getEffect().value().getDisplayName().getString()
                        + "  " + (effect.getDuration() / 20) + "s", x, yy);
                yy += 15;
            }
        }
        if (VisualFeatures.isEnabled("Totem Counter")) {
            int count = 0;
            for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
                if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) count += stack.getCount();
            }
            ItemStack offhandTotem = mc.player.getOffhandItem();
            if (offhandTotem.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                count += offhandTotem.getCount();
            }
            line(g, mc, "TOTEMS  " + count + "   POPS  " + totemPops, x, yy);
        }

        if (VisualFeatures.isEnabled("Array List") || VisualFeatures.isEnabled("Module List")) {
            int ry = 10;
            for (Map.Entry<String, Boolean> entry : VisualFeatures.getStates().entrySet()) {
                if (entry.getValue() && !entry.getKey().equals("Watermark")
                        && !entry.getKey().equals("Array List") && !entry.getKey().equals("Module List")) {
                    int w = mc.font.width(entry.getKey());
                    int rx = screenWidth - w - 14;
                    g.fill(rx - 4, ry - 2, rx + w + 5, ry + 11, PANEL);
                    g.fill(rx + w + 3, ry - 2, rx + w + 5, ry + 11, VisualFeatures.accentColor());
                    g.drawString(mc.font, entry.getKey(), rx, ry, TEXT, false);
                    ry += 14;
                }
            }
        }

        boolean customCrosshair = VisualFeatures.isEnabled("Crosshair") || VisualFeatures.isEnabled("Hit Color");
        if (customCrosshair) {
            int cx = screenWidth / 2;
            int cy = screenHeight / 2;
            int color = VisualFeatures.accentColor();
            if (VisualFeatures.isEnabled("Hit Color")
                    && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                    && entityHit.getEntity().isAlive()) {
                color = 0xFF58E6A0;
            }
            g.fill(cx - 1, cy - 7, cx + 1, cy - 3, color);
            g.fill(cx - 1, cy + 3, cx + 1, cy + 7, color);
            g.fill(cx - 7, cy - 1, cx - 3, cy + 1, color);
            g.fill(cx + 3, cy - 1, cx + 7, cy + 1, color);
            g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
        }

        if (VisualFeatures.isEnabled("Notifications")) {
            while (!notifications.isEmpty() && notifications.peekFirst().expiresAt() < now) {
                notifications.removeFirst();
            }
            int ny = 12;
            for (Notification notification : notifications) {
                int width = mc.font.width(notification.text()) + 18;
                int nx = screenWidth - width - 12;
                g.fill(nx, ny, nx + width, ny + 20, PANEL);
                g.fill(nx, ny, nx + 2, ny + 20, VisualFeatures.accentColor());
                g.drawString(mc.font, notification.text(), nx + 8, ny + 6, TEXT, false);
                ny += 24;
            }
        }
    }
}
