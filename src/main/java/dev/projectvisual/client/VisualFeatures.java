package dev.projectvisual.client;

import java.util.LinkedHashMap;
import java.util.Map;

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
            "Trainer","Guts","Akashi Seijuro","Dual Blades","Flying Spellbook","Hitboxes","Chest ESP"
        };
        for (String module : modules) STATES.put(module, false);
        STATES.put("FPS Counter", true);
        STATES.put("Watermark", true);
        STATES.put("Array List", true);
    }

    private VisualFeatures() {}

    public static boolean isEnabled(String module) {
        return STATES.getOrDefault(module, false);
    }

    public static void setEnabled(String module, boolean enabled) {
        if (STATES.containsKey(module)) STATES.put(module, enabled);
    }

    public static void toggle(String module) {
        boolean enabled = !isEnabled(module);
        if (enabled && isBodySkin(module)) {
            for (String skin : new String[]{"Queen Skin", "Invoker Skin", "Guts", "Akashi Seijuro"}) {
                if (!skin.equals(module)) setEnabled(skin, false);
            }
        }
        setEnabled(module, enabled);
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
    }

    public static double hitboxExpansion() {
        return hitboxExpansion;
    }

    public static void setHitboxExpansion(double value) {
        hitboxExpansion = Math.max(0.05D, Math.min(2.0D, value));
    }

    public static float hitboxVisualWidth() {
        return hitboxVisualWidth;
    }

    public static void setHitboxVisualWidth(float value) {
        hitboxVisualWidth = Math.max(0.5F, Math.min(5.0F, value));
    }

    public static int hitParticleStyle() {
        return hitParticleStyle;
    }

    public static void cycleHitParticleStyle() {
        hitParticleStyle = (hitParticleStyle + 1) % 5;
    }

    public static int hitParticleCount() {
        return hitParticleCount;
    }

    public static void setHitParticleCount(int value) {
        hitParticleCount = Math.max(2, Math.min(24, value));
    }

    public static int damageTintColor() {
        return damageTintColor;
    }

    public static void cycleDamageTintColor() {
        int[] palette = {0x00F04444, 0x009B7BFF, 0x003CCFCF, 0x00FFB347};
        int current = 0;
        for (int i = 0; i < palette.length; i++) if (palette[i] == damageTintColor) current = i;
        damageTintColor = palette[(current + 1) % palette.length];
    }

    public static int damageTintDuration() {
        return damageTintDuration;
    }

    public static void setDamageTintDuration(int value) {
        damageTintDuration = Math.max(150, Math.min(1500, value));
    }

    public static float damageTintOpacity() {
        return damageTintOpacity;
    }

    public static void setDamageTintOpacity(float value) {
        damageTintOpacity = Math.max(0.1F, Math.min(1.0F, value));
    }

    public static int swingStyle() {
        return swingStyle;
    }

    public static void cycleSwingStyle() {
        swingStyle = (swingStyle + 1) % 4;
    }

    public static float swingStrength() {
        return swingStrength;
    }

    public static void setSwingStrength(float value) {
        swingStrength = Math.max(0.2F, Math.min(2.0F, value));
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
