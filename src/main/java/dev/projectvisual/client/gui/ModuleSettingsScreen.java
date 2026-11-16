package dev.projectvisual.client.gui;

import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.hud.HudWindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ModuleSettingsScreen extends Screen {
    private static final int BG = 0xF00D0F18;
    private static final int CARD = 0xFF1D2130;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;

    private final String module;
    private final int returnCategory;

    public ModuleSettingsScreen(String module, int returnCategory) {
        super(Component.literal("PROJECT VISUAL — " + module));
        this.module = module;
        this.returnCategory = returnCategory;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        switch (module) {
            case "Hit Particles" -> {
                rows.add(new Row("Тип частиц", particleStyleName(), RowType.CYCLE));
                rows.add(new Row("Количество частиц", VisualFeatures.hitParticleCount() + " / 24", RowType.PARTICLE_COUNT));
            }
            case "Damage Tint" -> {
                rows.add(new Row("Цвет эффекта", tintName(), RowType.TINT_COLOR));
                rows.add(new Row("Длительность", VisualFeatures.damageTintDuration() + " ms", RowType.TINT_DURATION));
                rows.add(new Row("Непрозрачность", String.format(java.util.Locale.ROOT, "%.0f%%",
                        VisualFeatures.damageTintOpacity() * 100.0F), RowType.TINT_OPACITY));
            }
            case "Hitboxes" -> {
                rows.add(new Row("Размер расширения", String.format(java.util.Locale.ROOT, "%.2f",
                        VisualFeatures.hitboxExpansion()), RowType.HITBOX_EXPANSION));
                rows.add(new Row("Сброс размера", "0.50", RowType.HITBOX_RESET));
            }
            case "Hitbox Visual" -> {
                rows.add(new Row("Толщина контура", String.format(java.util.Locale.ROOT, "%.1f px",
                        VisualFeatures.hitboxVisualWidth()), RowType.HITBOX_WIDTH));
                rows.add(new Row("Сброс толщины", "2.0 px", RowType.HITBOX_WIDTH_RESET));
            }
            case "Swing Animation" -> {
                rows.add(new Row("Стиль замаха", swingStyleName(), RowType.SWING_STYLE));
                rows.add(new Row("Сила анимации", String.format(java.util.Locale.ROOT, "%.0f%%",
                        VisualFeatures.swingStrength() * 100.0F), RowType.SWING_STRENGTH));
            }
            case "Perspective" -> rows.add(new Row("Ракурс камеры",
                    VisualFeatures.perspectiveMode() == 0 ? "От третьего лица — сзади" : "От третьего лица — спереди",
                    RowType.PERSPECTIVE_MODE));
            case "Camera Clip" -> rows.add(new Row("Коллизия камеры", "Камера проходит через блоки", RowType.INFO));
            case "Trainer" -> rows.add(new Row("Окно тренера", HudWindowManager.isVisible("trainer")
                    ? "Показывается" : "Скрыто", RowType.TRAINER_WINDOW));
            case "Watermark", "FPS Counter", "Ping" ->
                    rows.add(new Row("Окно статистики", HudWindowManager.isVisible("watermark") ? "Показывается" : "Скрыто",
                            RowType.WATERMARK_WINDOW));
            case "Keystrokes", "CPS Counter" ->
                    rows.add(new Row("Окно управления", HudWindowManager.isVisible("controls") ? "Показывается" : "Скрыто",
                            RowType.CONTROLS_WINDOW));
            case "Armor HUD", "Potion HUD", "Totem Counter" ->
                    rows.add(new Row("Окно статуса игрока", HudWindowManager.isVisible("player_stats") ? "Показывается" : "Скрыто",
                            RowType.PLAYER_STATS_WINDOW));
            case "Target HUD", "Target Info" ->
                    rows.add(new Row("Окно цели", HudWindowManager.isVisible("target") ? "Показывается" : "Скрыто",
                            RowType.TARGET_WINDOW));
            case "Array List", "Module List" ->
                    rows.add(new Row("Окно списка модулей", HudWindowManager.isVisible("arraylist") ? "Показывается" : "Скрыто",
                            RowType.ARRAY_WINDOW));
            case "Notifications" ->
                    rows.add(new Row("Окно уведомлений", HudWindowManager.isVisible("notifications") ? "Показывается" : "Скрыто",
                            RowType.NOTIFICATIONS_WINDOW));
            default -> rows.add(new Row("Дополнительных параметров пока нет", "ЛКМ по карточке включает/выключает модуль",
                    RowType.INFO));
        }
        return rows;
    }

    private String particleStyleName() {
        return switch (VisualFeatures.hitParticleStyle()) {
            case 0 -> "Урон + крит";
            case 1 -> "Критические частицы";
            case 2 -> "Магические частицы";
            case 3 -> "Огонь";
            default -> "Выключены";
        };
    }

    private String tintName() {
        return switch (VisualFeatures.damageTintColor()) {
            case 0x009B7BFF -> "Фиолетовый";
            case 0x003CCFCF -> "Бирюзовый";
            case 0x00FFB347 -> "Оранжевый";
            default -> "Красный";
        };
    }

    private String swingStyleName() {
        return switch (VisualFeatures.swingStyle()) {
            case 0 -> "Classic";
            case 1 -> "Arc";
            case 2 -> "Spin";
            default -> "Snap";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB9000000);
        int panelW = Math.min(650, width - 28);
        int panelH = Math.min(390, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        g.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0xFF5B5278);
        g.fill(px, py, px + panelW, py + panelH, BG);
        g.fill(px, py, px + panelW, py + 3, VisualFeatures.accentColor());
        g.drawString(font, "НАСТРОЙКИ МОДУЛЯ", px + 20, py + 18, MUTED, false);
        g.drawString(font, module, px + 20, py + 38, TEXT, false);
        g.drawString(font, "ЛКМ в меню — включить/выключить. ПКМ — открыть эти настройки.",
                px + 20, py + 58, MUTED, false);

        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = py + 91 + i * 55;
            boolean hover = mouseX >= px + 18 && mouseX <= px + panelW - 18
                    && mouseY >= y && mouseY <= y + 45;
            g.fill(px + 18, y, px + panelW - 18, y + 45, hover ? 0xFF292E40 : CARD);
            g.fill(px + 18, y, px + 20, y + 45, VisualFeatures.accentColor());
            g.drawString(font, row.label(), px + 31, y + 8, TEXT, false);
            g.drawString(font, row.value(), px + panelW - font.width(row.value()) - 34,
                    y + 8, 0xFFB89AFF, false);
            if (isSlider(row.type())) {
                int sx = px + 31, sy = y + 29, sw = panelW - 62;
                g.fill(sx, sy, sx + sw, sy + 4, 0xFF44485A);
                float ratio = sliderRatio(row.type());
                g.fill(sx, sy, sx + Math.max(2, Math.round(sw * ratio)), sy + 4, 0xFF77DDB2);
            } else {
                g.drawString(font, row.type() == RowType.INFO ? " " : "НАЖМИ ДЛЯ ИЗМЕНЕНИЯ",
                        px + 31, y + 27, MUTED, false);
            }
        }

        g.drawString(font, "ESC — назад", px + panelW - 94, py + panelH - 18, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isSlider(RowType type) {
        return type == RowType.PARTICLE_COUNT || type == RowType.TINT_DURATION
                || type == RowType.TINT_OPACITY || type == RowType.HITBOX_EXPANSION
                || type == RowType.HITBOX_WIDTH || type == RowType.SWING_STRENGTH;
    }

    private float sliderRatio(RowType type) {
        return switch (type) {
            case PARTICLE_COUNT -> (VisualFeatures.hitParticleCount() - 2) / 22.0F;
            case TINT_DURATION -> (VisualFeatures.damageTintDuration() - 150) / 1350.0F;
            case TINT_OPACITY -> (VisualFeatures.damageTintOpacity() - 0.1F) / 0.9F;
            case HITBOX_EXPANSION -> (float) ((VisualFeatures.hitboxExpansion() - 0.05D) / 1.95D);
            case HITBOX_WIDTH -> (VisualFeatures.hitboxVisualWidth() - 0.5F) / 4.5F;
            case SWING_STRENGTH -> (VisualFeatures.swingStrength() - 0.2F) / 1.8F;
            default -> 0.0F;
        };
    }

    private void setSlider(RowType type, float ratio) {
        float value = Math.max(0.0F, Math.min(1.0F, ratio));
        switch (type) {
            case PARTICLE_COUNT -> VisualFeatures.setHitParticleCount(Math.round(2 + value * 22));
            case TINT_DURATION -> VisualFeatures.setDamageTintDuration(Math.round(150 + value * 1350));
            case TINT_OPACITY -> VisualFeatures.setDamageTintOpacity(0.1F + value * 0.9F);
            case HITBOX_EXPANSION -> VisualFeatures.setHitboxExpansion(0.05D + value * 1.95D);
            case HITBOX_WIDTH -> VisualFeatures.setHitboxVisualWidth(0.5F + value * 4.5F);
            case SWING_STRENGTH -> VisualFeatures.setSwingStrength(0.2F + value * 1.8F);
            default -> { }
        }
    }

    private void activate(RowType type) {
        switch (type) {
            case CYCLE -> VisualFeatures.cycleHitParticleStyle();
            case TINT_COLOR -> VisualFeatures.cycleDamageTintColor();
            case HITBOX_RESET -> VisualFeatures.setHitboxExpansion(0.5D);
            case HITBOX_WIDTH_RESET -> VisualFeatures.setHitboxVisualWidth(2.0F);
            case SWING_STYLE -> VisualFeatures.cycleSwingStyle();
            case PERSPECTIVE_MODE -> VisualFeatures.cyclePerspectiveMode();
            case TRAINER_WINDOW -> HudWindowManager.setVisible("trainer", !HudWindowManager.isVisible("trainer"));
            case WATERMARK_WINDOW -> HudWindowManager.setVisible("watermark", !HudWindowManager.isVisible("watermark"));
            case CONTROLS_WINDOW -> HudWindowManager.setVisible("controls", !HudWindowManager.isVisible("controls"));
            case PLAYER_STATS_WINDOW -> HudWindowManager.setVisible("player_stats", !HudWindowManager.isVisible("player_stats"));
            case TARGET_WINDOW -> HudWindowManager.setVisible("target", !HudWindowManager.isVisible("target"));
            case ARRAY_WINDOW -> HudWindowManager.setVisible("arraylist", !HudWindowManager.isVisible("arraylist"));
            case NOTIFICATIONS_WINDOW -> HudWindowManager.setVisible("notifications", !HudWindowManager.isVisible("notifications"));
            default -> { }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int panelW = Math.min(650, width - 28);
        int panelH = Math.min(390, height - 28);
        int px = (width - panelW) / 2;
        int py = (height - panelH) / 2;
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            int y = py + 91 + i * 55;
            if (event.x() < px + 18 || event.x() > px + panelW - 18
                    || event.y() < y || event.y() > y + 45) continue;
            Row row = rows.get(i);
            if (isSlider(row.type())) {
                int sx = px + 31;
                int sw = panelW - 62;
                setSlider(row.type(), (float) ((event.x() - sx) / sw));
            } else {
                activate(row.type());
            }
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ClickGuiScreen(returnCategory));
    }

    private enum RowType {
        CYCLE, PARTICLE_COUNT, TINT_COLOR, TINT_DURATION, TINT_OPACITY,
        HITBOX_EXPANSION, HITBOX_RESET, HITBOX_WIDTH, HITBOX_WIDTH_RESET,
        SWING_STYLE, SWING_STRENGTH, PERSPECTIVE_MODE, TRAINER_WINDOW, WATERMARK_WINDOW,
        CONTROLS_WINDOW, PLAYER_STATS_WINDOW, TARGET_WINDOW, ARRAY_WINDOW,
        NOTIFICATIONS_WINDOW, INFO
    }

    private record Row(String label, String value, RowType type) {}
}
