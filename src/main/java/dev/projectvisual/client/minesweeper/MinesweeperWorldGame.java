package dev.projectvisual.client.minesweeper;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.projectvisual.client.ProjectVisualClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

/** A world-space 3D Minesweeper board, independent from the chess match state. */
public final class MinesweeperWorldGame {
    private static final int SIDE = 9;
    private static final int CELLS = SIDE * SIDE;
    private static final int MINE_COUNT = 10;
    private static final double CELL = 0.49D;
    private static final double HALF = SIDE * CELL * 0.5D;
    private static final int INTRO_TICKS = 42;

    private static final int FRAME = 0xFF242536;
    private static final int EDGE = 0xFF9C7757;
    private static final int HIDDEN_A = 0xFF697A91;
    private static final int HIDDEN_B = 0xFF53647B;
    private static final int OPEN_A = 0xFFD4D9DE;
    private static final int OPEN_B = 0xFFBEC6CF;
    private static final int HOVER = 0xFF80E8D8;
    private static final int FLAG_RED = 0xFFE34F62;
    private static final int MINE_BLACK = 0xFF20212B;

    private static final RenderPipeline SOLID_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "pipeline/minesweeper_solid"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withoutBlend()
            .withDepthWrite(true)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();
    private static final RenderType SOLID = RenderType.create(
            "project_visual_minesweeper_solid",
            RenderSetup.builder(SOLID_PIPELINE).createRenderSetup());

    private static final boolean[] mines = new boolean[CELLS];
    private static final boolean[] revealed = new boolean[CELLS];
    private static final boolean[] flagged = new boolean[CELLS];
    private static final int[] adjacent = new int[CELLS];

    private static boolean registered;
    private static boolean active;
    private static boolean generated;
    private static boolean gameOver;
    private static boolean won;
    private static boolean rightWasDown;
    private static int revealedCount;
    private static int flagCount;
    private static int introTick;
    private static int exploded = -1;
    private static int hovered = -1;

    private static Vec3 center = Vec3.ZERO;
    private static Vec3 right = new Vec3(1, 0, 0);
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static double boardY;

    private static KeyMapping exitKey;
    private static KeyMapping restartKey;

    private MinesweeperWorldGame() {}

