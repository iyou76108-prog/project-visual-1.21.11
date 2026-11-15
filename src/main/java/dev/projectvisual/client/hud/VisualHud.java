package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
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

    private record Notification(String text, long expiresAt) {}

    private VisualHud() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void triggerDamageFlash() {
        damageFlashUntil = System.currentTimeMillis() + 420L;
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

    private static void line(GuiGraphics g, Minecraft mc, String value, int x, int y) {
        int w = mc.font.width(value);
        g.fill(x - 4, y - 3, x + w + 5, y + 10, PANEL);
        g.fill(x - 4, y - 3, x - 2, y + 10, VisualFeatures.accentColor());
        g.drawString(mc.font, value, x, y, TEXT, false);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options.hideGui || mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        if (VisualFeatures.isEnabled("Damage Tint") && now < damageFlashUntil) {
            float remaining = (float) (damageFlashUntil - now) / 420.0F;
            int alpha = Math.max(0, Math.min(72, (int) (remaining * 72.0F)));
            g.fill(0, 0, screenWidth, screenHeight, (alpha << 24) | 0x00F04444);
        }
        if (VisualFeatures.isEnabled("Ambience")) {
            g.fill(0, 0, screenWidth, screenHeight, 0x0B9B7BFF);
        }

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
            if (target != null && target.isAlive()) {
                String label = target.getName().getString();
                if (target instanceof net.minecraft.world.entity.LivingEntity living) {
                    label += "  " + String.format(java.util.Locale.ROOT, "%.1f", living.getHealth()) + " HP";
                }
                if (VisualFeatures.isEnabled("Target HUD")) {
                    line(g, mc, "TARGET  " + label, x, yy);
                    yy += 17;
                }
                if (VisualFeatures.isEnabled("Target Info")) {
                    line(g, mc, "DIST  " + String.format(java.util.Locale.ROOT, "%.1f",
                            mc.player.distanceTo(target)) + "m", x, yy);
                    yy += 17;
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
            line(g, mc, "TOTEMS  " + count, x, yy);
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

        if (VisualFeatures.isEnabled("Crosshair")) {
            int cx = screenWidth / 2;
            int cy = screenHeight / 2;
            int color = VisualFeatures.accentColor();
            if (VisualFeatures.isEnabled("Hit Color")) {
                color = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult
                        ? 0xFF57E39A : 0xFFFF646E;
            }
            g.fill(cx - 5, cy, cx + 5, cy + 1, color);
            g.fill(cx, cy - 5, cx + 1, cy + 5, color);
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
