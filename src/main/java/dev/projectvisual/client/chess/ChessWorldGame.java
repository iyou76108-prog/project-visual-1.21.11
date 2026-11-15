package dev.projectvisual.client.chess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.projectvisual.client.ProjectVisualClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side, world-space chess table. The board is anchored in the world when summoned;
 * all geometry is emitted into the normal Fabric world render pass.
 */
public final class ChessWorldGame {
    private static final float CELL = 0.48F;
    private static final float BOARD_SIZE = CELL * 8.0F;
    private static final float HALF = BOARD_SIZE * 0.5F;
    private static final int INTRO_TICKS = 54;
    private static final int MOVE_TICKS = 15;

    private static final int WOOD = 0xFF302333;
    private static final int WOOD_EDGE = 0xFF9A7048;
    private static final int LIGHT_TILE = 0xFFE8D9BD;
    private static final int DARK_TILE = 0xFF51434A;
    private static final int WHITE_BODY = 0xFFEDE7D9;
    private static final int WHITE_HI = 0xFFFFF8E8;
    private static final int WHITE_SHADE = 0xFFB6A98F;
    private static final int GOLD = 0xFFD8B66A;
    private static final int BLACK_BODY = 0xFF222638;
    private static final int BLACK_HI = 0xFF69738B;
    private static final int BLACK_SHADE = 0xFF111522;
    private static final int CYAN = 0xFF63D9E8;
    private static final int SELECTED = 0xFF76E5D5;
    private static final int TARGET = 0xFF82CFA6;

