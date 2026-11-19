package dev.projectvisual.client.hud;

/** Converts a screen drag callback's click-origin and offsets to the live pointer position. */
public final class HudDragCoordinates {
    private HudDragCoordinates() {}

    public static Point fromDrag(double clickX, double clickY, double dragX, double dragY) {
        return new Point(clickX + dragX, clickY + dragY);
    }

    public record Point(double x, double y) {}
}
