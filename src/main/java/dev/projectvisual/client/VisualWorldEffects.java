package dev.projectvisual.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VisualWorldEffects {
    private static final int MAX_TRAIL_POINTS = 18;
    private static final int MAX_BREADCRUMBS = 42;
    private static final Map<UUID, Deque<Vec3>> PLAYER_TRAILS = new HashMap<>();
    private static final Deque<Vec3> BREADCRUMBS = new ArrayDeque<>();

    private static KeyMapping zoomKey;
    private static boolean smoothCameraApplied;
    private static boolean previousSmoothCamera;
    private static int tickCounter;
    private static int previousHurtTime;

    private VisualWorldEffects() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "visual"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.zoom", GLFW.GLFW_KEY_C, category));

        ClientTickEvents.END_CLIENT_TICK.register(VisualWorldEffects::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(VisualWorldEffects::renderWorld);
    }

    public static boolean isZoomKeyDown() {
        return zoomKey != null && zoomKey.isDown();
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            restoreSmoothCamera(client);
            previousHurtTime = 0;
            return;
        }

        boolean smoothEnabled = VisualFeatures.isEnabled("Smooth Camera");
        if (smoothEnabled && !smoothCameraApplied) {
            previousSmoothCamera = client.options.smoothCamera;
            client.options.smoothCamera = true;
            smoothCameraApplied = true;
        } else if (!smoothEnabled) {
            restoreSmoothCamera(client);
        }

        if (client.player.hurtTime > 0 && previousHurtTime == 0
                && VisualFeatures.isEnabled("Damage Tint")) {
            dev.projectvisual.client.hud.VisualHud.triggerDamageFlash();
        }
        previousHurtTime = client.player.hurtTime;

        tickCounter++;
        if ((tickCounter & 1) != 0) return;

        if (VisualFeatures.isEnabled("Breadcrumbs")) {
            Vec3 position = client.player.position();
            if (BREADCRUMBS.isEmpty() || BREADCRUMBS.peekLast().distanceToSqr(position) > 0.16D) {
                BREADCRUMBS.addLast(position);
                while (BREADCRUMBS.size() > MAX_BREADCRUMBS) BREADCRUMBS.removeFirst();
            }
        } else {
            BREADCRUMBS.clear();
        }

        if (VisualFeatures.isEnabled("Player Trails")) {
            Set<UUID> online = new HashSet<>();
            for (Player player : client.level.players()) {
                if (player == client.player || !player.isAlive()
                        || client.player.distanceToSqr(player) > 64.0D * 64.0D) continue;
                UUID id = player.getUUID();
                online.add(id);
                Deque<Vec3> trail = PLAYER_TRAILS.computeIfAbsent(id, ignored -> new ArrayDeque<>());
                Vec3 position = player.position().add(0.0D, 0.08D, 0.0D);
                if (trail.isEmpty() || trail.peekLast().distanceToSqr(position) > 0.12D) {
                    trail.addLast(position);
                    while (trail.size() > MAX_TRAIL_POINTS) trail.removeFirst();
                }
            }
            PLAYER_TRAILS.keySet().removeIf(id -> !online.contains(id));
        } else {
            PLAYER_TRAILS.clear();
        }
    }

    private static void restoreSmoothCamera(Minecraft client) {
        if (smoothCameraApplied) {
            client.options.smoothCamera = previousSmoothCamera;
            smoothCameraApplied = false;
        }
    }

    private static void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) return;

        boolean drawHitboxes = VisualFeatures.isEnabled("Hitbox Visual");
        boolean drawTrails = VisualFeatures.isEnabled("Player Trails");
        boolean drawBreadcrumbs = VisualFeatures.isEnabled("Breadcrumbs");
        if (!drawHitboxes && !drawTrails && !drawBreadcrumbs) return;

        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());

        if (drawHitboxes) {
            for (Player player : client.level.players()) {
                if (player == client.player || !player.isAlive()
                        || client.player.distanceToSqr(player) > 64.0D * 64.0D) continue;
                int color = VisualFeatures.accentColor();
                drawBox(context, lines, player.getBoundingBox(), camera, color, 1.7F);
            }
        }

        if (drawTrails) {
            for (Deque<Vec3> trail : new ArrayList<>(PLAYER_TRAILS.values())) {
                int index = 0;
                int size = trail.size();
                for (Vec3 point : trail) {
                    float alpha = 0.25F + 0.65F * ((float) ++index / Math.max(1, size));
                    int color = (Math.min(255, (int) (alpha * 255.0F)) << 24) | 0x0054D6C8;
                    drawMarker(context, lines, point, camera, color, 0.055D);
                }
            }
        }

        if (drawBreadcrumbs) {
            int index = 0;
            int size = BREADCRUMBS.size();
            for (Vec3 point : BREADCRUMBS) {
                index++;
                if ((index % 3) != 0 && index != size) continue;
                float alpha = 0.2F + 0.7F * ((float) index / Math.max(1, size));
                int color = (Math.min(255, (int) (alpha * 255.0F)) << 24)
                        | (VisualFeatures.accentColor() & 0x00FFFFFF);
                drawMarker(context, lines, point, camera, color, 0.075D);
            }
        }
    }

    private static void drawBox(WorldRenderContext context, VertexConsumer lines, AABB worldBox,
                                Vec3 camera, int color, float lineWidth) {
        AABB relative = worldBox.move(-camera.x, -camera.y, -camera.z).inflate(0.015D);
        ShapeRenderer.renderShape(context.matrices(), lines, Shapes.create(relative),
                0.0D, 0.0D, 0.0D, color, lineWidth);
    }

    private static void drawMarker(WorldRenderContext context, VertexConsumer lines, Vec3 worldPoint,
                                   Vec3 camera, int color, double radius) {
        AABB marker = new AABB(
                worldPoint.x - radius - camera.x, worldPoint.y - radius - camera.y, worldPoint.z - radius - camera.z,
                worldPoint.x + radius - camera.x, worldPoint.y + radius - camera.y, worldPoint.z + radius - camera.z);
        ShapeRenderer.renderShape(context.matrices(), lines, Shapes.create(marker),
                0.0D, 0.0D, 0.0D, color, 1.0F);
    }
}
