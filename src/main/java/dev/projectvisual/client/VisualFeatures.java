package dev.projectvisual.client;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class VisualFeatures {
    private static final Map<String, Boolean> STATES = new LinkedHashMap<>();
    private static int customAccentColor = 0xFF54D6C8;
    private static double hitboxExpansion = 0.5D;
    private static float hitboxVisualWidth = 2.0F;
    private static int hitParticleStyle;
    private static int hitParticleCount = 7;
    private static int damageTintColor = 0x00F04444;
    private static int damageTintDuration = 520;
    private static float damageTintOpacity = 1.0F;
    private static int swingStyle = 1;
    private static float swingStrength = 1.0F;
    private static int perspectiveMode;
    private static float zoomScale = 0.25F;
    private static int espDistance = 64;
    private static int chestEspRadius = 24;
    private static float chinaHatSize = 1.0F;
    private static float viewModelScale = 0.93F;
    private static float crosshairSize = 1.0F;
    private static int crosshairStyle;

    static {
        String[] modules = {
            "FPS Counter","Ping","CPS Counter","Keystrokes","Armor HUD","Potion HUD","Target HUD","Array List",
            "Hit Particles","Damage Tint","Hit Color","Crosshair","Target Info","Totem Counter",
            "China Hat","Player Trails","Name Tags","Item Physics","Hand Cosmetics","Cape Effects",
            "Fullbright","Weather","Time Changer","Ambience","Block Overlay","Breadcrumbs",
            "Swing Animation","Old Animations","Item Sway","Eating Animation","Rod Animation","Shield Animation",
            "View Model","Camera Clip","Zoom","Smooth Camera","Perspective",
            "Notifications","Watermark","Module List","Custom Font","Color Theme","Player ESP","Hitbox Visual",
            "Queen Skin","Magnus AFK","Invoker Skin","Axe AFK","Interworlder Mask","Watcher AFK",
            "Trainer","Guts","Akashi Seijuro","Dual Blades","Flying Spellbook","Hitboxes","Chest ESP",
            "Pencil Sword","Divine Rapier"
        };
        for (String module : modules) STATES.put(module, false);
        STATES.put("FPS Counter", true);
        STATES.put("Watermark", true);
        STATES.put("Array List", true);
        loadConfig();
    }

    private VisualFeatures() {}

    public static boolean isEnabled(String module) {
        return STATES.getOrDefault(module, false);
    }

    public static void setEnabled(String module, boolean enabled) {
        if (STATES.containsKey(module)) {
            STATES.put(module, enabled);
            saveConfig();
        }
    }

    public static void toggle(String module) {
        boolean enabled = !isEnabled(module);
        if (enabled && isBodySkin(module)) {
            for (String skin : new String[]{"Queen Skin", "Invoker Skin", "Guts", "Akashi Seijuro"}) {
                if (!skin.equals(module)) setEnabled(skin, false);
            }
        }
        if (enabled && isSwordSkin(module)) {
            for (String skin : new String[]{"Pencil Sword", "Divine Rapier"}) {
                if (!skin.equals(module)) setEnabled(skin, false);
            }
        }
        setEnabled(module, enabled);
    }

    private static boolean isSwordSkin(String module) {
        return module.equals("Pencil Sword") || module.equals("Divine Rapier");
    }

    private static boolean isBodySkin(String module) {
        return module.equals("Queen Skin") || module.equals("Invoker Skin")
                || module.equals("Guts") || module.equals("Akashi Seijuro");
    }

    public static Map<String, Boolean> getStates() {
        return Map.copyOf(STATES);
    }

    public static int accentColor() {
        return isEnabled("Color Theme") ? customAccentColor : 0xFF9B7BFF;
    }

    public static int customAccentColor() {
        return customAccentColor;
    }

    public static void setCustomAccentColor(int color) {
        customAccentColor = 0xFF000000 | (color & 0x00FFFFFF);
        setEnabled("Color Theme", true);
        saveConfig();
    }

    public static double hitboxExpansion() {
        return hitboxExpansion;
    }

    public static void setHitboxExpansion(double value) {
        hitboxExpansion = Math.max(0.05D, Math.min(2.0D, value));
        saveConfig();
    }

    public static float hitboxVisualWidth() {
        return hitboxVisualWidth;
    }

    public static void setHitboxVisualWidth(float value) {
        hitboxVisualWidth = Math.max(0.5F, Math.min(5.0F, value));
        saveConfig();
    }

    public static int hitParticleStyle() {
        return hitParticleStyle;
    }

    public static void cycleHitParticleStyle() {
        hitParticleStyle = (hitParticleStyle + 1) % 5;
        saveConfig();
    }

    public static int hitParticleCount() {
        return hitParticleCount;
    }

    public static void setHitParticleCount(int value) {
        hitParticleCount = Math.max(2, Math.min(24, value));
        saveConfig();
    }

    public static int damageTintColor() {
        return damageTintColor;
    }

    public static void cycleDamageTintColor() {
        int[] palette = {0x00F04444, 0x009B7BFF, 0x003CCFCF, 0x00FFB347};
        int current = 0;
        for (int i = 0; i < palette.length; i++) if (palette[i] == damageTintColor) current = i;
        damageTintColor = palette[(current + 1) % palette.length];
        saveConfig();
    }

    public static int damageTintDuration() {
        return damageTintDuration;
    }

    public static void setDamageTintDuration(int value) {
        damageTintDuration = Math.max(150, Math.min(1500, value));
        saveConfig();
    }

    public static float damageTintOpacity() {
        return damageTintOpacity;
    }

    public static void setDamageTintOpacity(float value) {
        damageTintOpacity = Math.max(0.1F, Math.min(1.0F, value));
        saveConfig();
    }

    public static int swingStyle() {
        return swingStyle;
    }

    public static void cycleSwingStyle() {
        swingStyle = (swingStyle + 1) % 4;
        saveConfig();
    }

    public static float swingStrength() {
        return swingStrength;
    }

    public static void setSwingStrength(float value) {
        swingStrength = Math.max(0.2F, Math.min(2.0F, value));
        saveConfig();
    }

    public static int perspectiveMode() {
        return perspectiveMode;
    }

    public static void cyclePerspectiveMode() {
        perspectiveMode = (perspectiveMode + 1) % 2;
        saveConfig();
    }

    public static float zoomScale() { return zoomScale; }
    public static void setZoomScale(float value) {
        zoomScale = Math.max(0.1F, Math.min(0.75F, value));
        saveConfig();
    }

    public static int espDistance() { return espDistance; }
    public static void setEspDistance(int value) {
        espDistance = Math.max(8, Math.min(128, value));
        saveConfig();
    }

    public static int chestEspRadius() { return chestEspRadius; }
    public static void setChestEspRadius(int value) {
        chestEspRadius = Math.max(8, Math.min(32, value));
        saveConfig();
    }

    public static float chinaHatSize() { return chinaHatSize; }
    public static void setChinaHatSize(float value) {
        chinaHatSize = Math.max(0.5F, Math.min(1.75F, value));
        saveConfig();
    }

    public static float viewModelScale() { return viewModelScale; }
    public static void setViewModelScale(float value) {
        viewModelScale = Math.max(0.5F, Math.min(1.5F, value));
        saveConfig();
    }

    public static float crosshairSize() { return crosshairSize; }
    public static void setCrosshairSize(float value) {
        crosshairSize = Math.max(0.5F, Math.min(2.0F, value));
        saveConfig();
    }

    public static int crosshairStyle() { return crosshairStyle; }
    public static void cycleCrosshairStyle() {
        crosshairStyle = (crosshairStyle + 1) % 3;
        saveConfig();
    }


    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("project_visual_modules.properties");
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        Path file = configPath();
        if (!Files.exists(file)) return;
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            for (String module : STATES.keySet()) {
                String value = properties.getProperty("module." + module);
                if (value != null) STATES.put(module, Boolean.parseBoolean(value));
            }
            customAccentColor = parseInt(properties, "accent", customAccentColor);
            hitboxExpansion = clamp(parseDouble(properties, "hitboxExpansion", hitboxExpansion), 0.05D, 2.0D);
            hitboxVisualWidth = (float) clamp(parseDouble(properties, "hitboxVisualWidth", hitboxVisualWidth), 0.5D, 5.0D);
            hitParticleStyle = (int) clamp(parseInt(properties, "hitParticleStyle", hitParticleStyle), 0, 4);
            hitParticleCount = (int) clamp(parseInt(properties, "hitParticleCount", hitParticleCount), 2, 24);
            damageTintColor = parseInt(properties, "damageTintColor", damageTintColor);
            damageTintDuration = (int) clamp(parseInt(properties, "damageTintDuration", damageTintDuration), 150, 1500);
            damageTintOpacity = (float) clamp(parseDouble(properties, "damageTintOpacity", damageTintOpacity), 0.1D, 1.0D);
            swingStyle = (int) clamp(parseInt(properties, "swingStyle", swingStyle), 0, 3);
            swingStrength = (float) clamp(parseDouble(properties, "swingStrength", swingStrength), 0.2D, 2.0D);
            perspectiveMode = (int) clamp(parseInt(properties, "perspectiveMode", perspectiveMode), 0, 1);
            zoomScale = (float) clamp(parseDouble(properties, "zoomScale", zoomScale), 0.1D, 0.75D);
            espDistance = (int) clamp(parseInt(properties, "espDistance", espDistance), 8, 128);
            chestEspRadius = (int) clamp(parseInt(properties, "chestEspRadius", chestEspRadius), 8, 32);
            chinaHatSize = (float) clamp(parseDouble(properties, "chinaHatSize", chinaHatSize), 0.5D, 1.75D);
            viewModelScale = (float) clamp(parseDouble(properties, "viewModelScale", viewModelScale), 0.5D, 1.5D);
            crosshairSize = (float) clamp(parseDouble(properties, "crosshairSize", crosshairSize), 0.5D, 2.0D);
            crosshairStyle = (int) clamp(parseInt(properties, "crosshairStyle", crosshairStyle), 0, 2);
        } catch (IOException | NumberFormatException ignored) {
            // Defaults are safe when a damaged config is encountered.
        }
    }

    private static void saveConfig() {
        Properties properties = new Properties();
        STATES.forEach((module, enabled) -> properties.setProperty("module." + module, Boolean.toString(enabled)));
        properties.setProperty("accent", Integer.toString(customAccentColor));
        properties.setProperty("hitboxExpansion", Double.toString(hitboxExpansion));
        properties.setProperty("hitboxVisualWidth", Float.toString(hitboxVisualWidth));
        properties.setProperty("hitParticleStyle", Integer.toString(hitParticleStyle));
        properties.setProperty("hitParticleCount", Integer.toString(hitParticleCount));
        properties.setProperty("damageTintColor", Integer.toString(damageTintColor));
        properties.setProperty("damageTintDuration", Integer.toString(damageTintDuration));
        properties.setProperty("damageTintOpacity", Float.toString(damageTintOpacity));
        properties.setProperty("swingStyle", Integer.toString(swingStyle));
        properties.setProperty("swingStrength", Float.toString(swingStrength));
        properties.setProperty("perspectiveMode", Integer.toString(perspectiveMode));
        properties.setProperty("zoomScale", Float.toString(zoomScale));
        properties.setProperty("espDistance", Integer.toString(espDistance));
        properties.setProperty("chestEspRadius", Integer.toString(chestEspRadius));
        properties.setProperty("chinaHatSize", Float.toString(chinaHatSize));
        properties.setProperty("viewModelScale", Float.toString(viewModelScale));
        properties.setProperty("crosshairSize", Float.toString(crosshairSize));
        properties.setProperty("crosshairStyle", Integer.toString(crosshairStyle));
        try {
            Path file = configPath();
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "PROJECT VISUAL module states and settings");
            }
        } catch (IOException ignored) {
            // The client remains usable if its config folder is read-only.
        }
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double parseDouble(Properties properties, String key, double fallback) {
        try { return Double.parseDouble(properties.getProperty(key, Double.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean isPlayerEsp() {
        return isEnabled("Player ESP");
    }

    public static void setPlayerEsp(boolean value) {
        setEnabled("Player ESP", value);
    }

    public static boolean isHitboxEnabled() {
        return isEnabled("Hitbox Visual");
    }

    public static void setHitboxEnabled(boolean value) {
        setEnabled("Hitbox Visual", value);
    }
}
