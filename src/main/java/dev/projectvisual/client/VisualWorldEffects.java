package dev.projectvisual.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class VisualWorldEffects {
    private static final int MAX_TRAIL_POINTS = 18;
    private static final int MAX_BREADCRUMBS = 42;
    private static final Map<UUID, Deque<Vec3>> PLAYER_TRAILS = new HashMap<>();
    private static final Deque<Vec3> BREADCRUMBS = new ArrayDeque<>();
    private static final List<BlockPos> CHEST_ESP_BLOCKS = new ArrayList<>();

    private static final RenderPipeline THROUGH_WALL_LINES_PIPELINE =
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(
                            ProjectVisualClient.MOD_ID, "pipeline/through_wall_lines"))
                    .withVertexShader("core/rendertype_lines")
                    .withFragmentShader("core/rendertype_lines")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, VertexFormat.Mode.LINES)
                    .build();

    private static final RenderType THROUGH_WALL_LINES = RenderType.create(
            "project_visual_through_wall_lines",
            RenderSetup.builder(THROUGH_WALL_LINES_PIPELINE).createRenderSetup());

    private static KeyMapping zoomKey;
    private static boolean smoothCameraApplied;
    private static boolean previousSmoothCamera;
    private static int tickCounter;

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

        tickCounter++;
        if ((tickCounter % 20) == 0) refreshChestEsp(client);
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

    public static void spawnHitParticles(LivingEntity target) {
        Minecraft client = Minecraft.getInstance();
        if (!VisualFeatures.isEnabled("Hit Particles") || client.level == null
                || target.level() != client.level || !target.isAlive()) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double centerY = target.getY() + target.getBbHeight() * 0.58D;
        for (int i = 0; i < 7; i++) {
            double x = target.getX() + random.nextDouble(-0.32D, 0.32D) * target.getBbWidth();
            double y = centerY + random.nextDouble(-0.12D, 0.28D) * target.getBbHeight();
            double z = target.getZ() + random.nextDouble(-0.32D, 0.32D) * target.getBbWidth();
            double vx = random.nextDouble(-0.11D, 0.11D);
            double vy = random.nextDouble(0.04D, 0.16D);
            double vz = random.nextDouble(-0.11D, 0.11D);
            client.level.addParticle(ParticleTypes.DAMAGE_INDICATOR, x, y, z, vx, vy, vz);
            if ((i & 1) == 0) {
                client.level.addParticle(ParticleTypes.CRIT, x, y, z, vx * 1.35D, vy, vz * 1.35D);
            }
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

        boolean drawPlayerHitboxes = VisualFeatures.isEnabled("Hitbox Visual");
        boolean drawEntityHitboxes = VisualFeatures.isEnabled("Hitboxes");
        boolean drawChestEsp = VisualFeatures.isEnabled("Chest ESP");
        boolean drawTrails = VisualFeatures.isEnabled("Player Trails");
        boolean drawBreadcrumbs = VisualFeatures.isEnabled("Breadcrumbs");
        boolean drawHat = VisualFeatures.isEnabled("China Hat");
        boolean drawBlockOverlay = VisualFeatures.isEnabled("Block Overlay");
        boolean drawNormalLines = drawPlayerHitboxes || drawTrails || drawBreadcrumbs || drawHat || drawBlockOverlay;
        if (!drawNormalLines && !drawEntityHitboxes && !drawChestEsp) return;

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        VertexConsumer lines = drawNormalLines
                ? context.consumers().getBuffer(RenderTypes.lines()) : null;

        if (drawPlayerHitboxes) {
            for (Player player : client.level.players()) {
                if (player == client.player || !player.isAlive()
                        || client.player.distanceToSqr(player) > 64.0D * 64.0D) continue;
                int color = VisualFeatures.accentColor();
                drawBox(context, lines, player.getBoundingBox(), camera, color, 1.7F);
            }
        }

        if (drawBlockOverlay && client.hitResult instanceof BlockHitResult blockHit) {
            var pos = blockHit.getBlockPos();
            AABB selectedBlock = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
            drawBox(context, lines, selectedBlock, camera, VisualFeatures.accentColor(), 2.0F);
        }

        if (drawHat) {
            renderChinaHat(context, lines, client.player, camera);
        }

        if (drawTrails) {
            for (Deque<Vec3> trail : new ArrayList<>(PLAYER_TRAILS.values())) {
                int index = 0;
                int size = trail.size();
                for (Vec3 point : trail) {
                    float alpha = 0.25F + 0.65F * ((float) ++index / Math.max(1, size));
                    int color = (Math.min(255, (int) (alpha * 255.0F)) << 24)
                        | (VisualFeatures.accentColor() & 0x00FFFFFF);
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

        // ESP overlays use a no-depth-test line pipeline, so boxes remain visible
        // through terrain and other world geometry like a conventional client ESP.
        if (drawEntityHitboxes || drawChestEsp) {
            VertexConsumer espLines = context.consumers().getBuffer(THROUGH_WALL_LINES);
            int color = VisualFeatures.accentColor();
            if (drawEntityHitboxes) {
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (!(entity instanceof Player target) || target == client.player
                            || !target.isAlive() || client.player.distanceToSqr(target) > 64.0D * 64.0D) continue;
                    drawBox(context, espLines, target.getBoundingBox().inflate(VisualFeatures.hitboxExpansion()),
                            camera, color, 2.0F);
                }
            }
            if (drawChestEsp) {
                for (BlockPos pos : CHEST_ESP_BLOCKS) {
                    AABB chest = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
                    drawBox(context, espLines, chest, camera, color, 2.5F);
                }
            }
        }
    }

    private static void refreshChestEsp(Minecraft client) {
        if (!VisualFeatures.isEnabled("Chest ESP") || client.player == null || client.level == null) {
            CHEST_ESP_BLOCKS.clear();
            return;
        }

        CHEST_ESP_BLOCKS.clear();
        BlockPos origin = client.player.blockPosition();
        int horizontalRadius = 24;
        int verticalRadius = 12;
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!client.level.hasChunkAt(pos)) continue;
                    var block = client.level.getBlockState(pos).getBlock();
                    if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                            || block == Blocks.ENDER_CHEST || block == Blocks.BARREL) {
                        CHEST_ESP_BLOCKS.add(pos.immutable());
                    }
                }
            }
        }
    }

    private static void renderChinaHat(WorldRenderContext context, VertexConsumer lines,
                                       Player player, Vec3 camera) {
        Vec3 base = player.position();
        double brimY = player.getY() + player.getBbHeight() - 0.02D;
        int color = VisualFeatures.accentColor();

        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0D * i) / 16.0D;
            Vec3 point = new Vec3(base.x + Math.cos(angle) * 0.48D, brimY,
                    base.z + Math.sin(angle) * 0.48D);
            drawMarker(context, lines, point, camera, color, 0.035D);
        }
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i) / 12.0D;
            Vec3 point = new Vec3(base.x + Math.cos(angle) * 0.23D, brimY + 0.22D,
                    base.z + Math.sin(angle) * 0.23D);
            drawMarker(context, lines, point, camera, color, 0.03D);
        }
        drawMarker(context, lines, new Vec3(base.x, brimY + 0.43D, base.z),
                camera, color, 0.04D);
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
