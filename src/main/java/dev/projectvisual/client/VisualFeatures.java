package dev.projectvisual.client;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VisualFeatures {
    private static final Map<String, Boolean> STATES = new LinkedHashMap<>();
    private static int customAccentColor = 0xFF54D6C8;
    private static double hitboxExpansion = 0.18D;

    static {
        String[] modules = {
            "FPS Counter","Ping","CPS Counter","Keystrokes","Armor HUD","Potion HUD","Target HUD","Array List",
            "Hit Particles","Damage Tint","Hit Color","Crosshair","Target Info","Totem Counter",
            "China Hat","Player Trails","Name Tags","Item Physics","Hand Cosmetics","Cape Effects",
            "Fullbright","Weather","Time Changer","Ambience","Block Overlay","Breadcrumbs",
            "Swing Animation","Old Animations","Item Sway","Eating Animation","Rod Animation","Shield Animation",
            "View Model","Camera Clip","Zoom","Smooth Camera","Perspective",
            "Notifications","Watermark","Module List","Custom Font","Color Theme","Player ESP","Hitbox Visual",
            "Queen Skin","Magnus AFK","Invoker Skin","Axe AFK","Hitboxes","Chest ESP"
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
        setEnabled(module, !isEnabled(module));
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
        hitboxExpansion = Math.max(0.05D, Math.min(0.75D, value));
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
