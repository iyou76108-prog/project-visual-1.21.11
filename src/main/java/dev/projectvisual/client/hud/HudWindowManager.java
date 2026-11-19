package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Draggable, collapsible, independently closable HUD windows. */
public final class HudWindowManager {
    private static final int HEADER = 19;
    private static final int MIN_X = 0xFF;
    private static final Map<String, Window> WINDOWS = new LinkedHashMap<>();
    private static KeyMapping editKey;
    private static boolean editMode;
    private static double mouseX;
    private static double mouseY;
    private static boolean loaded;

    private HudWindowManager() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "hud"));
        editKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.hud_editor", GLFW.GLFW_KEY_H, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (editKey.consumeClick()) {
                if (client.screen instanceof HudEditorScreen) {
                    client.setScreen(null);
                } else if (client.screen == null) {
                    client.setScreen(new HudEditorScreen());
                }
            }
        });
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static void setEditMode(boolean enabled) {
        editMode = enabled;
        stopDragging();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    editMode ? "§bHUD EDITOR §7— перетаскивай окна за заголовок, H или ESC — выход"
                            : "§7HUD EDITOR выключен"), true);
        }
    }

    public static void toggleEditMode() {
        setEditMode(!editMode);
    }

    public static Frame begin(GuiGraphics g, String id, String title, int screenWidth,
                              int screenHeight, int width, int contentHeight,
                              int defaultX, int defaultY, int accentColor) {
        ensureLoaded();
        Window window = WINDOWS.computeIfAbsent(id, ignored -> new Window());
        window.width = width;
        if (!window.positioned) {
            window.x = defaultX;
            window.y = defaultY;
            window.positioned = true;
        }
        window.x = Math.max(0, Math.min(screenWidth - width, window.x));
        window.y = Math.max(0, Math.min(screenHeight - HEADER, window.y));
        if (!window.visible) return null;

        int totalHeight = window.collapsed ? HEADER : HEADER + contentHeight;
        int x = window.x;
        int y = window.y;
        g.fill(x, y, x + width, y + totalHeight, 0xD9111420);
        g.fill(x, y, x + width, y + HEADER, editMode ? 0xFF30334C : 0xFF202438);
        g.fill(x, y, x + 2, y + totalHeight, accentColor);
        g.fill(x, y, x + width, y + 1, editMode ? 0xFFB89AFF : 0xFF45495A);
        g.drawString(Minecraft.getInstance().font, title, x + 8, y + 6, 0xFFF3F0FF, false);
        int collapseX = x + width - 29;
        int closeX = x + width - 16;
        g.fill(collapseX, y + 3, collapseX + 11, y + 15, 0xFF363B50);
        g.fill(closeX, y + 3, closeX + 12, y + 15, 0xFF49323D);
        g.drawCenteredString(Minecraft.getInstance().font, window.collapsed ? "+" : "−",
                collapseX + 5, y + 5, 0xFFE2E0F0);
        g.drawCenteredString(Minecraft.getInstance().font, "×",
                closeX + 6, y + 5, 0xFFFFA7B8);
        if (editMode) {
            g.drawString(Minecraft.getInstance().font, "H: edit", x + width - 71, y + HEADER + 4,
                    0xFFB89AFF, false);
        }
        if (window.collapsed) return null;
        return new Frame(x + 8, y + HEADER + 5, width - 16, contentHeight - 8);
    }

    public static void setVisible(String id, boolean visible) {
        ensureLoaded();
        Window window = WINDOWS.computeIfAbsent(id, ignored -> new Window());
        window.visible = visible;
        save();
    }

    public static boolean isVisible(String id) {
        ensureLoaded();
        Window window = WINDOWS.get(id);
        return window == null || window.visible;
    }

    public static void setCollapsed(String id, boolean collapsed) {
        ensureLoaded();
        WINDOWS.computeIfAbsent(id, ignored -> new Window()).collapsed = collapsed;
        save();
    }

    public static void onMouseMove(double rawX, double rawY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.screen != null) return;
        onEditorMouseMove(
                rawX * mc.getWindow().getGuiScaledWidth() / Math.max(1.0D, mc.getWindow().getScreenWidth()),
                rawY * mc.getWindow().getGuiScaledHeight() / Math.max(1.0D, mc.getWindow().getScreenHeight()));
    }

    /** Mouse coordinates from Screen are already in GUI-scaled units. */
    public static void onEditorMouseMove(double x, double y) {
        mouseX = x;
        mouseY = y;
        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        for (Window window : WINDOWS.values()) {
            if (window.dragging) {
                window.x = Math.max(0, Math.min(guiWidth - window.width,
                        (int) Math.round(mouseX - window.dragOffsetX)));
                window.y = Math.max(0, Math.min(guiHeight - HEADER,
                        (int) Math.round(mouseY - window.dragOffsetY)));
            }
        }
    }

    public static void onMouseButton(int button, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            if (!down) stopDragging();
            return;
        }
        handleEditorButton(button, down);
    }

    public static void onEditorMouseButton(int button, boolean down, double x, double y) {
        mouseX = x;
        mouseY = y;
        handleEditorButton(button, down);
    }

    private static void handleEditorButton(int button, boolean down) {
        if (!down) {
            stopDragging();
            save();
            return;
        }

        var entries = WINDOWS.entrySet().toArray(Map.Entry[]::new);
        for (int i = entries.length - 1; i >= 0; i--) {
            @SuppressWarnings("unchecked")
            Map.Entry<String, Window> entry = (Map.Entry<String, Window>) entries[i];
            Window window = entry.getValue();
            if (!window.visible) continue;
            int width = window.width > 0 ? window.width : defaultWidth(entry.getKey());
            int x = window.x;
            int y = window.y;
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + HEADER) continue;

            if (button == 0 && mouseX >= x + width - 17) {
                window.visible = false;
                save();
                return;
            }
            if (button == 0 && mouseX >= x + width - 31 && mouseX < x + width - 17) {
                window.collapsed = !window.collapsed;
                save();
                return;
            }
            if (button == 1 && mouseX < x + width - 32) {
                window.collapsed = !window.collapsed;
                save();
                return;
            }
            if (button == 0 && editMode && mouseX < x + width - 32) {
                window.dragging = true;
                window.dragOffsetX = mouseX - x;
                window.dragOffsetY = mouseY - y;
                return;
            }
        }
    }

    public static void rememberWidth(String id, int width) {
        ensureLoaded();
        WINDOWS.computeIfAbsent(id, ignored -> new Window()).width = width;
    }

    private static int defaultWidth(String id) {
        Window window = WINDOWS.get(id);
        return window == null ? 180 : window.width;
    }

    private static void stopDragging() {
        for (Window window : WINDOWS.values()) window.dragging = false;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Properties properties = new Properties();
        Path file = savePath();
        if (!Files.exists(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split("\\.");
                if (parts.length != 2) continue;
                Window window = WINDOWS.computeIfAbsent(parts[0], ignored -> new Window());
                switch (parts[1]) {
                    case "x" -> { window.x = Integer.parseInt(properties.getProperty(key)); window.positioned = true; }
                    case "y" -> { window.y = Integer.parseInt(properties.getProperty(key)); window.positioned = true; }
                    case "visible" -> window.visible = Boolean.parseBoolean(properties.getProperty(key));
                    case "collapsed" -> window.collapsed = Boolean.parseBoolean(properties.getProperty(key));
                    default -> { }
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            WINDOWS.clear();
        }
    }

    private static void save() {
        Properties properties = new Properties();
        WINDOWS.forEach((id, window) -> {
            properties.setProperty(id + ".x", Integer.toString(window.x));
            properties.setProperty(id + ".y", Integer.toString(window.y));
            properties.setProperty(id + ".visible", Boolean.toString(window.visible));
            properties.setProperty(id + ".collapsed", Boolean.toString(window.collapsed));
        });
        try {
            Path file = savePath();
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "PROJECT VISUAL HUD window layout");
            }
        } catch (IOException ignored) {
            // Window movement remains usable for this session if persistence is unavailable.
        }
    }

    private static Path savePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("project_visual_hud.properties");
    }

    public record Frame(int x, int y, int width, int height) {}

    private static final class Window {
        private int x;
        private int y;
        private int width = 180;
        private boolean positioned;
        private boolean visible = true;
        private boolean collapsed;
        private boolean dragging;
        private double dragOffsetX;
        private double dragOffsetY;
    }
}
