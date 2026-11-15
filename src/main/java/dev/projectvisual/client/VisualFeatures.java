package dev.projectvisual.client;

public final class VisualFeatures {
    private static boolean playerEsp;
    private static boolean hitboxEnabled;
    private static double hitboxExpansion = 0.15;

    private VisualFeatures() {}
    public static boolean isPlayerEsp() { return playerEsp; }
    public static void setPlayerEsp(boolean value) { playerEsp = value; }
    public static boolean isHitboxEnabled() { return hitboxEnabled; }
    public static void setHitboxEnabled(boolean value) { hitboxEnabled = value; }
    public static double getHitboxExpansion() { return hitboxExpansion; }
    public static void changeHitboxExpansion(double delta) {
        hitboxExpansion = Math.max(0.05, Math.min(1.0, hitboxExpansion + delta));
    }
}