    public static void register() {
        if (registered) return;
        registered = true;
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "minesweeper"));
        exitKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.minesweeper_exit", GLFW.GLFW_KEY_BACKSPACE, category));
        restartKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.minesweeper_restart", GLFW.GLFW_KEY_R, category));
        ClientTickEvents.END_CLIENT_TICK.register(MinesweeperWorldGame::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(MinesweeperWorldGame::render);
    }

    public static void start() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Arrays.fill(mines, false);
        Arrays.fill(revealed, false);
        Arrays.fill(flagged, false);
        Arrays.fill(adjacent, 0);
        generated = false;
        gameOver = false;
        won = false;
        revealedCount = 0;
        flagCount = 0;
        exploded = -1;
        hovered = -1;
        introTick = 0;
        rightWasDown = false;
        double yaw = Math.toRadians(client.player.getYRot());
        forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        boardY = client.player.getEyeY() - 0.88D;
        Vec3 anchor = client.player.position().add(forward.scale(3.55D));
        center = new Vec3(anchor.x, boardY, anchor.z);
        active = true;
        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§bPROJECT VISUAL MINESWEEPER §7— ПКМ открыть · Shift+ПКМ флаг · R заново · Backspace выйти"),
                false);
        portalParticles(client, 22);
    }

    public static boolean isActive() {
        return active;
    }

    public static void stop() {
        active = false;
        hovered = -1;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Доска сапёра закрыта."), false);
        }
    }

    private static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null) {
            active = false;
            return;
        }
        while (exitKey != null && exitKey.consumeClick()) {
            stop();
            return;
        }
        while (restartKey != null && restartKey.consumeClick()) {
            start();
            return;
        }
        introTick = Math.min(INTRO_TICKS, introTick + 1);
        if (introTick < INTRO_TICKS && introTick % 3 == 0) portalParticles(client, 3);
        if (client.screen != null) {
            rightWasDown = false;
            return;
        }
        boolean down = client.mouseHandler.isRightPressed();
        if (down && !rightWasDown && introTick >= INTRO_TICKS && !gameOver) {
            int cell = rayPick(client);
            if (cell >= 0) {
                if (client.options.keyShift.isDown()) toggleFlag(client, cell);
                else revealCell(client, cell);
            } else {
                actionBar(client, "§7Наведи прицел на клетку и нажми ПКМ");
            }
        }
        rightWasDown = down;
    }

    private static void toggleFlag(Minecraft client, int cell) {
        if (revealed[cell]) return;
        if (!flagged[cell] && flagCount >= MINE_COUNT) {
            actionBar(client, "§cВсе 10 флагов уже расставлены");
            return;
        }
        flagged[cell] = !flagged[cell];
        flagCount += flagged[cell] ? 1 : -1;
        actionBar(client, "§eФлагов: " + (MINE_COUNT - flagCount) + " §7· ПКМ открыть клетку");
    }

    private static void revealCell(Minecraft client, int cell) {
        if (flagged[cell] || revealed[cell]) return;
        if (!generated) generateMines(cell);
        if (mines[cell]) {
            exploded = cell;
            gameOver = true;
            for (int i = 0; i < CELLS; i++) if (mines[i]) revealed[i] = true;
            Vec3 hit = cellCenter(cell, 0.18D);
            for (int i = 0; i < 26; i++) {
                double angle = Math.PI * 2.0D * i / 26.0D;
                client.level.addParticle(ParticleTypes.CRIT, hit.x, hit.y, hit.z,
                        Math.cos(angle) * 0.16D, 0.08D + (i % 4) * 0.025D,
                        Math.sin(angle) * 0.16D);
            }
            client.level.addParticle(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 0, 0, 0);
            actionBar(client, "§cБУМ! §7Ты наступил на мину. Нажми R, чтобы сыграть ещё раз.");
            return;
        }
        floodReveal(cell);
        if (revealedCount >= CELLS - MINE_COUNT) {
            won = true;
            gameOver = true;
            actionBar(client, "§aПОБЕДА! §fВсе безопасные клетки открыты.");
            for (int i = 0; i < 30; i++) {
                double angle = Math.PI * 2.0D * i / 30.0D;
                client.level.addParticle(ParticleTypes.FIREWORK,
                        center.x + Math.cos(angle) * 2.0D, boardY + 0.4D,
                        center.z + Math.sin(angle) * 2.0D,
                        Math.cos(angle) * 0.05D, 0.09D, Math.sin(angle) * 0.05D);
            }
        } else {
            actionBar(client, "§bСАПЁР §7· Открыто: " + revealedCount + "/" + (CELLS - MINE_COUNT)
                    + " · Флагов осталось: " + (MINE_COUNT - flagCount));
        }
    }

    private static void generateMines(int safeCell) {
        generated = true;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int placed = 0;
        while (placed < MINE_COUNT) {
            int cell = random.nextInt(CELLS);
            if (mines[cell] || isNear(cell, safeCell, 1)) continue;
            mines[cell] = true;
            placed++;
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (!mines[cell]) adjacent[cell] = countAdjacentMines(cell);
        }
    }

    private static boolean isNear(int a, int b, int radius) {
        return Math.abs(a % SIDE - b % SIDE) <= radius
                && Math.abs(a / SIDE - b / SIDE) <= radius;
    }

    private static int countAdjacentMines(int cell) {
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int x = cell % SIDE + dx, z = cell / SIDE + dz;
            if (inside(x, z) && mines[z * SIDE + x]) count++;
        }
        return count;
    }

    private static void floodReveal(int start) {
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int cell = queue.remove();
            if (revealed[cell] || flagged[cell] || mines[cell]) continue;
            revealed[cell] = true;
            revealedCount++;
            if (adjacent[cell] != 0) continue;
            int x = cell % SIDE, z = cell / SIDE;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, nz = z + dz;
                if (inside(nx, nz)) {
                    int next = nz * SIDE + nx;
                    if (!revealed[next] && !flagged[next] && !mines[next]) queue.add(next);
                }
            }
        }
    }

    private static boolean inside(int x, int z) {
        return x >= 0 && x < SIDE && z >= 0 && z < SIDE;
    }

    private static int rayPick(Minecraft client) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getLookAngle();
        double bob = Math.sin(client.level.getGameTime() * 0.055D) * 0.012D;
        double planeY = boardY + 0.018D + introLift() + bob;
        if (look.y >= -0.015D) return -1;
        double distance = (planeY - eye.y) / look.y;
        if (distance <= 0.0D || distance > 16.0D) return -1;
        Vec3 hit = eye.add(look.scale(distance));
        Vec3 relative = hit.subtract(center);
        double x = relative.dot(right), z = relative.dot(forward);
        if (Math.abs(x) > HALF || Math.abs(z) > HALF) return -1;
        int file = Math.min(SIDE - 1, Math.max(0, (int) Math.floor((x + HALF) / CELL)));
        int rank = Math.min(SIDE - 1, Math.max(0, (int) Math.floor((z + HALF) / CELL)));
        return rank * SIDE + file;
    }

    private static void render(WorldRenderContext context) {
        if (!active) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        hovered = introTick >= INTRO_TICKS && !gameOver ? rayPick(client) : -1;
        float progress = smooth(introTick / (float) INTRO_TICKS);
        float scale = 0.14F + 0.86F * progress;
        double bob = Math.sin(client.level.getGameTime() * 0.055D) * 0.012D * progress;
        BoardTransform transform = new BoardTransform(center.subtract(camera), right, forward, scale,
                introLift() + bob);
        VertexConsumer quads = context.consumers().getBuffer(SOLID);
        renderBoard(quads, transform);
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        renderPortal(lines, transform, camera, progress);
    }

    private static void renderBoard(VertexConsumer out, BoardTransform t) {
        box(out, t, -HALF - 0.11D, -0.20D, -HALF - 0.11D,
                HALF + 0.11D, 0.015D, HALF + 0.11D, FRAME);
        box(out, t, -HALF - 0.095D, -0.23D, -HALF - 0.095D,
                HALF + 0.095D, -0.19D, HALF + 0.095D, EDGE);
        box(out, t, -HALF - 0.08D, 0.015D, -HALF - 0.08D,
                HALF + 0.08D, 0.045D, -HALF - 0.052D, 0xFFFFD58D);
        box(out, t, -HALF - 0.08D, 0.015D, HALF + 0.052D,
                HALF + 0.08D, 0.045D, HALF + 0.08D, 0xFFFFD58D);

        for (int z = 0; z < SIDE; z++) {
            for (int x = 0; x < SIDE; x++) {
                int cell = z * SIDE + x;
                double x0 = -HALF + x * CELL + 0.006D;
                double z0 = -HALF + z * CELL + 0.006D;
                double x1 = x0 + CELL - 0.012D;
                double z1 = z0 + CELL - 0.012D;
                boolean open = revealed[cell];
                int base = open ? (((x + z) & 1) == 0 ? OPEN_A : OPEN_B)
                        : (((x + z) & 1) == 0 ? HIDDEN_A : HIDDEN_B);
                if (cell == hovered) base = HOVER;
                box(out, t, x0, 0.045D, z0, x1, open ? 0.066D : 0.12D, z1, base);

                if (flagged[cell] && !open) renderFlag(out, t, (x0 + x1) * 0.5D, (z0 + z1) * 0.5D);
                if (open && mines[cell]) renderMine(out, t, (x0 + x1) * 0.5D, (z0 + z1) * 0.5D,
                        cell == exploded);
                else if (open && adjacent[cell] > 0) renderNumber(out, t, (x0 + x1) * 0.5D,
                        (z0 + z1) * 0.5D, adjacent[cell]);
            }
        }
    }

    private static void renderFlag(VertexConsumer out, BoardTransform t, double x, double z) {
        box(out, t, x - 0.018D, 0.12D, z - 0.018D, x + 0.018D, 0.34D, z + 0.018D, 0xFF343846);
        box(out, t, x + 0.012D, 0.27D, z - 0.13D, x + 0.16D, 0.34D, z + 0.015D, FLAG_RED);
        box(out, t, x - 0.075D, 0.115D, z - 0.075D, x + 0.075D, 0.145D, z + 0.075D, 0xFF343846);
    }

    private static void renderMine(VertexConsumer out, BoardTransform t, double x, double z, boolean hot) {
        int color = hot ? 0xFFFF553D : MINE_BLACK;
        sphere(out, t, x, 0.19D, z, 0.105D, color, 12, 8);
        box(out, t, x - 0.02D, 0.27D, z - 0.02D, x + 0.02D, 0.34D, z + 0.02D, 0xFFB8C1CC);
        box(out, t, x - 0.16D, 0.18D, z - 0.018D, x + 0.16D, 0.21D, z + 0.018D, 0xFF777D8B);
        box(out, t, x - 0.018D, 0.18D, z - 0.16D, x + 0.018D, 0.21D, z + 0.16D, 0xFF777D8B);
    }

    private static void renderNumber(VertexConsumer out, BoardTransform t, double x, double z, int number) {
        String[] segments = {"abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgcde", "abc", "abcdefg"};
        String pattern = segments[number];
        int[] colors = {0, 0xFF3576D5, 0xFF29944D, 0xFFD84B43, 0xFF7047B7,
                0xFF9A512C, 0xFF259CA5, 0xFF343A44, 0xFF7E4B62};
        int color = colors[number];
        double y0 = 0.145D, y1 = 0.17D;
        if (pattern.contains("a")) box(out,t,x-0.105D,y0,z-0.125D,x+0.105D,y1,z-0.095D,color);
        if (pattern.contains("g")) box(out,t,x-0.105D,y0,z-0.015D,x+0.105D,y1,z+0.015D,color);
        if (pattern.contains("d")) box(out,t,x-0.105D,y0,z+0.095D,x+0.105D,y1,z+0.125D,color);
        if (pattern.contains("f")) box(out,t,x-0.125D,y0,z-0.105D,x-0.095D,y1,z-0.015D,color);
        if (pattern.contains("e")) box(out,t,x-0.125D,y0,z+0.015D,x-0.095D,y1,z+0.105D,color);
        if (pattern.contains("b")) box(out,t,x+0.095D,y0,z-0.105D,x+0.125D,y1,z-0.015D,color);
        if (pattern.contains("c")) box(out,t,x+0.095D,y0,z+0.015D,x+0.125D,y1,z+0.105D,color);
    }

    private static void portalParticles(Minecraft client, int amount) {
        if (client.level == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 portal = center.add(forward.scale(0.20D)).add(0.0D, 0.78D, 0.0D);
        for (int i = 0; i < amount; i++) {
            double a = random.nextDouble(Math.PI * 2.0D);
            double radius = 1.0D + random.nextDouble(-0.12D, 0.12D);
            double x = portal.x + right.x * Math.cos(a) * radius;
            double z = portal.z + right.z * Math.cos(a) * radius;
            double y = portal.y + Math.sin(a) * radius;
            client.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    random.nextDouble(-0.025D, 0.025D), random.nextDouble(-0.02D, 0.05D),
                    random.nextDouble(-0.025D, 0.025D));
        }
    }

    private static void actionBar(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(message), true);
        }
    }

    private static Vec3 cellCenter(int cell, double height) {
        double x = -HALF + ((cell % SIDE) + 0.5D) * CELL;
        double z = -HALF + ((cell / SIDE) + 0.5D) * CELL;
        return center.add(right.scale(x)).add(forward.scale(z)).add(0.0D, height, 0.0D);
    }

    private static double introLift() {
        return (1.0D - smooth(introTick / (float) INTRO_TICKS)) * -0.78D;
    }

    private static float smooth(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static void renderPortal(VertexConsumer lines, BoardTransform t, Vec3 camera, float progress) {
        if (introTick >= INTRO_TICKS) return;
        double radius = 1.12D * (1.0D + Math.sin(introTick * 0.20D) * 0.07D);
        double y = 0.70D + (1.0D - progress) * 0.08D;
        for (int i = 0; i < 48; i++) {
            double a0 = Math.PI * 2.0D * i / 48.0D + introTick * 0.018D;
            double a1 = Math.PI * 2.0D * (i + 1) / 48.0D + introTick * 0.018D;
            Vec3 p0 = t.world(Math.cos(a0) * radius, y + Math.sin(a0) * radius, 0.28D);
            Vec3 p1 = t.world(Math.cos(a1) * radius, y + Math.sin(a1) * radius, 0.28D);
            emitLine(lines, p0, p1, (i % 4 == 0) ? 0xFFFFD58D : 0xFF62DCEB, 2.4F);
        }
    }

    private static void box(VertexConsumer out, BoardTransform t, double x0, double y0, double z0,
                            double x1, double y1, double z1, int color) {
        quad(out,t,color, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        quad(out,t,color, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        quad(out,t,color, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        quad(out,t,color, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(out,t,color, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(out,t,color, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
    }

    private static void sphere(VertexConsumer out, BoardTransform t, double x, double y, double z,
                               double radius, int color, int longitude, int latitude) {
        for (int lat = 0; lat < latitude; lat++) {
            double p0 = Math.PI * lat / latitude, p1 = Math.PI * (lat + 1) / latitude;
            for (int lon = 0; lon < longitude; lon++) {
                double a0 = Math.PI * 2.0D * lon / longitude, a1 = Math.PI * 2.0D * (lon + 1) / longitude;
                double x00=x+radius*Math.sin(p0)*Math.cos(a0), z00=z+radius*Math.sin(p0)*Math.sin(a0), y00=y+radius*Math.cos(p0);
                double x01=x+radius*Math.sin(p0)*Math.cos(a1), z01=z+radius*Math.sin(p0)*Math.sin(a1), y01=y+radius*Math.cos(p0);
                double x10=x+radius*Math.sin(p1)*Math.cos(a0), z10=z+radius*Math.sin(p1)*Math.sin(a0), y10=y+radius*Math.cos(p1);
                double x11=x+radius*Math.sin(p1)*Math.cos(a1), z11=z+radius*Math.sin(p1)*Math.sin(a1), y11=y+radius*Math.cos(p1);
                quad(out,t,color,x00,y00,z00,x10,y10,z10,x11,y11,z11,x01,y01,z01);
            }
        }
    }

    private static void quad(VertexConsumer out, BoardTransform t, int color,
                             double ax,double ay,double az,double bx,double by,double bz,
                             double cx,double cy,double cz,double dx,double dy,double dz) {
        emitVertex(out,t.world(ax,ay,az),color);
        emitVertex(out,t.world(bx,by,bz),color);
        emitVertex(out,t.world(cx,cy,cz),color);
        emitVertex(out,t.world(dx,dy,dz),color);
    }

    private static void emitVertex(VertexConsumer out, Vec3 point, int color) {
        out.addVertex((float) point.x,(float) point.y,(float) point.z).setColor(color);
    }

    private static void emitLine(VertexConsumer out, Vec3 a, Vec3 b, int color, float width) {
        out.addVertex((float)a.x,(float)a.y,(float)a.z).setColor(color)
                .setNormal(0,1,0).setLineWidth(width);
        out.addVertex((float)b.x,(float)b.y,(float)b.z).setColor(color)
                .setNormal(0,1,0).setLineWidth(width);
    }

    private static final class BoardTransform {
        private final Vec3 center;
        private final Vec3 right;
        private final Vec3 forward;
        private final float scale;
        private final double lift;
        private BoardTransform(Vec3 center, Vec3 right, Vec3 forward, float scale, double lift) {
            this.center=center; this.right=right; this.forward=forward; this.scale=scale; this.lift=lift;
        }
        private Vec3 world(double x,double y,double z) {
            return center.add(right.scale(x*scale)).add(0.0D,y*scale+lift,0.0D).add(forward.scale(z*scale));
        }
    }
}