    // DEBUG_QUADS is translucent and deliberately does not write to depth.
    // That is suitable for debug overlays, but it causes the rear faces of
    // closed 3D meshes to blend over their front faces. Use an opaque,
    // depth-writing pipeline for the chess board and pieces instead.
    private static final RenderPipeline CHESS_SOLID_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(
                            ProjectVisualClient.MOD_ID, "pipeline/chess_solid"))
                    .withoutBlend()
                    .withDepthWrite(true)
                    .withCull(false)
                    .build();

    private static final RenderType CHESS_SOLID_RENDER_TYPE = RenderType.create(
            "project_visual_chess_solid",
            RenderSetup.builder(CHESS_SOLID_PIPELINE).build());

    private static final ChessEngine GAME = new ChessEngine();

    private static boolean registered;
    private static boolean active;
    private static boolean rightWasDown;
    private static boolean aiPending;
    private static int aiDelay;
    private static int introTick;
    private static int selectedSquare = -1;
    private static String status = "Белые ходят";
    private static Vec3 boardCenter = Vec3.ZERO;
    private static Vec3 right = new Vec3(1, 0, 0);
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static double boardY;

    private static KeyMapping exitKey;
    private static KeyMapping restartKey;
    private static MoveAnimation moveAnimation;

    private ChessWorldGame() {}

    public static void register() {
        if (registered) return;
        registered = true;

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "chess"));
        exitKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.chess_exit", GLFW.GLFW_KEY_BACKSPACE, category));
        restartKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.chess_restart", GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(ChessWorldGame::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ChessWorldGame::render);
    }

    /** Starts a fresh match and summons the board a few blocks in front of the player. */
    public static void start() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        GAME.reset();
        selectedSquare = -1;
        status = "Белые ходят";
        aiPending = false;
        aiDelay = 0;
        moveAnimation = null;
        introTick = 0;
        rightWasDown = false;
        active = true;

        double yaw = Math.toRadians(client.player.getYRot());
        forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        boardY = client.player.getEyeY() - 0.84D;
        Vec3 anchor = client.player.position().add(forward.scale(3.75D));
        boardCenter = new Vec3(anchor.x, boardY, anchor.z);

        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§bPROJECT VISUAL CHESS §7— доска появляется из портала. §fПКМ: выбрать и переместить фигуру."),
                false);
        spawnPortalParticles(client, 16);
    }

    public static boolean isActive() {
        return active;
    }

    public static void stop() {
        active = false;
        selectedSquare = -1;
        aiPending = false;
        moveAnimation = null;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Шахматная доска закрыта."),
                    false);
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
        if (introTick < INTRO_TICKS && (introTick % 3) == 0) spawnPortalParticles(client, 3);

        if (moveAnimation != null) {
            moveAnimation.ticks++;
            if (moveAnimation.ticks >= MOVE_TICKS) moveAnimation = null;
        }

        if (client.screen != null) {
            rightWasDown = false;
            return;
        }

        boolean rightDown = client.mouseHandler.isRightPressed();
        if (rightDown && !rightWasDown && introTick >= INTRO_TICKS && !aiPending) {
            handleBoardClick(client);
        }
        rightWasDown = rightDown;

        if (aiPending) {
            aiDelay--;
            if (aiDelay <= 0) performAiMove(client);
        }
    }

    private static void handleBoardClick(Minecraft client) {
        int square = rayPickSquare(client);
        if (square < 0) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Наведи прицел на клетку доски и нажми ПКМ."),
                    true);
            return;
        }

        char piece = GAME.pieceAt(square);
        if (selectedSquare < 0) {
            if (piece != 0 && Character.isUpperCase(piece) && GAME.whiteToMove()) {
                selectedSquare = square;
                status = "Выбрана " + pieceName(piece) + " — укажи клетку назначения";
                actionBar(client, "§bВыбрана " + pieceName(piece) + " §7— ПКМ по клетке назначения");
            } else {
                actionBar(client, "§7Выбери белую фигуру. Сейчас ход белых.");
            }
            return;
        }

        if (square == selectedSquare) {
            selectedSquare = -1;
            actionBar(client, "§7Выбор отменён");
            return;
        }

        if (piece != 0 && Character.isUpperCase(piece)) {
            selectedSquare = square;
            actionBar(client, "§bВыбрана " + pieceName(piece) + " §7— выбери клетку");
            return;
        }

        int from = selectedSquare;
        char captured = GAME.pieceAt(square);
        char moving = GAME.pieceAt(from);
        if (GAME.play(from, square)) {
            selectedSquare = -1;
            moveAnimation = new MoveAnimation(from, square, moving, 0);
            status = "Чёрные думают…";
            if (captured != 0) spawnCaptureParticles(client, square);
            if (GAME.result() != ChessEngine.Result.ONGOING) {
                finishGame(client);
            } else {
                aiPending = true;
                aiDelay = 9;
                actionBar(client, "§7Ход чёрных — ИИ обдумывает ответ");
            }
        } else {
            actionBar(client, "§cТак ходить нельзя");
        }
    }

    private static int rayPickSquare(Minecraft client) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getLookAngle();
        double bob = Math.sin((client.level.getGameTime() % 100000L) * 0.055D) * 0.018D * easeOut(introProgress());
        double planeY = boardY + 0.035D + introLift() + bob;
        if (look.y >= -0.015D) return -1;
        double distance = (planeY - eye.y) / look.y;
        if (distance <= 0.0D || distance > 16.0D) return -1;

        Vec3 hit = eye.add(look.scale(distance));
        Vec3 relative = hit.subtract(boardCenter);
        double localX = relative.dot(right);
        double localZ = relative.dot(forward);
        if (Math.abs(localX) > HALF || Math.abs(localZ) > HALF) return -1;

        int file = Math.min(7, Math.max(0, (int) Math.floor((localX + HALF) / CELL)));
        int rank = Math.min(7, Math.max(0, (int) Math.floor((localZ + HALF) / CELL)));
        return rank * 8 + file;
    }

    private static void performAiMove(Minecraft client) {
        aiPending = false;
        ChessEngine.Move move = GAME.chooseAiMove(3);
        if (move == null) {
            finishGame(client);
            return;
        }
        char moving = GAME.pieceAt(move.from());
        char captured = GAME.pieceAt(move.to());
        if (GAME.play(move)) {
            moveAnimation = new MoveAnimation(move.from(), move.to(), moving, 0);
            if (captured != 0) spawnCaptureParticles(client, move.to());
            if (GAME.result() != ChessEngine.Result.ONGOING) {
                finishGame(client);
                return;
            }
            status = GAME.inCheck(true) ? "Шах белому королю!" : "Белые ходят";
            actionBar(client, GAME.inCheck(true)
                    ? "§cШАХ! §7Твой ход — защити короля"
                    : "§fТвой ход — выбирай фигуру ПКМ");
        } else {
            status = "Ошибка хода ИИ";
            actionBar(client, "§cНе удалось применить ход ИИ. Нажми R для перезапуска.");
        }
    }

    private static void finishGame(Minecraft client) {
        aiPending = false;
        ChessEngine.Result result = GAME.result();
        if (result == ChessEngine.Result.CHECKMATE) {
            boolean whiteWon = !GAME.whiteToMove();
            status = whiteWon ? "Мат — победа белых!" : "Мат — победа чёрных";
            actionBar(client, whiteWon ? "§aПОБЕДА! §fТы поставил мат." : "§cМАТ. §fИИ победил.");
            if (client.level != null) {
                for (int i = 0; i < 28; i++) {
                    double a = Math.PI * 2.0D * i / 28.0D;
                    client.level.addParticle(ParticleTypes.FIREWORK,
                            boardCenter.x + Math.cos(a) * 1.5D,
                            boardY + 0.5D + (i % 4) * 0.16D,
                            boardCenter.z + Math.sin(a) * 1.5D,
                            Math.cos(a) * 0.06D, 0.10D, Math.sin(a) * 0.06D);
                }
            }
        } else if (result == ChessEngine.Result.STALEMATE || result == ChessEngine.Result.DRAW) {
            status = "Ничья";
            actionBar(client, "§eНИЧЬЯ");
        }
    }

    private static void actionBar(Minecraft client, String text) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(text), true);
        }
    }

    private static String pieceName(char piece) {
        return switch (Character.toUpperCase(piece)) {
            case 'K' -> "король";
            case 'Q' -> "ферзь";
            case 'R' -> "ладья";
            case 'B' -> "слон";
            case 'N' -> "конь";
            default -> "пешка";
        };
    }

    private static void spawnPortalParticles(Minecraft client, int amount) {
        if (client.level == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 portalCenter = boardCenter.add(forward.scale(0.48D)).add(0.0D, 0.72D, 0.0D);
        for (int i = 0; i < amount; i++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double radius = 1.20D + random.nextDouble(-0.16D, 0.16D);
            double x = portalCenter.x + right.x * Math.cos(angle) * radius;
            double z = portalCenter.z + right.z * Math.cos(angle) * radius;
            double y = portalCenter.y + Math.sin(angle) * radius;
            client.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    -forward.x * 0.025D + random.nextDouble(-0.025D, 0.025D),
                    random.nextDouble(-0.035D, 0.055D),
                    -forward.z * 0.025D + random.nextDouble(-0.025D, 0.025D));
            if ((i % 3) == 0) {
                client.level.addParticle(ParticleTypes.END_ROD, x, y, z,
                        random.nextDouble(-0.012D, 0.012D), 0.015D,
                        random.nextDouble(-0.012D, 0.012D));
            }
        }
    }

    private static void spawnCaptureParticles(Minecraft client, int square) {
        if (client.level == null) return;
        Vec3 point = squareCenter(square, 0.28D);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2.0D * i / 10.0D;
            client.level.addParticle(ParticleTypes.CRIT, point.x, point.y, point.z,
                    Math.cos(a) * 0.10D, 0.06D + (i % 3) * 0.02D, Math.sin(a) * 0.10D);
        }
        client.level.addParticle(ParticleTypes.POOF, point.x, point.y, point.z, 0, 0.04D, 0);
    }

    private static Vec3 squareCenter(int square, double height) {
        int file = square % 8;
        int rank = square / 8;
        double x = -HALF + (file + 0.5D) * CELL;
        double z = -HALF + (rank + 0.5D) * CELL;
        return boardCenter.add(right.scale(x)).add(forward.scale(z)).add(0.0D, height, 0.0D);
    }

    private static float introProgress() {
        return Math.min(1.0F, introTick / (float) INTRO_TICKS);
    }

    private static float easeOut(float value) {
        float inv = 1.0F - value;
        return 1.0F - inv * inv * inv;
    }

    private static double introLift() {
        return (1.0D - easeOut(introProgress())) * -0.85D;
    }

    private static void render(WorldRenderContext context) {
        if (!active) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        float progress = easeOut(introProgress());
        float scale = 0.12F + 0.88F * progress;
        double lift = introLift() + Math.sin((client.level.getGameTime() % 100000L) * 0.055D) * 0.018D * progress;
        Vec3 renderOrigin = new Vec3(boardCenter.x - camera.x, boardCenter.y - camera.y, boardCenter.z - camera.z);
        BoardTransform transform = new BoardTransform(renderOrigin, right, forward, scale, lift);

        // MultiBufferSource buffers are only valid until getBuffer() is called
        // for another render type. Finish all quads before switching to lines.
        VertexConsumer quads = context.consumers().getBuffer(CHESS_SOLID_RENDER_TYPE);
        renderBoard(quads, transform);
        renderPieces(quads, transform, camera);

        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        renderPortal(lines, transform, camera, progress);
        renderHighlights(lines, transform, camera);
    }

    private static void renderPortal(VertexConsumer lines, BoardTransform transform,
                                     Vec3 camera, float progress) {
        if (introTick >= INTRO_TICKS) return;
        double pulse = 1.0D + Math.sin(introTick * 0.22D) * 0.08D;
        double radius = 1.34D * pulse;
        double centerY = 0.76D + (1.0D - progress) * 0.08D;
        double centerZ = 0.58D;
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments + introTick * 0.018D;
            double a1 = Math.PI * 2.0D * (i + 1) / segments + introTick * 0.018D;
            Vec3 p0 = transform.world(Math.cos(a0) * radius, centerY + Math.sin(a0) * radius, centerZ);
            Vec3 p1 = transform.world(Math.cos(a1) * radius, centerY + Math.sin(a1) * radius, centerZ);
            int color = (i % 4 == 0) ? 0xFFFFD68A : 0xFF59D7F0;
            emitLine(lines, p0, p1, camera, color, 2.6F);
            if ((i % 2) == 0) {
                double r2 = radius * 0.86D;
                Vec3 q0 = transform.world(Math.cos(a0 + 0.12D) * r2,
                        centerY + Math.sin(a0 + 0.12D) * r2, centerZ + 0.008D);
                Vec3 q1 = transform.world(Math.cos(a1 + 0.12D) * r2,
                        centerY + Math.sin(a1 + 0.12D) * r2, centerZ + 0.008D);
                emitLine(lines, q0, q1, camera, 0xFF8A8AFB, 1.6F);
            }
        }
    }

    private static void renderBoard(VertexConsumer out, BoardTransform t) {
        box(out, t, -HALF - 0.095F, -0.16F, -HALF - 0.095F,
                HALF + 0.095F, 0.005F, HALF + 0.095F, WOOD);
        box(out, t, -HALF - 0.08F, -0.19F, -HALF - 0.08F,
                HALF + 0.08F, -0.15F, HALF + 0.08F, WOOD_EDGE);

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                float x0 = -HALF + file * CELL + 0.003F;
                float z0 = -HALF + rank * CELL + 0.003F;
                int color = ((file + rank) & 1) == 0 ? LIGHT_TILE : DARK_TILE;
                box(out, t, x0, 0.004F, z0, x0 + CELL - 0.006F, 0.042F,
                        z0 + CELL - 0.006F, color);
            }
        }

        // Thin luminous trim around the floating table.
        box(out, t, -HALF - 0.09F, 0.006F, -HALF - 0.09F,
                HALF + 0.09F, 0.025F, -HALF - 0.055F, GOLD);
        box(out, t, -HALF - 0.09F, 0.006F, HALF + 0.055F,
                HALF + 0.09F, 0.025F, HALF + 0.09F, GOLD);
        box(out, t, -HALF - 0.09F, 0.006F, -HALF - 0.055F,
                -HALF - 0.055F, 0.025F, HALF + 0.055F, GOLD);
        box(out, t, HALF + 0.055F, 0.006F, -HALF - 0.055F,
                HALF + 0.09F, 0.025F, HALF + 0.055F, GOLD);
    }

    private static void renderHighlights(VertexConsumer lines, BoardTransform t, Vec3 camera) {
        if (selectedSquare < 0) return;
        drawSquareOutline(lines, t, camera, selectedSquare, SELECTED, 3.5F);
        for (ChessEngine.Move move : GAME.legalMoves()) {
            if (move.from() == selectedSquare) {
                drawSquareOutline(lines, t, camera, move.to(), TARGET, 2.2F);
            }
        }
    }

    private static void drawSquareOutline(VertexConsumer lines, BoardTransform t, Vec3 camera,
                                          int square, int color, float width) {
        int file = square % 8;
        int rank = square / 8;
        double x0 = -HALF + file * CELL + 0.015D;
        double z0 = -HALF + rank * CELL + 0.015D;
        double x1 = x0 + CELL - 0.03D;
        double z1 = z0 + CELL - 0.03D;
        double y = 0.055D;
        emitLine(lines, t.world(x0, y, z0), t.world(x1, y, z0), camera, color, width);
        emitLine(lines, t.world(x1, y, z0), t.world(x1, y, z1), camera, color, width);
        emitLine(lines, t.world(x1, y, z1), t.world(x0, y, z1), camera, color, width);
        emitLine(lines, t.world(x0, y, z1), t.world(x0, y, z0), camera, color, width);
    }

    private static void renderPieces(VertexConsumer out, BoardTransform t, Vec3 camera) {
        for (int square = 0; square < 64; square++) {
            char piece = GAME.pieceAt(square);
            if (piece == 0) continue;
            if (moveAnimation != null && moveAnimation.to == square) continue;
            float x = -HALF + ((square % 8) + 0.5F) * CELL;
            float z = -HALF + ((square / 8) + 0.5F) * CELL;
            renderPiece(out, t, x, z, piece, 0.0F);
        }

        if (moveAnimation != null) {
            float progress = Math.min(1.0F, moveAnimation.ticks / (float) MOVE_TICKS);
            float fromX = -HALF + ((moveAnimation.from % 8) + 0.5F) * CELL;
            float fromZ = -HALF + ((moveAnimation.from / 8) + 0.5F) * CELL;
            float toX = -HALF + ((moveAnimation.to % 8) + 0.5F) * CELL;
            float toZ = -HALF + ((moveAnimation.to / 8) + 0.5F) * CELL;
            float eased = progress * progress * (3.0F - 2.0F * progress);
            float x = fromX + (toX - fromX) * eased;
            float z = fromZ + (toZ - fromZ) * eased;
            float arc = (float) Math.sin(progress * Math.PI) * 0.24F;
            renderPiece(out, t, x, z, moveAnimation.piece, arc);
        }
    }

    /** Creates a recognizable, fully volumetric chess piece out of small shaded mesh primitives. */
    private static void renderPiece(VertexConsumer out, BoardTransform t,
                                    float x, float z, char piece, float animationLift) {
        boolean white = Character.isUpperCase(piece);
        int body = white ? WHITE_BODY : BLACK_BODY;
        int highlight = white ? WHITE_HI : BLACK_HI;
        int shade = white ? WHITE_SHADE : BLACK_SHADE;
        int trim = white ? GOLD : CYAN;
        float y = 0.045F + animationLift;

        cylinder(out, t, x, z, y, y + 0.045F, 0.155F, shade, 16);
        cylinder(out, t, x, z, y + 0.045F, y + 0.085F, 0.135F, trim, 16);
        cylinder(out, t, x, z, y + 0.085F, y + 0.115F, 0.115F, body, 16);

        switch (Character.toUpperCase(piece)) {
            case 'P' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.275F, 0.071F, body, 12);
                sphere(out, t, x, y + 0.315F, z, 0.105F, highlight, 12, 7);
                sphere(out, t, x, y + 0.315F, z, 0.047F, trim, 10, 5);
            }
            case 'N' -> {
                int facing = white ? 1 : -1;
                cylinder(out, t, x, z, y + 0.105F, y + 0.215F, 0.082F, body, 12);
                cylinder(out, t, x, z + facing * 0.018F, y + 0.18F, y + 0.30F,
                        0.078F, highlight, 12);
                sphere(out, t, x, y + 0.285F, z + facing * 0.038F,
                        0.098F, body, 12, 7);
                box(out, t, x - 0.062F, y + 0.255F, z + facing * 0.085F - 0.055F,
                        x + 0.062F, y + 0.315F, z + facing * 0.085F + 0.055F, highlight);
                box(out, t, x - 0.023F, y + 0.365F, z + facing * 0.015F - 0.025F,
                        x + 0.023F, y + 0.445F, z + facing * 0.015F + 0.025F, trim);
                sphere(out, t, x + 0.038F, y + 0.305F, z + facing * 0.075F,
                        0.018F, trim, 8, 5);
            }
            case 'B' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.32F, 0.066F, body, 12);
                sphere(out, t, x, y + 0.355F, z, 0.095F, highlight, 12, 7);
                cone(out, t, x, z, y + 0.39F, y + 0.49F, 0.052F, trim, 12);
                box(out, t, x - 0.018F, y + 0.31F, z - 0.10F,
                        x + 0.018F, y + 0.39F, z + 0.10F, shade);
            }
            case 'R' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.315F, 0.105F, body, 12);
                cylinder(out, t, x, z, y + 0.29F, y + 0.355F, 0.14F, highlight, 12);
                box(out, t, x - 0.135F, y + 0.35F, z - 0.135F,
                        x - 0.055F, y + 0.425F, z - 0.055F, trim);
                box(out, t, x + 0.055F, y + 0.35F, z - 0.135F,
                        x + 0.135F, y + 0.425F, z - 0.055F, trim);
                box(out, t, x - 0.135F, y + 0.35F, z + 0.055F,
                        x - 0.055F, y + 0.425F, z + 0.135F, trim);
                box(out, t, x + 0.055F, y + 0.35F, z + 0.055F,
                        x + 0.135F, y + 0.425F, z + 0.135F, trim);
            }
            case 'Q' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.37F, 0.064F, body, 12);
                cylinder(out, t, x, z, y + 0.34F, y + 0.39F, 0.145F, trim, 16);
                sphere(out, t, x, y + 0.435F, z, 0.068F, highlight, 10, 6);
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * 2.0D * i / 5.0D;
                    float px = x + (float) Math.cos(a) * 0.112F;
                    float pz = z + (float) Math.sin(a) * 0.112F;
                    sphere(out, t, px, y + 0.425F, pz, 0.043F, highlight, 8, 5);
                    cone(out, t, px, pz, y + 0.445F, y + 0.515F, 0.026F, trim, 7);
                }
            }
            case 'K' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.405F, 0.071F, body, 12);
                cylinder(out, t, x, z, y + 0.37F, y + 0.425F, 0.13F, trim, 16);
                sphere(out, t, x, y + 0.44F, z, 0.071F, highlight, 10, 6);
                box(out, t, x - 0.027F, y + 0.47F, z - 0.025F,
                        x + 0.027F, y + 0.61F, z + 0.025F, highlight);
                box(out, t, x - 0.085F, y + 0.515F, z - 0.025F,
                        x + 0.085F, y + 0.565F, z + 0.025F, trim);
            }
            default -> { }
        }
    }

    private static void box(VertexConsumer out, BoardTransform t, double x0, double y0, double z0,
                            double x1, double y1, double z1, int color) {
        // Top and bottom.
        quad(out, t, color, 0, 1, 0,
                x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        quad(out, t, color, 0,-1, 0,
                x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        // Four vertical faces.
        quad(out, t, color, 0,0,-1,
                x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        quad(out, t, color, 0,0,1,
                x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(out, t, color, -1,0,0,
                x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(out, t, color, 1,0,0,
                x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
    }

    private static void cylinder(VertexConsumer out, BoardTransform t, double x, double z,
                                 double y0, double y1, double radius, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = x + Math.cos(a0) * radius;
            double z0 = z + Math.sin(a0) * radius;
            double x1 = x + Math.cos(a1) * radius;
            double z1 = z + Math.sin(a1) * radius;
            double mid = (a0 + a1) * 0.5D;
            quad(out, t, color, Math.cos(mid),0,Math.sin(mid),
                    x0,y0,z0, x0,y1,z0, x1,y1,z1, x1,y0,z1);
            triangle(out, t, color, 0,1,0, x,y1,z, x1,y1,z1, x0,y1,z0);
            triangle(out, t, color, 0,-1,0, x,y0,z, x0,y0,z0, x1,y0,z1);
        }
    }

    private static void cone(VertexConsumer out, BoardTransform t, double x, double z,
                             double y0, double y1, double radius, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = x + Math.cos(a0) * radius;
            double z0 = z + Math.sin(a0) * radius;
            double x1 = x + Math.cos(a1) * radius;
            double z1 = z + Math.sin(a1) * radius;
            double mid = (a0 + a1) * 0.5D;
            quad(out, t, color, Math.cos(mid), radius / Math.max(0.001D, y1-y0), Math.sin(mid),
                    x0,y0,z0, x,y1,z, x,y1,z, x1,y0,z1);
            triangle(out, t, color, 0,-1,0, x,y0,z, x1,y0,z1, x0,y0,z0);
        }
    }

    private static void sphere(VertexConsumer out, BoardTransform t, double x, double y, double z,
                               double radius, int color, int longitude, int latitude) {
        for (int lat = 0; lat < latitude; lat++) {
            double p0 = Math.PI * lat / latitude;
            double p1 = Math.PI * (lat + 1) / latitude;
            for (int lon = 0; lon < longitude; lon++) {
                double a0 = Math.PI * 2.0D * lon / longitude;
                double a1 = Math.PI * 2.0D * (lon + 1) / longitude;
                double x00 = x + radius * Math.sin(p0) * Math.cos(a0);
                double z00 = z + radius * Math.sin(p0) * Math.sin(a0);
                double y00 = y + radius * Math.cos(p0);
                double x01 = x + radius * Math.sin(p0) * Math.cos(a1);
                double z01 = z + radius * Math.sin(p0) * Math.sin(a1);
                double y01 = y + radius * Math.cos(p0);
                double x10 = x + radius * Math.sin(p1) * Math.cos(a0);
                double z10 = z + radius * Math.sin(p1) * Math.sin(a0);
                double y10 = y + radius * Math.cos(p1);
                double x11 = x + radius * Math.sin(p1) * Math.cos(a1);
                double z11 = z + radius * Math.sin(p1) * Math.sin(a1);
                double y11 = y + radius * Math.cos(p1);
                double nx = Math.sin((p0+p1)*0.5D) * Math.cos((a0+a1)*0.5D);
                double ny = Math.cos((p0+p1)*0.5D);
                double nz = Math.sin((p0+p1)*0.5D) * Math.sin((a0+a1)*0.5D);
                quad(out, t, color, nx,ny,nz,
                        x00,y00,z00, x10,y10,z10, x11,y11,z11, x01,y01,z01);
            }
        }
    }

    private static void triangle(VertexConsumer out, BoardTransform t, int color,
                                 double nx, double ny, double nz,
                                 double ax, double ay, double az,
                                 double bx, double by, double bz,
                                 double cx, double cy, double cz) {
        quad(out, t, color, nx,ny,nz, ax,ay,az, bx,by,bz, cx,cy,cz, cx,cy,cz);
    }

    private static void quad(VertexConsumer out, BoardTransform t, int color,
                             double nx, double ny, double nz,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        Vec3 normal = t.normal(nx,ny,nz);
        emitVertex(out, t.world(ax,ay,az), color, normal);
        emitVertex(out, t.world(bx,by,bz), color, normal);
        emitVertex(out, t.world(cx,cy,cz), color, normal);
        emitVertex(out, t.world(dx,dy,dz), color, normal);
    }

    private static void emitVertex(VertexConsumer out, Vec3 point, int color, Vec3 normal) {
        // The chess solid pipeline uses POSITION_COLOR (unlit geometry),
        // so no normal attribute is part of this vertex format.
        out.addVertex((float) point.x, (float) point.y, (float) point.z)
                .setColor(color);
    }

    private static void emitLine(VertexConsumer out, Vec3 a, Vec3 b,
                                 Vec3 camera, int color, float width) {
        out.addVertex((float) a.x, (float) a.y, (float) a.z)
                .setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(width);
        out.addVertex((float) b.x, (float) b.y, (float) b.z)
                .setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(width);
    }

    private static final class BoardTransform {
        private final Vec3 center;
        private final Vec3 right;
        private final Vec3 forward;
        private final float scale;
        private final double lift;

        private BoardTransform(Vec3 center, Vec3 right, Vec3 forward, float scale, double lift) {
            this.center = center;
            this.right = right;
            this.forward = forward;
            this.scale = scale;
            this.lift = lift;
        }

        private Vec3 world(double x, double y, double z) {
            return center.add(right.scale(x * scale))
                    .add(0.0D, y * scale + lift, 0.0D)
                    .add(forward.scale(z * scale));
        }

        private Vec3 normal(double x, double y, double z) {
            return right.scale(x).add(0.0D, y, 0.0D).add(forward.scale(z)).normalize();
        }
    }

    private static final class MoveAnimation {
        private final int from;
        private final int to;
        private final char piece;
        private int ticks;

        private MoveAnimation(int from, int to, char piece, int ticks) {
            this.from = from;
            this.to = to;
            this.piece = piece;
            this.ticks = ticks;
        }
    }
}
