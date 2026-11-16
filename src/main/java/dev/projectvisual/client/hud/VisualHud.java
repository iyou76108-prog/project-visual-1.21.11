package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.training.TrainingManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class VisualHud {
    private static final int PANEL = 0xC910121B;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static final Deque<Notification> notifications = new ArrayDeque<>();
    private static long damageFlashUntil;
    private static int totemPops;
    private static Object lastWorld;

    private record Notification(String text, long expiresAt) {}

    private VisualHud() {}

    public static void triggerDamageFlash() {
        damageFlashUntil = System.currentTimeMillis() + VisualFeatures.damageTintDuration();
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

    private static HudWindowManager.Frame window(GuiGraphics g, String id, String title, int width,
                                                  int contentHeight, int x, int y, int screenWidth,
                                                  int screenHeight) {
        return HudWindowManager.begin(g, id, title, screenWidth, screenHeight, width, contentHeight,
                x, y, VisualFeatures.accentColor());
    }

    private static void text(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, value, x, y, color, false);
    }

    private static String fit(Minecraft mc, String value, int maxWidth) {
        if (mc.font.width(value) <= maxWidth) return value;
        String fitted = value;
        while (!fitted.isEmpty() && mc.font.width(fitted + "...") > maxWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        return fitted + "...";
    }

    private static void renderTrainerPanel(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight) {
        HudWindowManager.Frame frame = window(g, "trainer", "ТРЕНЕР", 252, 96,
                screenWidth - 264, 52, screenWidth, screenHeight);
        if (frame == null) return;
        text(g, fit(mc, TrainingManager.getQuestTitle(), frame.width()), frame.x(), frame.y(), TEXT);
        int barY = frame.y() + 19;
        g.fill(frame.x(), barY, frame.x() + frame.width(), barY + 7, 0xFF343545);
        int fillW = (int) Math.round(frame.width() * TrainingManager.getProgressRatio());
        if (fillW > 0) g.fill(frame.x(), barY, frame.x() + fillW, barY + 7, 0xFF69D8A7);
        text(g, TrainingManager.getProgressLabel(), frame.x(), barY + 12, TEXT);
        String stats = "КВЕСТЫ " + TrainingManager.getCompletedQuests()
                + "   КЕЙСЫ " + TrainingManager.getCaseCount();
        text(g, stats, frame.x(), barY + 28, MUTED);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        if (lastWorld != mc.level) {
            lastWorld = mc.level;
            totemPops = 0;
        }

        if (VisualFeatures.isEnabled("Damage Tint") && now < damageFlashUntil) {
            float remaining = Math.max(0.0F, Math.min(1.0F,
                    (float) (damageFlashUntil - now) / VisualFeatures.damageTintDuration()));
            for (int layer = 0; layer < 6; layer++) {
                int alpha = Math.max(0, (int) (remaining * VisualFeatures.damageTintOpacity() * (54 - layer * 8)));
                int tint = (alpha << 24) | (VisualFeatures.damageTintColor() & 0x00FFFFFF);
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

        if (VisualFeatures.isEnabled("Trainer")) {
            renderTrainerPanel(g, mc, screenWidth, screenHeight);
        }

        if (VisualFeatures.isEnabled("Watermark") || VisualFeatures.isEnabled("FPS Counter")
                || VisualFeatures.isEnabled("Ping")) {
            HudWindowManager.Frame frame = window(g, "watermark", "PROJECT VISUAL", 196, 46,
                    10, 10, screenWidth, screenHeight);
            if (frame != null) {
                if (VisualFeatures.isEnabled("Watermark")) {
                    text(g, "CLIENT  /  1.21.11", frame.x(), frame.y(), VisualFeatures.accentColor());
                }
                int y = frame.y() + 16;
                if (VisualFeatures.isEnabled("FPS Counter")) {
                    text(g, "FPS  " + mc.getFps(), frame.x(), y, TEXT);
                    y += 14;
                }
                if (VisualFeatures.isEnabled("Ping") && mc.getConnection() != null) {
                    var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) text(g, "PING  " + info.getLatency() + " ms", frame.x(), y, MUTED);
                }
            }
        }

        if (VisualFeatures.isEnabled("Keystrokes") || VisualFeatures.isEnabled("CPS Counter")) {
            HudWindowManager.Frame frame = window(g, "controls", "УПРАВЛЕНИЕ", 154, 52,
                    10, 70, screenWidth, screenHeight);
            if (frame != null) {
                int y = frame.y();
                if (VisualFeatures.isEnabled("Keystrokes")) {
                    String keys = (mc.options.keyUp.isDown() ? "W " : "_ ")
                            + (mc.options.keyLeft.isDown() ? "A " : "_ ")
                            + (mc.options.keyDown.isDown() ? "S " : "_ ")
                            + (mc.options.keyRight.isDown() ? "D" : "_");
                    text(g, "KEYS  " + keys, frame.x(), y, TEXT);
                    y += 16;
                    text(g, "JUMP " + (mc.options.keyJump.isDown() ? "ON" : "OFF"), frame.x(), y, MUTED);
                    y += 16;
                }
                if (VisualFeatures.isEnabled("CPS Counter")) {
                    text(g, "CPS  L " + leftClicks.size() + "  R " + rightClicks.size(),
                            frame.x(), y, VisualFeatures.accentColor());
                }
            }
        }

        if (VisualFeatures.isEnabled("Armor HUD") || VisualFeatures.isEnabled("Potion HUD")
                || VisualFeatures.isEnabled("Totem Counter")) {
            int potionCount = VisualFeatures.isEnabled("Potion HUD")
                    ? Math.min(5, mc.player.getActiveEffects().size()) : 0;
            int rows = (VisualFeatures.isEnabled("Armor HUD") ? 1 : 0)
                    + potionCount + (VisualFeatures.isEnabled("Totem Counter") ? 1 : 0);
            HudWindowManager.Frame frame = window(g, "player_stats", "СТАТУС ИГРОКА", 190,
                    12 + rows * 15, 10, 132, screenWidth, screenHeight);
            if (frame != null) {
                int y = frame.y();
                if (VisualFeatures.isEnabled("Armor HUD")) {
                    StringBuilder armor = new StringBuilder("БРОНЯ  ");
                    for (EquipmentSlot slot : new EquipmentSlot[]{
                            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        ItemStack stack = mc.player.getItemBySlot(slot);
                        armor.append(stack.isEmpty() ? "-" : stack.getMaxDamage() - stack.getDamageValue()).append("  ");
                    }
                    text(g, fit(mc, armor.toString(), frame.width()), frame.x(), y, TEXT);
                    y += 15;
                }
                if (VisualFeatures.isEnabled("Potion HUD")) {
                    int count = 0;
                    for (var effect : mc.player.getActiveEffects()) {
                        if (count++ >= 5) break;
                        String label = effect.getEffect().value().getDisplayName().getString()
                                + "  " + (effect.getDuration() / 20) + "s";
                        text(g, fit(mc, label, frame.width()), frame.x(), y, MUTED);
                        y += 15;
                    }
                }
                if (VisualFeatures.isEnabled("Totem Counter")) {
                    int count = 0;
                    for (ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
                        if (stack.is(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
                    }
                    ItemStack offhandTotem = mc.player.getOffhandItem();
                    if (offhandTotem.is(Items.TOTEM_OF_UNDYING)) count += offhandTotem.getCount();
                    text(g, "TOTEMS  " + count + "   POPS  " + totemPops, frame.x(), y, MUTED);
                }
            }
        }

        if (VisualFeatures.isEnabled("Target HUD") || VisualFeatures.isEnabled("Target Info")) {
            var target = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                    ? hit.getEntity() : null;
            if (target != null && target.isAlive() && target != mc.player) {
                HudWindowManager.Frame frame = window(g, "target", "ЦЕЛЬ", 174, 61,
                        screenWidth - 186, screenHeight - 88, screenWidth, screenHeight);
                if (frame != null) {
                    text(g, fit(mc, target.getName().getString(), frame.width()), frame.x(), frame.y(), TEXT);
                    String distance = String.format(java.util.Locale.ROOT, "%.1fm", mc.player.distanceTo(target));
                    text(g, distance, frame.x(), frame.y() + 15, MUTED);
                    if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                        float maxHealth = Math.max(1.0F, living.getMaxHealth());
                        float health = Math.max(0.0F, Math.min(maxHealth, living.getHealth()));
                        text(g, String.format(java.util.Locale.ROOT, "%.1f / %.1f HP", health, maxHealth),
                                frame.x(), frame.y() + 29, TEXT);
                        int barY = frame.y() + 46;
                        g.fill(frame.x(), barY, frame.x() + frame.width(), barY + 6, 0xFF393545);
                        int fillW = Math.max(0, Math.min(frame.width(), Math.round(frame.width() * health / maxHealth)));
                        if (fillW > 0) g.fill(frame.x(), barY, frame.x() + fillW, barY + 6, 0xFF57D99A);
                    }
                }
            }
        }

        if (VisualFeatures.isEnabled("Array List") || VisualFeatures.isEnabled("Module List")) {
            HudWindowManager.Frame frame = window(g, "arraylist", "АКТИВНЫЕ МОДУЛИ", 184, 146,
                    screenWidth - 196, 164, screenWidth, screenHeight);
            if (frame != null) {
                int y = frame.y();
                for (Map.Entry<String, Boolean> entry : VisualFeatures.getStates().entrySet()) {
                    if (entry.getValue() && !entry.getKey().equals("Watermark")
                            && !entry.getKey().equals("Array List") && !entry.getKey().equals("Module List")) {
                        text(g, fit(mc, entry.getKey(), frame.width()), frame.x(), y, TEXT);
                        y += 13;
                        if (y > frame.y() + frame.height() - 10) break;
                    }
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
            if (!notifications.isEmpty()) {
                int height = 8 + notifications.size() * 15;
                HudWindowManager.Frame frame = window(g, "notifications", "УВЕДОМЛЕНИЯ", 210, height,
                        screenWidth - 222, 10, screenWidth, screenHeight);
                if (frame != null) {
                    int y = frame.y();
                    for (Notification notification : notifications) {
                        text(g, fit(mc, notification.text(), frame.width()), frame.x(), y, TEXT);
                        y += 15;
                    }
                }
            }
        }
    }
}
