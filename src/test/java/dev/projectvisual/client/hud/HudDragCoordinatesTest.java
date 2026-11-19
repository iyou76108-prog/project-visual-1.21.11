package dev.projectvisual.client.hud;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class HudDragCoordinatesTest {
    @Test
    public void resolvesPointerFromClickOriginAndDragOffsets() {
        HudDragCoordinates.Point point = HudDragCoordinates.fromDrag(120.0, 80.0, 31.5, -12.0);
        assertEquals(151.5, point.x(), 0.0001);
        assertEquals(68.0, point.y(), 0.0001);
    }
}
